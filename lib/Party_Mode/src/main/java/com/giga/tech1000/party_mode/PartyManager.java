package com.giga.tech1000.party_mode;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.media3.common.Player;

import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.giga.tech1000.party_mode.streaming.StreamProvider;
import com.giga.tech1000.party_mode.webrtc.WebRtcPartyManager;

import android.net.Uri;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Facade for the Party Mode architecture.
 * Simplifies hosting and joining parties using WebRTC for real-time communication.
 */
/** @deprecated Use app PartySession / Firebase party repository. WebRTC audio path is frozen; see FROZEN_TRANSPORT.md and §7 cloud URL migration. */
@Deprecated
public class PartyManager implements StreamProvider {
    private static final String TAG = "PartyManager";
    private static final int HTTP_PORT = 8080;

    public interface PartyManagerListener {
        void onHostCreated(PartyHost host);
        void onGuestListUpdated(List<String> guests);
        void onGuestAuthorized(String guestAddress);
        void onPartyStopped();
        void onSyncDataReceived(SyncPacket syncPacket);
    }

    private final Context context;
    private final String partyId;
    private Player player;

    private PartyState currentState = PartyState.IDLE;

    private PartyHost hostInfo;
    private String partyPin = "";
    private Uri currentUri;
    private String currentMediaId;

    private final Set<String> connectedGuests = Collections.synchronizedSet(new HashSet<>());
    private PartyManagerListener listener;

    // WebRTC Components (replacing UDP-based components)
    private WebRtcPartyManager webRtcPartyManager;

    public PartyManager(Context context) {
        this.context = context.getApplicationContext();
        this.partyId = UUID.randomUUID().toString();
        // Initialize WebRtcPartyManager for WebRTC-based communication
        this.webRtcPartyManager = new WebRtcPartyManager(context);
        // Set up listener forwarding
        this.webRtcPartyManager.setListener(new WebRtcPartyManager.WebRtcPartyManagerListener() {
            @Override
            public void onHostCreated(PartyHost host) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onHostCreated(host);
                }
            }

            @Override
            public void onGuestListUpdated(List<String> guests) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onGuestListUpdated(guests);
                }
            }

            @Override
            public void onGuestAuthorized(String guestAddress) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onGuestAuthorized(guestAddress);
                }
            }

            @Override
            public void onPartyStopped() {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onPartyStopped();
                }
            }

            @Override
            public void onSyncDataReceived(SyncPacket syncPacket) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onSyncDataReceived(syncPacket);
                }
            }
        });
    }

    public void setListener(PartyManagerListener listener) {
        this.listener = listener;
        // Also set listener on WebRtcPartyManager
        this.webRtcPartyManager.setListener(new WebRtcPartyManager.WebRtcPartyManagerListener() {
            @Override
            public void onHostCreated(PartyHost host) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onHostCreated(host);
                }
            }

            @Override
            public void onGuestListUpdated(List<String> guests) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onGuestListUpdated(guests);
                }
            }

            @Override
            public void onGuestAuthorized(String guestAddress) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onGuestAuthorized(guestAddress);
                }
            }

            @Override
            public void onPartyStopped() {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onPartyStopped();
                }
            }

            @Override
            public void onSyncDataReceived(SyncPacket syncPacket) {
                if (PartyManager.this.listener != null) {
                    PartyManager.this.listener.onSyncDataReceived(syncPacket);
                }
            }
        });
    }

    public PartyHost getHostInfo() {
        return hostInfo;
    }

    public boolean hasPartyPin() {
        return partyPin != null && !partyPin.isEmpty();
    }

    public boolean verifyPin(String pin) {
        if (!hasPartyPin()) return true;
        return TextUtils.equals(partyPin, pin);
    }

    @Override
    public void onGuestConnected(String remoteAddress) {
        // In WebRTC implementation, guest connections are handled through peer connections
        // This method is kept for compatibility with StreamProvider interface
        addGuestConnection(remoteAddress);
    }

    public List<String> getConnectedGuests() {
        return new ArrayList<>(connectedGuests);
    }

    public void confirmJoined() {
        this.currentState = PartyState.JOINED;
    }

    public void updateTrack(Uri uri, String mediaId) {
        this.currentUri = uri;
        this.currentMediaId = mediaId;
        // Note: With WebRTC, media ID synchronization would happen via data channels
        // rather than the SyncMaster/SyncFollower approach
    }

    public PartyHost startHosting(String partyName, String pin) {
        if (currentState != PartyState.IDLE) stop();

        currentState = PartyState.HOSTING;

        this.partyPin = pin != null ? pin : "";

        this.hostInfo = new PartyHost(
                partyId,
                partyName,
                android.os.Build.MODEL,
                getLocalIpAddress(),
                pin,
                HTTP_PORT, // Port kept for compatibility but not used for WebRTC
                getOwnerId(), // Using actual user ID for ownership
                System.currentTimeMillis()
        );
        this.hostInfo.isPasswordProtected = hasPartyPin();

        // Start WebRTC-based hosting
        webRtcPartyManager.startHosting(partyName, pin);

        if (listener != null) {
            listener.onHostCreated(hostInfo);
        }

        Log.i(TAG, "Started hosting via WebRTC: " + partyName + " on " + hostInfo.ipAddress);
        return hostInfo;
    }

    // Note: startScanning and stopScanning are kept for API compatibility
    // but actual discovery now happens through WebRTC/Firebase signaling
    public void startScanning(Object listener) {
        // In WebRTC implementation, discovery is handled through Firebase signaling
        // This method is kept for compatibility but doesn't perform actual scanning
        Log.w(TAG, "startScanning() called - discovery now handled via WebRTC/Firebase signaling");
        currentState = PartyState.SEARCHING;
    }

    public void stopScanning() {
        if (currentState == PartyState.SEARCHING) {
            currentState = PartyState.IDLE;
        }
        Log.w(TAG, "stopScanning() called - discovery now handled via WebRTC/Firebase signaling");
    }

    public void joinParty(PartyHost host, String pin) {
        if (currentState != PartyState.IDLE) stop();

        currentState = PartyState.CONNECTING;
        this.partyPin = pin != null ? pin : "";
        this.hostInfo = host;
        this.currentUri = null;
        this.currentMediaId = null;

        // Join party using WebRTC
        webRtcPartyManager.joinParty(host, pin);

        Log.i(TAG, "Joining party via WebRTC: " + host.partyName);
    }

    public void stop() {
        currentState = PartyState.IDLE;

        // Stop WebRTC session
        if (webRtcPartyManager != null) {
            webRtcPartyManager.release();
        }

        connectedGuests.clear();
        hostInfo = null;
        partyPin = "";

        if (listener != null) {
            listener.onPartyStopped();
        }
        Log.i(TAG, "Party Mode stopped");
    }

    @Override
    public Uri getCurrentUri() {
        // WebRTC doesn't use HTTP URIs for streaming - media is sent peer-to-peer
        // Return null to indicate WebRTC streaming is being used
        return null;
    }

    @Override
    public InputStream getStream() throws IOException {
        // WebRTC doesn't use traditional input streams for audio.
        // Audio is sent directly via WebRTC peer connection
        return null;
    }

    public boolean isProtected() {
        return hasPartyPin();
    }

    public void addGuestConnection(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isEmpty()) return;
        if (connectedGuests.add(remoteAddress) && listener != null) {
            listener.onGuestAuthorized(remoteAddress);
            listener.onGuestListUpdated(new ArrayList<>(connectedGuests));
        }
    }

    private String getLocalIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) return sAddr;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    /**
     * Get the current user ID for ownership purposes
     * @return The user ID if authenticated, null otherwise
     */
    private String getOwnerId() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
}