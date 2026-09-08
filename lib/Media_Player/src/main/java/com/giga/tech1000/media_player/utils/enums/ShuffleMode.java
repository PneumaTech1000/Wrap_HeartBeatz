package com.giga.tech1000.media_player.utils.enums;

public enum ShuffleMode {
    OFF(false),
    ON(true);

    private final boolean enabled;

    ShuffleMode(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean toBoolean() {
        return enabled;
    }

    public static ShuffleMode fromBoolean(boolean enabled) {
        return enabled ? ON : OFF;
    }
}
