package com.giga.tech1000.heartbeatz.architecture.freeshelf;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads bundled + optional remote legal catalog. Rejects unknown licenses and
 * non-http(s) URLs. Never scrapes commercial music services.
 */
public final class FreeShelfRepository {

    private static final String TAG = "FreeShelf";
    private static final String ASSET_CATALOG = "freeshelf/catalog_v1.json";
    private static final String PREFS = "hb_free_shelf_v1";
    private static final String KEY_CACHE_AT = "remote_cache_at";
    private static final String CACHE_FILE = "freeshelf_remote_v1.json";

    private final Context app;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "free-shelf-io");
        t.setDaemon(true);
        return t;
    });

    private final MutableLiveData<List<FreeShelfTrack>> tracksLive =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loadingLive = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLive = new MutableLiveData<>(null);
    private final MutableLiveData<String> policyLive = new MutableLiveData<>(null);

    private final Map<String, FreeShelfTrack> byId = new LinkedHashMap<>();

    public FreeShelfRepository(@NonNull Context context) {
        this.app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public LiveData<List<FreeShelfTrack>> getTracks() {
        return tracksLive;
    }

    @NonNull
    public LiveData<Boolean> isLoading() {
        return loadingLive;
    }

    @NonNull
    public LiveData<String> getError() {
        return errorLive;
    }

    @NonNull
    public LiveData<String> getPolicyText() {
        return policyLive;
    }

    @Nullable
    public FreeShelfTrack getById(@Nullable String id) {
        if (id == null) return null;
        synchronized (byId) {
            return byId.get(id);
        }
    }

    /** Load bundled catalog immediately, then refresh remote if configured. */
    public void loadAsync() {
        loadingLive.postValue(true);
        errorLive.postValue(null);
        io.execute(() -> {
            try {
                List<FreeShelfTrack> bundled = parseCatalog(readAsset(ASSET_CATALOG), false);
                publish(bundled);
                if (FreeShelfConfig.hasRemoteIndex()) {
                    List<FreeShelfTrack> remote = loadRemoteWithCache();
                    if (remote != null && !remote.isEmpty()) {
                        // Remote overrides same ids; append new
                        Map<String, FreeShelfTrack> merged = new LinkedHashMap<>();
                        for (FreeShelfTrack t : bundled) merged.put(t.id, t);
                        for (FreeShelfTrack t : remote) merged.put(t.id, t);
                        publish(new ArrayList<>(merged.values()));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
                errorLive.postValue(e.getMessage());
            } finally {
                loadingLive.postValue(false);
            }
        });
    }

    /** Force re-fetch remote index (ignores TTL). */
    public void refreshRemoteAsync() {
        if (!FreeShelfConfig.hasRemoteIndex()) {
            errorLive.postValue("No remote index URL configured");
            return;
        }
        loadingLive.postValue(true);
        io.execute(() -> {
            try {
                prefs.edit().putLong(KEY_CACHE_AT, 0L).apply();
                List<FreeShelfTrack> remote = loadRemoteWithCache();
                List<FreeShelfTrack> bundled = parseCatalog(readAsset(ASSET_CATALOG), false);
                Map<String, FreeShelfTrack> merged = new LinkedHashMap<>();
                for (FreeShelfTrack t : bundled) merged.put(t.id, t);
                if (remote != null) {
                    for (FreeShelfTrack t : remote) merged.put(t.id, t);
                }
                publish(new ArrayList<>(merged.values()));
                errorLive.postValue(null);
            } catch (Exception e) {
                Log.e(TAG, "refresh failed", e);
                errorLive.postValue(e.getMessage());
            } finally {
                loadingLive.postValue(false);
            }
        });
    }

    private void publish(@NonNull List<FreeShelfTrack> list) {
        List<FreeShelfTrack> playable = new ArrayList<>();
        synchronized (byId) {
            byId.clear();
            for (FreeShelfTrack t : list) {
                if (!t.license.isAllowedOnShelf()) continue;
                byId.put(t.id, t);
                if (t.isPlayable()) playable.add(t);
            }
        }
        tracksLive.postValue(Collections.unmodifiableList(playable));
        Log.i(TAG, "published tracks=" + playable.size() + " (filtered from " + list.size() + ")");
    }

    @Nullable
    private List<FreeShelfTrack> loadRemoteWithCache() throws Exception {
        long at = prefs.getLong(KEY_CACHE_AT, 0L);
        File cache = new File(app.getFilesDir(), CACHE_FILE);
        long now = System.currentTimeMillis();
        if (cache.exists() && (now - at) < FreeShelfConfig.REMOTE_CACHE_TTL_MS) {
            String json = readFile(cache);
            return parseCatalog(json, true);
        }
        String url = FreeShelfConfig.getRemoteIndexUrl();
        String json = httpGet(url);
        if (json == null || json.isEmpty()) return null;
        writeFile(cache, json);
        prefs.edit().putLong(KEY_CACHE_AT, now).apply();
        return parseCatalog(json, true);
    }

    @NonNull
    private List<FreeShelfTrack> parseCatalog(@NonNull String json, boolean remote) throws Exception {
        JSONObject root = new JSONObject(json);
        String policy = root.optString("policy", null);
        if (policy != null && !policy.isEmpty()) {
            policyLive.postValue(policy);
        }
        JSONArray arr = root.optJSONArray("tracks");
        if (arr == null) return Collections.emptyList();
        List<FreeShelfTrack> out = new ArrayList<>();
        int limit = remote ? FreeShelfConfig.MAX_REMOTE_TRACKS : arr.length();
        for (int i = 0; i < arr.length() && out.size() < limit; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            FreeShelfTrack t = fromJson(o, remote);
            if (t != null) out.add(t);
        }
        return out;
    }

    @Nullable
    private FreeShelfTrack fromJson(@NonNull JSONObject o, boolean remote) {
        String id = o.optString("id", "").trim();
        String title = o.optString("title", "").trim();
        String artist = o.optString("artist", "Unknown").trim();
        String streamUrl = o.optString("streamUrl", "").trim();
        FreeShelfLicense license = FreeShelfLicense.fromString(o.optString("license", ""));
        if (id.isEmpty() || title.isEmpty()) return null;
        if (!license.isAllowedOnShelf()) {
            Log.w(TAG, "reject track id=" + id + " license=" + license);
            return null;
        }
        // Allow empty stream for policy notes; filtered at publish for playable list
        if (!streamUrl.isEmpty()
                && !streamUrl.startsWith("https://")
                && !streamUrl.startsWith("http://")) {
            Log.w(TAG, "reject non-http stream id=" + id);
            return null;
        }
        String attribution = o.optString("attribution", "").trim();
        if (attribution.isEmpty()) {
            attribution = title + " — " + artist + " (" + license.label + ")";
        }
        return new FreeShelfTrack(
                id,
                title,
                artist,
                emptyToNull(o.optString("album", null)),
                streamUrl,
                emptyToNull(o.optString("artworkUrl", null)),
                license,
                attribution,
                emptyToNull(o.optString("sourceName", null)),
                emptyToNull(o.optString("sourcePageUrl", null)),
                o.optLong("durationMs", 0L),
                remote);
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        if (s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s)) return null;
        return s.trim();
    }

    @NonNull
    private String readAsset(@NonNull String path) throws Exception {
        try (InputStream in = app.getAssets().open(path);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    @NonNull
    private static String readFile(@NonNull File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private static void writeFile(@NonNull File f, @NonNull String data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nullable
    private static String httpGet(@NonNull String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(12_000);
        c.setReadTimeout(20_000);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        if (in == null) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + ": " + sb);
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
