package com.giga.tech1000.media_player;

import android.content.Context;
import android.media.session.PlaybackState;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.legacy.PlaybackStateCompat;

import com.giga.tech1000.media_player.interfaces.IPlaybackCallback;
import com.giga.tech1000.media_player.interfaces.IPlayerCallback;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.media_player.utils.PlaybackSubThread;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.giga.tech1000.utils.statics.SessionEvents;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

/**
 * Bridge between the UI and Media3 MediaController.
 * Maps low-level Media3 events from MediaPlayerController to high-level Song-based callbacks.
 */
@OptIn(markerClass = UnstableApi.class)
public class CorePlayer implements MediaPlayerController.ControllerEventsListener {

    private final Context context;
    private final MediaPlayerController controllerManager;
    private final IPlaybackCallback uiCallback;
    private IPlayerCallback inputCallback;
    
    private final PlaybackSubThread pollingThread;

    private final List<IPlaybackCallback> listeners = new java.util.ArrayList<>();

    public CorePlayer(Context context, IPlaybackCallback uiCallback) {
        this.context = context;
        this.uiCallback = uiCallback;
        if (uiCallback != null) listeners.add(uiCallback);
        this.controllerManager = new MediaPlayerController(context, this);
        
        this.pollingThread = new PlaybackSubThread(() -> {
            MediaController controller = controllerManager.getMediaController();
            if (controller != null) {
                long pos = controller.getCurrentPosition();
                long dur = controller.getDuration();
                for (IPlaybackCallback listener : listeners) {
                    listener.onProgressUpdate(pos, dur);
                }
            }
        });
    }

    public void onStart() {
        controllerManager.onStart();
    }

    public void onDestroy() {
        pollingThread.release();
        controllerManager.onDestroy();
        listeners.clear();
    }

    public void addListener(IPlaybackCallback listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(IPlaybackCallback listener) {
        listeners.remove(listener);
    }

    public MediaController getMediaController() {
        return controllerManager.getMediaController();
    }

    public Song getCurrentSong() {
        MediaController controller = getMediaController();
        if (controller != null) {
            MediaItem mediaItem = controller.getCurrentMediaItem();
            if (mediaItem != null) {
                try {
                    int id = Integer.parseInt(mediaItem.mediaId);
                    return SongRepository.getInstance().getCachedSongs().get(id);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    public IPlayerCallback getCallback() {
        if (inputCallback == null) {
            inputCallback = new IPlayerCallback() {
                private MediaController get() {
                    return controllerManager.getMediaController();
                }

                @Override
                public void onClickPlay(int queueIndex, List<Integer> queue, ItemSource source) {
                    Log.d("CorePlayer", "onClickPlay: " + queueIndex);
                    MediaController c = get();
                    if (c != null) {
                        sendPlayQueueCommand(c, queueIndex, queue, source);
                    } else {
                        ListenableFuture<MediaController> future = controllerManager.getControllerFuture();
                        if (future != null) {
                            future.addListener(() -> {
                                try {
                                    MediaController controller = future.get();
                                    sendPlayQueueCommand(controller, queueIndex, queue, source);
                                } catch (ExecutionException | InterruptedException e) {
                                    Log.e("CorePlayer", "Failed to get MediaController for play command", e);
                                }
                            }, MoreExecutors.directExecutor());
                        } else {
                            Log.e("CorePlayer", "MediaController and future are both null");
                        }
                    }
                }

                private void sendPlayQueueCommand(MediaController c, int queueIndex, List<Integer> queue, ItemSource source) {
                    Bundle extras = new Bundle();
                    extras.putIntegerArrayList(SessionEvents.EXTRA_QUEUE_TRACKS, new ArrayList<>(queue));
                    extras.putInt(SessionEvents.EXTRA_TRACK_INDEX, queueIndex);
                    extras.putString(SessionEvents.EXTRA_SOURCE, source.getValue());
                    c.sendCustomCommand(new SessionCommand(SessionEvents.ACTION_PLAY_QUEUE, Bundle.EMPTY), extras);
                }

                @Override
                public void onClickPlayIndex(int index) {
                    if (get() != null) get().seekToDefaultPosition(index);
                }

                @Override
                public void onClickPlayNext() {
                    if (get() != null) get().seekToNext();
                }

                @Override
                public void onClickPlayPrev() {
                    if (get() != null) get().seekToPrevious();
                }

                @Override
                public void onClickPlayPause() {
                    MediaController c = get();
                    if (c == null) return;
                    if (c.isPlaying()) c.pause();
                    else c.play();
                }

                @Override
                public void onSetSeekbar(int position) {
                    if (get() != null) {
                        get().seekTo(position);
                        uiCallback.onProgressUpdate(position, get().getDuration());
                    }
                }

                @Override
                public void onSetRepeatState(int state) {
                    if (get() != null) get().setRepeatMode(state);
                }

                @Override
                public void onSetShuffleMode(boolean isShuffleMode) {
                    if (get() != null) get().setShuffleModeEnabled(isShuffleMode);
                }

                @Override
                public void onSetPlaybackSpeed(float speed) {
                    if (get() != null) get().setPlaybackSpeed(speed);
                }

                @Override
                public void onSetPlaybackPitch(float pitch) {
                    if (get() != null) {
                        Bundle b = new Bundle();
                        b.putFloat(SessionEvents.EXTRA_PITCH, pitch);
                        get().sendCustomCommand(new SessionCommand(SessionEvents.EXTRA_PITCH_UPDATE, Bundle.EMPTY), b);
                    }
                }

                @Override
                public void onCreateParty(String name, String pin) {
                    MediaController c = get();
                    if (c != null) {
                        Bundle b = new Bundle();
                        b.putString(SessionEvents.EXTRA_PARTY_NAME, name);
                        b.putString(SessionEvents.EXTRA_PARTY_PIN, pin);
                        c.sendCustomCommand(new SessionCommand(SessionEvents.EXTRA_CREATE_PARTY_UPDATE, Bundle.EMPTY), b);
                    }
                }

                @Override
                public void onJoinParty(PartyHost host, String pin) {
                    MediaController c = get();
                    if (c != null) {
                        Bundle b = new Bundle();
                        b.putParcelable("party_host", host);
                        b.putString(SessionEvents.EXTRA_PARTY_PIN, pin);
                        c.sendCustomCommand(new SessionCommand(SessionEvents.EXTRA_JOIN_PARTY_UPDATE, Bundle.EMPTY), b);
                    }
                }

                @Override
                public void onJoinAddress(String ip, int port, String pin) {
                    MediaController c = get();
                    if (c != null) {
                        Bundle b = new Bundle();
                        b.putString("ip", ip);
                        b.putInt("port", port);
                        b.putString(SessionEvents.EXTRA_PARTY_PIN, pin);
                        c.sendCustomCommand(new SessionCommand("JOIN_ADDRESS", Bundle.EMPTY), b);
                    }
                }

                @Override
                public void onLeaveParty() {
                    MediaController c = get();
                    if (c != null) {
                        c.sendCustomCommand(new SessionCommand("LEAVE_PARTY", Bundle.EMPTY), Bundle.EMPTY);
                    }
                }

                @Override
                public void onAcceptHandover(String pin) {
                    MediaController c = get();
                    if (c != null) {
                        Bundle b = new Bundle();
                        b.putString(SessionEvents.EXTRA_PARTY_PIN, pin);
                        c.sendCustomCommand(new SessionCommand("ACCEPT_HANDOVER", Bundle.EMPTY), b);
                    }
                }

                @Override
                public void onDeclineHandover() {
                    MediaController c = get();
                    if (c != null) {
                        c.sendCustomCommand(new SessionCommand("DECLINE_HANDOVER", Bundle.EMPTY), Bundle.EMPTY);
                    }
                }

                @Override
                public void onInitiateHandover(String guestName) {
                    MediaController c = get();
                    if (c != null) {
                        Bundle b = new Bundle();
                        b.putString("guest_name", guestName);
                        c.sendCustomCommand(new SessionCommand("INITIATE_HANDOVER", Bundle.EMPTY), b);
                    }
                }

                @Override
                public void onStartDiscovery() {
                    MediaController c = get();
                    if (c != null) {
                        c.sendCustomCommand(new SessionCommand("START_DISCOVERY", Bundle.EMPTY), Bundle.EMPTY);
                    }
                }

                @Override
                public void onStopDiscovery() {
                    MediaController c = get();
                    if (c != null) {
                        c.sendCustomCommand(new SessionCommand("STOP_DISCOVERY", Bundle.EMPTY), Bundle.EMPTY);
                    }
                }

                @Override
                public void onUpdateQueue(List<Integer> q, int i, ItemSource s) {
                }

                @Override
                public void onDestroy() {
                    CorePlayer.this.onDestroy();
                }

                @Override
                public void onStart() {
                    CorePlayer.this.onStart();
                }
            };
        }
        return inputCallback;
    }

    // --- ControllerEventsListener Implementation ---

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        Song song = null;
        if (mediaItem != null) {
            try {
                int id = Integer.parseInt(mediaItem.mediaId);
                song = SongRepository.getInstance().getCachedSongs().get(id);
            } catch (NumberFormatException ignored) {}
        }
        for (IPlaybackCallback listener : listeners) {
            listener.onSongChanged(song);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean playWhenReady, int state) {
        for (IPlaybackCallback listener : listeners) {
            listener.onPlaybackStateChanged(playWhenReady, state);
        }
        if (state == Player.STATE_READY) {
            if (playWhenReady) pollingThread.start();
        } else if (!playWhenReady || state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
            pollingThread.stop();
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        for (IPlaybackCallback listener : listeners) {
            listener.onRepeatModeChanged(repeatMode);
        }
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean enabled) {
        for (IPlaybackCallback listener : listeners) {
            listener.onShuffleModeChanged(enabled);
        }
    }

    @Override
    public void onCustomCommand(@NonNull SessionCommand command, @NonNull Bundle args) {
        switch (command.customAction) {
            case SessionEvents.EXTRA_SESSION -> {
                int sessionId = args.getInt(SessionEvents.EXTRA_SESSION_ID);
                for (IPlaybackCallback listener : listeners) listener.onSessionIdReady(sessionId);
            }
            case SessionEvents.EXTRA_QUEUE_UPDATE -> {
                List<Integer> queue = args.getIntegerArrayList(SessionEvents.EXTRA_QUEUE);
                int index = args.getInt(SessionEvents.EXTRA_QUEUE_INDEX);
                for (IPlaybackCallback listener : listeners) listener.onQueueChanged(queue, index);
            }
            case SessionEvents.EXTRA_STATE_UPDATE -> {
                boolean isPlaying = args.getBoolean(SessionEvents.EXTRA_IS_PLAYING);
                int state = args.getInt(SessionEvents.EXTRA_PLAYBACK_STATE);
                long position = args.getLong(SessionEvents.EXTRA_POSITION);
                long duration = args.getLong(SessionEvents.EXTRA_DURATION);
                
                if (args.containsKey(SessionEvents.EXTRA_SESSION_ID)) {
                    int sid = args.getInt(SessionEvents.EXTRA_SESSION_ID);
                    for (IPlaybackCallback listener : listeners) listener.onSessionIdReady(sid);
                }

                MediaController mc = controllerManager.getMediaController();
                if (mc != null) {
                    MediaItem currentItem = mc.getCurrentMediaItem();
                    if (currentItem != null) {
                        onMediaItemTransition(currentItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
                    }
                }

                onPlaybackStateChanged(isPlaying, state);
                for (IPlaybackCallback listener : listeners) listener.onProgressUpdate(position, duration);
            }
            case SessionEvents.EXTRA_HOST_DISCOVERED_UPDATE -> {
                PartyHost host = args.getParcelable("party_host");
                if (host != null) {
                    for (IPlaybackCallback listener : listeners) listener.onPartyHostDiscovered(host);
                }
            }
            case SessionEvents.EXTRA_SERVICE_REGISTERED_UPDATE -> {
                PartyHost host = args.getParcelable("party_host");
                if (host != null) {
                    for (IPlaybackCallback listener : listeners) listener.onPartyServiceRegistered(host);
                }
            }
            case SessionEvents.EVENT_PARTY_HOST_CREATED -> {
                PartyHost host = args.getParcelable(SessionEvents.EXTRA_PARTY_HOST);
                if (host != null) {
                    for (IPlaybackCallback listener : listeners) listener.onPartyHostCreated(host);
                }
            }
            case SessionEvents.EVENT_PARTY_AUTH_SUCCESS, SessionEvents.EVENT_AUTH_SUCCESS -> {
                for (IPlaybackCallback listener : listeners) listener.onPartyAuthSuccess();
            }
            case SessionEvents.EVENT_METADATA_RECEIVED -> {
                SyncPacket sync = args.getParcelable("sync_packet");
                for (IPlaybackCallback listener : listeners) listener.onPartyMetadataReceived(sync);
            }
            case SessionEvents.EVENT_PARTY_GUESTS_UPDATED, SessionEvents.EVENT_GUESTS_UPDATED -> {
                java.util.ArrayList<String> guests = args.getStringArrayList(SessionEvents.EXTRA_GUEST_LIST);
                if (guests == null) guests = new java.util.ArrayList<>();
                for (IPlaybackCallback listener : listeners) listener.onPartyGuestsUpdated(guests);
            }
            case SessionEvents.EVENT_PARTY_CONNECTION_FAILED, SessionEvents.EVENT_CONNECTION_FAILED -> {
                for (IPlaybackCallback listener : listeners) listener.onPartyConnectionFailed();
            }
            case SessionEvents.EVENT_DISCONNECTED -> {
                for (IPlaybackCallback listener : listeners) listener.onPartyDisconnected();
            }
            case SessionEvents.EVENT_SETUP_REQUIRED -> {
                for (IPlaybackCallback listener : listeners) listener.onPartySetupRequired();
            }
            case SessionEvents.EVENT_HOTSPOT_STARTED -> {
                // Future: add onHotspotStarted to IPlaybackCallback
            }
            case SessionEvents.EVENT_HOTSPOT_STOPPED -> {
                // Future: add onHotspotStopped to IPlaybackCallback
            }
        }
    }
}
