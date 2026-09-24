package com.giga.tech1000.heartbeatz.view_models.extended_models;

import android.annotation.SuppressLint;
import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.architecture.repositories.EnhancedFirebasePartyHostRepository;
import com.giga.tech1000.heartbeatz.architecture.session.PartySession;
import com.giga.tech1000.heartbeatz.architecture.session.FirebasePartySession;
import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;
import com.giga.tech1000.heartbeatz.architecture.repositories.PartyHostRepository;
import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.giga.tech1000.heartbeatz.architecture.party.PartyLiveBridge;
import com.giga.tech1000.heartbeatz.architecture.party.PartyPlaybackSync;
import com.giga.tech1000.utils.interfaces.PartyModeUICallback;

/**
 * PartyViewModel - Modernized MVVM Architecture
 *
 * **NO SINGLETON DEPENDENCIES FROM UI**
 *
 * This ViewModel uses dependency injection instead of getInstance() calls.
 * All state is exposed through LiveData for reactive UI updates.
 *
 * Key Improvements:
 * 1. Dependencies injected in constructor (enables testing)
 * 2. No direct calls to MediaPlayerThread.getInstance() from UI
 * 3. No direct access to CorePlayer listeners
 * 4. Single source of truth for all state via LiveData
 * 5. Proper lifecycle management with cleanup in onCleared()
 * 6. Combines playback state + party state cohesively
 * 7. Type-safe, reactive architecture
 * 8. Full backward compatibility with legacy UI code *
 * NOTE: This implementation uses Firebase Realtime Database for party discovery
 * and management, replacing the previous UDP-based approach.
 */
@OptIn(markerClass = UnstableApi.class)
public class PartyViewModel extends AndroidViewModel {

    private static final String TAG = "PartyViewModel";

    // ============ INJECTED REPOSITORIES ============

    /** May be null until UIThread.init(); resolved lazily. */
    private PlaybackStateRepository playbackState;
    private final PartyHostRepository partyHost;
    private final PartySession partySession;

    // ============ PARTY STATE (Local) ============

    private final MutableLiveData<PartyState> partyState =
            new MutableLiveData<>(PartyState.IDLE);

    private final MutableLiveData<SyncPacket> currentSync =
            new MutableLiveData<>(null);

    private final MutableLiveData<String> partyError =
            new MutableLiveData<>(null);

    private final MutableLiveData<Boolean> isSetupRequired =
            new MutableLiveData<>(false);

    private String pendingPartyName = "";
    private String pendingPartyPin = "";

    // ============ CONSTRUCTORS ============

    /**
     * Default constructor - creates repositories automatically
     * Uses Firebase-based repositories for party management
     */
    public PartyViewModel(@NonNull Application application) {
        // PartySession from AppContainer (single instance). Playback attached later.
        this(application,
                HeartBeatzApp.container(application).playbackRepositoryOrNull(),
                HeartBeatzApp.container(application).partySession());
    }

    /**
     * Constructor with dependency injection (for testing / after UIThread.init)
     */
    public PartyViewModel(
            @NonNull Application application,
            @Nullable PlaybackStateRepository playbackStateRepo,
            @NonNull PartySession partySession) {
        super(application);

        this.playbackState = playbackStateRepo;
        this.partySession = partySession;
        if (partySession instanceof FirebasePartySession) {
            this.partyHost = ((FirebasePartySession) partySession).asHostRepository();
        } else {
            throw new IllegalArgumentException("PartySession must expose PartyHostRepository for this ViewModel revision");
        }

        Log.d(TAG, "PartyViewModel created playbackReady=" + (playbackStateRepo != null));
        initializeStateObservers();
    }

    /** @deprecated prefer PartySession constructor */
    @Deprecated
    public PartyViewModel(
            @NonNull Application application,
            @Nullable PlaybackStateRepository playbackStateRepo,
            @NonNull PartyHostRepository partyHostRepo) {
        super(application);
        this.playbackState = playbackStateRepo;
        this.partyHost = partyHostRepo;
        this.partySession = null;
        Log.d(TAG, "PartyViewModel (legacy repo ctor) playbackReady=" + (playbackStateRepo != null));
        initializeStateObservers();
    }

    /**
     * Bind shared Media3 playback repository once UIThread.init() has run.
     */
    public void attachPlaybackRepository(@NonNull PlaybackStateRepository repo) {
        if (this.playbackState == repo) return;
        this.playbackState = repo;
        Log.d(TAG, "PlaybackStateRepository attached");
        initializePlaybackObservers();
    }

    @NonNull
    private PlaybackStateRepository requirePlayback() {
        if (playbackState == null) {
            try {
                UIThread ui = HeartBeatzApp.container(getApplication()).uiThreadOrNull();
                if (ui != null) {
                    playbackState = ui.getPlaybackStateRepository();
                }
            } catch (Exception e) {
                Log.w(TAG, "PlaybackStateRepository not ready: " + e.getMessage());
            }
        }
        if (playbackState == null) {
            throw new IllegalStateException(
                    "PlaybackStateRepository not ready — call UIThread.init() then attachPlaybackRepository()");
        }
        return playbackState;
    }

    /**
     * Set up observers to sync repository state
     */
    private void initializePlaybackObservers() {
        if (playbackState == null) return;
        // Do not drive PartyState from PlaybackState party host — repository is source of truth
    }

    private void initializeStateObservers() {
        if (playbackState != null) {
            initializePlaybackObservers();
        }

        // Host created a party successfully
        partyHost.getHostedParty().observeForever(host -> {
            if (host != null && partyHost.isHosting()) {
                partyState.postValue(PartyState.HOSTING);
                pendingPartyName = host.getPartyName();
                Log.d(TAG, "State → HOSTING (" + host.getPartyName() + ")");
                startHostBridge(host.getPartyId());
            }
        });

        // Guest: connected host is the party we joined (do NOT force IDLE on null)
        partyHost.getConnectedHost().observeForever(host -> {
            if (host != null) {
                PartyState cur = partyState.getValue();
                if (cur == PartyState.CONNECTING || cur == PartyState.SEARCHING || cur == PartyState.FOUND) {
                    // Stay CONNECTING until isGuestAuthenticated
                    Log.d(TAG, "Connected host set while " + cur + " — waiting auth");
                }
            }
            // host == null: leaveParty / stopHosting owns transition to IDLE
        });

        partyHost.isGuestAuthenticated().observeForever(authenticated -> {
            if (Boolean.TRUE.equals(authenticated) && !partyHost.isHosting()) {
                partyState.postValue(PartyState.JOINED);
                Log.d(TAG, "State → JOINED");
                PartyHost ch = partyHost.getConnectedHost().getValue();
                if (ch != null && ch.getPartyId() != null) {
                    startGuestBridge(ch.getPartyId());
                }
            } else if (!Boolean.TRUE.equals(authenticated) && !partyHost.isHosting()) {
                stopPartyBridge();
            }
        });

        ((EnhancedFirebasePartyHostRepository) partyHost).isNetworkConnected().observeForever(isConnected -> {
            if (!Boolean.TRUE.equals(isConnected)) {
                if (isDiscovering()) {
                    stopDiscovery();
                    partyError.postValue("Lost network connection");
                }
            }
        });

        ((EnhancedFirebasePartyHostRepository) partyHost).getPartyError().observeForever(error -> {
            if (error != null && !error.isEmpty()) {
                partyError.postValue(error);
                Log.e(TAG, "Party error: " + error);
                PartyState cur = partyState.getValue();
                if (cur == PartyState.CONNECTING) {
                    partyState.postValue(PartyState.SEARCHING);
                } else if (cur == PartyState.CREATING) {
                    partyState.postValue(PartyState.IDLE);
                }
            }
        });
    }

    // ============ PARTY STATE OBSERVABLES ============

    /**
     * Current party state (IDLE, SEARCHING, HOSTING, JOINED, SETUP_REQUIRED)
     */
    @NonNull
    public LiveData<PartyState> getPartyState() {
        return partyState;
    }

    @NonNull
    public PlaybackStateRepository getPlaybackState() {
        return requirePlayback();
    }


    /**
     * Current sync packet from host (guest only)
     */
    @NonNull
    public LiveData<SyncPacket> getCurrentSync() {
        return currentSync;
    }

    /**
     * Discovered parties available to join
     */
    @NonNull
    public LiveData<java.util.List<PartyHost>> getDiscoveredParties() {
        return partyHost.getDiscoveredHosts();
    }

    /**
     * Party currently being hosted
     */
    @NonNull
    public LiveData<PartyHost> getHostedParty() {
        return partyHost.getHostedParty();
    }

    /**
     * @deprecated Use getHostedParty() instead. Legacy name for backward compatibility.
     */
    @Deprecated
    @NonNull
    public LiveData<PartyHost> getActiveHost() {
        return partyHost.getHostedParty();
    }

    /**
     * Party currently joined to
     */
    @NonNull
    public LiveData<PartyHost> getConnectedParty() {
        return partyHost.getConnectedHost();
    }

    /**
     * List of guests connected to this host
     */
    @NonNull
    public LiveData<java.util.List<String>> getGuestList() {
        return partyHost.getConnectedGuests();
    }

    /**
     * Number of connected guests
     */
    @NonNull
    public LiveData<Integer> getGuestCount() {
        return partyHost.getGuestCount();
    }

    /**
     * Whether guest is authenticated and receiving stream
     */
    @NonNull
    public LiveData<Boolean> isGuestAuthenticated() {
        return partyHost.isGuestAuthenticated();
    }

    /**
     * Last error message (null if no error)
     */
    @NonNull
    public LiveData<String> getPartyError() {
        return partyError;
    }

    /**
     * Whether Wi-Fi setup is required
     */
    @NonNull
    public LiveData<Boolean> isSetupRequired() {
        return isSetupRequired;
    }

    // ============ PLAYBACK STATE OBSERVABLES ============
    // Delegated to PlaybackStateRepository for proper separation

    /**
     * Current song being played
     */
    @NonNull
    public LiveData<Song> getCurrentSong() {
        return playbackState.getCurrentSong();
    }

    /**
     * Current playback position (milliseconds)
     */
    @NonNull
    public LiveData<Long> getCurrentPosition() {
        return playbackState.getCurrentPosition();
    }

    /**
     * Current song duration (milliseconds)
     */
    @NonNull
    public LiveData<Long> getCurrentDuration() {
        return playbackState.getCurrentDuration();
    }

    /**
     * Whether currently playing
     */
    @NonNull
    public LiveData<Boolean> isPlaying() {
        return playbackState.isPlaying();
    }

    /**
     * Current queue of song IDs
     */
    @NonNull
    public LiveData<java.util.List<Integer>> getQueue() {
        return playbackState.getQueue();
    }

    /**
     * Current index in queue
     */
    @NonNull
    public LiveData<Integer> getCurrentQueueIndex() {
        return playbackState.getCurrentQueueIndex();
    }

    /**
     * Current repeat mode
     */
    @NonNull
    public LiveData<Integer> getRepeatMode() {
        return playbackState.getRepeatMode();
    }

    /**
     * Whether shuffle is enabled
     */
    @NonNull
    public LiveData<Boolean> isShuffleEnabled() {
        return playbackState.isShuffleEnabled();
    }

    /**
     * Playback speed (1.0 = normal)
     */
    @NonNull
    public LiveData<Float> getPlaybackSpeed() {
        return playbackState.getPlaybackSpeed();
    }

    /**
     * Audio pitch (1.0 = normal)
     */
    @NonNull
    public LiveData<Float> getPlaybackPitch() {
        return playbackState.getPlaybackPitch();
    }

    // ============ PARTY MODE COMMANDS ============

    /**
     * Start discovering parties
     */
    public void startDiscovery() {
        Log.d(TAG, "Starting party discovery");
        partyState.postValue(PartyState.SEARCHING);
        partyHost.startDiscovery();
    }

    /**
     * Stop discovering parties
     */
    public void stopDiscovery() {
        Log.d(TAG, "Stopping party discovery");
        if (partyState.getValue() == PartyState.SEARCHING) {
            partyState.postValue(PartyState.IDLE);
        }
        partyHost.stopDiscovery();
    }

    /**
     * Create and host a new party
     */
    public void createParty(@NonNull String partyName, @NonNull String pin) {
        partyState.postValue(PartyState.CREATING);
        partyError.postValue(null);
        Log.d(TAG, "Creating party: " + partyName);
        pendingPartyName = partyName;
        pendingPartyPin = pin;
        partyHost.createParty(partyName, pin);
    }

    /**
     * Join an existing party
     */
    public void joinParty(@Nullable PartyHost host, @NonNull String pin) {
        if (host == null) {
            partyError.postValue("Host not available");
            return;
        }
        Log.d(TAG, "Joining party: " + host.getPartyName());
        pendingPartyPin = pin;
        partyState.postValue(PartyState.CONNECTING);
        partyHost.joinParty(host, pin);
    }

    /**
     * Leave current party (host or guest)
     */
    public void leaveParty() {
        Log.d(TAG, "Leaving party (hosting=" + partyHost.isHosting()
                + " guest=" + partyHost.isGuest() + ")");
        partyError.postValue(null);
        stopPartyBridge();

        if (partyHost.isHosting()) {
            partyHost.stopHosting();
        } else if (partyHost.isGuest()) {
            partyHost.leaveParty();
        }
        partyState.postValue(PartyState.IDLE);
    }

    private void startHostBridge(@Nullable String partyId) {
        if (partyId == null) return;
        try {
            PartyLiveBridge bridge = HeartBeatzApp.container(getApplication()).partyLiveBridge();
            PlaybackStateRepository repo = null;
            try { repo = requirePlayback(); } catch (Exception ignored) { }
            bridge.startHost(partyId, repo);
        } catch (Exception e) {
            Log.e(TAG, "startHostBridge failed", e);
        }
    }

    private void startGuestBridge(@Nullable String partyId) {
        if (partyId == null) return;
        try {
            var bridge = HeartBeatzApp.container(getApplication()).partyLiveBridge();
            PlaybackStateRepository repo = null;
            try { repo = requirePlayback(); } catch (Exception ignored) { }
            bridge.attachPlaybackForGuest(repo);
            bridge.attachPlayback(repo);
            bridge.startGuest(partyId);
        } catch (Exception e) {
            Log.e(TAG, "startGuestBridge failed", e);
        }
    }

    private void stopPartyBridge() {
        try {
            HeartBeatzApp.container(getApplication()).partyLiveBridge().stopAll();
        } catch (Exception ignored) { }
    }

    @NonNull
    public LiveData<PartyPlaybackSync> getPartyPlaybackSync() {
        return HeartBeatzApp.container(getApplication()).partyLiveBridge().getLatestSync();
    }

    @NonNull
    public LiveData<Boolean> isGuestPlayerLocked() {
        return HeartBeatzApp.container(getApplication()).partyLiveBridge().isGuestPlayerLocked();
    }

    // ============ PLAYBACK COMMANDS ============

    /**
     * Play current song or resume
     */
    public void play() {
        playbackState.play();
    }

    /**
     * Pause playback
     */
    public void pause() {
        playbackState.pause();
    }

    /**
     * Toggle play/pause
     */
    public void togglePlayPause() {
        playbackState.togglePlayPause();
    }

    /**
     * Seek to position in current song
     */
    public void seekTo(long positionMs) {
        playbackState.seekTo(positionMs);
    }

    /**
     * Skip to next song
     */
    public void next() {
        playbackState.next();
    }

    /**
     * Skip to previous song
     */
    public void previous() {
        playbackState.previous();
    }

    /**
     * Set repeat mode
     */
    public void setRepeatMode(int mode) {
        playbackState.setRepeatMode(mode);
    }

    /**
     * Toggle shuffle mode
     */
    public void toggleShuffle() {
        playbackState.toggleShuffle();
    }

    /**
     * Set playback speed
     */
    public void setPlaybackSpeed(float speed) {
        playbackState.setPlaybackSpeed(speed);
    }

    /**
     * Set audio pitch
     */
    public void setPlaybackPitch(float pitch) {
        playbackState.setPlaybackPitch(pitch);
    }

    // ============ HELPER METHODS FOR LEGACY CODE ============

    /**
     * Get host IP address (convenience method)
     */
    @NonNull
    public String getHostIp() {
        PartyHost host = partyHost.getHostedParty().getValue();
        if (host == null) {
            host = playbackState.getPartyHost().getValue();
        }
        if (host != null && host.getIpAddress() != null) {
            return host.getIpAddress();
        }
        return "0.0.0.0";
    }

    /**
     * Get host port (convenience method)
     */
    public int getHostPort() {
        PartyHost host = partyHost.getHostedParty().getValue();
        if (host == null) {
            host = playbackState.getPartyHost().getValue();
        }
        if (host != null && host.getPort() > 0) {
            return host.getPort();
        }
        return 8080;
    }

    /**
     * Get party name (from pending or active host)
     */
    @NonNull
    public String getPartyName() {
        PartyHost host = partyHost.getHostedParty().getValue();
        if (host == null) {
            host = playbackState.getPartyHost().getValue();
        }
        if (host != null && host.getPartyName() != null && !host.getPartyName().isEmpty()) {
            return host.getPartyName();
        }
        return pendingPartyName;
    }

    /**
     * Get party pin
     */
    @NonNull
    public String getPartyId() {
        PartyHost host = partyHost.getHostedParty().getValue();
        if (host == null && playbackState != null) {
            host = playbackState.getPartyHost().getValue();
        }
        if (host == null) {
            host = partyHost.getConnectedHost().getValue();
        }
        if (host != null && host.getPartyId() != null) {
            return host.getPartyId();
        }
        return "";
    }

    @NonNull
    public String getPartyPin() {
        if (pendingPartyPin != null && !pendingPartyPin.isEmpty()) {
            return pendingPartyPin;
        }
        PartyHost host = partyHost.getHostedParty().getValue();
        if (host == null && playbackState != null) {
            try { host = playbackState.getPartyHost().getValue(); } catch (Exception ignored) { }
        }
        if (host != null && host.getPin() != null && !host.getPin().isEmpty()) {
            return host.getPin();
        }
        return "";
    }

    // ============ SYNCHRONOUS QUERIES (for initial UI setup) ============

    /**
     * Get current song immediately (without waiting for LiveData)
     */
    @Nullable
    public Song getCurrentSongNow() {
        return playbackState.getCurrentSongSync();
    }

    /**
     * Get current position immediately
     */
    public long getCurrentPositionNow() {
        return playbackState.getCurrentPositionSync();
    }

    /**
     * Get current queue immediately
     */
    @NonNull
    public java.util.List<Integer> getQueueNow() {
        return playbackState.getQueueSync();
    }

    /**
     * Check if playing immediately
     */
    public boolean isPlayingNow() {
        return playbackState.isPlayingSync();
    }

    // ============ STATE CHECKERS ============

    /**
     * Check if currently hosting a party
     */
    public boolean isHosting() {
        return partyHost.isHosting();
    }

    /**
     * Check if currently guest are at a party
     */
    public boolean isGuest() {
        return partyHost.isGuest();
    }

    /**
     * Check if in any party mode
     */
    public boolean isInPartyMode() {
        return partyHost.isInPartyMode();
    }

    /**
     * Check if currently discovering parties
     */
    public boolean isDiscovering() {
        return partyHost.isDiscoveringHosts();
    }

    // ============ LEGACY CALLBACK SUPPORT (for backward compatibility) ============

    /**
     * @deprecated UI callbacks are no longer supported. Use LiveData observers instead.
     */
    @Deprecated
    public void setUiCallback(PartyModeUICallback callback) {
        // Legacy support - no-op, callbacks are handled by repositories
        Log.w(TAG, "setUiCallback() is deprecated. Use LiveData observers instead.");
    }

    /**
     * @deprecated Handover feature not implemented in modern architecture.
     */
    @Deprecated
    public LiveData<String> getHandoverRequest() {
        return new MutableLiveData<>(null);
    }

    /**
     * @deprecated Handover feature not implemented in modern architecture.
     */
    @Deprecated
    public void initiateHandover(String guestName) {
        Log.w(TAG, "initiateHandover() is not implemented in modern architecture");
    }

    /**
     * @deprecated Handover feature not implemented in modern architecture.
     */
    @Deprecated
    public void acceptHandover() {
        Log.w(TAG, "acceptHandover() is not implemented in modern architecture");
    }

    /**
     * @deprecated Handover feature not implemented in modern architecture.
     */
    @Deprecated
    public void declineHandover() {
        Log.w(TAG, "declineHandover() is not implemented in modern architecture");
    }

    // ============ ENHANCED FEATURES ============

    /**
     * Check if network is connected
     */
    public boolean isNetworkConnected() {
        if (partyHost instanceof EnhancedFirebasePartyHostRepository) {
            Boolean connected = ((EnhancedFirebasePartyHostRepository) partyHost).isNetworkConnected().getValue();
            return connected != null && connected;
        }
        return false;
    }

    /**
     * Get network connectivity LiveData for UI observation
     */
    public LiveData<Boolean> getNetworkConnectivity() {
        if (partyHost instanceof EnhancedFirebasePartyHostRepository) {
            return ((EnhancedFirebasePartyHostRepository) partyHost).isNetworkConnected();
        }
        // Return a default LiveData if not using enhanced repository
        MutableLiveData<Boolean> defaultData = new MutableLiveData<>();
        defaultData.setValue(false);
        return defaultData;
    }

    /**
     * Start presence listening (require enhanced repository)
     * @param listener Callback for presence updates
     */
    public void startPresenceListener(EnhancedFirebasePartyHostRepository.PresenceListener listener) {
        if (partyHost instanceof EnhancedFirebasePartyHostRepository) {
            ((EnhancedFirebasePartyHostRepository) partyHost).startPresenceListener(listener);
        }
    }

    /**
     * Stop presence listening
     */
    public void stopPresenceListener() {
        if (partyHost instanceof EnhancedFirebasePartyHostRepository) {
            ((EnhancedFirebasePartyHostRepository) partyHost).stopPresenceListener();
        }
    }

    /**
     * Update user presence status
     * @param isOnline Whether the user is online
     */
    public void updateUserPresence(boolean isOnline) {
        if (partyHost instanceof EnhancedFirebasePartyHostRepository) {
            String userId = getCurrentUserId();
            if (userId != null) {
                ((EnhancedFirebasePartyHostRepository) partyHost).updateUserPresence(userId, isOnline);
            }
        }
    }

    // Helper method to get current user ID
    @Nullable
    private String getCurrentUserId() {
        try {
            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) return user.getUid();
        } catch (Exception ignored) { }
        return null;
    }

    // ============ LIFECYCLE ============


    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared: Cleaning up ViewModel resources");

        // Stop discovery if active
        if (isDiscovering()) {
            stopDiscovery();
        }

        // Do not release shared PlaybackStateRepository — owned by UIThread for app lifetime
        if (partyHost instanceof EnhancedFirebasePartyHostRepository) {
            ((EnhancedFirebasePartyHostRepository) partyHost).release();
        }
    }
}