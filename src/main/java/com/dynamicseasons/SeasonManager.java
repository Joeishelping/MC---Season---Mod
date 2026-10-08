package com.dynamicseasons;

import com.dynamicseasons.network.SeasonSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.ServerWorldProperties;
import org.jetbrains.annotations.Nullable;

/** Server-side season bookkeeping: advancing the clock, saving it, syncing it, announcing it. */
public final class SeasonManager {
    private static final int SYNC_INTERVAL_TICKS = 40;

    @Nullable
    private static SeasonData data;
    private static long lastTimeOfDay = Long.MIN_VALUE;
    @Nullable
    private static Season lastSeason;
    private static int syncTimer;

    private SeasonManager() {
    }

    public static void onServerStarted(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        data = overworld.getPersistentStateManager().getOrCreate(SeasonData.TYPE, DynamicSeasons.MOD_ID);
        int configured = SeasonsConfig.get().seasonLengthDays;
        if (data.seasonLengthDays <= 0) {
            data.seasonLengthDays = configured;
            data.markDirty();
        } else if (data.seasonLengthDays != configured) {
            // Season length was changed while this world was closed: keep the same season & progress.
            applyLength(configured);
        }
        publish();
        lastTimeOfDay = overworld.getTimeOfDay();
        lastSeason = SeasonClock.season();
        syncTimer = 0;
        DynamicSeasons.LOGGER.info("Seasons: {} day {} of {} (year {})",
                lastSeason.id(), SeasonClock.dayOfSeason(), SeasonClock.seasonLengthDays(), SeasonClock.year() + 1);
    }

    public static void onServerStopped(MinecraftServer server) {
        data = null;
        lastTimeOfDay = Long.MIN_VALUE;
        lastSeason = null;
    }

    public static void onOverworldTick(ServerWorld overworld) {
        if (data == null) {
            return;
        }
        // Seasons follow in-game time: normal day/night cycle, sleeping and /time add all move
        // them forward. Setting the time backwards (e.g. /time set day) never rewinds a season.
        long now = overworld.getTimeOfDay();
        long delta = lastTimeOfDay == Long.MIN_VALUE ? 0 : now - lastTimeOfDay;
        lastTimeOfDay = now;
        if (delta > 0) {
            data.elapsedTicks += delta;
            data.markDirty();
            publish();
        }

        Season season = SeasonClock.season();
        if (season != lastSeason) {
            Season previous = lastSeason;
            lastSeason = season;
            onSeasonChanged(overworld.getServer(), previous, season);
            syncTimer = SYNC_INTERVAL_TICKS; // sync right away
        }

        if (++syncTimer >= SYNC_INTERVAL_TICKS) {
            syncTimer = 0;
            syncAll(overworld.getServer());
        }
    }

    private static void publish() {
        if (data != null) {
            SeasonClock.set(data.elapsedTicks, data.seasonLengthDays);
        }
        SeasonClock.setDrySnowLineY(SeasonsConfig.get().drySnowLineY);
    }

    private static void onSeasonChanged(MinecraftServer server, @Nullable Season previous, Season season) {
        if (previous != null && SeasonsConfig.get().announceSeasons) {
            Text message = Text.translatable("message.dynamic_seasons.season_start",
                    Text.translatable(season.translationKey()).formatted(season.color()));
            server.getPlayerManager().broadcast(message, false);
        }
        shortenCarriedOverDrySpell(server);
    }

    /**
     * A dry spell rolled during summer could last days into the rainy spring. When a season
     * starts, cap the remaining natural dry spell to something the new season would roll.
     * Never touches /weather commands (those set the clear-weather timer, which we respect).
     */
    private static void shortenCarriedOverDrySpell(MinecraftServer server) {
        if (!SeasonsConfig.get().seasonalWeather) {
            return;
        }
        ServerWorld overworld = server.getOverworld();
        if (!overworld.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
            return;
        }
        ServerWorldProperties properties = server.getSaveProperties().getMainWorldProperties();
        if (properties.getClearWeatherTime() == 0 && !properties.isRaining()) {
            int cap = Math.max(1, Math.round(ServerWorld.CLEAR_WEATHER_DURATION_PROVIDER.getMax()
                    * SeasonClock.clearDurationMultiplier()));
            if (properties.getRainTime() > cap) {
                properties.setRainTime(overworld.getRandom().nextBetween(1, cap));
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Commands / config
    // ------------------------------------------------------------------------------------------

    public static boolean isRunning() {
        return data != null;
    }

    public static void setSeason(MinecraftServer server, Season season, int day) {
        if (data == null) {
            return;
        }
        data.elapsedTicks = SeasonClock.elapsedFor(season, day, data.seasonLengthDays);
        data.markDirty();
        publish();
        syncAll(server);
    }

    /** Changes the season length while keeping the current season and progress through it. */
    public static void applyLength(int newLength) {
        if (data == null) {
            return;
        }
        int clamped = Math.max(SeasonsConfig.MIN_SEASON_LENGTH, Math.min(SeasonsConfig.MAX_SEASON_LENGTH, newLength));
        if (clamped != data.seasonLengthDays) {
            data.elapsedTicks = SeasonClock.rescale(data.elapsedTicks, data.seasonLengthDays, clamped);
            data.seasonLengthDays = clamped;
            data.markDirty();
        }
        publish();
    }

    /** Re-reads the config (length, snow line) and pushes it to everyone. */
    public static void onConfigChanged(MinecraftServer server) {
        applyLength(SeasonsConfig.get().seasonLengthDays);
        syncAll(server);
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sync(player);
        }
    }

    public static void sync(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, SeasonSyncPayload.ID)) {
            ServerPlayNetworking.send(player, new SeasonSyncPayload(
                    SeasonClock.elapsedTicks(), SeasonClock.seasonLengthDays(), SeasonClock.drySnowLineY()));
        }
    }
}
