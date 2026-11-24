package com.giga.tech1000.heartbeatz.architecture.party.live;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioTrack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live Lock session controller — star topology (host initiates to each guest), max
 * {@link LiveLockConfig#MAX_PEERS} concurrent peer links.
 * <p>
 * When {@link LiveLockState#isAudioAuthoritative()}, guests should mute SET Media3 chase
 * and play remote WebRTC audio (timeline = stream).
 */
public final class LiveLockManager implements LiveLockPeerLink.Callback {

    private final Context app;
    private final LiveLockSignaling signaling = new LiveLockSignaling();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final MutableLiveData<LiveLockState> stateLive =
            new MutableLiveData<>(LiveLockState.IDLE);
    private final MutableLiveData<Integer> connectedPeersLive = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> audioAuthoritativeLive = new MutableLiveData<>(false);

    private final Map<String, LiveLockPeerLink> peers = new ConcurrentHashMap<>();
    private final Set<String> iceConnected = ConcurrentHashMap.newKeySet();

    @Nullable private String partyId;
    @Nullable private String selfUid;
    private boolean hostRole;
    @Nullable private LiveLockFactory factory;
    @Nullable private ValueEventListener membersListener;
    @Nullable private WifiManager.WifiLock wifiLock;
    @Nullable private PowerManager.WakeLock wakeLock;

    public LiveLockManager(@NonNull Context context) {
        this.app = context.getApplicationContext();
    }

    @NonNull
    public LiveData<LiveLockState> getState() {
        return stateLive;
    }

    @NonNull
    public LiveData<Integer> getConnectedPeerCount() {
        return connectedPeersLive;
    }

    /**
     * True when guest should treat WebRTC as audio source of truth (suppress SET seek).
     */
    @NonNull
    public LiveData<Boolean> isAudioAuthoritative() {
        return audioAuthoritativeLive;
    }

    public boolean isLiveNow() {
        LiveLockState s = stateLive.getValue();
        return s != null && s.isAudioAuthoritative();
    }

    /**
     * Host starts Live Lock for an active party. Requires RECORD_AUDIO at runtime.
     */
    public void startAsHost(@NonNull String partyId) {
        if (!LiveLockConfig.FEATURE_AVAILABLE) {
            PartyLog.w("LiveLock", "feature disabled");
            return;
        }
        stop();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            stateLive.postValue(LiveLockState.FAILED);
            PartyLog.e("LiveLock", "host start: not authenticated");
            return;
        }
        this.partyId = partyId;
        this.selfUid = user.getUid();
        this.hostRole = true;
        this.factory = LiveLockFactory.get(app);
        acquireLocks();

        LiveLockSignaling.LiveLockSessionMeta meta = new LiveLockSignaling.LiveLockSessionMeta();
        meta.enabled = true;
        meta.hostUid = selfUid;
        meta.maxPeers = LiveLockConfig.MAX_PEERS;
        meta.version = LiveLockConfig.SIGNAL_VERSION;
        signaling.publishSession(partyId, meta);

        signaling.attach(partyId, selfUid, null, this::onSignal);
        listenMembers(partyId);
        stateLive.postValue(LiveLockState.HOSTING);
        PartyLog.i("LiveLock", "host session started party=" + partyId);
    }

    /**
     * Guest attaches; auto-connects when host session enabled.
     */
    public void startAsGuest(@NonNull String partyId) {
        if (!LiveLockConfig.FEATURE_AVAILABLE) return;
        stop();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            stateLive.postValue(LiveLockState.FAILED);
            return;
        }
        this.partyId = partyId;
        this.selfUid = user.getUid();
        this.hostRole = false;
        this.factory = LiveLockFactory.get(app);
        acquireLocks();

        signaling.attach(partyId, selfUid, meta -> {
            if (meta != null && meta.enabled && meta.hostUid != null) {
                stateLive.postValue(LiveLockState.CONNECTING);
                // Wait for host offer; ensure we can answer
                ensureGuestPeer(meta.hostUid);
            } else {
                // Host turned off — fall back
                closeAllPeers();
                stateLive.postValue(LiveLockState.IDLE);
                setAuthoritative(false);
            }
        }, this::onSignal);
        PartyLog.i("LiveLock", "guest watching party=" + partyId);
    }

    public void stop() {
        if (partyId != null && hostRole) {
            signaling.clearSession(partyId);
        }
        if (partyId != null && selfUid != null) {
            try {
                signaling.send(partyId,
                        LiveLockSignaling.LiveLockSignalMessage.bye(selfUid, null));
            } catch (Exception ignored) { }
        }
        if (partyId != null && membersListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("parties").child(partyId).child("members")
                    .removeEventListener(membersListener);
        }
        membersListener = null;
        closeAllPeers();
        signaling.detach();
        if (factory != null && hostRole) {
            factory.releaseLocalAudio();
        }
        releaseLocks();
        partyId = null;
        selfUid = null;
        hostRole = false;
        setAuthoritative(false);
        stateLive.postValue(LiveLockState.STOPPED);
        connectedPeersLive.postValue(0);
        PartyLog.i("LiveLock", "session stopped");
    }

    private void listenMembers(@NonNull String partyId) {
        membersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!hostRole || selfUid == null) return;
                Set<String> memberUids = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String uid = child.getKey();
                    if (uid != null && !uid.equals(selfUid)) {
                        memberUids.add(uid);
                    }
                }
                // Remove peers that left
                for (String existing : new HashSet<>(peers.keySet())) {
                    if (!memberUids.contains(existing)) {
                        LiveLockPeerLink link = peers.remove(existing);
                        if (link != null) link.close();
                        iceConnected.remove(existing);
                    }
                }
                // Add peers up to cap
                int slots = LiveLockConfig.MAX_PEERS - peers.size();
                for (String uid : memberUids) {
                    if (slots <= 0) break;
                    if (peers.containsKey(uid)) continue;
                    main.postDelayed(() -> ensureHostPeer(uid), LiveLockConfig.PEER_CREATE_DEBOUNCE_MS);
                    slots--;
                }
                publishPeerCount();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                PartyLog.w("LiveLock", "members: " + error.getMessage());
            }
        };
        FirebaseDatabase.getInstance()
                .getReference("parties").child(partyId).child("members")
                .addValueEventListener(membersListener);
    }

    private void ensureHostPeer(@NonNull String guestUid) {
        if (!hostRole || partyId == null || selfUid == null || factory == null) return;
        if (peers.containsKey(guestUid)) return;
        if (peers.size() >= LiveLockConfig.MAX_PEERS) {
            PartyLog.w("LiveLock", "peer cap reached, skip " + guestUid);
            return;
        }
        AudioTrack local = factory.ensureLocalAudioTrack();
        LiveLockPeerLink link = new LiveLockPeerLink(
                partyId, selfUid, guestUid, true, factory, this);
        peers.put(guestUid, link);
        link.start(local);
        publishPeerCount();
    }

    private void ensureGuestPeer(@NonNull String hostUid) {
        if (hostRole || partyId == null || selfUid == null || factory == null) return;
        if (peers.containsKey(hostUid)) return;
        LiveLockPeerLink link = new LiveLockPeerLink(
                partyId, selfUid, hostUid, false, factory, this);
        peers.put(hostUid, link);
        // Guest does not send mic by default — receive only
        link.start(null);
        publishPeerCount();
    }

    private void onSignal(@NonNull LiveLockSignaling.LiveLockSignalMessage msg) {
        if (msg.from == null || msg.type == null) return;
        String remote = msg.from;
        LiveLockPeerLink link = peers.get(remote);
        if ("bye".equals(msg.type)) {
            if (link != null) {
                link.close();
                peers.remove(remote);
                iceConnected.remove(remote);
                publishPeerCount();
            }
            return;
        }
        if (link == null && !hostRole && "offer".equals(msg.type)) {
            ensureGuestPeer(remote);
            link = peers.get(remote);
        }
        if (link == null) {
            PartyLog.d("LiveLock", "no link for signal from " + remote + " type=" + msg.type);
            return;
        }
        switch (msg.type) {
            case "offer":
                if (msg.sdp != null && msg.sdpType != null) {
                    link.handleRemoteOffer(msg.sdp, msg.sdpType);
                }
                break;
            case "answer":
                if (msg.sdp != null && msg.sdpType != null) {
                    link.handleRemoteAnswer(msg.sdp, msg.sdpType);
                }
                break;
            case "candidate":
                if (msg.candidate != null) {
                    link.handleRemoteCandidate(msg.candidate, msg.sdpMid, msg.sdpMLineIndex);
                }
                break;
            default:
                break;
        }
    }

    private void closeAllPeers() {
        for (LiveLockPeerLink link : peers.values()) {
            try {
                link.close();
            } catch (Exception ignored) { }
        }
        peers.clear();
        iceConnected.clear();
    }

    private void publishPeerCount() {
        connectedPeersLive.postValue(iceConnected.size());
        LiveLockState cur = stateLive.getValue();
        if (iceConnected.isEmpty()) {
            if (hostRole && cur == LiveLockState.LIVE) {
                stateLive.postValue(LiveLockState.HOSTING);
            }
            if (!hostRole) {
                setAuthoritative(false);
            }
        } else {
            stateLive.postValue(LiveLockState.LIVE);
            if (!hostRole) {
                setAuthoritative(true);
            }
        }
    }

    private void setAuthoritative(boolean on) {
        Boolean prev = audioAuthoritativeLive.getValue();
        if (prev != null && prev == on) return;
        audioAuthoritativeLive.postValue(on);
        PartyLog.i("LiveLock", "audioAuthoritative=" + on);
    }

    @Override
    public void onIceConnected(@NonNull String remoteUid) {
        iceConnected.add(remoteUid);
        publishPeerCount();
    }

    @Override
    public void onIceDisconnected(@NonNull String remoteUid) {
        iceConnected.remove(remoteUid);
        if (!hostRole) {
            stateLive.postValue(LiveLockState.DEGRADED);
        }
        publishPeerCount();
    }

    @Override
    public void onIceFailed(@NonNull String remoteUid) {
        iceConnected.remove(remoteUid);
        LiveLockPeerLink link = peers.remove(remoteUid);
        if (link != null) link.close();
        if (!hostRole) {
            stateLive.postValue(LiveLockState.FAILED);
            setAuthoritative(false);
        }
        publishPeerCount();
        PartyLog.w("LiveLock", "ICE failed remote=" + remoteUid);
    }

    @Override
    public void onRemoteAudio(@NonNull String remoteUid, @NonNull AudioTrack track) {
        // WebRTC plays via JavaAudioDeviceModule automatically when track enabled
        PartyLog.i("LiveLock", "remote audio active from " + remoteUid);
        if (!hostRole) {
            setAuthoritative(true);
            stateLive.postValue(LiveLockState.LIVE);
        }
    }

    @Override
    public void sendSignal(@NonNull LiveLockSignaling.LiveLockSignalMessage msg) {
        if (partyId == null) return;
        signaling.send(partyId, msg);
    }

    private void acquireLocks() {
        try {
            WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wifiLock == null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HeartBeatz:LiveLock");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Exception e) {
            PartyLog.w("LiveLock", "wifi lock: " + e.getMessage());
        }
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartBeatz:LiveLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(4 * 60 * 60 * 1000L);
            }
        } catch (Exception e) {
            PartyLog.w("LiveLock", "wake lock: " + e.getMessage());
        }
    }

    private void releaseLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) { }
        wifiLock = null;
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) { }
        wakeLock = null;
    }
}
