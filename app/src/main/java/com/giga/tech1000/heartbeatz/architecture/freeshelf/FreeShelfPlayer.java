package com.giga.tech1000.heartbeatz.architecture.freeshelf;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;

/**
 * Plays a shelf track via the shared Media3 path (HTTP progressive).
 * Shows attribution for licenses that require it (caller displays UI).
 */
public final class FreeShelfPlayer {

    private static final String TAG = "FreeShelf";

    private final PlaybackStateRepository playback;
    @Nullable private FreeShelfTrack lastPlayed;

    public FreeShelfPlayer(@NonNull PlaybackStateRepository playback) {
        this.playback = playback;
    }

    /**
     * @return attribution string if the UI should surface a credit (CC-BY etc.), else null
     */
    @Nullable
    public String play(@NonNull FreeShelfTrack track) {
        if (!track.isPlayable()) {
            Log.w(TAG, "not playable: " + track.id);
            return null;
        }
        lastPlayed = track;
        String mediaId = "freeshelf:" + track.id;
        playback.playPartyStream(
                track.streamUrl,
                mediaId,
                track.title,
                track.artist,
                track.album != null ? track.album : "Legal Free Shelf",
                0L,
                true);
        Log.i(TAG, "play id=" + track.id + " license=" + track.license
                + " url=" + track.streamUrl);
        if (track.license.requiresAttribution) {
            return track.attribution;
        }
        // Still return soft credit for PD/CC0 so UI can show source optionally
        return track.attribution;
    }

    @Nullable
    public FreeShelfTrack getLastPlayed() {
        return lastPlayed;
    }
}
