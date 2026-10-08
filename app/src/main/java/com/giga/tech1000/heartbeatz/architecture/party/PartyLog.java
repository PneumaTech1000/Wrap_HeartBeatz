package com.giga.tech1000.heartbeatz.architecture.party;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Single logcat tag for the entire party pipeline (host, guest, sync, TimeEngine, upload).
 * <p>
 * Filter in Android Studio / adb:
 * <pre>
 *   adb logcat -s PartyFlow
 * </pre>
 * or Logcat filter: {@code tag:PartyFlow}
 */
public final class PartyLog {

    /** Central filter word — do not change casually. */
    public static final String TAG = "PartyFlow";

    private PartyLog() {}

    public static void d(@NonNull String msg) {
        Log.d(TAG, msg);
    }

    public static void d(@NonNull String component, @NonNull String msg) {
        Log.d(TAG, "[" + component + "] " + msg);
    }

    public static void i(@NonNull String msg) {
        Log.i(TAG, msg);
    }

    public static void i(@NonNull String component, @NonNull String msg) {
        Log.i(TAG, "[" + component + "] " + msg);
    }

    public static void w(@NonNull String msg) {
        Log.w(TAG, msg);
    }

    public static void w(@NonNull String component, @NonNull String msg) {
        Log.w(TAG, "[" + component + "] " + msg);
    }

    public static void w(@NonNull String msg, @Nullable Throwable t) {
        Log.w(TAG, msg, t);
    }

    public static void w(@NonNull String component, @NonNull String msg, @Nullable Throwable t) {
        Log.w(TAG, "[" + component + "] " + msg, t);
    }

    public static void e(@NonNull String msg) {
        Log.e(TAG, msg);
    }

    public static void e(@NonNull String component, @NonNull String msg) {
        Log.e(TAG, "[" + component + "] " + msg);
    }

    public static void e(@NonNull String msg, @Nullable Throwable t) {
        Log.e(TAG, msg, t);
    }

    public static void e(@NonNull String component, @NonNull String msg, @Nullable Throwable t) {
        Log.e(TAG, "[" + component + "] " + msg, t);
    }
}
