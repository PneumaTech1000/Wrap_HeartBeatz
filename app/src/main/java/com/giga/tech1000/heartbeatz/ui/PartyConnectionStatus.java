package com.giga.tech1000.heartbeatz.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.party_mode.core.PartyState;

/**
 * User-facing connection labels for party chrome (banner / chip).
 * Cloud upload states reserved for §7 media migration.
 */
public final class PartyConnectionStatus {

    private PartyConnectionStatus() {}

    @NonNull
    public static String label(@Nullable PartyState state) {
        if (state == null) return "Idle";
        return switch (state) {
            case IDLE -> "Idle";
            case SEARCHING -> "Searching…";
            case CREATING -> "Creating party…";
            case CONNECTING -> "Connecting…";
            case FOUND -> "Party found";
            case HOSTING -> "Hosting — ready";
            case JOINED -> "Connected — synced";
            case ERROR -> "Connection problem";
            case SETUP_REQUIRED -> "Setup required";
        };
    }

    public static boolean isActive(@Nullable PartyState state) {
        return state == PartyState.HOSTING
                || state == PartyState.JOINED
                || state == PartyState.CONNECTING
                || state == PartyState.SEARCHING
                || state == PartyState.CREATING;
    }
}
