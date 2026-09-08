package com.giga.tech1000.heartbeatz.party_mode.model;

import com.giga.tech1000.party_mode.webrtc.SignalingMessage;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

/**
 * Firebase data model for WebRTC signaling messages
 * Used for storing and retrieving signaling data from Firebase Realtime Database
 */
@IgnoreExtraProperties
public class FirebaseSignalingMessage {

    public String type; // "offer", "answer", or "ice"
    public String senderId;
    public String payload; // JSON string containing SDP or ICE candidate data
    public long timestamp;

    // Default constructor required for Firebase
    public FirebaseSignalingMessage() {}

    public FirebaseSignalingMessage(String type, String senderId, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    @Exclude
    public SignalingMessage.Type getSignalingType() {
        switch (type) {
            case "offer":
                return SignalingMessage.Type.OFFER;
            case "answer":
                return SignalingMessage.Type.ANSWER;
            case "ice":
                return SignalingMessage.Type.ICE_CANDIDATE;
            default:
                return null;
        }
    }

    @Exclude
    public SignalingMessage toSignalingMessage() {
        return new SignalingMessage(
                getSignalingType(),
                senderId,
                payload
        );
    }
}