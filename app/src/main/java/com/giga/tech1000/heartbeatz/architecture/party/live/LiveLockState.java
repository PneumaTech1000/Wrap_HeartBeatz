package com.giga.tech1000.heartbeatz.architecture.party.live;

/**
 * Coarse Live Lock session state for UI chips and SET interaction policy.
 */
public enum LiveLockState {
    IDLE,
    /** Host published session; waiting for peers. */
    HOSTING,
    /** Guest joining / ICE in progress. */
    CONNECTING,
    /** At least one peer ICE connected; audio path active. */
    LIVE,
    /** Temporary ICE failure; may recover. */
    DEGRADED,
    FAILED,
    STOPPED;

    public boolean isAudioAuthoritative() {
        return this == LIVE || this == DEGRADED;
    }
}
