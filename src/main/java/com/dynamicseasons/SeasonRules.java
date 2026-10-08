package com.dynamicseasons;

import com.dynamicseasons.mixin.BiomeAccessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * Position-level climate decisions. Everything that changes the world goes through here so the
 * "freeze" and "thaw" rules can never disagree with each other.
 *
 * <p>Key invariant: places that are naturally cold (vanilla would snow there) are never touched.
 * The mod only ever adds things where vanilla would not, so removing them later always restores
 * the original vanilla state.
 */
public final class SeasonRules {
    /** Vanilla's snow threshold: precipitation is snow below this temperature. */
    public static final float SNOW_TEMPERATURE = 0.15f;
    /** Biomes at or above this temperature never get seasonal leaf/grass colors (tropics, deserts). */
    public static final float TROPICAL_TEMPERATURE = 0.95f;
    /** Dry biomes this hot or hotter (deserts, savannas, badlands) may only snow above the snow line. */
    private static final float HOT_DRY_TEMPERATURE = 1.0f;

    private SeasonRules() {
    }

    /** The vanilla temperature at a position (includes altitude cooling and noise). */
    public static float baseTemperature(Biome biome, BlockPos pos) {
        return ((BiomeAccessor) (Object) biome).dynamicSeasons$getTemperature(pos);
    }

    public static boolean isNaturallyCold(Biome biome, BlockPos pos) {
        return baseTemperature(biome, pos) < SNOW_TEMPERATURE;
    }

    /** Temperature with the current season applied. Naturally cold places never get warmer. */
    public static float seasonalTemperature(Biome biome, BlockPos pos) {
        float base = baseTemperature(biome, pos);
        float adjusted = base + SeasonClock.temperatureOffset();
        return base < SNOW_TEMPERATURE ? Math.min(base, adjusted) : adjusted;
    }

    /** True for deserts/savannas/badlands high enough up during winter. */
    public static boolean isDryBiomeWinterSnow(Biome biome, BlockPos pos) {
        return !biome.hasPrecipitation()
                && biome.getTemperature() >= HOT_DRY_TEMPERATURE
                && SeasonClock.season() == Season.WINTER
                && pos.getY() >= SeasonClock.drySnowLineY();
    }

    /** May the client draw precipitation in this biome at all right now? */
    public static boolean mayPrecipitate(Biome biome) {
        return biome.hasPrecipitation()
                || (biome.getTemperature() >= HOT_DRY_TEMPERATURE && SeasonClock.season() == Season.WINTER);
    }

    /**
     * Seasonal precipitation at a position, or {@code null} to keep vanilla's answer.
     */
    public static Biome.Precipitation precipitation(Biome biome, BlockPos pos) {
        if (!biome.hasPrecipitation()) {
            return isDryBiomeWinterSnow(biome, pos) ? Biome.Precipitation.SNOW : null;
        }
        return seasonalTemperature(biome, pos) < SNOW_TEMPERATURE ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
    }

    /**
     * True when this spot is cold ONLY because of the season - i.e. vanilla would never put snow
     * or ice here. This is the only place the mod places seasonal snow and ice.
     */
    public static boolean isSeasonallyFrozen(Biome biome, BlockPos pos) {
        if (isNaturallyCold(biome, pos)) {
            return false;
        }
        if (!biome.hasPrecipitation()) {
            return isDryBiomeWinterSnow(biome, pos);
        }
        return seasonalTemperature(biome, pos) < SNOW_TEMPERATURE;
    }

    /** Seasonal snow/ice at this spot should melt. */
    public static boolean shouldThaw(Biome biome, BlockPos pos) {
        return !isSeasonallyFrozen(biome, pos);
    }

    /** Plants at this spot should wither now. */
    public static boolean shouldWither(Biome biome, BlockPos pos) {
        if (!SeasonsConfig.get().witherPlants || !biome.hasPrecipitation() || isNaturallyCold(biome, pos)) {
            return false;
        }
        Season season = SeasonClock.season();
        return (season == Season.FALL || season == Season.WINTER) && seasonalTemperature(biome, pos) < SNOW_TEMPERATURE;
    }

    /**
     * Withered plants at this spot should come back. Deliberately generous so nothing can get
     * stuck dead: always in summer, in spring once it is warm, and immediately if withering is
     * turned off or the spot could never wither in the first place.
     */
    public static boolean shouldRevive(Biome biome, BlockPos pos) {
        if (!SeasonsConfig.get().witherPlants || !biome.hasPrecipitation() || isNaturallyCold(biome, pos)) {
            return true;
        }
        Season season = SeasonClock.season();
        if (season == Season.SUMMER) {
            return true;
        }
        return season == Season.SPRING && seasonalTemperature(biome, pos) >= SNOW_TEMPERATURE;
    }
}
