package com.giga.tech1000.media_player;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaLibraryService;

import com.giga.tech1000.media_player.engine.ExoPlayerEngine;
import com.giga.tech1000.media_player.interfaces.IPlaybackCallback;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.media_player.utils.PlaybackListener;
import com.giga.tech1000.media_player.utils.PlaybackSubThread;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.party_mode.core.PartyState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

@OptIn(markerClass = UnstableApi.class)
public class PlaybackManager {

    private ItemSource queueSource;
    private final Context context;
    private final IPlaybackCallback callback;
    private final Player.Listener listener;
    private final AudioManager audioManager;
    private final PlaybackSubThread thread;

    private ExoPlayer exoPlayer;
    private AudioFocusRequest audioFocusRequest;
    private MediaLibraryService.MediaLibrarySession mediaLibrarySession;

    private boolean hasAudioFocus = false;
    private boolean playOnFocusGain = false;

    private int currentQueueIndex = -1;

    private Song currentSong;
    private List<Integer> queue = new ArrayList<>();
    private TreeMap<Integer, Song> treeMapOfSongs;

    private final ExoPlayerEngine exoPlayerEngine;
    private PartyState partyState = PartyState.IDLE;

    // DSP Audio Processor for advanced effects
    private DspAudioProcessor dspAudioProcessor;

    public PlaybackManager(Context context, IPlaybackCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.thread = new PlaybackSubThread(this::onUpdatePlaybackState);

        // Create listener inline to avoid circular dependencies
        this.listener = new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(@Player.State int playbackState) {
                if (callback != null) {
                    callback.onPlaybackStateChanged(getPlayer().getPlayWhenReady(), playbackState);
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (callback != null) {
                    callback.onMediaItemTransition(mediaItem, reason);
                }
            }

            @Override
            public void onRepeatModeChanged(int replayMode) {
                if (callback != null) {
                    callback.onRepeatModeChanged(replayMode);
                }
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean enabled) {
                if (callback != null) {
                    callback.onShuffleModeChanged(enabled);
                }
            }
        };

        this.exoPlayerEngine = new ExoPlayerEngine();
        // Initialize DSP audio processor
        this.dspAudioProcessor = new DspAudioProcessor();
        initializeExoPlayer();
    }

    private void initializeExoPlayer() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
        }

        exoPlayer = exoPlayerEngine.createPlayer(context, partyState);
        // Insert DSP audio processor into the audio chain
        exoPlayer.setAudioProcessor(dspAudioProcessor);
        exoPlayer.addListener(listener);

        // 🔥 If we have an active session, we must update its player reference.
        // This is critical when switching between normal, host, and client modes.
        if (mediaLibrarySession != null) {
            mediaLibrarySession.setPlayer(exoPlayer);
        }
    }

    public void onAudioFocusChanged(int focusChange) {
        switch (focusChange) {

            case AudioManager.AUDIOFOCUS_GAIN:
                hasAudioFocus = true;

                if (exoPlayer != null) {
                    exoPlayer.setVolume(1.0f);

                    if (playOnFocusGain) {
                        playOnFocusGain = false;
                        exoPlayer.play();
                    }
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                hasAudioFocus = false;

                if (exoPlayer != null) {
                    playOnFocusGain = false;
                    exoPlayer.pause();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                hasAudioFocus = false;

                if (exoPlayer != null && exoPlayer.getPlayWhenReady()) {
                    playOnFocusGain = true;
                    exoPlayer.pause();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (exoPlayer != null) {
                    exoPlayer.setVolume(0.2f);
                }
                break;
        }
    }

    private boolean requestAudioFocus() {
        if (audioFocusRequest == null) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(audioAttributes.getPlatformAudioAttributes()).setAcceptsDelayedFocusGain(true).setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    onAudioFocusChanged(focusChange);
                }
            }).build();
        }

        int result = audioManager.requestAudioFocus(audioFocusRequest);

        switch (result) {
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                hasAudioFocus = true;
                playOnFocusGain = false;
                return true;

            case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                hasAudioFocus = false;
                playOnFocusGain = true;
                return false;

            default:
                hasAudioFocus = false;
                playOnFocusGain = false;
                return false;
        }
    }

    private void abandonAudioFocus() {
        if (!hasAudioFocus) return;

        audioManager.abandonAudioFocusRequest(audioFocusRequest);
        hasAudioFocus = false;
    }


    public Player getPlayer() {
        return exoPlayer;
    }

    public int getAudioSessionId() {
        return exoPlayer != null ? exoPlayer.getAudioSessionId() : -1;
    }

    public void setPartyState(PartyState newState) {
        if (this.partyState == newState) return;
        this.partyState = newState;
        initializeExoPlayer();
    }


    public void onPlay() {
        if (exoPlayer != null && (hasAudioFocus || requestAudioFocus())) {
            exoPlayer.play();
        }
    }

    public void onPause() {
        if (exoPlayer != null) exoPlayer.pause();
    }

    public void onStop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        abandonAudioFocus();
    }

    public void onSeekTo(long position) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(position);
            onFlush();
        }
    }


    /**
     * Called when a specific index in the queue is requested to be played.
     * This method now handles setting the entire queue as MediaItems in ExoPlayer.
     */
    public void onPlayIndex(int queueIndex, boolean isPlayingReady) {
        Log.i("PlaybackManager", "onPlayIndex: reached, index=" + queueIndex);
        if (exoPlayer == null) return;

        // Ensure we have audio focus if we want to play immediately
        if (isPlayingReady && !hasAudioFocus) {
            requestAudioFocus();
        }

        if (queue.isEmpty() || queueIndex < 0 || queueIndex >= queue.size()) {
            Log.e("PlaybackManager", "Queue is empty or index out of bounds: " + queueIndex);
            return;
        }


        TreeMap<Integer, Song> treeMapOfSongs = SongRepository.getInstance().getCachedSongs();
        if (treeMapOfSongs == null || treeMapOfSongs.isEmpty()) {
            Log.w("PlaybackManager", "Song repository is empty. Playback might fail.");
        }

        // Map the entire queue to MediaItems
        List<MediaItem> mediaItems = new ArrayList<>();
        for (int songId : queue) {
            Song song = treeMapOfSongs != null ? treeMapOfSongs.get(songId) : null;
            if (song != null) {
                mediaItems.add(convertToMediaItem(song));
            } else {
                Log.w("PlaybackManager", "Song with ID " + songId + " not found in repository.");
            }
        }

        if (mediaItems.isEmpty()) {
            Log.e("PlaybackManager", "No valid MediaItems found for the queue.");
            return;
        }

        // Set the items and start from the specific index
        exoPlayer.setMediaItems(mediaItems, queueIndex, C.TIME_UNSET);
        exoPlayer.setPlayWhenReady(isPlayingReady);
        exoPlayer.prepare();

        this.currentQueueIndex = queueIndex;
        int id = queue.get(queueIndex);
        this.currentSong = treeMapOfSongs != null ? treeMapOfSongs.get(id) : null;

        if (callback != null) {
            callback.onSongChanged(currentSong);
            callback.onQueueChanged(new ArrayList<>(queue), currentQueueIndex);
        }
    }

    public void onPlayerReady() {
        if (exoPlayer.getPlayWhenReady()) this.onPlay();
    }

    private MediaItem convertToMediaItem(Song song) {
        MediaItem.Builder builder = new MediaItem.Builder().setMediaId(String.valueOf(song.getId())).setUri(song.getUri()).setMediaMetadata(new MediaMetadata.Builder().setTitle(song.getTitle()).setArtist(song.getArtist()).setAlbumTitle(song.getAlbum()).setArtworkUri(song.getAlbumArt()).build());

        if (song.getMimeType() != null) {
            builder.setMimeType(song.getMimeType());
        }

        return builder.build();
    }

    public void onAudioCompleted() {
        if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ONE) {
            exoPlayer.seekTo(0);
            exoPlayer.play();
        } else if (exoPlayer.getShuffleModeEnabled()) {
            onPlayIndex(new Random().nextInt(queue.size()), true);
        } else {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNext();
            } else if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) {
                onPlayIndex(0, true);
            }
        }
    }

    public void onUpdatePlaybackState() {
        if (callback != null && exoPlayer != null) {
            callback.onPlaybackStateChanged(exoPlayer.getPlayWhenReady(), exoPlayer.getPlaybackState());
            callback.onProgressUpdate(exoPlayer.getCurrentPosition(), exoPlayer.getDuration());
        }
    }

    public boolean canPlayNext() {
        return exoPlayer != null && exoPlayer.hasNextMediaItem();
    }

    public boolean canPlayPrev() {
        return exoPlayer != null && exoPlayer.hasPreviousMediaItem();
    }

    public void onPlayNext() {
        if (exoPlayer != null) exoPlayer.seekToNext();
    }

    public void onPlayPrevious() {
        if (exoPlayer != null) exoPlayer.seekToPrevious();
    }

    public void setSetQueueSource(ItemSource source) {
        this.queueSource = source;
    }

    public void onSetRepeatState(int repeatMode) {
        exoPlayer.setRepeatMode(repeatMode);
    }

    public void setShuffleMode(boolean enabled) {
        exoPlayer.setShuffleModeEnabled(enabled);
    }

    public void setPlaybackSpeed(float speed) {
        exoPlayer.setPlaybackParameters(new PlaybackParameters(speed, exoPlayer.getPlaybackParameters().pitch));
    }

    public void setPlaybackPitch(float pitch) {
        exoPlayer.setPlaybackParameters(new PlaybackParameters(exoPlayer.getPlaybackParameters().speed, pitch));
    }



    public float getPlaybackSpeed() {
        return exoPlayer.getPlaybackParameters().speed;
    }

    public float getPlaybackPitch() {
        return exoPlayer.getPlaybackParameters().pitch;
    }

    public ItemSource getQueueSource() {
        return queueSource;
    }

    public TreeMap<Integer, Song> getTreeMapOfSongs() {
        return SongRepository.getInstance().getCachedSongs();
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public List<Integer> getQueue() {
        return queue;
    }

    public int getCurrentQueueIndex() {
        return currentQueueIndex;
    }

    public void onSetQueue(List<Integer> queue) {
        this.queue = queue;
    }

    public void onUpdateIndex(int index) {
        this.currentQueueIndex = index;
    }

    public PlaybackSubThread getThread() {
        return thread;
    }

    public void setSessionId(int id) {
        Log.i("PlaybackManager", "setSessionId: " + id);
        if (callback != null) callback.onSessionIdReady(id);
    }

    public void setMediaSession(MediaLibraryService.MediaLibrarySession mediaLibrarySession) {
        this.mediaLibrarySession = mediaLibrarySession;
    }


    public void onFlush() {
        // No-op in new architecture as we use standard HTTP streaming
    }

    public void release() {
        if (exoPlayer != null) {
            exoPlayer.removeListener(listener);
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        // Release DSP audio processor
        if (dspAudioProcessor != null) {
            dspAudioProcessor.release();
        }
        abandonAudioFocus();
        if (thread != null) {
            thread.stop();
        }
    }

    public void setQueue(ItemSource lastQueueSource, long lastPlayedSongId) {
        this.setSetQueueSource(lastQueueSource);
        // set song id
    }
}
