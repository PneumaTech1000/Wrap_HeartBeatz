package com.giga.tech1000.heartbeatz.architecture.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result of uploading a party track to object storage.
 */
public final class PartyMediaObject {

    /** Storage key, e.g. parties/{partyId}/tracks/{hash}.mp3 */
    @NonNull
    public final String objectKey;

    /** Playable URL (public or signed). Guests pass this to Media3. */
    @NonNull
    public final String mediaUrl;

    @Nullable
    public final String contentHash;

    public final long sizeBytes;

    public PartyMediaObject(
            @NonNull String objectKey,
            @NonNull String mediaUrl,
            @Nullable String contentHash,
            long sizeBytes) {
        this.objectKey = objectKey;
        this.mediaUrl = mediaUrl;
        this.contentHash = contentHash;
        this.sizeBytes = sizeBytes;
    }
}
