package com.giga.tech1000.media_player.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;

/**
 * Persists party state to SharedPreferences for recovery after service restart.
 * 
 * Ensures party session survives:
 * - System killing the service
 * - Device backgrounding
 * - Service crashes
 * 
 * On service restart, PartyStateRepository can restore party state
 * to keep hosting/guest sessions alive.
 */
@OptIn(markerClass = UnstableApi.class)
public class PartyStateRepository {
    
    private static final String TAG = "PartyStateRepository";
    private static final String PREFS_NAME = "party_state_prefs";
    private static final String KEY_PARTY_STATE = "party_state";
    private static final String KEY_PARTY_NAME = "party_name";
    private static final String KEY_PARTY_PIN = "party_pin";
    private static final String KEY_PARTY_HOST_ID = "party_host_id";
    private static final String KEY_PARTY_HOST_NAME = "party_host_name";
    private static final String KEY_PARTY_HOST_IP = "party_host_ip";
    private static final String KEY_PARTY_HOST_PORT = "party_host_port";
    private static final String KEY_LAST_SAVED_TIME = "last_saved_time";
    private static final long STATE_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes
    
    private final SharedPreferences prefs;
    
    public PartyStateRepository(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Save current party state when hosting
     */
    public void saveHostingState(@NonNull String partyName, @NonNull String pin, @NonNull PartyHost host) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PARTY_STATE, PartyState.HOSTING.name());
        editor.putString(KEY_PARTY_NAME, partyName);
        editor.putString(KEY_PARTY_PIN, pin);
        
        if (host != null) {
            editor.putString(KEY_PARTY_HOST_ID, host.partyId);
            editor.putString(KEY_PARTY_HOST_NAME, host.partyName);
            editor.putString(KEY_PARTY_HOST_IP, host.ipAddress);
            editor.putInt(KEY_PARTY_HOST_PORT, host.port);
        }
        
        editor.putLong(KEY_LAST_SAVED_TIME, System.currentTimeMillis());
        editor.apply();
        
        Log.d(TAG, "Saved HOSTING state for party: " + partyName);
    }
    
    /**
     * Save current party state when joined as guest
     */
    public void saveGuestState(@NonNull PartyHost host, @NonNull String pin) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PARTY_STATE, PartyState.JOINED.name());
        editor.putString(KEY_PARTY_PIN, pin);
        
        if (host != null) {
            editor.putString(KEY_PARTY_HOST_ID, host.partyId);
            editor.putString(KEY_PARTY_HOST_NAME, host.partyName);
            editor.putString(KEY_PARTY_HOST_IP, host.ipAddress);
            editor.putInt(KEY_PARTY_HOST_PORT, host.port);
        }
        
        editor.putLong(KEY_LAST_SAVED_TIME, System.currentTimeMillis());
        editor.apply();
        
        Log.d(TAG, "Saved JOINED state for party: " + (host != null ? host.partyName : "unknown"));
    }
    
    /**
     * Clear saved party state
     */
    public void clearState() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared saved party state");
    }
    
    /**
     * Get saved party state
     */
    @Nullable
    public PartyState getSavedPartyState() {
        String stateStr = prefs.getString(KEY_PARTY_STATE, null);
        if (stateStr == null) {
            return null;
        }
        
        // Check if state has expired
        long savedTime = prefs.getLong(KEY_LAST_SAVED_TIME, 0);
        long elapsed = System.currentTimeMillis() - savedTime;
        
        if (elapsed > STATE_EXPIRY_MS) {
            Log.d(TAG, "Saved state expired after " + (elapsed / 1000) + "s");
            clearState();
            return null;
        }
        
        try {
            return PartyState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid saved party state: " + stateStr, e);
            return null;
        }
    }
    
    /**
     * Get saved party name (for hosting)
     */
    @Nullable
    public String getSavedPartyName() {
        return prefs.getString(KEY_PARTY_NAME, null);
    }
    
    /**
     * Get saved PIN
     */
    @Nullable
    public String getSavedPin() {
        return prefs.getString(KEY_PARTY_PIN, null);
    }
    
    /**
     * Reconstruct PartyHost from saved data
     */
    @Nullable
    public PartyHost getSavedHost() {
        String hostId = prefs.getString(KEY_PARTY_HOST_ID, null);
        String hostName = prefs.getString(KEY_PARTY_HOST_NAME, null);
        String hostIp = prefs.getString(KEY_PARTY_HOST_IP, null);
        int port = prefs.getInt(KEY_PARTY_HOST_PORT, 0);
        
        if (hostId == null || hostName == null || hostIp == null || port == 0) {
            return null;
        }
        
        PartyHost host = new PartyHost();
        host.partyId = hostId;
        host.partyName = hostName;
        host.ipAddress = hostIp;
        host.port = port;
        
        return host;
    }
    
    /**
     * Check if there's valid saved state that can be recovered
     */
    public boolean hasValidSavedState() {
        PartyState state = getSavedPartyState();
        if (state == null) {
            return false;
        }
        
        // Verify we have required data for recovery
        if (state == PartyState.HOSTING) {
            return getSavedPartyName() != null && getSavedPin() != null;
        } else if (state == PartyState.JOINED) {
            return getSavedHost() != null && getSavedPin() != null;
        }
        
        return false;
    }
    
    /**
     * Get time elapsed since state was saved (in seconds)
     */
    public long getTimeSinceSave() {
        long savedTime = prefs.getLong(KEY_LAST_SAVED_TIME, 0);
        if (savedTime == 0) return 0;
        return (System.currentTimeMillis() - savedTime) / 1000;
    }
    
    /**
     * Get remaining time before state expires (in seconds)
     */
    public long getRemainingTimeBeforeExpiry() {
        long elapsed = getTimeSinceSave() * 1000;
        long remaining = STATE_EXPIRY_MS - elapsed;
        return Math.max(0, remaining / 1000);
    }
}
