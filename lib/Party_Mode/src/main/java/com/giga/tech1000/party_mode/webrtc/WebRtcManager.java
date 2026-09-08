package com.giga.tech1000.party_mode.webrtc;

import static com.giga.tech1000.party_mode.webrtc.WebRtcPartyManager.AUDIO_LABEL;
import static com.giga.tech1000.party_mode.webrtc.WebRtcPartyManager.SYNC_LABEL;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.party_mode.model.SyncPacket;
import com.giga.tech1000.party_mode.webrtc.interfaces.SignalingClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.RTCConfiguration;
import org.webrtc.PeerConnection.Observer;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main WebRTC manager for party mode functionality
 * Coordinates peer connections, signaling, and audio streaming
 *
 * ROLE-BASED AWARENESS:
 * - Tracks whether this instance is host or guest
 * - Used for logging and potential role-based optimizations
 * - Actual send/receive permissions enforced at application level
 */
public class WebRtcManager implements Observer, SdpObserver {

    private static final String TAG = "WebRtcManager";
    private static final String VIDEO_TRACK_ID = "100";
    private static final String AUDIO_TRACK_ID = "101";

    private Context context;
    private PeerConnectionManager peerConnectionManager;
    private SignalingClient signalingClient;
    private AudioStreamManager audioStreamManager;

    private PeerConnection peerConnection;
    private boolean isInitiator = false;
    private boolean isConnected = false;
    private boolean isHost = false; // Track host/guest role for application logic

    // Callbacks for WebRTC events
    public interface WebRtcEventListener {
        void onPeerConnectionReady();
        void onPeerConnectionClosed();
        void onPeerConnectionError(String description);
        void onLocalAudioTrackReady();
        void onRemoteAudioTrackReady();
        void onIceConnectionChange(PeerConnection.IceConnectionState newState);
        void onIceConnectionFailure();
        void onIceConnectionSuccess();
        void onSyncDataReceived(SyncPacket syncPacket);
    }

    private WebRtcEventListener eventListener;
    private AudioStreamManager.AudioDataReceiver audioDataReceiver;

    public WebRtcManager(Context context) {
        this.context = context;
        this.peerConnectionManager = new PeerConnectionManager(context);
        this.audioStreamManager = new AudioStreamManager(context);

        // Set up audio data receiver
        this.audioDataReceiver = new AudioStreamManager.AudioDataReceiver() {
            @Override
            public void onAudioDataReceived(byte[] audioData) {
                // Handle incoming audio data - could be passed to audio player
                if (eventListener != null) {
                    // Forward audio data to interested parties
                    // Implementation would depend on audio playback mechanism
                }
            }

            @Override
            public void onAudioDataChannelStateChange(DataChannel.State state) {
                if (eventListener != null) {
                    switch (state) {
                        case OPEN:
                            eventListener.onIceConnectionSuccess();
                            break;
                        case CLOSED:
                        case CLOSING:
                            eventListener.onIceConnectionFailure();
                            break;
                        default:
                            break;
                    }
                }
            }
        };

        audioStreamManager.initialize(null, audioDataReceiver); // Will be initialized properly when peer connection is ready
    }

    /**
     * Sets the event listener for WebRTC events
     */
    public void setEventListener(WebRtcEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Initializes WebRTC for hosting a party
     */
    public void initializeAsHost(String roomId, String clientId, SignalingClient signalingClient) {
        this.isInitiator = true;
        this.isHost = true; // Mark as host
        this.signalingClient = signalingClient;

        // Set up signaling client callback
        if (signalingClient instanceof FirebaseSignalingClient) {
            ((FirebaseSignalingClient) signalingClient).setCallback(this::handleSignalingMessage);
        }

        signalingClient.initialize(roomId);
        createPeerConnection();
    }

    /**
     * Initializes WebRTC for joining a party
     */
    public void initializeAsGuest(String roomId, String clientId, SignalingClient signalingClient) {
        this.isInitiator = false;
        this.isHost = false; // Mark as guest
        this.signalingClient = signalingClient;

        // Set up signaling client callback
        if (signalingClient instanceof FirebaseSignalingClient) {
            ((FirebaseSignalingClient) signalingClient).setCallback(new FirebaseSignalingClient.SignalingCallback() {
                @Override
                public void onMessageReceived(SignalingMessage message) {
                    handleSignalingMessage(message);
                }
            });
        }

        signalingClient.initialize(roomId);
        createPeerConnection();
    }

    /**
     * Creates the peer connection with appropriate configuration
     */
    private void createPeerConnection() {
        RTCConfiguration config = new RTCConfiguration(getIceServers());
        peerConnection = peerConnectionManager.createPeerConnection(config, this);

        if (peerConnection == null) {
            Log.e(TAG, "Failed to create peer connection");
            if (eventListener != null) {
                eventListener.onPeerConnectionError("Failed to create peer connection");
            }
            return;
        }

        // Create local audio track
        AudioTrack localAudioTrack = peerConnectionManager.createAudioTrack(AUDIO_TRACK_ID);
        if (localAudioTrack != null) {
            peerConnection.addTrack(localAudioTrack, java.util.Collections.singletonList(AUDIO_TRACK_ID));
            if (eventListener != null) {
                eventListener.onLocalAudioTrackReady();
            }
        }

        // Create offer if we're the initiator
        if (isInitiator) {
            peerConnection.createOffer(this, new MediaConstraints());
        }
    }

    /**
     * Gets ICE servers for peer connection
     * Using Google's public STUN servers for simplicity
     */
    private List<PeerConnection.IceServer> getIceServers() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        // In production, you might want to add TURN servers here
        return iceServers;
    }

    /**
     * Handles incoming signaling messages
     */
    private void handleSignalingMessage(SignalingMessage message) {
        if (signalingClient == null) {
            Log.w(TAG, "Received signaling message but client is not initialized");
            return;
        }

        switch (message.type) {
            case OFFER:
                handleOffer(message);
                break;
            case ANSWER:
                handleAnswer(message);
                break;
            case ICE_CANDIDATE:
                handleIceCandidate(message);
                break;
            default:
                Log.w(TAG, "Unknown signaling message type: " + message.type);
                break;
        }
    }

    /**
     * Handles an incoming offer
     */
    private void handleOffer(SignalingMessage message) {
        try {
            JSONObject json = new JSONObject(message.payload);
            String sdp = json.getString("sdp");
            String type = json.getString("type");

            SessionDescription.Type sdpType = SessionDescription.Type.fromCanonicalForm(type);
            SessionDescription offer = new SessionDescription(sdpType, sdp);

            peerConnection.setRemoteDescription(this, offer);

            // Create answer
            peerConnection.createAnswer(this, new MediaConstraints());

        } catch (Exception e) {
            Log.e(TAG, "Error handling offer", e);
        }
    }

    /**
     * Handles an incoming answer
     */
    private void handleAnswer(SignalingMessage message) {
        try {
            JSONObject json = new JSONObject(message.payload);
            String sdp = json.getString("sdp");
            String type = json.getString("type");

            SessionDescription.Type sdpType = SessionDescription.Type.fromCanonicalForm(type);
            SessionDescription answer = new SessionDescription(sdpType, sdp);

            peerConnection.setRemoteDescription(this, answer);

        } catch (Exception e) {
            Log.e(TAG, "Error handling answer", e);
        }
    }

    /**
     * Handles an incoming ICE candidate
     */
    private void handleIceCandidate(SignalingMessage message) {
        try {
            JSONObject json = new JSONObject(message.payload);
            String candidate = json.getString("candidate");
            String sdpMid = json.getString("id");
            int sdpMLineIndex = json.getInt("label");

            IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            peerConnection.addIceCandidate(iceCandidate);

        } catch (Exception e) {
            Log.e(TAG, "Error handling ICE candidate", e);
        }
    }

    /**
     * Sends an offer to the remote peer
     */
    public void sendOffer() {
        if (peerConnection == null) {
            Log.w(TAG, "Cannot send offer: peer connection not initialized");
            return;
        }

        peerConnection.createOffer(this, new MediaConstraints());
    }

    /**
     * Sends an answer to the remote peer
     */
    public void sendAnswer() {
        if (peerConnection == null) {
            Log.w(TAG, "Cannot send answer: peer connection not initialized");
            return;
        }

        peerConnection.createAnswer(this, new MediaConstraints());
    }

    /**
     * Sends an ICE candidate to the remote peer
     */
    public void sendIceCandidate(IceCandidate candidate) {
        if (signalingClient == null) {
            Log.w(TAG, "Cannot send ICE candidate: signaling client not initialized");
            return;
        }

        signalingClient.sendIceCandidate(candidate);
    }

    /**
     * Closes the peer connection and cleans up resources
     */
    public void close() {
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }

        if (audioStreamManager != null) {
            audioStreamManager.close();
        }

        if (signalingClient != null) {
            signalingClient.close();
        }

        isConnected = false;
        if (eventListener != null) {
            eventListener.onPeerConnectionClosed();
        }
    }

    // PeerConnection.Observer implementation
    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        if (iceCandidate != null) {
            sendIceCandidate(iceCandidate);
        }
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        // Not used
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
        // Not used
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
        Log.d(TAG, "ICE connection state changed: " + newState);
        if (eventListener != null) {
            eventListener.onIceConnectionChange(newState);
        }

        switch (newState) {
            case CONNECTED:
            case COMPLETED:
                isConnected = true;
                if (eventListener != null) {
                    eventListener.onPeerConnectionReady();

                    // Set up data channels when connection is ready
                    setupDataChannels();
                }
                break;
            case FAILED:
            case DISCONNECTED:
            case CLOSED:
                isConnected = false;
                if (eventListener != null) {
                    eventListener.onIceConnectionFailure();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        // Not used
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
        // Not used
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        // Not used with PeerConnection API that uses addTrack
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        // Not used
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        if (dataChannel != null) {
            String label = dataChannel.label();
            dataChannels.put(label, dataChannel);

            Log.d(TAG, "Data channel received: " + label + (isHost ? " [HOST]" : " [GUEST]"));

            // Set up data channel observer
            dataChannel.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long l) {
                    // Not used
                }

                @Override
                public void onStateChange() {
                    Log.d(TAG, "Data channel state changed: " + dataChannel.state() + " for label: " + label +
                            (isHost ? " [HOST]" : " [GUEST]"));
                    if (eventListener != null) {
                        if (dataChannel.state() == DataChannel.State.OPEN) {
                            eventListener.onIceConnectionSuccess();
                        } else if (dataChannel.state() == DataChannel.State.CLOSED ||
                                 dataChannel.state() == DataChannel.State.CLOSING) {
                            eventListener.onIceConnectionFailure();
                        }
                    }
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                    // Handle incoming data
                    if (buffer.binary) {
                        byte[] data = new byte[buffer.data.remaining()];
                        buffer.data.get(data);

                        // Route data based on channel label
                        if (AUDIO_LABEL.equals(label)) {
                            // Handle audio data
                            if (audioDataReceiver != null) {
                                audioDataReceiver.onAudioDataReceived(data);
                            }
                        } else if (SYNC_LABEL.equals(label)) {
                            // Handle sync data
                            String syncData = new String(data);
                            if (eventListener != null) {
                                // In a real implementation, we'd parse and act on sync data
                                Log.d(TAG, "Received sync data: " + syncData.substring(0, Math.min(100, syncData.length())) +
                                        (isHost ? " [HOST]" : " [GUEST]"));
                            }
                        }
                    }
                }
            });
        }
    }

    // Data channels map - initialize it
    private final java.util.Map<String, DataChannel> dataChannels = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onRenegotiationNeeded() {
        // Not used
    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
        // Handle incoming tracks if needed
        if (eventListener != null && receiver.track() instanceof AudioTrack) {
            eventListener.onRemoteAudioTrackReady();
        }
        if (eventListener != null && receiver.track() instanceof VideoTrack) {
            // Handle video track if needed
        }
    }

    // SdpObserver implementation
    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        if (peerConnection == null) {
            Log.w(TAG, "Received SDP but peer connection is null");
            return;
        }

        peerConnection.setLocalDescription(this, sessionDescription);

        // Send the SDP to the remote peer via signaling
        if (signalingClient != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("type", sessionDescription.type.canonicalForm());
                json.put("sdp", sessionDescription.description);

                SignalingMessage message = new SignalingMessage(
                        sessionDescription.type == SessionDescription.Type.OFFER ?
                                SignalingMessage.Type.OFFER : SignalingMessage.Type.ANSWER,
                        "local_client", // In real implementation, this would be the actual client ID
                        json.toString()
                );

                if (sessionDescription.type == SessionDescription.Type.OFFER) {
                    signalingClient.sendOffer(sessionDescription);
                } else {
                    signalingClient.sendAnswer(sessionDescription);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error creating signaling message", e);
            }
        }
    }

    @Override
    public void onSetSuccess() {
        // Successfully set local or remote description
    }

    @Override
    public void onCreateFailure(String s) {
        Log.e(TAG, "Failed to create SDP: " + s);
        if (eventListener != null) {
            eventListener.onPeerConnectionError("Failed to create SDP: " + s);
        }
    }

    @Override
    public void onSetFailure(String s) {
        Log.e(TAG, "Failed to set SDP: " + s);
        if (eventListener != null) {
            eventListener.onPeerConnectionError("Failed to set SDP: " + s);
        }
    }

    /**
     * Checks if the peer connection is ready
     */
    public boolean isConnected() {
        return isConnected && peerConnection != null &&
               peerConnection.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED;
    }

    /**
     * Set up data channels for communication
     */
    private void setupDataChannels() {
        if (peerConnection == null) {
            Log.w(TAG, "Cannot setup data channels: peer connection not initialized");
            return;
        }

        try {
            // Create audio data channel
            DataChannel.Init audioOptions = new DataChannel.Init();
            audioOptions.ordered = true;
            audioOptions.negotiated = true;
            audioOptions.id = 0; // ID for audio channel
            DataChannel audioDataChannel = peerConnection.createDataChannel(AUDIO_LABEL, audioOptions);
            audioDataChannel.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long l) {
                    // Not used
                }

                @Override
                public void onStateChange() {
                    Log.d(TAG, "Audio data channel state: " + audioDataChannel.state() +
                            (isHost ? " [HOST]" : " [GUEST]"));
                    if (eventListener != null) {
                        if (audioDataChannel.state() == DataChannel.State.OPEN) {
                            eventListener.onIceConnectionSuccess();
                        } else if (audioDataChannel.state() == DataChannel.State.CLOSED ||
                                 audioDataChannel.state() == DataChannel.State.CLOSING) {
                            eventListener.onIceConnectionFailure();
                        }
                    }
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                    // Handle incoming audio data
                    if (buffer.binary) {
                        byte[] data = new byte[buffer.data.remaining()];
                        buffer.data.get(data);
                        if (audioDataReceiver != null) {
                            audioDataReceiver.onAudioDataReceived(data);
                        }
                    }
                }
            });
            dataChannels.put(AUDIO_LABEL, audioDataChannel);
            Log.d(TAG, "Audio data channel created" + (isHost ? " [HOST]" : " [GUEST]"));

            // Create sync data channel
            DataChannel.Init syncOptions = new DataChannel.Init();
            syncOptions.ordered = true;
            syncOptions.negotiated = true;
            syncOptions.id = 1; // ID for sync channel
            DataChannel syncDataChannel = peerConnection.createDataChannel(SYNC_LABEL, syncOptions);
            syncDataChannel.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long l) {
                    // Not used
                }

                @Override
                public void onStateChange() {
                    Log.d(TAG, "Sync data channel state: " + syncDataChannel.state() +
                            (isHost ? " [HOST]" : " [GUEST]"));
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                    // Handle sync data
                    if (buffer.binary) {
                        byte[] data = new byte[buffer.data.remaining()];
                        buffer.data.get(data);
                        String json = new String(data);
                        // Parse and apply sync data
                        if (eventListener != null) {
                            try {
                                // Parse the JSON sync data
                                JSONObject jsonObject = new JSONObject(json);
                                SyncPacket syncPacket = new SyncPacket();
                                syncPacket.state = jsonObject.optString("state", "UNKNOWN");
                                syncPacket.positionMs = jsonObject.optLong("positionMs", 0);
                                syncPacket.durationMs = jsonObject.optLong("durationMs", 0);
                                syncPacket.playbackSpeed = (float) jsonObject.optDouble("playbackSpeed", 1.0);
                                syncPacket.mediaId = jsonObject.optString("mediaId", "");
                                syncPacket.title = jsonObject.optString("title", "");
                                syncPacket.artist = jsonObject.optString("artist", "");
                                syncPacket.album = jsonObject.optString("album", "");
                                syncPacket.sentAt = jsonObject.optLong("sentAt", 0);

                                // Notify listener about the sync data
                                eventListener.onSyncDataReceived(syncPacket);
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing sync data: " + e.getMessage(), e);
                            }
                        }
                    }
                }
            });
            dataChannels.put(SYNC_LABEL, syncDataChannel);
            Log.d(TAG, "Sync data channel created" + (isHost ? " [HOST]" : " [GUEST]"));

        } catch (Exception e) {
            Log.e(TAG, "Error setting up data channels", e);
        }
    }

    /**
     * Helper method to send data only if we're the host
     * Applications should use this to enforce host-only sending
     */
    protected boolean isHostSendingAllowed() {
        return isHost;
    }
}