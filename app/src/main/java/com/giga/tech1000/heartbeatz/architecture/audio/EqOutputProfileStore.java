package com.giga.tech1000.heartbeatz.architecture.audio;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Persists EQ curve + signature name per {@link EqOutputRoute} (+ optional device id).
 * Key format: {@code eq_out_{routeKey}[_{deviceSuffix}]}
 */
public final class EqOutputProfileStore {

    private static final String PREFS = "heartbeatz_eq_output_profiles_v1";
    private static final String KEY_LAST_ROUTE = "last_route_key";
    private static final String KEY_LAST_DEVICE = "last_device_suffix";

    private final SharedPreferences prefs;

    public EqOutputProfileStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public String storageKey(@NonNull EqOutputRoute route, @Nullable String deviceSuffix) {
        if (deviceSuffix == null || deviceSuffix.isEmpty()) {
            return "eq_out_" + route.key;
        }
        String safe = deviceSuffix.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safe.length() > 48) safe = safe.substring(0, 48);
        return "eq_out_" + route.key + "_" + safe;
    }

    public void save(
            @NonNull EqOutputRoute route,
            @Nullable String deviceSuffix,
            @Nullable String profileName,
            @NonNull float[] gainsDb,
            boolean eqEnabled,
            boolean limiterEnabled) {
        try {
            JSONObject o = new JSONObject();
            o.put("profileName", profileName != null ? profileName : "Custom");
            o.put("eqEnabled", eqEnabled);
            o.put("limiterEnabled", limiterEnabled);
            JSONArray arr = new JSONArray();
            for (float g : gainsDb) {
                arr.put(g);
            }
            o.put("gainsDb", arr);
            o.put("savedAt", System.currentTimeMillis());
            prefs.edit()
                    .putString(storageKey(route, deviceSuffix), o.toString())
                    .putString(KEY_LAST_ROUTE, route.key)
                    .putString(KEY_LAST_DEVICE, deviceSuffix != null ? deviceSuffix : "")
                    .apply();
        } catch (Exception ignored) {
        }
    }

    @Nullable
    public Snapshot load(@NonNull EqOutputRoute route, @Nullable String deviceSuffix) {
        String raw = prefs.getString(storageKey(route, deviceSuffix), null);
        if (raw == null && deviceSuffix != null) {
            // Fall back to route-only profile
            raw = prefs.getString(storageKey(route, null), null);
        }
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            JSONArray arr = o.optJSONArray("gainsDb");
            float[] gains = new float[10];
            if (arr != null) {
                for (int i = 0; i < Math.min(10, arr.length()); i++) {
                    gains[i] = (float) arr.optDouble(i, 0);
                }
            }
            return new Snapshot(
                    o.optString("profileName", "Custom"),
                    gains,
                    o.optBoolean("eqEnabled", true),
                    o.optBoolean("limiterEnabled", false));
        } catch (Exception e) {
            return null;
        }
    }

    public static final class Snapshot {
        @NonNull public final String profileName;
        @NonNull public final float[] gainsDb;
        public final boolean eqEnabled;
        public final boolean limiterEnabled;

        public Snapshot(
                @NonNull String profileName,
                @NonNull float[] gainsDb,
                boolean eqEnabled,
                boolean limiterEnabled) {
            this.profileName = profileName;
            this.gainsDb = gainsDb;
            this.eqEnabled = eqEnabled;
            this.limiterEnabled = limiterEnabled;
        }
    }
}
