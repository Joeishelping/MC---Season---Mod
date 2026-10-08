package com.dynamicseasons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SeasonsConfig {
    public static final int MIN_SEASON_LENGTH = 1;
    public static final int MAX_SEASON_LENGTH = 100;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("dynamic_seasons.json");
    private static volatile SeasonsConfig instance = new SeasonsConfig();

    /** Length of each season in Minecraft days. All four seasons are always equal. */
    public int seasonLengthDays = 10;
    /** How many snow layers can pile up in places that only get snow because of the season. */
    public int maxSnowLayers = 3;
    /** Above this Y level, dry biomes (desert, savanna, badlands) can get snow in winter. */
    public int drySnowLineY = 160;
    /** Seasons change how often and how long it rains. */
    public boolean seasonalWeather = true;
    /** Exposed water freezes in winter and thaws back to water in spring. */
    public boolean freezeWater = true;
    /** Grass, ferns and flowers wither in the cold and come back exactly as they were in spring. */
    public boolean witherPlants = true;
    /** Crops grow faster in summer and slower in fall/winter (only when they can see the sky). */
    public boolean seasonalCropGrowth = true;
    /** Chat message when a new season begins. */
    public boolean announceSeasons = true;

    // Client-only options
    /** Grass and leaves change color with the seasons. */
    public boolean seasonalColors = true;
    /** Show the current season and day in the top-left corner. */
    public boolean showHud = true;

    public static SeasonsConfig get() {
        return instance;
    }

    public static synchronized void load() {
        SeasonsConfig loaded = null;
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                loaded = GSON.fromJson(reader, SeasonsConfig.class);
            } catch (Exception e) {
                DynamicSeasons.LOGGER.error("Could not read {}, using defaults", PATH, e);
            }
        }
        instance = loaded == null ? new SeasonsConfig() : loaded;
        instance.clamp();
        save();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Path tmp = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(instance, writer);
            }
            Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            DynamicSeasons.LOGGER.error("Could not save {}", PATH, e);
        }
    }

    public static synchronized void replace(SeasonsConfig config) {
        config.clamp();
        instance = config;
        save();
    }

    public SeasonsConfig copy() {
        return GSON.fromJson(GSON.toJson(this), SeasonsConfig.class);
    }

    private void clamp() {
        seasonLengthDays = Math.max(MIN_SEASON_LENGTH, Math.min(MAX_SEASON_LENGTH, seasonLengthDays));
        maxSnowLayers = Math.max(1, Math.min(8, maxSnowLayers));
    }
}
