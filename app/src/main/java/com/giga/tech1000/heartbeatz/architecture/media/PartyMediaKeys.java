package com.giga.tech1000.heartbeatz.architecture.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stable object-key layout shared by Supabase and R2.
 * {@code parties/{partyId}/tracks/{hashOrTrackId}.{ext}}
 */
public final class PartyMediaKeys {

    private PartyMediaKeys() {}

    @NonNull
    public static String partyPrefix(@NonNull String partyId) {
        return "parties/" + sanitize(partyId) + "/";
    }

    @NonNull
    public static String trackKey(
            @NonNull String partyId,
            @NonNull String trackId,
            @Nullable String contentHash,
            @NonNull String fileNameHint) {
        String ext = extensionOf(fileNameHint);
        String leaf = (contentHash != null && contentHash.length() >= 8)
                ? contentHash.substring(0, Math.min(32, contentHash.length()))
                : sanitize(trackId);
        return partyPrefix(partyId) + "tracks/" + leaf + ext;
    }

    @NonNull
    private static String sanitize(@NonNull String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @NonNull
    private static String extensionOf(@NonNull String name) {
        int i = name.lastIndexOf('.');
        if (i >= 0 && i < name.length() - 1) {
            String ext = name.substring(i).toLowerCase();
            if (ext.matches("\\.(mp3|m4a|aac|wav|ogg|flac|mp4)")) return ext;
        }
        return ".mp3";
    }
}
