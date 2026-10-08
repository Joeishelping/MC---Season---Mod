package com.dynamicseasons;

import net.minecraft.util.Formatting;

public enum Season {
    SPRING("spring", Formatting.GREEN),
    SUMMER("summer", Formatting.YELLOW),
    FALL("fall", Formatting.GOLD),
    WINTER("winter", Formatting.AQUA);

    private final String id;
    private final Formatting color;

    Season(String id, Formatting color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public Formatting color() {
        return color;
    }

    public String translationKey() {
        return "season.dynamic_seasons." + id;
    }

    public static Season byId(String id) {
        for (Season season : values()) {
            if (season.id.equalsIgnoreCase(id)) {
                return season;
            }
        }
        return null;
    }
}
