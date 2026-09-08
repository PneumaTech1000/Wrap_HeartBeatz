package com.giga.tech1000.party_mode.core;

/**
 * Defines the state of the Party Mode for both backend logic and UI.
 */
public enum PartyState {
    IDLE,
    SEARCHING,
    CREATING,
    CONNECTING,
    FOUND,
    HOSTING,
    JOINED,
    ERROR,
    SETUP_REQUIRED
}
