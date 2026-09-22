package com.giga.tech1000.heartbeatz.architecture.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.InputStream;

/**
 * Dev stub: no network. Produces deterministic fake URLs so sync/UI can be tested offline.
 */
public final class NoOpPartyMediaStore implements PartyMediaStore {

    @NonNull
    @Override
    public PartyMediaObject upload(
            @NonNull String partyId,
            @NonNull String trackId,
            @NonNull File file,
            @NonNull String mimeType,
            @Nullable String contentHash,
            @Nullable ProgressListener progress) {
        if (progress != null) progress.onProgress(1f);
        String key = PartyMediaKeys.trackKey(partyId, trackId, contentHash, file.getName());
        return new PartyMediaObject(key, "https://example.invalid/" + key, contentHash, file.length());
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
            @Nullable ProgressListener progress) {
        if (progress != null) progress.onProgress(1f);
        String key = PartyMediaKeys.trackKey(partyId, trackId, contentHash, "audio.bin");
        return new PartyMediaObject(key, "https://example.invalid/" + key, contentHash, contentLength);
    }

    @Override
    public void delete(@NonNull String objectKey) { }

    @Override
    public void deletePartyPrefix(@NonNull String partyId) { }
}
