package com.giga.tech1000.media_player.services;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSession.ConnectionResult;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;

import com.giga.tech1000.media_player.PlaybackManager;
import com.giga.tech1000.utils.statics.SessionEvents;
import com.giga.tech1000.media_player.database.setting.SettingRepository;
import com.giga.tech1000.media_player.engine.PartyRouter;
import com.giga.tech1000.media_player.interfaces.IPlaybackCallback;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.media_player.utils.enums.RepeatMode;
import com.giga.tech1000.media_player.utils.enums.ShuffleMode;
import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.giga.tech1000.utils.statics.SessionEvents;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@OptIn(markerClass = UnstableApi.class)
public class MediaPlayerService extends MediaLibraryService implements IPlaybackCallback {

    private static final String TAG = "MediaPlayerService";
    private MediaLibrarySession mediaLibrarySession;
    private PlaybackManager playbackManager;
    private PartyRouter partyRouter;
    private SettingRepository repo;
    private boolean settingsApplied = false;

    // Background & Foreground Management
    private ForegroundServiceManager foregroundServiceManager;
    private PartyStateRepository partyStateRepository;
    private ServiceLifecycleManager lifecycleManager;
    private WiFiStateMonitor wiFiStateMonitor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isApplyingSettings = false;

    /**
     * Shuts down the executor service to prevent resource leaks.
     * Should be called when the service is destroyed.
     */
    public void shutdownExecutor() {
        if (executor != null) {
            executor.shutdown();
            try {
                // Wait a moment for tasks to complete
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkAndApplySettings() {
        if (!settingsApplied && !isApplyingSettings && repo != null && repo.getCached() != null && SongRepository.getInstance().getCachedSongs() != null && !SongRepository.getInstance().getCachedSongs().isEmpty()) {
            isApplyingSettings = true;
            // Apply settings on background thread to avoid blocking service startup
            executor.execute(this::applySetting);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Initializing service");

        // Initialize repositories and managers early
        this.partyStateRepository = new PartyStateRepository(this);
        this.foregroundServiceManager = new ForegroundServiceManager(this, this);

        this.playbackManager = new PlaybackManager(this, this);

        repo = SettingRepository.getInstance(this);

        SongRepository.getInstance().getSongs().observe(this, songTreeMap -> {
            //this.playbackManager.updateTreeMapOfSongs(songTreeMap);
            checkAndApplySettings();
        });

        Player player = playbackManager.getPlayer();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        // 🔥 Set handleAudioFocus to FALSE because PlaybackManager handles it manually
        player.setAudioAttributes(audioAttributes, false);

        this.mediaLibrarySession = new MediaLibrarySession.Builder(this, player, new MediaLibraryCallback()).build();

        // Ensure PlaybackManager can update the session if the player instance changes (e.g. for Party Mode)
        this.playbackManager.setMediaSession(this.mediaLibrarySession);

        Intent intent = new Intent("com.giga.tech1000.heartbeatz.MainActivity");
        intent.setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        this.mediaLibrarySession.setSessionActivity(pendingIntent);

        this.partyRouter = new PartyRouter(this, playbackManager, mediaLibrarySession,
                foregroundServiceManager, partyStateRepository);

        // Initialize lifecycle manager for recovery from backgrounding
        this.lifecycleManager = new ServiceLifecycleManager(
                partyStateRepository,
                mediaLibrarySession,
                foregroundServiceManager
        );

        // Attempt recovery of party session if service was restarted
        this.lifecycleManager.attemptRecovery();

        // Set up WiFi monitoring for robustness
        this.wiFiStateMonitor = new WiFiStateMonitor(this, new WiFiStateMonitor.WiFiStateListener() {
            @Override
            public void onWiFiConnected() {
                Log.d(TAG, "WiFi connected - party session may resume");
                if (partyRouter != null && partyStateRepository != null) {
                    PartyState savedState = partyStateRepository.getSavedPartyState();
                    if (savedState != null && (savedState == PartyState.HOSTING || savedState == PartyState.JOINED)) {
                        Log.d(TAG, "WiFi restored during active party - initiating recovery");
                        lifecycleManager.attemptRecovery();
                    }
                }
            }

            @Override
            public void onWiFiDisconnected() {
                Log.d(TAG, "WiFi disconnected - party will be interrupted");
                if (partyRouter != null) {
                    // Notify UI about WiFi loss
                    if (mediaLibrarySession != null) {
                        Bundle extras = new Bundle();
                        extras.putString("error", "WiFi connection lost");
                        mediaLibrarySession.broadcastCustomCommand(
                                new SessionCommand(SessionEvents.EVENT_SETUP_REQUIRED, Bundle.EMPTY),
                                extras
                        );
                    }
                }
            }
        });
        this.wiFiStateMonitor.startMonitoring();

        Log.d(TAG, "Service initialization complete with background support");
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle explicit service actions (like leaving party from notification)
        if (intent != null && intent.getAction() != null) {
            if ("com.heartbeatz.action.LEAVE_PARTY".equals(intent.getAction())) {
                Log.d(TAG, "Leave party action received from notification");
                if (partyRouter != null) {
                    partyRouter.stopPartyMode();
                }
                if (lifecycleManager != null) {
                    lifecycleManager.clearSavedState();
                }
                if (foregroundServiceManager != null) {
                    foregroundServiceManager.stopPartyForeground();
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public MediaLibrarySession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaLibrarySession;
    }

    private void applySetting() {
        SettingEntity setting = repo.getCached();
        if (setting != null) {
            settingsApplied = true;
            Log.d(TAG, "applySetting: Restoring playback state. SongID: " + setting.getLastPlayedSongId() + ", Position: " + setting.getLastPlayedSongPosition());

            // 🔥 Accessing ExoPlayer MUST be done on the Main Thread.
            // Since this method is called from a background executor, we must post to the main handler.
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (playbackManager != null && playbackManager.getPlayer() != null) {
                    // Apply playback parameters
                    playbackManager.getPlayer().setPlaybackParameters(
                            playbackManager.getPlayer().getPlaybackParameters()
                                    .withPitch(setting.getPlaybackPitch())
                                    .withSpeed(setting.getPlaybackSpeed())
                    );

                    // Restore Shuffle and Repeat modes
                    playbackManager.setShuffleMode(setting.getShuffleValue().toBoolean());
                    playbackManager.onSetRepeatState(setting.getRepeatMode().toMedia3());

                    // Reconstruct queue if possible, or just load the last song
                    if (setting.getLastQueueSource() != ItemSource.NONE) {
                        playbackManager.setQueue(setting.getLastQueueSource(), setting.getLastPlayedSongId());
                        playbackManager.onSeekTo(setting.getLastPlayedSongPosition());
                        playbackManager.onPause(); // Start in paused state when restoring
                    }
                }
            });
        }
    }

    private void persistSetting() {
        try {
            if (playbackManager != null && playbackManager.getPlayer() != null && repo != null) {
                Player player = playbackManager.getPlayer();
                Song currentSong = playbackManager.getCurrentSong();

                // Capture all required values on the main thread before passing to background update
                long songId = currentSong != null ? currentSong.getId() : -1;
                long position = player.getCurrentPosition();
                float pitch = player.getPlaybackParameters().pitch;
                float speed = player.getPlaybackParameters().speed;
                boolean shuffleEnabled = player.getShuffleModeEnabled();
                int repeatMode = player.getRepeatMode();
                ItemSource queueSource = playbackManager.getQueueSource();
                List<Integer> currentQueue = playbackManager.getQueue();

                repo.update(current -> {
                    try {
                        if (songId != -1) {
                            current.setLastPlayedSongId(songId);
                        }
                        current.setLastPlayedSongPosition(position);
                        current.setPlaybackPitch(pitch);
                        current.setPlaybackSpeed(speed);

                        current.setShuffleValue(ShuffleMode.fromBoolean(shuffleEnabled));
                        current.setRepeatMode(RepeatMode.fromMedia3(repeatMode));
                        current.setLastQueueSource(queueSource);
                        current.setLastQueueIdList(currentQueue);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in settings update lambda", e);
                    }
                });
                Log.d(TAG, "persistSetting: Queued update for settings");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error persisting settings", e);
        }
    }

    @Override
    public void onSongChanged(Song song) {
        persistSetting();
        if (partyRouter != null) {
            partyRouter.broadcastMetadata(song);
        }

        // Broadcast custom command for UI sync if needed
        if (mediaLibrarySession != null) {
            Bundle extras = new Bundle();
            extras.putParcelable("song", song);
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EXTRA_SONG_UPDATE, Bundle.EMPTY), extras);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, int playbackState) {
        persistSetting();
    }

    @Override
    public void onQueueChanged(List<Integer> queue, int currentIndex) {
        // Handle queue updates if needed for UI
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
    }

    @Override
    public void onShuffleModeChanged(boolean enabled) {
    }

    @Override
    public void onSessionIdReady(int sessionId) {
        // Handle audio session ID for visualizers
    }

    @Override
    public void onProgressUpdate(long position, long duration) {
        // Periodically persist position or handle in onPlaybackStateChanged
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {

    }

    @Override
    public void onPartyHostDiscovered(PartyHost host) {
        if (mediaLibrarySession != null) {
            Bundle extras = new Bundle();
            extras.putParcelable("party_host", host);
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EXTRA_HOST_DISCOVERED_UPDATE, Bundle.EMPTY), extras);
        }
    }

    @Override
    public void onPartyServiceRegistered(PartyHost host) {
        if (mediaLibrarySession != null) {
            Bundle extras = new Bundle();
            extras.putParcelable("party_host", host);
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EXTRA_SERVICE_REGISTERED_UPDATE, Bundle.EMPTY), extras);
        }
    }

    @Override
    public void onPartyHostCreated(PartyHost host) {
        IPlaybackCallback.super.onPartyHostCreated(host);
    }

    @Override
    public void onPartyAuthSuccess() {
        if (mediaLibrarySession != null) {
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EVENT_AUTH_SUCCESS, Bundle.EMPTY), Bundle.EMPTY);
        }
    }

    @Override
    public void onPartyMetadataReceived(SyncPacket sync) {
        if (mediaLibrarySession != null) {
            Bundle extras = new Bundle();
            extras.putParcelable("sync_packet", sync);
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EVENT_METADATA_RECEIVED, Bundle.EMPTY), extras);
        }
    }

    @Override
    public void onPartyConnectionFailed() {
        if (mediaLibrarySession != null) {
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EVENT_CONNECTION_FAILED, Bundle.EMPTY), Bundle.EMPTY);
        }
    }

    @Override
    public void onPartyDisconnected() {
        if (mediaLibrarySession != null) {
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EVENT_DISCONNECTED, Bundle.EMPTY), Bundle.EMPTY);
        }
    }

    @Override
    public void onPartyGuestsUpdated(java.util.List<String> guests) {
        if (mediaLibrarySession != null) {
            Bundle extras = new Bundle();
            extras.putStringArrayList(SessionEvents.EXTRA_GUEST_LIST, new java.util.ArrayList<>(guests));
            extras.putInt(SessionEvents.EXTRA_GUEST_COUNT, guests != null ? guests.size() : 0);
            mediaLibrarySession.broadcastCustomCommand(new SessionCommand(SessionEvents.EVENT_GUESTS_UPDATED, Bundle.EMPTY), extras);
        }
    }

    @Override
    public void onPartySetupRequired() {
        IPlaybackCallback.super.onPartySetupRequired();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Cleaning up service resources");

        // Persist current playback state
        persistSetting();

        // Shut down background executor
        if (executor != null) {
            executor.shutdown();
        }

        // Clean up WiFi monitoring
        if (wiFiStateMonitor != null && wiFiStateMonitor.isMonitoring()) {
            wiFiStateMonitor.stopMonitoring();
            wiFiStateMonitor = null;
        }

        // Notify lifecycle manager of destruction
        if (lifecycleManager != null) {
            lifecycleManager.onServiceDestroying();
        }

        // Save party state if active (for recovery on restart)
        if (partyRouter != null && partyStateRepository != null) {
            // PartyRouter should have already persisted state, but ensure cleanup
            partyRouter.persistCurrentState();
        }

        // Clean up playback
        if (playbackManager != null) {
            playbackManager.release();
        }

        // Release media session
        if (mediaLibrarySession != null) {
            mediaLibrarySession.release();
            mediaLibrarySession = null;
        }

        // Stop foreground if still running
        if (foregroundServiceManager != null && foregroundServiceManager.isPartyModeActive()) {
            foregroundServiceManager.stopPartyForeground();
        }

        Log.d(TAG, "Service cleanup complete");
        super.onDestroy();
    }

    private class MediaLibraryCallback implements MediaLibrarySession.Callback {
        @NonNull
        @Override
        public ConnectionResult onConnect(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controllerInfo) {
            ConnectionResult.AcceptedResultBuilder builder = new ConnectionResult.AcceptedResultBuilder(session);
            SessionCommands.Builder commands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon();

            // Support both old and new command contracts for backwards compatibility
            // Legacy SessionEvents commands
            commands.add(new SessionCommand(SessionEvents.EXTRA_SONG_UPDATE, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EXTRA_CREATE_PARTY_UPDATE, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EXTRA_JOIN_PARTY_UPDATE, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EXTRA_HOST_DISCOVERED_UPDATE, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EXTRA_SERVICE_REGISTERED_UPDATE, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_AUTH_SUCCESS, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_METADATA_RECEIVED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_CONNECTION_FAILED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_DISCONNECTED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_GUESTS_UPDATED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_SETUP_REQUIRED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_START_DISCOVERY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_CREATE_PARTY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_JOIN_PARTY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_LEAVE_PARTY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_PLAY_QUEUE, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_UPDATE_FOREGROUND_NOTIFICATION, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_HOST_DISCOVERED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_HOST_CREATED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_JOINED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_AUTH_SUCCESS, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_METADATA_RECEIVED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_GUESTS_UPDATED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_LEFT, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_PARTY_CONNECTION_FAILED, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.EVENT_SERVICE_RECOVERED, Bundle.EMPTY));

            // Party session commands
            commands.add(new SessionCommand(SessionEvents.ACTION_START_DISCOVERY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_STOP_DISCOVERY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_CREATE_PARTY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_JOIN_PARTY, Bundle.EMPTY));
            commands.add(new SessionCommand(SessionEvents.ACTION_LEAVE_PARTY, Bundle.EMPTY));

            return builder.setAvailableSessionCommands(commands.build()).build();
        }

        @NonNull
        @Override
        public ListenableFuture<SessionResult> onCustomCommand(
                @NonNull MediaSession session,
                @NonNull MediaSession.ControllerInfo controllerInfo,
                @NonNull SessionCommand customCommand,
                @NonNull Bundle args) {

            try {
                String action = customCommand.customAction;

                // Handle SessionEvents commands
                if (SessionEvents.ACTION_START_DISCOVERY.equals(action)) {
                    if (partyRouter != null) partyRouter.startDiscovery();
                } else if (SessionEvents.ACTION_STOP_DISCOVERY.equals(action)) {
                    if (partyRouter != null) partyRouter.stopDiscovery();
                } else if (SessionEvents.ACTION_CREATE_PARTY.equals(action)) {
                    if (partyRouter != null) {
                        String name = args.getString(SessionEvents.EXTRA_PARTY_NAME, "HeartBeatz Party");
                        String pin = args.getString(SessionEvents.EXTRA_PARTY_PIN, "");
                        partyRouter.startHostMode(name, pin);
                    }
                } else if (SessionEvents.ACTION_JOIN_PARTY.equals(action)) {
                    if (partyRouter != null) {
                        PartyHost host = args.getParcelable(SessionEvents.EXTRA_PARTY_HOST);
                        String pin = args.getString(SessionEvents.EXTRA_PARTY_PIN, "");
                        if (host != null) partyRouter.connectToHost(host, pin);
                    }
                } else if (SessionEvents.ACTION_LEAVE_PARTY.equals(action)) {
                    if (partyRouter != null) partyRouter.stopPartyMode();
                } else if (SessionEvents.ACTION_PLAY_QUEUE.equals(action)) {
                    handlePlayQueue(args);
                } else if (SessionEvents.ACTION_UPDATE_FOREGROUND_NOTIFICATION.equals(action)) {
                    // Optional: update notification with latest info
                    if (foregroundServiceManager != null) {
                        String partyName = args.getString(SessionEvents.EXTRA_PARTY_NAME);
                        int guestCount = args.getInt(SessionEvents.EXTRA_GUEST_COUNT, 0);
                        if (partyName != null) {
                            foregroundServiceManager.updateGuestCount(guestCount);
                        }
                    }
                } else if (SessionEvents.EXTRA_CREATE_PARTY_UPDATE.equals(action)) {
                    if (partyRouter != null) {
                        String name = args.getString(SessionEvents.EXTRA_PARTY_NAME, "HeartBeatz Party");
                        String pin = args.getString(SessionEvents.EXTRA_PARTY_PIN, "");
                        partyRouter.startHostMode(name, pin);
                    }
                } else if (SessionEvents.EXTRA_JOIN_PARTY_UPDATE.equals(action)) {
                    if (partyRouter != null) {
                        PartyHost host = args.getParcelable("party_host");
                        String pin = args.getString(SessionEvents.EXTRA_PARTY_PIN, "");
                        if (host != null) partyRouter.connectToHost(host, pin);
                    }
                }

                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
            } catch (Exception e) {
                Log.e(TAG, "Error handling custom command: " + customCommand.customAction, e);
                return Futures.immediateFuture(new SessionResult(SessionError.ERROR_UNKNOWN));
            }
        }

        /**
         * Helper to handle play queue commands
         */
        private void handlePlayQueue(@NonNull Bundle args) {
            if (playbackManager != null) {
                List<Integer> queue = args.getIntegerArrayList(SessionEvents.EXTRA_QUEUE_TRACKS);
                int index = args.getInt(SessionEvents.EXTRA_TRACK_INDEX, 0);
                String sourceVal = args.getString(SessionEvents.EXTRA_SOURCE);
                ItemSource source = ItemSource.ALL_SONGS; // Default
                if (sourceVal != null) {
                    source = ItemSource.fromValue(sourceVal);
                }

                if (queue != null) {
                    playbackManager.onSetQueue(queue);
                    playbackManager.setSetQueueSource(source);
                    playbackManager.onPlayIndex(index, true);
                    Log.i("PlaybackManager", "end of service");
                }
            }
        }
    }
}
