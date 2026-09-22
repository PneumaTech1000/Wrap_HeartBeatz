package com.giga.tech1000.heartbeatz.architecture.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * Supabase Storage backend (testing).
 * <p>
 * Upload API: {@code POST /storage/v1/object/{bucket}/{path}}
 * Public URL (if bucket public): {@code {supabaseUrl}/storage/v1/object/public/{bucket}/{path}}
 * <p>
 * Edit credentials in {@link PartyMediaConfig}.
 */
public final class SupabasePartyMediaStore implements PartyMediaStore {

    private final PartyMediaConfig config;
    private final OkHttpClient http;

    public SupabasePartyMediaStore(@NonNull PartyMediaConfig config) {
        this.config = config;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.MINUTES)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @NonNull
    @Override
    public PartyMediaObject upload(
            @NonNull String partyId,
            @NonNull String trackId,
            @NonNull File file,
            @NonNull String mimeType,
            @Nullable String contentHash,
            @Nullable ProgressListener progress) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return upload(partyId, trackId, in, mimeType, file.length(), contentHash, progress);
        }
    }

    @NonNull
    @Override
    public PartyMediaObject upload(
            @NonNull String partyId,
            @NonNull String trackId,
            @NonNull InputStream data,
            @NonNull String mimeType,
            long contentLength,
            @Nullable String contentHash,
            @Nullable ProgressListener progress) throws IOException {

        if (!config.isConfigured()) {
            throw new IOException("Supabase not configured — edit PartyMediaConfig.supabaseUrl / supabaseApiKey");
        }

        String objectKey = PartyMediaKeys.trackKey(partyId, trackId, contentHash, "track" + guessExt(mimeType));
        String url = trimSlash(config.supabaseUrl)
                + "/storage/v1/object/"
                + config.supabaseBucket
                + "/"
                + objectKey;

        MediaType mediaType = MediaType.parse(mimeType);
        if (mediaType == null) mediaType = MediaType.parse("application/octet-stream");

        RequestBody body = countingBody(mediaType, data, contentLength, progress);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.supabaseApiKey)
                .header("apikey", config.supabaseApiKey)
                .header("x-upsert", "true")
                .put(body)
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("Supabase upload HTTP " + response.code() + ": " + err);
            }
        }

        // Public object URL (bucket must allow public read or use signed URL later)
        String mediaUrl = trimSlash(config.supabaseUrl)
                + "/storage/v1/object/public/"
                + config.supabaseBucket
                + "/"
                + objectKey;

        return new PartyMediaObject(objectKey, mediaUrl, contentHash, contentLength);
    }

    @Override
    public void delete(@NonNull String objectKey) throws IOException {
        if (!config.isConfigured()) return;
        String url = trimSlash(config.supabaseUrl)
                + "/storage/v1/object/"
                + config.supabaseBucket
                + "/"
                + objectKey;
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer " + config.supabaseApiKey)
                .header("apikey", config.supabaseApiKey)
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IOException("Supabase delete HTTP " + response.code());
            }
        }
    }

    @Override
    public void deletePartyPrefix(@NonNull String partyId) throws IOException {
        // Supabase has no single “delete prefix” REST call; list+delete is backend-specific.
        // Host should delete known track keys; lifecycle rules can purge stale prefixes.
    }

    @NonNull
    private static String trimSlash(@NonNull String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    @NonNull
    private static String guessExt(@NonNull String mime) {
        if (mime.contains("mp4") || mime.contains("aac") || mime.contains("m4a")) return ".m4a";
        if (mime.contains("ogg")) return ".ogg";
        if (mime.contains("wav")) return ".wav";
        if (mime.contains("flac")) return ".flac";
        return ".mp3";
    }

    @NonNull
    private static RequestBody countingBody(
            @NonNull MediaType mediaType,
            @NonNull InputStream data,
            long contentLength,
            @Nullable ProgressListener progress) {
        return new RequestBody() {
            @Override public MediaType contentType() { return mediaType; }
            @Override public long contentLength() { return contentLength >= 0 ? contentLength : -1; }

            @Override
            public void writeTo(@NonNull BufferedSink sink) throws IOException {
                try (Source source = Okio.source(data)) {
                    long total = contentLength > 0 ? contentLength : -1;
                    long written = 0;
                    long read;
                    okio.Buffer buf = new okio.Buffer();
                    while ((read = source.read(buf, 8192)) != -1) {
                        sink.write(buf, read);
                        written += read;
                        if (progress != null && total > 0) {
                            progress.onProgress(Math.min(1f, written / (float) total));
                        }
                    }
                    if (progress != null) progress.onProgress(1f);
                }
            }
        };
    }
}
