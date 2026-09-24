package com.giga.tech1000.heartbeatz.architecture.timeengine;

/**
 * Guest / session lifecycle relative to the shared track timeline.
 */
public enum TimeEnginePhase {
    /** No active schedule. */
    IDLE,
    /** Media URL known; loading/preparing. */
    LOADING,
    /** Media prepared; waiting until release instant (buffer held). */
    BUFFERING,
    /** Parked at target position; waiting for exact release time. */
    ARMED,
    /** Playing under TimeEngine lock; soft drift correction allowed. */
    LOCKED,
    /** Large drift; applying correction before returning to LOCKED. */
    DRIFT_CORRECT
}
