package com.giga.tech1000.heartbeatz.ui;

/** Mini-player visibility flag for nav padding (set by UIThread). */
public final class UIThreadBridgePad {
    private static volatile boolean playerBarVisible;

    private UIThreadBridgePad() {}

    public static boolean isPlayerBarVisible() {
        return playerBarVisible;
    }

    public static void setPlayerBarVisible(boolean v) {
        playerBarVisible = v;
    }
}
