package com.giga.tech1000.heartbeatz.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight party analytics. Logs structured events only — no emails, names, or PINs.
 * Swap implementation later for Firebase Analytics / custom backend.
 */
public final class PartyAnalytics {

    private static final String TAG = "PartyAnalytics";

    private PartyAnalytics() {}

    public static void partyCreated(boolean hasPin) {
        event("party_created", "has_pin", hasPin ? "1" : "0");
    }

    public static void partyCreateFailed(@Nullable String reasonCode) {
        event("party_create_failed", "reason", sanitize(reasonCode));
    }

    public static void partyJoinAttempt(boolean viaDeepLink) {
        event("party_join_attempt", "via_deeplink", viaDeepLink ? "1" : "0");
    }

    public static void partyJoinSuccess() {
        event("party_join_success");
    }

    public static void partyJoinFailed(@Nullable String reasonCode) {
        event("party_join_failed", "reason", sanitize(reasonCode));
    }

    public static void partyLeft(boolean wasHost) {
        event("party_left", "role", wasHost ? "host" : "guest");
    }

    public static void inviteShared() {
        event("party_invite_shared");
    }

    public static void connectionState(@NonNull String state) {
        event("party_connection", "state", sanitize(state));
    }

    private static void event(@NonNull String name) {
        Log.i(TAG, name);
    }

    private static void event(@NonNull String name, @NonNull String k, @Nullable String v) {
        Log.i(TAG, name + " " + k + "=" + (v != null ? v : ""));
    }

    @NonNull
    private static String sanitize(@Nullable String s) {
        if (s == null) return "unknown";
        // Strip anything that might accidentally contain a PIN or email
        String t = s.replaceAll("[0-9]{4,}", "***");
        if (t.length() > 48) t = t.substring(0, 48);
        return t;
    }
}
