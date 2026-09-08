package com.giga.tech1000.media_player.utils.enums;

import androidx.media3.common.Player;

public enum RepeatMode {

    OFF(Player.REPEAT_MODE_OFF),
    ONE(Player.REPEAT_MODE_ONE),
    ALL(Player.REPEAT_MODE_ALL);

    private final int media3Value;

    RepeatMode(int media3Value) {
        this.media3Value = media3Value;
    }

    public int toMedia3() {
        return media3Value;
    }

    public static RepeatMode fromMedia3(int value) {
        for (RepeatMode mode : values()) {
            if (mode.media3Value == value) {
                return mode;
            }
        }
        return OFF;
    }
}
