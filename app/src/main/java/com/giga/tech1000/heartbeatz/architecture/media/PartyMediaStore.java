package com.giga.tech1000.heartbeatz.architecture.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;

/**
 * Abstraction over party track object storage.
 * Implementations: Supabase (test), Cloudflare R2 (prod), NoOp (local UI).
 * <p>
 * Call from a background {@link Executor}; never block the main thread.
 */
public interface PartyMediaStore {

    /**
     * Upload local audio for a party track.
     *
     * @param partyId   Firebase party id
     * @param trackId   app track / media store id
     * @param file      local file (or use {@link #upload(String, String, InputStream, String, long, ProgressListener)})
     * @param mimeType  e.g. audio/mpeg
     * @param contentHash optional dedupe hash (SHA-256 hex); may be used in object key
     * @param progress  optional 0..1 callbacks on calling executor
     */
    @NonNull
    PartyMediaObject upload(
            @NonNull String partyId,
            @NonNull String trackId,
            @NonNull File file,
            @NonNull String mimeType,
            @Nullable String contentHash,
            @Nullable ProgressListener progress
    ) throws IOException;

    @NonNull
    PartyMediaObject upload(
            @NonNull String partyId,
            @NonNull String trackId,
            @NonNull InputStream data,
            @NonNull String mimeType,
            long contentLength,
            @Nullable String contentHash,
            @Nullable ProgressListener progress
    ) throws IOException;

    /** Best-effort delete of a single object. */
    void delete(@NonNull String objectKey) throws IOException;

    /** Best-effort delete of parties/{partyId}/ prefix when party ends. */
    void deletePartyPrefix(@NonNull String partyId) throws IOException;

    /**
     * Refresh a playable URL if the backend uses short-lived signed URLs.
     * Default: return existing mediaUrl.
     */
    @NonNull
    default String resolvePlayableUrl(@NonNull PartyMediaObject object) throws IOException {
        return object.mediaUrl;
    }

    interface ProgressListener {
        void onProgress(float fraction01);
    }
}
