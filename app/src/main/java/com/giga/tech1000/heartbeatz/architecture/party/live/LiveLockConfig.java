package com.giga.tech1000.heartbeatz.architecture.party.live;

import androidx.annotation.NonNull;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Live Lock (P5) — free mesh/star WebRTC party audio overlay.
 * <p>
 * Caps concurrency for free-tier / device heat. Public STUN only (no paid TURN);
 * best on same Wi‑Fi (pairs with Venue). Optional TURN URLs can be injected later.
 */
public final class LiveLockConfig {

    /** Hard product cap — do not raise without SFU. */
    public static final int MAX_PEERS = 8;

    /** Feature master switch (compile-time default on; host still opts in per party). */
    public static final boolean FEATURE_AVAILABLE = true;

    public static final String SIGNAL_VERSION = "1";

    /** ICE gather timeout before treating peer as failed. */
    public static final long ICE_TIMEOUT_MS = 20_000L;

    /** Debounce peer create when members list floods. */
    public static final long PEER_CREATE_DEBOUNCE_MS = 400L;

    private LiveLockConfig() {}

    @NonNull
    public static List<PeerConnection.IceServer> defaultIceServers() {
        List<PeerConnection.IceServer> list = new ArrayList<>();
        // Free public STUN — sufficient on same LAN / friendly NAT
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer());
        return Collections.unmodifiableList(list);
    }

    @NonNull
    public static PeerConnection.RTCConfiguration rtcConfig() {
        PeerConnection.RTCConfiguration cfg =
                new PeerConnection.RTCConfiguration(defaultIceServers());
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        cfg.continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        cfg.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        cfg.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        cfg.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        return cfg;
    }
}
