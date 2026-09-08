package com.giga.tech1000.party_mode.webrtc;


/**
 * Data class for signaling messages
 */
public class SignalingMessage {
    public enum Type { OFFER, ANSWER, ICE_CANDIDATE }

    Type type;
    String senderId;
    String payload; // JSON string containing SDP or ICE candidate

    public SignalingMessage() {}

    public SignalingMessage(Type type, String senderId, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
    }
}
