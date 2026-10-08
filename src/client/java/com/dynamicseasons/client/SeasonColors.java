package com.dynamicseasons.client;

import com.dynamicseasons.SeasonClock;
import com.dynamicseasons.SeasonRules;
import com.dynamicseasons.SeasonsConfig;

/**
 * Seasonal tints for grass and leaves. Purely visual and computed on the fly from the biome's own
 * color, so when the season moves on the original colors come back automatically - nothing is
 * ever written to the world.
 */
public final class SeasonColors {
    private static final int STEPS = 8;

    private static final int SPRING_GRASS = 0x6CC849;
    private static final int SPRING_FOLIAGE = 0x5DBE3A;
    private static final int AUTUMN_GRASS = 0xB8A445;
    private static final int AUTUMN_FOLIAGE = 0xD8762A;
    private static final int WINTER_GRASS = 0x9A9670;
    private static final int WINTER_FOLIAGE = 0x8C7550;

    private static volatile float spring;
    private static volatile float autumn;
    private static volatile float winter;
    private static volatile int key = -1;

    private SeasonColors() {
    }

    /**
     * Recomputes the (quantized) blend amounts. Returns true when they changed, meaning chunks
     * must be re-meshed to show the new colors.
     */
    public static boolean refresh() {
        int s = 0;
        int a = 0;
        int w = 0;
        if (SeasonsConfig.get().seasonalColors) {
            s = Math.round(SeasonClock.springAmount() * STEPS);
            a = Math.round(SeasonClock.autumnAmount() * STEPS);
            w = Math.round(SeasonClock.winterAmount() * STEPS);
        }
        int newKey = (s * 100) + (a * 10) + w;
        if (newKey == key) {
            return false;
        }
        spring = s / (float) STEPS;
        autumn = a / (float) STEPS;
        winter = w / (float) STEPS;
        key = newKey;
        return true;
    }

    public static void reset() {
        key = -1;
    }

    public static int grass(int color, float biomeTemperature) {
        if (key <= 0 || biomeTemperature >= SeasonRules.TROPICAL_TEMPERATURE) {
            return color;
        }
        color = blend(color, SPRING_GRASS, spring * 0.30f);
        color = blend(color, AUTUMN_GRASS, autumn * 0.55f);
        color = blend(color, WINTER_GRASS, winter * 0.60f);
        return color;
    }

    public static int foliage(int color, float biomeTemperature) {
        if (key <= 0 || biomeTemperature >= SeasonRules.TROPICAL_TEMPERATURE) {
            return color;
        }
        color = blend(color, SPRING_FOLIAGE, spring * 0.30f);
        color = blend(color, AUTUMN_FOLIAGE, autumn * 0.75f);
        color = blend(color, WINTER_FOLIAGE, winter * 0.65f);
        return color;
    }

    private static int blend(int from, int to, float t) {
        if (t <= 0f) {
            return from;
        }
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (from & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
