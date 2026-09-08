package com.giga.tech1000.party_mode.webrtc;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.party_mode.webrtc.SignalingMessage;
import com.giga.tech1000.party_mode.webrtc.interfaces.SignalingClient;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase-based implementation of SignalingClient
 * Uses Firebase Realtime Database for WebRTC signaling
 */
public class FirebaseSignalingClient implements SignalingClient {

    private static final String TAG = "FirebaseSignalingClient";
    private static final String SIGNALING_NODE = "webrtc_signaling";

    private DatabaseReference signalingRef;
    private String roomId;
    private String clientId;
    private SignalingCallback callback;

    // Event listeners
    private ChildEventListener messageEventListener;

    public FirebaseSignalingClient(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public void initialize(String roomId) {
        this.roomId = roomId;
        Log.d(TAG, "Initializing signaling for room: " + roomId);

        // Reference to the signaling node for this room
        signalingRef = FirebaseDatabase.getInstance()
                .getReference(SIGNALING_NODE)
                .child(roomId);

        // Listen for incoming messages
        if (messageEventListener != null) {
            signalingRef.removeEventListener(messageEventListener);
        }

        messageEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                SignalingMessage message = snapshot.getValue(SignalingMessage.class);
                if (message != null && !message.senderId.equals(clientId)) {
                    // Ignore our own messages
                    if (callback != null) {
                        callback.onMessageReceived(message);
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Not used for signaling
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                // Not used for signaling
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Not used for signaling
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Signaling listener cancelled: " + error.getMessage());
            }
        };

        signalingRef.addChildEventListener(messageEventListener);
    }

    @Override
    public void sendOffer(SessionDescription sdp) {
        if (signalingRef == null) {
            Log.w(TAG, "Cannot send offer: not initialized");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("type", sdp.type.canonicalForm());
            json.put("sdp", sdp.description);

            SignalingMessage message = new SignalingMessage(
                    SignalingMessage.Type.OFFER,
                    clientId,
                    json.toString()
            );

            // Push to Firebase
            signalingRef.push().setValue(message)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Offer sent successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to send offer", e));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating offer JSON", e);
        }
    }

    @Override
    public void sendAnswer(SessionDescription sdp) {
        if (signalingRef == null) {
            Log.w(TAG, "Cannot send answer: not initialized");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("type", sdp.type.canonicalForm());
            json.put("sdp", sdp.description);

            SignalingMessage message = new SignalingMessage(
                    SignalingMessage.Type.ANSWER,
                    clientId,
                    json.toString()
            );

            // Push to Firebase
            signalingRef.push().setValue(message)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Answer sent successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to send answer", e));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating answer JSON", e);
        }
    }

    @Override
    public void sendIceCandidate(IceCandidate candidate) {
        if (signalingRef == null) {
            Log.w(TAG, "Cannot send ICE candidate: not initialized");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("type", "ice");
            json.put("label", candidate.sdpMLineIndex);
            json.put("id", candidate.sdpMid);
            json.put("candidate", candidate.sdp);

            SignalingMessage message = new SignalingMessage(
                    SignalingMessage.Type.ICE_CANDIDATE,
                    clientId,
                    json.toString()
            );

            // Push to Firebase
            signalingRef.push().setValue(message)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "ICE candidate sent successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to send ICE candidate", e));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating ICE candidate JSON", e);
        }
    }



    @Override
    public void onMessageReceived(SignalingMessage message) {
        // This method is called by the Firebase listener
        // Actual handling is done in the callback
    }

    @Override
    public void close() {
        if (signalingRef != null && messageEventListener != null) {
            signalingRef.removeEventListener(messageEventListener);
            messageEventListener = null;
        }
        signalingRef = null;
    }

    /**
     * Set callback for handling incoming messages
     */
    public void setCallback(SignalingCallback callback) {
        this.callback = callback;
    }

    /**
     * Callback for signaling events
     */
    public interface SignalingCallback {
        void onMessageReceived(SignalingMessage message);
    }


}