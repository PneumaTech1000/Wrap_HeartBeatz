package com.giga.tech1000.heartbeatz.architecture.party.live;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.party.PartyFirebasePaths;
import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase RTDB signaling under {@code parties/{partyId}/live_lock}.
 * <p>
 * Paths:
 * <ul>
 *   <li>{@code session} — host enable metadata</li>
 *   <li>{@code signal/{pushId}} — offer/answer/candidate directed by {@code to}</li>
 * </ul>
 */
public final class LiveLockSignaling {

    public interface SessionListener {
        void onSessionChanged(@Nullable LiveLockSessionMeta meta);
    }

    public interface SignalListener {
        void onSignal(@NonNull LiveLockSignalMessage msg);
    }

    private final FirebaseDatabase db = FirebaseDatabase.getInstance();
    @Nullable private DatabaseReference root;
    @Nullable private ValueEventListener sessionListener;
    @Nullable private ChildEventListener signalListener;
    @Nullable private String selfUid;

    public void attach(
            @NonNull String partyId,
            @NonNull String selfUid,
            @Nullable SessionListener sessionListener,
            @Nullable SignalListener signalListener) {
        detach();
        this.selfUid = selfUid;
        this.root = db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.LIVE_LOCK);

        if (sessionListener != null) {
            this.sessionListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    LiveLockSessionMeta meta = snapshot.child("session")
                            .getValue(LiveLockSessionMeta.class);
                    sessionListener.onSessionChanged(meta);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    PartyLog.w("LiveLock", "session listen: " + error.getMessage());
                }
            };
            root.addValueEventListener(this.sessionListener);
        }

        if (signalListener != null) {
            this.signalListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    LiveLockSignalMessage msg = snapshot.getValue(LiveLockSignalMessage.class);
                    if (msg == null) return;
                    msg.id = snapshot.getKey();
                    if (selfUid.equals(msg.from)) return; // ignore echo
                    if (msg.to != null && !msg.to.isEmpty() && !selfUid.equals(msg.to)) {
                        return; // directed elsewhere
                    }
                    signalListener.onSignal(msg);
                    // Best-effort cleanup of consumed signals
                    snapshot.getRef().removeValue();
                }

                @Override public void onChildChanged(@NonNull DataSnapshot s, @Nullable String p) {}
                @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
                @Override public void onChildMoved(@NonNull DataSnapshot s, @Nullable String p) {}
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    PartyLog.w("LiveLock", "signal listen: " + error.getMessage());
                }
            };
            root.child("signal").addChildEventListener(this.signalListener);
        }

        PartyLog.i("LiveLock", "signaling attached party=" + partyId + " uid=" + selfUid);
    }

    public void detach() {
        if (root != null && sessionListener != null) {
            root.removeEventListener(sessionListener);
        }
        if (root != null && signalListener != null) {
            root.child("signal").removeEventListener(signalListener);
        }
        sessionListener = null;
        signalListener = null;
        root = null;
        selfUid = null;
    }

    public void publishSession(@NonNull String partyId, @NonNull LiveLockSessionMeta meta) {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", meta.enabled);
        map.put("hostUid", meta.hostUid);
        map.put("maxPeers", meta.maxPeers);
        map.put("version", meta.version != null ? meta.version : LiveLockConfig.SIGNAL_VERSION);
        map.put("updatedAt", ServerValue.TIMESTAMP);
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.LIVE_LOCK)
                .child("session")
                .updateChildren(map)
                .addOnFailureListener(e ->
                        PartyLog.e("LiveLock", "publishSession failed", e));
    }

    public void clearSession(@NonNull String partyId) {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", false);
        map.put("updatedAt", ServerValue.TIMESTAMP);
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.LIVE_LOCK)
                .child("session")
                .updateChildren(map);
    }

    public void send(@NonNull String partyId, @NonNull LiveLockSignalMessage msg) {
        if (msg.from == null || msg.type == null) return;
        Map<String, Object> map = new HashMap<>();
        map.put("from", msg.from);
        if (msg.to != null) map.put("to", msg.to);
        map.put("type", msg.type);
        if (msg.sdp != null) map.put("sdp", msg.sdp);
        if (msg.sdpType != null) map.put("sdpType", msg.sdpType);
        if (msg.candidate != null) map.put("candidate", msg.candidate);
        if (msg.sdpMid != null) map.put("sdpMid", msg.sdpMid);
        map.put("sdpMLineIndex", msg.sdpMLineIndex);
        map.put("ts", ServerValue.TIMESTAMP);
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.LIVE_LOCK)
                .child("signal")
                .push()
                .setValue(map)
                .addOnFailureListener(e ->
                        PartyLog.e("LiveLock", "send " + msg.type + " failed", e));
    }

    public static class LiveLockSessionMeta {
        public boolean enabled;
        @Nullable public String hostUid;
        public int maxPeers = LiveLockConfig.MAX_PEERS;
        @Nullable public String version;
        @Nullable public Object updatedAt;

        public LiveLockSessionMeta() {}
    }

    public static class LiveLockSignalMessage {
        @Nullable public String id;
        @Nullable public String from;
        @Nullable public String to;
        /** offer | answer | candidate | bye */
        @Nullable public String type;
        @Nullable public String sdp;
        @Nullable public String sdpType;
        @Nullable public String candidate;
        @Nullable public String sdpMid;
        public int sdpMLineIndex = -1;
        @Nullable public Object ts;

        public LiveLockSignalMessage() {}

        @NonNull
        public static LiveLockSignalMessage offer(
                @NonNull String from, @NonNull String to,
                @NonNull String sdp, @NonNull String sdpType) {
            LiveLockSignalMessage m = new LiveLockSignalMessage();
            m.from = from;
            m.to = to;
            m.type = "offer";
            m.sdp = sdp;
            m.sdpType = sdpType;
            return m;
        }

        @NonNull
        public static LiveLockSignalMessage answer(
                @NonNull String from, @NonNull String to,
                @NonNull String sdp, @NonNull String sdpType) {
            LiveLockSignalMessage m = new LiveLockSignalMessage();
            m.from = from;
            m.to = to;
            m.type = "answer";
            m.sdp = sdp;
            m.sdpType = sdpType;
            return m;
        }

        @NonNull
        public static LiveLockSignalMessage candidate(
                @NonNull String from, @NonNull String to,
                @NonNull String candidate, @Nullable String sdpMid, int mLine) {
            LiveLockSignalMessage m = new LiveLockSignalMessage();
            m.from = from;
            m.to = to;
            m.type = "candidate";
            m.candidate = candidate;
            m.sdpMid = sdpMid;
            m.sdpMLineIndex = mLine;
            return m;
        }

        @NonNull
        public static LiveLockSignalMessage bye(@NonNull String from, @Nullable String to) {
            LiveLockSignalMessage m = new LiveLockSignalMessage();
            m.from = from;
            m.to = to;
            m.type = "bye";
            return m;
        }
    }
}
