package com.giga.tech1000.heartbeatz.architecture.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.party_mode.model.PartyHost;

import java.util.List;

/**
 * Abstraction for party host management (Firebase-backed).
 * Prefer {@link com.giga.tech1000.heartbeatz.architecture.session.PartySession} from AppContainer.
 * Do not reintroduce UDP discovery, PartyRouter, or WebRTC audio here.
 */
public interface PartyHostRepository {
    
    // ============ HOST DISCOVERY ============
    
    /**
     * Start scanning for available party hosts on local network
     */
    void startDiscovery();
    
    /**
     * Stop scanning for hosts
     */
    void stopDiscovery();
    
    /**
     * Recently discovered hosts (cleared when discovery stops)
     */
    @NonNull
    LiveData<List<PartyHost>> getDiscoveredHosts();
    
    /**
     * Check if currently scanning
     */
    boolean isDiscoveringHosts();
    
    // ============ HOST CREATION & MANAGEMENT ============
    
    /**
     * Create a new party to host
     * 
     * @param partyName Display name for this party
     * @param pin 4-digit PIN for security
     */
    void createParty(@NonNull String partyName, @NonNull String pin);
    
    /**
     * Stop hosting and disconnect all guests
     */
    void stopHosting();
    
    /**
     * Get the party currently being hosted
     */
    @NonNull
    LiveData<PartyHost> getHostedParty();
    
    /**
     * List of guests connected to this host
     */
    @NonNull
    LiveData<List<String>> getConnectedGuests();
    
    /**
     * Number of connected guests
     */
    @NonNull
    LiveData<Integer> getGuestCount();
    
    // ============ GUEST CONNECTION ============
    
    /**
     * Join a party as guest
     * 
     * @param host The host to connect to
     * @param pin The 4-digit PIN provided by host
     */
    void joinParty(@NonNull PartyHost host, @NonNull String pin);
    
    /**
     * Leave current party
     */
    void leaveParty();
    
    /**
     * The host we're currently connected to (null if not guest)
     */
    @NonNull
    LiveData<PartyHost> getConnectedHost();
    
    /**
     * Whether successfully authenticated as guest
     */
    @NonNull
    LiveData<Boolean> isGuestAuthenticated();
    
    // ============ STATE CHECKS ============
    
    /**
     * Check if currently hosting
     */
    boolean isHosting();
    
    /**
     * Check if currently joined as guest
     */
    boolean isGuest();
    
    /**
     * Check if actively in party mode
     */
    boolean isInPartyMode();
}
