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
 * Cloudflare R2 backend (production) via S3-compatible HTTPS PUT.
 * <p>
 * <b>Migration from Supabase:</b> set {@link PartyMediaConfig#backend} to
 * {@link PartyMediaConfig.Backend#CLOUDFLARE_R2} and fill R2 fields. Same
 * {@link PartyMediaKeys} layout keeps paths portable.
 * <p>
 * Full AWS SigV4 signing should be added before production traffic (or upload
 * via your backend that returns presigned PUTs). This class uses a simple
 * PUT when the bucket accepts the request with static keys for early bring-up;
 * replace {@link #authorizedPut} with SigV4 or presigned URL when hardening.
 */
public final class R2PartyMediaStore implements PartyMediaStore {

    private final PartyMediaConfig config;
    private final OkHttpClient http;

    public R2PartyMediaStore(@NonNull PartyMediaConfig config) {
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
            throw new IOException("R2 not configured — edit PartyMediaConfig r2* fields");
        }

        String objectKey = PartyMediaKeys.trackKey(partyId, trackId, contentHash, "track.mp3");
        String putUrl = trimSlash(config.r2Endpoint) + "/" + config.r2Bucket + "/" + objectKey;

        MediaType mediaType = MediaType.parse(mimeType);
        if (mediaType == null) mediaType = MediaType.parse("application/octet-stream");

        final MediaType mMediaType = mediaType;
        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return mMediaType;
            }

            @Override
            public long contentLength() {
                return contentLength >= 0 ? contentLength : -1;
            }

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

        authorizedPut(putUrl, body);

        String mediaUrl;
        if (config.r2PublicBaseUrl != null && !config.r2PublicBaseUrl.isEmpty()) {
            mediaUrl = trimSlash(config.r2PublicBaseUrl) + "/" + objectKey;
        } else {
            mediaUrl = putUrl; // replace with presigned GET in production
        }
        return new PartyMediaObject(objectKey, mediaUrl, contentHash, contentLength);
    }

    private void authorizedPut(@NonNull String putUrl, @NonNull RequestBody body) throws IOException {
        // TODO(production): AWS SigV4 or presigned URL from your backend.
        // Placeholders: access key headers are NOT sufficient for real R2 without signing.
        Request request = new Request.Builder()
                .url(putUrl)
                .put(body)
                .header("Content-Type", body.contentType() != null
                        ? body.contentType().toString()
                        : "application/octet-stream")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException(
                        "R2 upload HTTP " + response.code() + ": " + err
                                + " — implement SigV4/presign (see CLOUD_MEDIA.md)");
            }
        }
    }

    @Override
    public void delete(@NonNull String objectKey) throws IOException {
        // TODO: SigV4 DELETE or backend call
    }

    @Override
    public void deletePartyPrefix(@NonNull String partyId) throws IOException {
        // Prefer R2 lifecycle rules + backend batch delete
    }

    @NonNull
    private static String trimSlash(@NonNull String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
