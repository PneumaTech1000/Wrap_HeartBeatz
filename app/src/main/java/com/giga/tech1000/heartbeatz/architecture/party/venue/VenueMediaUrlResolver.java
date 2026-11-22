package com.giga.tech1000.heartbeatz.architecture.party.venue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Prefer LAN media when Venue is reachable; otherwise cloud URL.
 * Probe is short and off the main thread; results may be cached briefly.
 */
public final class VenueMediaUrlResolver {

    private static final long PROBE_TIMEOUT_MS = 900L;
    private static final long CACHE_TTL_MS = 15_000L;

    private final OkHttpClient probeClient = new OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_TIMEOUT_MS + 200, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .build();

    private final ExecutorService probeExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "venue-probe");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    /**
     * Blocking resolve (call from background or short wait on main with care).
     * Returns cloud URL immediately if LAN is null/disabled.
     */
    @NonNull
    public String resolve(
            @Nullable String cloudUrl,
            @Nullable String lanUrl,
            boolean venueEnabled) {
        String cloud = cloudUrl != null ? cloudUrl : "";
        if (!venueEnabled || lanUrl == null || lanUrl.isEmpty()) {
            return cloud.isEmpty() ? (lanUrl != null ? lanUrl : "") : cloud;
        }

        CacheEntry c = cache.get();
        long now = System.currentTimeMillis();
        if (c != null && c.lanUrl.equals(lanUrl) && (now - c.atMs) < CACHE_TTL_MS) {
            return c.useLan ? lanUrl : (cloud.isEmpty() ? lanUrl : cloud);
        }

        boolean ok = probeLan(lanUrl);
        cache.set(new CacheEntry(lanUrl, ok, now));
        if (ok) {
            PartyLog.i("Venue", "using LAN media " + lanUrl);
            return lanUrl;
        }
        PartyLog.i("Venue", "LAN unreachable → cloud fallback");
        return cloud.isEmpty() ? lanUrl : cloud;
    }

    /**
     * Async resolve; callback may run on probe thread — post to main in callers if needed.
     */
    public void resolveAsync(
            @Nullable String cloudUrl,
            @Nullable String lanUrl,
            boolean venueEnabled,
            @NonNull java.util.function.Consumer<String> onResult) {
        probeExec.execute(() -> onResult.accept(resolve(cloudUrl, lanUrl, venueEnabled)));
    }

    private boolean probeLan(@NonNull String lanUrl) {
        String health = lanUrl;
        int idx = lanUrl.indexOf("/party/track/");
        if (idx > 0) {
            health = lanUrl.substring(0, idx) + "/party/health";
        }
        return headOk(health) || headOk(lanUrl);
    }

    private boolean headOk(@NonNull String url) {
        Request req = new Request.Builder()
                .url(url)
                .head()
                .header("Connection", "close")
                .build();
        try (Response resp = probeClient.newCall(req).execute()) {
            int code = resp.code();
            return code >= 200 && code < 400;
        } catch (IOException e) {
            // Some servers dislike HEAD — try GET range 0-1
            return rangeOk(url);
        }
    }

    private boolean rangeOk(@NonNull String url) {
        Request req = new Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-1")
                .header("Connection", "close")
                .build();
        try (Response resp = probeClient.newCall(req).execute()) {
            int code = resp.code();
            return code >= 200 && code < 400;
        } catch (IOException e) {
            return false;
        }
    }

    private static final class CacheEntry {
        final String lanUrl;
        final boolean useLan;
        final long atMs;

        CacheEntry(String lanUrl, boolean useLan, long atMs) {
            this.lanUrl = lanUrl;
            this.useLan = useLan;
            this.atMs = atMs;
        }
    }
}
