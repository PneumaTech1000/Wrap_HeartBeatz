package com.giga.tech1000.heartbeatz.ui;

import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.views.panels.RootNavigationBarPanel;

/** Package bridge so PlayerChromeController can update content insets without a hard cycle. */
public final class UIThreadBridge {
    @Nullable
    private static RootNavigationBarPanel nav;

    private UIThreadBridge() {}

    public static void setNav(@Nullable RootNavigationBarPanel n) {
        nav = n;
    }

    @Nullable
    public static RootNavigationBarPanel getNav() {
        return nav;
    }
}
