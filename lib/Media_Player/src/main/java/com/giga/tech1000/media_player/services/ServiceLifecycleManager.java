package com.giga.tech1000.media_player.services;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.SessionCommand;

import com.giga.tech1000.utils.statics.SessionEvents;
import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;

/**
 * Handles service lifecycle events and recovery after backgrounding/restart.
 * 
 * When the service is killed and restarted:
 * 1. Checks PartyStateRepository for saved state
 * 2. If valid state exists and is not expired, automatically recovers party session
 * 3. Sends appropriate MediaSession commands to resume hosting/joining
 * 4. Keeps foreground notification active
 */
@OptIn(markerClass = UnstableApi.class)
public class ServiceLifecycleManager {
    
    private static final String TAG = "ServiceLifecycleManager";
    
    private final PartyStateRepository stateRepo;
    private final MediaLibraryService.MediaLibrarySession mediaSession;
    private final ForegroundServiceManager foregroundManager;
    
    private boolean isRecovering = false;
    
    public ServiceLifecycleManager(
            @NonNull PartyStateRepository stateRepo,
            @NonNull MediaLibraryService.MediaLibrarySession mediaSession,
            @NonNull ForegroundServiceManager foregroundManager) {
        this.stateRepo = stateRepo;
        this.mediaSession = mediaSession;
        this.foregroundManager = foregroundManager;
    }
    
    /**
     * Check for saved party state and recover if valid
     * Called from MediaPlayerService.onCreate()
     */
    public void attemptRecovery() {
        // Check if there's a valid saved state
        if (!stateRepo.hasValidSavedState()) {
            Log.d(TAG, "No valid saved state to recover");
            return;
        }
        
        PartyState savedState = stateRepo.getSavedPartyState();
        long timeSinceSave = stateRepo.getTimeSinceSave();
        long timeRemaining = stateRepo.getRemainingTimeBeforeExpiry();
        
        Log.d(TAG, "Found saved party state: " + savedState + 
                   " (saved " + timeSinceSave + "s ago, expires in " + timeRemaining + "s)");
        
        isRecovering = true;
        
        if (savedState == PartyState.HOSTING) {
            recoverHostingState();
        } else if (savedState == PartyState.JOINED) {
            recoverGuestState();
        }
    }
    
    /**
     * Recover hosting state
     */
    private void recoverHostingState() {
        String partyName = stateRepo.getSavedPartyName();
        String pin = stateRepo.getSavedPin();
        
        if (partyName == null || pin == null) {
            Log.e(TAG, "Missing data to recover hosting state");
            return;
        }
        
        Log.d(TAG, "Recovering hosting state for party: " + partyName);
        
        // Update foreground notification
        foregroundManager.startPartyHosting(partyName, 0);
        
        // Send recovery event
        Bundle recoveryExtras = new Bundle();
        recoveryExtras.putString(SessionEvents.EXTRA_PARTY_STATE, PartyState.HOSTING.name());
        recoveryExtras.putString(SessionEvents.EXTRA_PARTY_NAME, partyName);
        mediaSession.broadcastCustomCommand(
                new SessionCommand(SessionEvents.EVENT_SERVICE_RECOVERED, Bundle.EMPTY),
                recoveryExtras
        );
        
        // Recreate party hosting
        Bundle createPartyExtras = new Bundle();
        createPartyExtras.putString(SessionEvents.EXTRA_PARTY_NAME, partyName);
        createPartyExtras.putString(SessionEvents.EXTRA_PARTY_PIN, pin);
        mediaSession.broadcastCustomCommand(
                new SessionCommand(SessionEvents.ACTION_CREATE_PARTY, Bundle.EMPTY),
                createPartyExtras
        );
    }
    
    /**
     * Recover guest state
     */
    private void recoverGuestState() {
        PartyHost host = stateRepo.getSavedHost();
        String pin = stateRepo.getSavedPin();
        
        if (host == null || pin == null) {
            Log.e(TAG, "Missing data to recover guest state");
            return;
        }
        
        Log.d(TAG, "Recovering guest state for party: " + host.partyName);
        
        // Update foreground notification
        foregroundManager.startPartyGuest(host.partyName);
        
        // Send recovery event
        Bundle recoveryExtras = new Bundle();
        recoveryExtras.putString(SessionEvents.EXTRA_PARTY_STATE, PartyState.JOINED.name());
        recoveryExtras.putParcelable(SessionEvents.EXTRA_PARTY_HOST, host);
        mediaSession.broadcastCustomCommand(
                new SessionCommand(SessionEvents.EVENT_SERVICE_RECOVERED, Bundle.EMPTY),
                recoveryExtras
        );
        
        // Rejoin the party
        Bundle joinExtras = new Bundle();
        joinExtras.putParcelable(SessionEvents.EXTRA_PARTY_HOST, host);
        joinExtras.putString(SessionEvents.EXTRA_PARTY_PIN, pin);
        mediaSession.broadcastCustomCommand(
                new SessionCommand(SessionEvents.ACTION_JOIN_PARTY, Bundle.EMPTY),
                joinExtras
        );
    }
    
    /**
     * Mark service destruction, persist current state
     * Called from MediaPlayerService.onDestroy()
     */
    public void onServiceDestroying() {
        // State persistence should be handled by PartyRouter
        // This is just a hook for any cleanup needed
        Log.d(TAG, "Service is being destroyed");
    }
    
    /**
     * Check if recovery is currently in progress
     */
    public boolean isRecovering() {
        return isRecovering;
    }
    
    /**
     * Mark recovery as complete
     */
    public void markRecoveryComplete() {
        isRecovering = false;
        Log.d(TAG, "Recovery complete");
    }
    
    /**
     * Clear saved state (called when user manually leaves party)
     */
    public void clearSavedState() {
        stateRepo.clearState();
        isRecovering = false;
    }
}
