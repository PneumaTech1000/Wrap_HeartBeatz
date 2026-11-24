package com.giga.tech1000.heartbeatz.architecture.freeshelf;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Remote index is optional. Leave URL empty to use bundled catalog only.
 * Production: host your own JSON of CC0/CC-BY tracks you have rights to list.
 */
public final class FreeShelfConfig {

    /**
     * Optional HTTPS JSON index. Must be HTTPS in production.
     * Schema: {@code { "version":1, "tracks":[ {...} ] }}
     * <p>
     * Edit this constant or set via {@link #setRemoteIndexUrl(String)} at runtime
     * (e.g. from remote config later). Empty = bundled only.
     */
    @NonNull
    private static volatile String remoteIndexUrl = "";

    /** Cache TTL for remote index. */
    public static final long REMOTE_CACHE_TTL_MS = 24L * 60L * 60L * 1000L;

    /** Max tracks accepted from remote (abuse protection). */
    public static final int MAX_REMOTE_TRACKS = 500;

    private FreeShelfConfig() {}

    @NonNull
    public static String getRemoteIndexUrl() {
        return remoteIndexUrl;
    }

    public static void setRemoteIndexUrl(@Nullable String url) {
        remoteIndexUrl = url != null ? url.trim() : "";
    }

    public static boolean hasRemoteIndex() {
        String u = remoteIndexUrl;
        return u.startsWith("https://") || u.startsWith("http://");
    }
}
