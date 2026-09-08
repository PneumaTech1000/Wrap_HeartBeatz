package com.giga.tech1000.media_player.interfaces;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;
import java.util.List;

/**
 * Domain-level listener for media playback events.
 * Decouples the UI from Media3 specific classes.
 */
public interface IPlaybackCallback {
    void onSongChanged(@Nullable Song song);
    void onPlaybackStateChanged(boolean isPlaying, int playbackState);
    void onQueueChanged(List<Integer> queue, int currentIndex);
    void onRepeatModeChanged(int repeatMode);
    void onShuffleModeChanged(boolean enabled);
    void onSessionIdReady(int sessionId);
    void onProgressUpdate(long position, long duration);
    void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason);

    // Party Mode Updates
    default void onPartyHostDiscovered(PartyHost host) {}
    default void onPartyServiceRegistered(PartyHost host) {}
    default void onPartyHostCreated(PartyHost host) {}
    default void onPartyAuthSuccess() {}
    default void onPartyMetadataReceived(SyncPacket sync) {}
    default void onPartyConnectionFailed() {}
    default void onPartyDisconnected() {}
    default void onPartyGuestsUpdated(java.util.List<String> guests) {}
    default void onPartySetupRequired() {}
}
