package com.giga.tech1000.media_player.engine;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.SessionCommand;

import com.giga.tech1000.media_player.PlaybackManager;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.services.ForegroundServiceManager;
import com.giga.tech1000.media_player.services.PartyStateRepository;
import com.giga.tech1000.party_mode.PartyManager;
import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.giga.tech1000.utils.statics.SessionEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes party mode commands and manages party state lifecycle.
 * 
 * Integrated with:
 * - ForegroundServiceManager: Keeps service alive with persistent notifications
 * - PartyStateRepository: Persists party state for recovery
 * - MediaSession: Uses formal SessionEvents for commands
 */
@OptIn(markerClass = UnstableApi.class)
public class PartyRouter {
    private static final String TAG = "PartyRouter";
    
    private final PartyManager partyManager;
    public PartyState partyState = PartyState.IDLE;

    private final PlaybackManager playbackManager;
    private final MediaLibraryService.MediaLibrarySession mediaSession;
    private final Context context;
    private final ForegroundServiceManager foregroundServiceManager;
    private final PartyStateRepository partyStateRepository;
    
    private String currentPartyName = "";
    private String currentPin = "";
    private PartyHost currentHost = null;

    public PartyRouter(
            Context context, 
            PlaybackManager manager, 
            MediaLibraryService.MediaLibrarySession session,
            ForegroundServiceManager foregroundManager,
            PartyStateRepository stateRepository) {
        this.context = context;
        this.playbackManager = manager;
        this.mediaSession = session;
        this.foregroundServiceManager = foregroundManager;
        this.partyStateRepository = stateRepository;
        this.partyManager = new PartyManager(context);
        //this.partyManager.setPlayer(manager.getPlayer());

        this.partyManager.setListener(new PartyManager.PartyManagerListener() {
            @Override
            public void onHostCreated(PartyHost host) {
                currentHost = host;
                currentPartyName = host.partyName;
                Log.d(TAG, "Host created: " + host.partyName);
                
                // Persist hosting state for recovery
                if (partyStateRepository != null) {
                    partyStateRepository.saveHostingState(host.partyName, currentPin, host);
                }
                
                // Update foreground notification to keep service alive
                if (foregroundServiceManager != null) {
                    foregroundServiceManager.startPartyHosting(host.partyName, 0);
                }
                
                // Broadcast to UI using formal contract
                Bundle extras = new Bundle();
                extras.putParcelable(SessionEvents.EXTRA_PARTY_HOST, host);
                mediaSession.broadcastCustomCommand(
                        new SessionCommand(SessionEvents.EVENT_PARTY_HOST_CREATED, Bundle.EMPTY),
                        extras
                );
            }

            @Override
            public void onGuestListUpdated(List<String> guests) {
                Log.d(TAG, "Guest list updated: " + (guests != null ? guests.size() : 0) + " guests");
                
                // Update foreground notification with guest count
                if (foregroundServiceManager != null && guests != null) {
                    foregroundServiceManager.updateGuestCount(guests.size());
                }
                
                // Broadcast using formal contract
                Bundle extras = new Bundle();
                extras.putStringArrayList(SessionEvents.EXTRA_GUEST_LIST, new ArrayList<>(guests));
                extras.putInt(SessionEvents.EXTRA_GUEST_COUNT, guests != null ? guests.size() : 0);
                mediaSession.broadcastCustomCommand(
                        new SessionCommand(SessionEvents.EVENT_PARTY_GUESTS_UPDATED, Bundle.EMPTY),
                        extras
                );
            }

            @Override
            public void onGuestAuthorized(String remoteAddress) {
                Log.d(TAG, "Guest authorized: " + remoteAddress);
                // Broadcast authorization so UI knows a guest is attempting to join/successfully pinged
                mediaSession.broadcastCustomCommand(
                        new SessionCommand(SessionEvents.EVENT_AUTH_SUCCESS, Bundle.EMPTY),
                        Bundle.EMPTY
                );
            }

            @Override
            public void onPartyStopped() {
                Log.d(TAG, "Party stopped");
                mediaSession.broadcastCustomCommand(
                        new SessionCommand(SessionEvents.EVENT_PARTY_LEFT, Bundle.EMPTY),
                        Bundle.EMPTY
                );
            }

            @Override
            public void onSyncDataReceived(SyncPacket syncPacket) {

            }


        });

        manager.getPlayer().addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (partyState == PartyState.CONNECTING && state == Player.STATE_READY) {
                    partyManager.confirmJoined();
                    Log.d(TAG, "Guest playback ready, party joined");
                    
                    // Persist guest state for recovery
                    if (partyStateRepository != null && currentHost != null) {
                        partyStateRepository.saveGuestState(currentHost, currentPin);
                    }
                    
                    // Update state early
                    partyState = PartyState.JOINED;
                    playbackManager.setPartyState(PartyState.JOINED);

                    // Broadcast joined success
                    mediaSession.broadcastCustomCommand(
                            new SessionCommand(SessionEvents.EVENT_AUTH_SUCCESS, Bundle.EMPTY),
                            Bundle.EMPTY
                    );
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Playback error during party: " + error.getMessage());
                if (partyState == PartyState.CONNECTING || partyState == PartyState.JOINED) {
                    Bundle extras = new Bundle();
                    extras.putString(SessionEvents.EXTRA_ERROR_MESSAGE, error.getMessage());
                    mediaSession.broadcastCustomCommand(
                            new SessionCommand(SessionEvents.EVENT_PARTY_CONNECTION_FAILED, Bundle.EMPTY),
                            extras
                    );
                    stopPartyMode();
                }
            }
        });
    }

    private boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public void startHostMode(@NonNull String partyName, @NonNull String pin) {
        Log.d(TAG, "Starting host mode: " + partyName);
        
        if (!isWifiEnabled()) {
            Log.w(TAG, "WiFi not enabled, cannot start hosting");
            mediaSession.broadcastCustomCommand(
                    new SessionCommand(SessionEvents.EVENT_SETUP_REQUIRED, Bundle.EMPTY),
                    Bundle.EMPTY
            );
            return;
        }

        // Save current playback state BEFORE stopping any existing party
        List<Integer> currentQueue = new ArrayList<>();
        int currentIndex = -1;
        boolean shouldPlay = false;

        if (playbackManager != null) {
            currentQueue = new ArrayList<>(playbackManager.getQueue());
            currentIndex = playbackManager.getCurrentQueueIndex();
            shouldPlay = playbackManager.getPlayer().getPlayWhenReady();
        }

        stopPartyMode();
        partyState = PartyState.HOSTING;
        playbackManager.setPartyState(PartyState.HOSTING);

        // Restore playback state to the new player
        if (!currentQueue.isEmpty() && currentIndex >= 0 && currentIndex < currentQueue.size()) {
            playbackManager.onPlayIndex(currentIndex, shouldPlay);
        }
        
        this.currentPartyName = partyName;
        this.currentPin = pin;
        
        PartyHost host = partyManager.startHosting(partyName, pin);
        if (host == null) {
            Log.e(TAG, "Failed to create party host");
            Bundle extras = new Bundle();
            extras.putString(SessionEvents.EXTRA_ERROR_MESSAGE, "Failed to start hosting");
            mediaSession.broadcastCustomCommand(
                    new SessionCommand(SessionEvents.EVENT_PARTY_CONNECTION_FAILED, Bundle.EMPTY),
                    extras
            );
            return;
        }

        this.currentHost = host;
        Log.d(TAG, "Host created successfully");
    }

    public void startDiscovery() {
        Log.d(TAG, "Starting party discovery");
        
        if (!isWifiEnabled()) {
            Log.w(TAG, "WiFi not enabled, cannot discover");
            mediaSession.broadcastCustomCommand(
                    new SessionCommand(SessionEvents.EVENT_SETUP_REQUIRED, Bundle.EMPTY),
                    Bundle.EMPTY
            );
            return;
        }

        partyManager.startScanning(new Object());
    }

    public void stopDiscovery() {
        Log.d(TAG, "Stopping discovery");
        partyManager.stopScanning();
    }

    public void connectToHost(@Nullable PartyHost host, @NonNull String pin) {
        if (host == null) {
            Log.e(TAG, "Cannot connect to null host");
            return;
        }
        
        Log.d(TAG, "Connecting to host: " + host.partyName);
        
        if (!isWifiEnabled()) {
            Log.w(TAG, "WiFi not enabled, cannot join");
            mediaSession.broadcastCustomCommand(
                    new SessionCommand(SessionEvents.EVENT_SETUP_REQUIRED, Bundle.EMPTY),
                    Bundle.EMPTY
            );
            return;
        }

        stopPartyMode();
        partyState = PartyState.CONNECTING;
        playbackManager.setPartyState(PartyState.CONNECTING);
        
        this.currentHost = host;
        this.currentPin = pin;
        
        // Start foreground mode as guest before connecting
        if (foregroundServiceManager != null) {
            foregroundServiceManager.startPartyGuest(host.partyName);
        }
        
        // Persist guest state early for quick recovery
        if (partyStateRepository != null) {
            partyStateRepository.saveGuestState(host, pin);
        }
        
        partyManager.joinParty(host, pin);
        Log.d(TAG, "Join request sent to host");
    }

    public void stopPartyMode() {
        Log.d(TAG, "Stopping party mode");
        partyManager.stop();
        partyState = PartyState.IDLE;
        playbackManager.setPartyState(PartyState.IDLE);
        
        // Clear state and stop foreground notification
        currentPartyName = "";
        currentPin = "";
        currentHost = null;
        
        if (foregroundServiceManager != null) {
            foregroundServiceManager.stopPartyForeground();
        }
        
        if (partyStateRepository != null) {
            partyStateRepository.clearState();
        }
        
        // Broadcast party left event
        mediaSession.broadcastCustomCommand(
                new SessionCommand(SessionEvents.EVENT_PARTY_LEFT, Bundle.EMPTY),
                Bundle.EMPTY
        );
    }

    public void broadcastMetadata(Song song) {
        if (partyState == PartyState.HOSTING && song != null && song.getUri() != null) {
            partyManager.updateTrack(song.getUri(), String.valueOf(song.getId()));
        }
    }
    
    /**
     * Persist current party state (called from service lifecycle)
     */
    public void persistCurrentState() {
        if (partyState == PartyState.HOSTING && currentHost != null && partyStateRepository != null) {
            Log.d(TAG, "Persisting hosting state");
            partyStateRepository.saveHostingState(currentPartyName, currentPin, currentHost);
        } else if (partyState == PartyState.JOINED && currentHost != null && partyStateRepository != null) {
            Log.d(TAG, "Persisting guest state");
            partyStateRepository.saveGuestState(currentHost, currentPin);
        }
    }
    
    /**
     * Get current party state
     */
    public PartyState getPartyState() {
        return partyState;
    }
    
    /**
     * Check if currently hosting
     */
    public boolean isHosting() {
        return partyState == PartyState.HOSTING;
    }
    
    /**
     * Check if currently joined as guest
     */
    public boolean isGuest() {
        return partyState == PartyState.JOINED;
    }
}
