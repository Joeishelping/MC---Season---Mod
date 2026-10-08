package com.dynamicseasons.command;

import com.dynamicseasons.Season;
import com.dynamicseasons.SeasonClock;
import com.dynamicseasons.SeasonManager;
import com.dynamicseasons.SeasonsConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * /season                       - show the current season (anyone)
 * /season set <season> [day]    - jump to a season (ops)
 * /season length <days>         - change how long every season lasts (ops)
 */
public final class SeasonCommand {
    private SeasonCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> set = CommandManager.literal("set")
                .requires(source -> source.hasPermissionLevel(2));
        for (Season season : Season.values()) {
            set.then(CommandManager.literal(season.id())
                    .executes(ctx -> setSeason(ctx.getSource(), season, 1))
                    .then(CommandManager.argument("day", IntegerArgumentType.integer(1, SeasonsConfig.MAX_SEASON_LENGTH))
                            .executes(ctx -> setSeason(ctx.getSource(), season, IntegerArgumentType.getInteger(ctx, "day")))));
        }

        dispatcher.register(CommandManager.literal("season")
                .executes(ctx -> info(ctx.getSource()))
                .then(set)
                .then(CommandManager.literal("length")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.translatable("commands.dynamic_seasons.length.query",
                                    SeasonClock.seasonLengthDays()), false);
                            return SeasonClock.seasonLengthDays();
                        })
                        .then(CommandManager.argument("days",
                                        IntegerArgumentType.integer(SeasonsConfig.MIN_SEASON_LENGTH, SeasonsConfig.MAX_SEASON_LENGTH))
                                .executes(ctx -> setLength(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days"))))));
    }

    private static int info(ServerCommandSource source) {
        Season season = SeasonClock.season();
        source.sendFeedback(() -> Text.translatable("commands.dynamic_seasons.info",
                Text.translatable(season.translationKey()).formatted(season.color()),
                SeasonClock.dayOfSeason(), SeasonClock.seasonLengthDays(), SeasonClock.year() + 1), false);
        return season.ordinal() + 1;
    }

    private static int setSeason(ServerCommandSource source, Season season, int day) {
        int clampedDay = Math.min(day, SeasonClock.seasonLengthDays());
        SeasonManager.setSeason(source.getServer(), season, clampedDay);
        source.sendFeedback(() -> Text.translatable("commands.dynamic_seasons.set",
                Text.translatable(season.translationKey()).formatted(season.color()), clampedDay), true);
        return 1;
    }

    private static int setLength(ServerCommandSource source, int days) {
        SeasonsConfig config = SeasonsConfig.get().copy();
        config.seasonLengthDays = days;
        SeasonsConfig.replace(config);
        SeasonManager.onConfigChanged(source.getServer());
        source.sendFeedback(() -> Text.translatable("commands.dynamic_seasons.length.set", days), true);
        return days;
    }
}
