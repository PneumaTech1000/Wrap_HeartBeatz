package com.giga.tech1000.media_player.contracts;

import android.os.Bundle;

import androidx.media3.session.SessionCommand;

/**
 * Formal contract for party mode commands in MediaSession.
 * 
 * This centralizes all party-related session commands, ensuring:
 * - Consistent command naming across the app
 * - Type-safe extras handling
 * - Documented expected behaviors
 * - Robust background operation
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PartySessionContract {

    // ============ PARTY CONTROL COMMANDS ============
    
    /**
     * Start party discovery (scanning for available parties)
     * <p>
     * Extras: (none)
     * Response: Broadcasting PARTY_HOST_DISCOVERED when hosts found
     */
    public static final String ACTION_START_DISCOVERY = "com.heartbeatz.party.START_DISCOVERY";
    
    /**
     * Stop party discovery
     * <p>
     * Extras: (none)
     */
    public static final String ACTION_STOP_DISCOVERY = "com.heartbeatz.party.STOP_DISCOVERY";
    
    /**
     * Create a new party (become host)
     * <p>
     * Extras:
     * - EXTRA_PARTY_NAME: String (party name, e.g., "John's Party")
     * - EXTRA_PARTY_PIN: String (4-digit PIN for security)
     * 
     * Response: Broadcasting PARTY_HOST_CREATED with host details
     */
    public static final String ACTION_CREATE_PARTY = "com.heartbeatz.party.CREATE_PARTY";
    
    /**
     * Join an existing party (become guest)
     * <p>
     * Extras:
     * - EXTRA_PARTY_HOST: PartyHost parcelable
     * - EXTRA_PARTY_PIN: String (4-digit PIN)
     * 
     * Response: Broadcasting PARTY_JOINED when connected
     */
    public static final String ACTION_JOIN_PARTY = "com.heartbeatz.party.JOIN_PARTY";
    
    /**
     * Leave current party and return to solo mode
     * <p>
     * Extras: (none)
     * Response: Broadcasting PARTY_LEFT
     */
    public static final String ACTION_LEAVE_PARTY = "com.heartbeatz.party.LEAVE_PARTY";
    
    // ============ QUEUE & PLAYBACK COMMANDS ============
    
    /**
     * Play specific queue from source
     * <p>
     * Extras:
     * - EXTRA_QUEUE_TRACKS: ArrayList<Integer> (song IDs)
     * - EXTRA_TRACK_INDEX: int (starting track index)
     * - EXTRA_SOURCE: String (ItemSource enum value)
     */
    public static final String ACTION_PLAY_QUEUE = "com.heartbeatz.party.PLAY_QUEUE";
    
    /**
     * Keep service alive in foreground during party hosting
     * <p>
     * Extras:
     * - EXTRA_PARTY_NAME: String
     * - EXTRA_GUEST_COUNT: int
     * 
     * Called periodically to ensure notification stays updated
     */
    public static final String ACTION_UPDATE_FOREGROUND_NOTIFICATION = 
            "com.heartbeatz.party.UPDATE_FOREGROUND_NOTIFICATION";
    
    // ============ STATE EVENT BROADCASTS ============
    
    /**
     * Host discovered during discovery phase
     * <p>
     * Broadcast Extras:
     * - EXTRA_PARTY_HOST: PartyHost parcelable
     */
    public static final String EVENT_PARTY_HOST_DISCOVERED = "com.heartbeatz.party.HOST_DISCOVERED";
    
    /**
     * Party created successfully (host side)
     * <p>
     * Broadcast Extras:
     * - EXTRA_PARTY_HOST: PartyHost parcelable (includes QR code data)
     */
    public static final String EVENT_PARTY_HOST_CREATED = "com.heartbeatz.party.HOST_CREATED";
    
    /**
     * Successfully joined a party (guest side)
     * <p>
     * Broadcast Extras:
     * - EXTRA_PARTY_HOST: PartyHost (the host we connected to)
     */
    public static final String EVENT_PARTY_JOINED = "com.heartbeatz.party.JOINED";
    
    /**
     * Authentication successful, receiving stream
     * <p>
     * Broadcast Extras: (none)
     */
    public static final String EVENT_PARTY_AUTH_SUCCESS = "com.heartbeatz.party.AUTH_SUCCESS";
    
    /**
     * Metadata (current track) received from host
     * <p>
     * Broadcast Extras:
     * - EXTRA_SYNC_PACKET: SyncPacket parcelable
     */
    public static final String EVENT_PARTY_METADATA_RECEIVED = "com.heartbeatz.party.METADATA_RECEIVED";
    
    /**
     * Guest list updated (host side)
     * <p>
     * Broadcast Extras:
     * - EXTRA_GUEST_LIST: ArrayList<String> (guest names)
     * - EXTRA_GUEST_COUNT: int
     */
    public static final String EVENT_PARTY_GUESTS_UPDATED = "com.heartbeatz.party.GUESTS_UPDATED";
    
    /**
     * Left party or connection dropped
     * <p>
     * Broadcast Extras: (none)
     */
    public static final String EVENT_PARTY_LEFT = "com.heartbeatz.party.LEFT";
    
    /**
     * Connection failed (WiFi issue, host unavailable, etc.)
     * <p>
     * Broadcast Extras:
     * - EXTRA_ERROR_MESSAGE: String (reason for failure)
     */
    public static final String EVENT_PARTY_CONNECTION_FAILED = "com.heartbeatz.party.CONNECTION_FAILED";
    
    /**
     * WiFi not available or disabled
     * <p>
     * Broadcast Extras: (none)
     */
    public static final String EVENT_SETUP_REQUIRED = "com.heartbeatz.party.SETUP_REQUIRED";
    
    /**
     * Service is recovering from background - party session preserved
     * <p>
     * Broadcast Extras:
     * - EXTRA_PARTY_STATE: String (HOSTING or JOINED)
     */
    public static final String EVENT_SERVICE_RECOVERED = "com.heartbeatz.party.SERVICE_RECOVERED";
    
    // ============ EXTRAS KEYS ============
    
    public static final String EXTRA_PARTY_NAME = "com.heartbeatz.party.PARTY_NAME";
    public static final String EXTRA_PARTY_PIN = "com.heartbeatz.party.PARTY_PIN";
    public static final String EXTRA_PARTY_HOST = "com.heartbeatz.party.PARTY_HOST";
    public static final String EXTRA_PARTY_STATE = "com.heartbeatz.party.PARTY_STATE";
    
    public static final String EXTRA_GUEST_LIST = "com.heartbeatz.party.GUEST_LIST";
    public static final String EXTRA_GUEST_COUNT = "com.heartbeatz.party.GUEST_COUNT";
    
    public static final String EXTRA_SYNC_PACKET = "com.heartbeatz.party.SYNC_PACKET";
    public static final String EXTRA_SONG_UPDATE = "com.heartbeatz.party.SONG_UPDATE";
    
    public static final String EXTRA_QUEUE_TRACKS = "com.heartbeatz.party.QUEUE_TRACKS";
    public static final String EXTRA_TRACK_INDEX = "com.heartbeatz.party.TRACK_INDEX";
    public static final String EXTRA_SOURCE = "com.heartbeatz.party.SOURCE";
    
    public static final String EXTRA_ERROR_MESSAGE = "com.heartbeatz.party.ERROR_MESSAGE";
    
    // Private constructor to prevent instantiation
    private PartySessionContract() {
        throw new AssertionError("No instances");
    }
    
    /**
     * Helper to create a SessionCommand with custom action
     */
    public static SessionCommand createCommand(String action) {
        return new SessionCommand(action, Bundle.EMPTY);
    }
    
    /**
     * Helper to create a SessionCommand with custom action and extras
     */
    public static SessionCommand createCommand(String action, Bundle extras) {
        return new SessionCommand(action, Bundle.EMPTY);
    }
}
