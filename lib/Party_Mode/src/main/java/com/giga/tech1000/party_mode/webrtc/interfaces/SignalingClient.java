package com.giga.tech1000.party_mode.webrtc.interfaces;

import com.giga.tech1000.party_mode.webrtc.SignalingMessage;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

/**
 * Interface for WebRTC signaling via Firebase Realtime Database
 * Handles exchanging SDP offers/answers and ICE candidates
 */
public interface SignalingClient {
    void initialize(String roomId);
    void sendOffer(SessionDescription sdp);
    void sendAnswer(SessionDescription sdp);
    void sendIceCandidate(IceCandidate candidate);
    void onMessageReceived(SignalingMessage message);
    void close();
}

