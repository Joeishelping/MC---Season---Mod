package com.dynamicseasons;

/**
 * The single source of truth for "what time of year is it".
 *
 * <p>Written by the server every tick (and, on a remote client, by the sync packet), read from
 * anywhere - including render threads - so the fields are volatile and every derived value is a
 * pure function of them.
 */
public final class SeasonClock {
    public static final long TICKS_PER_DAY = 24000L;

    /**
     * Temperature offset key frames over the year. X is the position in the year measured in
     * seasons (0 = first tick of spring, 4 = end of winter), Y is the offset added to a biome's
     * temperature. Snow falls below 0.15, so -0.7 is enough to make plains (0.8) snowy while
     * jungles (0.95) and deserts/savannas (2.0) never freeze.
     */
    private static final float[][] TEMPERATURE_CURVE = {
            {0.00f, -0.70f}, // early spring: still cold, snow in cooler biomes turning to rain
            {1.00f, 0.00f},  // late spring: vanilla temperatures
            {1.50f, 0.15f},  // mid summer: warmest
            {2.00f, 0.00f},  // early fall
            {3.00f, -0.70f}, // late fall: frost, plants die off
            {3.25f, -0.80f}, // deep winter
            {3.75f, -0.80f},
            {4.00f, -0.70f},
    };

    private static volatile long elapsedTicks = 0L;
    private static volatile int seasonLengthDays = 10;
    private static volatile int drySnowLineY = 160;

    private SeasonClock() {
    }

    public static void set(long elapsed, int lengthDays) {
        elapsedTicks = Math.max(0L, elapsed);
        seasonLengthDays = Math.max(1, lengthDays);
    }

    public static void setDrySnowLineY(int y) {
        drySnowLineY = y;
    }

    /** Above this Y, hot dry biomes can get snow in winter (synced from the server's config). */
    public static int drySnowLineY() {
        return drySnowLineY;
    }

    public static long elapsedTicks() {
        return elapsedTicks;
    }

    public static int seasonLengthDays() {
        return seasonLengthDays;
    }

    public static long seasonTicks() {
        return seasonLengthDays * TICKS_PER_DAY;
    }

    public static long yearTicks() {
        return 4L * seasonTicks();
    }

    /** Position in the year in "seasons", in [0, 4). */
    public static double yearPosition() {
        return yearPosition(elapsedTicks, seasonLengthDays);
    }

    public static double yearPosition(long elapsed, int lengthDays) {
        long season = Math.max(1, lengthDays) * TICKS_PER_DAY;
        long year = 4L * season;
        return Math.floorMod(elapsed, year) / (double) season;
    }

    public static Season season() {
        return Season.values()[Math.min(3, (int) Math.floor(yearPosition()))];
    }

    /** Progress through the current season, in [0, 1). */
    public static double seasonProgress() {
        double pos = yearPosition();
        return pos - Math.floor(pos);
    }

    /** 1-based day of the current season. */
    public static int dayOfSeason() {
        return Math.min(seasonLengthDays, (int) Math.floor(seasonProgress() * seasonLengthDays) + 1);
    }

    /** Number of whole years that have passed (0-based). */
    public static long year() {
        return Math.floorDiv(elapsedTicks, yearTicks());
    }

    /**
     * Computes the elapsed tick value that corresponds to the start of {@code day} of
     * {@code season} in the current year, keeping the current time of day.
     */
    public static long elapsedFor(Season season, int day, int lengthDays) {
        long seasonTicks = Math.max(1, lengthDays) * TICKS_PER_DAY;
        long yearStart = year() * 4L * seasonTicks;
        long timeOfDay = Math.floorMod(elapsedTicks, TICKS_PER_DAY);
        int clampedDay = Math.max(1, Math.min(lengthDays, day));
        return yearStart + season.ordinal() * seasonTicks + (clampedDay - 1) * TICKS_PER_DAY + timeOfDay;
    }

    /**
     * Converts an elapsed value from one season length to another while keeping the same
     * season, the same relative progress through it, and the same year count.
     */
    public static long rescale(long elapsed, int oldLength, int newLength) {
        long oldYear = 4L * Math.max(1, oldLength) * TICKS_PER_DAY;
        long newYear = 4L * Math.max(1, newLength) * TICKS_PER_DAY;
        long years = Math.floorDiv(elapsed, oldYear);
        double fraction = Math.floorMod(elapsed, oldYear) / (double) oldYear;
        return years * newYear + Math.round(fraction * newYear);
    }

    // ------------------------------------------------------------------------------------------
    // Derived climate values
    // ------------------------------------------------------------------------------------------

    public static float temperatureOffset() {
        double pos = yearPosition();
        for (int i = 0; i < TEMPERATURE_CURVE.length - 1; i++) {
            float[] a = TEMPERATURE_CURVE[i];
            float[] b = TEMPERATURE_CURVE[i + 1];
            if (pos >= a[0] && pos <= b[0]) {
                double t = (pos - a[0]) / (b[0] - a[0]);
                return (float) (a[1] + (b[1] - a[1]) * t);
            }
        }
        return TEMPERATURE_CURVE[0][1];
    }

    /** Random-tick growth multiplier for crops and saplings that can see the sky. */
    public static float growthMultiplier() {
        return switch (season()) {
            case SPRING -> 1.0f;
            case SUMMER -> 1.5f;
            case FALL -> 0.75f;
            case WINTER -> 0.25f;
        };
    }

    /** Multiplier applied to how long it rains once rain starts. */
    public static float rainDurationMultiplier() {
        return switch (season()) {
            case SPRING -> 1.5f;
            case SUMMER -> 1.0f;
            case FALL -> 1.0f;
            case WINTER -> 1.25f;
        };
    }

    /** Multiplier applied to how long it stays clear between rain storms. */
    public static float clearDurationMultiplier() {
        return switch (season()) {
            case SPRING -> 0.35f;
            case SUMMER -> 0.6f;
            case FALL -> 1.0f;
            case WINTER -> 0.5f;
        };
    }

    /** Multiplier applied to the gap between thunderstorms. Summer storms are more common. */
    public static float thunderDelayMultiplier() {
        return season() == Season.SUMMER ? 0.6f : 1.0f;
    }

    // ------------------------------------------------------------------------------------------
    // Visual blend amounts (0..1), used by the client to tint grass and leaves.
    // ------------------------------------------------------------------------------------------

    public static float autumnAmount() {
        double pos = yearPosition();
        if (pos >= 2.0 && pos < 3.0) {
            return (float) (pos - 2.0);              // fall: green -> orange
        }
        if (pos >= 3.0 && pos < 3.25) {
            return (float) (1.0 - (pos - 3.0) * 4.0); // early winter: orange fades into winter brown
        }
        return 0f;
    }

    public static float winterAmount() {
        double pos = yearPosition();
        if (pos >= 3.0 && pos < 3.25) {
            return (float) ((pos - 3.0) * 4.0);
        }
        if (pos >= 3.25) {
            return 1f;
        }
        if (pos < 0.25) {
            return (float) (1.0 - pos * 4.0);        // early spring thaw
        }
        return 0f;
    }

    public static float springAmount() {
        double pos = yearPosition();
        if (pos >= 0.25 && pos < 0.5) {
            return (float) ((pos - 0.25) * 4.0);
        }
        if (pos >= 0.5 && pos < 1.0) {
            return 1f;
        }
        if (pos >= 1.0 && pos < 1.25) {
            return (float) (1.0 - (pos - 1.0) * 4.0);
        }
        return 0f;
    }
}
