package com.giga.tech1000.heartbeatz.architecture.timeengine;

/**
 * Guest / session lifecycle relative to the shared track timeline.
 * See TIME_ENGINE_ARCHITECTURE.md.
 */
public enum TimeEnginePhase {
    /** No active schedule. */
    IDLE,
    /** Media URL known; opening stream. */
    LOADING,
    /** Waiting for enough buffered media past ideal + margin. */
    BUFFERING,
    /** Muted; waiting for release schedule time (or next checkpoint). */
    ARMED,
    /** Audible; small rate correction only. */
    LOCKED,
    /**
     * Lagged, underbuffered, or hard drift — audio must stay silent
     * until re-armed on a valid window.
     */
    STALE
}
