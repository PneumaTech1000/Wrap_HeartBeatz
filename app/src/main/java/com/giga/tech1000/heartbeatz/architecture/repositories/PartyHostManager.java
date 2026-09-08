package com.giga.tech1000.heartbeatz.architecture.repositories;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.ui.MediaPlayerThread;
import com.giga.tech1000.media_player.interfaces.IPlaybackCallback;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete implementation of PartyHostRepository.
 * 
 * Wraps PartyRouter singleton and exposes party state through LiveData.
 * Acts as an adapter/bridge between legacy PartyRouter
 * and modern reactive architecture.
 * 
 * IMPORTANT: This is a TEMPORARY implementation during refactoring.
 * Long-term goal is to eliminate PartyRouter dependency entirely
 * and use MediaSession directly.
 * 
 * For now, this provides a clean interface that UI can depend on
 * without exposing PartyRouter or WiFi implementation details.
 */
@OptIn(markerClass = UnstableApi.class)
public class PartyHostManager implements PartyHostRepository {
    
    private static final String TAG = "PartyHostManager";
    
    // LiveData - single source of truth
    private final MutableLiveData<List<PartyHost>> discoveredHosts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<PartyHost> hostedParty = new MutableLiveData<>(null);
    private final MutableLiveData<List<String>> connectedGuests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> guestCount = new MutableLiveData<>(0);
    private final MutableLiveData<PartyHost> connectedHost = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> guestAuthenticated = new MutableLiveData<>(false);
    
    // Legacy callback for state updates from CorePlayer
    private final IPlaybackCallback playbackListener = new IPlaybackCallback() {
        @Override
        public void onSongChanged(@androidx.annotation.Nullable com.giga.tech1000.media_player.models.Song song) {}

        @Override
        public void onPlaybackStateChanged(boolean isPlaying, int playbackState) {}

        @Override
        public void onQueueChanged(List<Integer> queue, int currentIndex) {}

        @Override
        public void onRepeatModeChanged(int repeatMode) {}

        @Override
        public void onShuffleModeChanged(boolean enabled) {}

        @Override
        public void onSessionIdReady(int sessionId) {}

        @Override
        public void onProgressUpdate(long position, long duration) {}

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {

        }

        @Override
        public void onPartyHostDiscovered(PartyHost host) {
            IPlaybackCallback.super.onPartyHostDiscovered(host);
        }

        @Override
        public void onPartyServiceRegistered(PartyHost host) {
            IPlaybackCallback.super.onPartyServiceRegistered(host);
        }

        @Override
        public void onPartyHostCreated(PartyHost host) {
            hostedParty.postValue(host);
        }

        @Override
        public void onPartyAuthSuccess() {
            Log.d(TAG, "Guest authentication successful");
            guestAuthenticated.postValue(true);
        }

        @Override
        public void onPartyMetadataReceived(SyncPacket sync) {
            IPlaybackCallback.super.onPartyMetadataReceived(sync);
        }

        @Override
        public void onPartyGuestsUpdated(List<String> guests) {
            Log.d(TAG, "Guest list updated: " + guests.size() + " guests");
            connectedGuests.postValue(new ArrayList<>(guests));
            guestCount.postValue(guests.size());
        }

        @Override
        public void onPartySetupRequired() {
            IPlaybackCallback.super.onPartySetupRequired();
        }

        @Override
        public void onPartyConnectionFailed() {
            connectedHost.postValue(null);
            guestAuthenticated.postValue(false);
        }

        @Override
        public void onPartyDisconnected() {
            connectedHost.postValue(null);
            guestAuthenticated.postValue(false);
        }
    };

    private boolean isDiscovering = false;
    private MediaPlayerThread playerThread;
    
    public PartyHostManager(MediaPlayerThread mediaPlayerThread) {
        Log.d(TAG, "PartyHostManager created");
        this.playerThread = mediaPlayerThread;
        registerPlaybackListener();
    }

    private void registerPlaybackListener() {
        if (playerThread != null && playerThread.getCorePlayer() != null) {
            playerThread.getCorePlayer().addListener(playbackListener);
        }
    }
    
    // ============ HOST DISCOVERY ============
    
    @Override
    public void startDiscovery() {
        Log.d(TAG, "Starting discovery");
        try {
            if (playerThread != null) {
                playerThread.getCallback().onStartDiscovery();
                isDiscovering = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start discovery", e);
        }
    }
    
    @Override
    public void stopDiscovery() {
        Log.d(TAG, "Stopping discovery");
        try {
            if (playerThread != null) {
                playerThread.getCallback().onStopDiscovery();
                isDiscovering = false;
                discoveredHosts.postValue(new ArrayList<>()); // Clear discovered hosts
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop discovery", e);
        }
    }
    
    @NonNull
    @Override
    public LiveData<List<PartyHost>> getDiscoveredHosts() {
        return discoveredHosts;
    }
    
    @Override
    public boolean isDiscoveringHosts() {
        return isDiscovering;
    }
    
    // ============ HOST CREATION & MANAGEMENT ============
    
    @Override
    public void createParty(@NonNull String partyName, @NonNull String pin) {
        Log.d(TAG, "Creating party: " + partyName);
        try {
            if (playerThread != null) {
                playerThread.getCallback().onCreateParty(partyName, pin);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create party", e);
        }
    }
    
    @Override
    public void stopHosting() {
        Log.d(TAG, "Stopping hosting");
        try {
            if (playerThread != null) {
                playerThread.getCallback().onLeaveParty();
            }
            hostedParty.postValue(null);
            connectedGuests.postValue(new ArrayList<>());
            guestCount.postValue(0);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop hosting", e);
        }
    }
    
    @NonNull
    @Override
    public LiveData<PartyHost> getHostedParty() {
        return hostedParty;
    }
    
    @NonNull
    @Override
    public LiveData<List<String>> getConnectedGuests() {
        return connectedGuests;
    }
    
    @NonNull
    @Override
    public LiveData<Integer> getGuestCount() {
        return guestCount;
    }
    
    // ============ GUEST CONNECTION ============
    
    @Override
    public void joinParty(@NonNull PartyHost host, @NonNull String pin) {
        Log.d(TAG, "Joining party: " + host.partyName);
        try {
            if (playerThread != null) {
                playerThread.getCallback().onJoinParty(host, pin);
                connectedHost.postValue(host);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to join party", e);
        }
    }
    
    @Override
    public void leaveParty() {
        Log.d(TAG, "Leaving party");
        try {
            if (playerThread != null) {
                playerThread.getCallback().onLeaveParty();
            }
            connectedHost.postValue(null);
            guestAuthenticated.postValue(false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to leave party", e);
        }
    }
    
    @NonNull
    @Override
    public LiveData<PartyHost> getConnectedHost() {
        return connectedHost;
    }
    
    @NonNull
    @Override
    public LiveData<Boolean> isGuestAuthenticated() {
        return guestAuthenticated;
    }
    
    // ============ STATE CHECKS ============
    
    @Override
    public boolean isHosting() {
        return hostedParty.getValue() != null;
    }
    
    @Override
    public boolean isGuest() {
        return connectedHost.getValue() != null;
    }
    
    @Override
    public boolean isInPartyMode() {
        return isHosting() || isGuest();
    }
    
    /**
     * Update hosted party info (called from UI callbacks)
     */
    public void setHostedParty(@NonNull PartyHost party) {
        hostedParty.postValue(party);
    }
    
    /**
     * Update guest list (called from UI callbacks)
     */
    public void setConnectedGuests(@NonNull List<String> guests) {
        connectedGuests.postValue(new ArrayList<>(guests));
        guestCount.postValue(guests.size());
    }
    
    /**
     * Mark guest as authenticated (called from UI callbacks)
     */
    public void setGuestAuthenticated(boolean authenticated) {
        guestAuthenticated.postValue(authenticated);
    }
    
    /**
     * Cleanup when ViewModel is destroyed
     */
    public void release() {
        Log.d(TAG, "Released");
        if (isDiscovering) {
            stopDiscovery();
        }
        if (playerThread != null && playerThread.getCorePlayer() != null) {
            playerThread.getCorePlayer().removeListener(playbackListener);
        }
    }
}
