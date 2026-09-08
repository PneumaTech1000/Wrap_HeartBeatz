package com.giga.tech1000.utils.statics;

/**
 * Centralized static constants for all session events and extras.
 * Consolidates both legacy SessionEvents and PartySessionContract constants.
 */
public final class SessionEvents {
    
    // ============ LEGACY SESSION EVENTS ============
    public static final String EXTRA_SESSION_ID = "extra_session_id";
    public static final String EXTRA_SESSION = "audio_session_id_update";

    public static final String EXTRA_QUEUE = "extra_queue";
    public static final String EXTRA_QUEUE_INDEX = "extra_queue_title";
    public static final String EXTRA_QUEUE_UPDATE = "extra_queue_update";

    public static final String EXTRA_SOURCE = "extra_source";
    public static final String EXTRA_SOURCE_UPDATE = "extra_source_update";

    public static final String EXTRA_PITCH = "extra_pitch";
    public static final String EXTRA_PITCH_UPDATE = "extra_pitch_update";
    
    public static final String EXTRA_SPEED = "extra_speed";
    public static final String EXTRA_SPEED_UPDATE = "extra_speed_update";

    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_DURATION = "extra_duration";
    public static final String EXTRA_IS_PLAYING = "extra_is_playing";
    public static final String EXTRA_PLAYBACK_STATE = "extra_playback_state";
    public static final String EXTRA_STATE_UPDATE = "extra_state_update";

    public static final String EXTRA_SONG_UPDATE = "extra_song_update";

    public static final String EXTRA_CREATE_PARTY_UPDATE = "extra_create_party_update";
    public static final String EXTRA_JOIN_PARTY_UPDATE = "extra_join_party_update";
    public static final String EXTRA_HOST_DISCOVERED_UPDATE = "extra_host_discovered_update";
    public static final String EXTRA_SERVICE_REGISTERED_UPDATE = "extra_service_registered_update";
    
    public static final String EVENT_AUTH_SUCCESS = "on_auth_success";
    public static final String EVENT_METADATA_RECEIVED = "on_metadata_received";
    public static final String EVENT_CONNECTION_FAILED = "on_connection_failed";
    public static final String EVENT_DISCONNECTED = "on_disconnected";
    public static final String EVENT_GUESTS_UPDATED = "on_guest_list_updated";
    public static final String EXTRA_GUEST_LIST = "extra_guest_list";
    public static final String EXTRA_GUEST_COUNT = "extra_guest_count";
    
    public static final String EVENT_SETUP_REQUIRED = "on_setup_required";
    public static final String EVENT_HOTSPOT_STARTED = "event_hotspot_started";
    public static final String EVENT_HOTSPOT_STOPPED = "event_hotspot_stopped";
    
    // ============ PARTY SESSION CONTROL COMMANDS ============
    
    public static final String ACTION_START_DISCOVERY = "com.heartbeatz.party.START_DISCOVERY";
    public static final String ACTION_STOP_DISCOVERY = "com.heartbeatz.party.STOP_DISCOVERY";
    public static final String ACTION_CREATE_PARTY = "com.heartbeatz.party.CREATE_PARTY";
    public static final String ACTION_JOIN_PARTY = "com.heartbeatz.party.JOIN_PARTY";
    public static final String ACTION_LEAVE_PARTY = "com.heartbeatz.party.LEAVE_PARTY";
    public static final String ACTION_PLAY_QUEUE = "com.heartbeatz.party.PLAY_QUEUE";
    public static final String ACTION_UPDATE_FOREGROUND_NOTIFICATION = "com.heartbeatz.party.UPDATE_FOREGROUND_NOTIFICATION";
    
    // ============ PARTY STATE EVENT BROADCASTS ============
    
    public static final String EVENT_PARTY_HOST_DISCOVERED = "com.heartbeatz.party.HOST_DISCOVERED";
    public static final String EVENT_PARTY_HOST_CREATED = "com.heartbeatz.party.HOST_CREATED";
    public static final String EVENT_PARTY_JOINED = "com.heartbeatz.party.JOINED";
    public static final String EVENT_PARTY_AUTH_SUCCESS = "com.heartbeatz.party.AUTH_SUCCESS";
    public static final String EVENT_PARTY_METADATA_RECEIVED = "com.heartbeatz.party.METADATA_RECEIVED";
    public static final String EVENT_PARTY_GUESTS_UPDATED = "com.heartbeatz.party.GUESTS_UPDATED";
    public static final String EVENT_PARTY_LEFT = "com.heartbeatz.party.LEFT";
    public static final String EVENT_PARTY_CONNECTION_FAILED = "com.heartbeatz.party.CONNECTION_FAILED";
    public static final String EVENT_SERVICE_RECOVERED = "com.heartbeatz.party.SERVICE_RECOVERED";
    
    // ============ PARTY EXTRAS KEYS ============
    
    public static final String EXTRA_PARTY_NAME = "com.heartbeatz.party.PARTY_NAME";
    public static final String EXTRA_PARTY_PIN = "com.heartbeatz.party.PARTY_PIN";
    public static final String EXTRA_PARTY_HOST = "com.heartbeatz.party.PARTY_HOST";
    public static final String EXTRA_PARTY_STATE = "com.heartbeatz.party.PARTY_STATE";
    public static final String EXTRA_SYNC_PACKET = "com.heartbeatz.party.SYNC_PACKET";
    public static final String EXTRA_QUEUE_TRACKS = "com.heartbeatz.party.QUEUE_TRACKS";
    public static final String EXTRA_TRACK_INDEX = "com.heartbeatz.party.TRACK_INDEX";
    public static final String EXTRA_ERROR_MESSAGE = "com.heartbeatz.party.ERROR_MESSAGE";
    
    // Private constructor to prevent instantiation
    private SessionEvents() {
        throw new AssertionError("No instances");
    }
}
