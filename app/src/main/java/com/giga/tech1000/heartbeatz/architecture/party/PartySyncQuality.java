package com.giga.tech1000.heartbeatz.architecture.party;

/**
 * Guest-side estimate of how tightly local playback tracks the SET ideal.
 * Host does not compute this (no visibility into guest clocks).
 */
public enum PartySyncQuality {
    /** Not in a guest session or no anchor yet. */
    UNKNOWN,
    /** Loading / buffering / armed — not audible free-run. */
    BUFFERING,
    /** Host paused (or guest paused with host). */
    PAUSED,
    /** |delta| sustained under ~150ms. */
    TIGHT,
    /** |delta| under ~750ms — acceptable soft lock. */
    SOFT,
    /** Larger error or unstable. */
    DRIFTING;

    public static PartySyncQuality fromDeltaMs(long deltaMs, boolean hostPlaying, boolean buffering) {
        if (buffering) return BUFFERING;
        if (!hostPlaying) return PAUSED;
        long abs = Math.abs(deltaMs);
        if (abs <= 150L) return TIGHT;
        if (abs <= 750L) return SOFT;
        return DRIFTING;
    }
}
