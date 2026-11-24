package com.giga.tech1000.heartbeatz.architecture.party.live;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One host↔guest PeerConnection with UNIFIED_PLAN audio.
 */
public final class LiveLockPeerLink {

    public interface Callback {
        void onIceConnected(@NonNull String remoteUid);
        void onIceDisconnected(@NonNull String remoteUid);
        void onIceFailed(@NonNull String remoteUid);
        void onRemoteAudio(@NonNull String remoteUid, @NonNull AudioTrack track);
        void sendSignal(@NonNull LiveLockSignaling.LiveLockSignalMessage msg);
    }

    private final String partyId;
    private final String selfUid;
    private final String remoteUid;
    private final boolean initiator;
    private final LiveLockFactory factory;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<IceCandidate> pendingRemoteCandidates = new ArrayList<>();
    private final AtomicBoolean remoteDescriptionSet = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Nullable private PeerConnection pc;
    @Nullable private PeerConnection.IceConnectionState lastIce;

    public LiveLockPeerLink(
            @NonNull String partyId,
            @NonNull String selfUid,
            @NonNull String remoteUid,
            boolean initiator,
            @NonNull LiveLockFactory factory,
            @NonNull Callback callback) {
        this.partyId = partyId;
        this.selfUid = selfUid;
        this.remoteUid = remoteUid;
        this.initiator = initiator;
        this.factory = factory;
        this.callback = callback;
    }

    @NonNull
    public String getRemoteUid() {
        return remoteUid;
    }

    public boolean isConnected() {
        return lastIce == PeerConnection.IceConnectionState.CONNECTED
                || lastIce == PeerConnection.IceConnectionState.COMPLETED;
    }

    public void start(@Nullable AudioTrack localTrack) {
        if (closed.get()) return;
        PeerConnection.Observer observer = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState state) {
                PartyLog.d("LiveLock", "signalState remote=" + remoteUid + " " + state);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                lastIce = state;
                PartyLog.i("LiveLock", "ICE remote=" + remoteUid + " → " + state);
                main.post(() -> {
                    if (state == PeerConnection.IceConnectionState.CONNECTED
                            || state == PeerConnection.IceConnectionState.COMPLETED) {
                        callback.onIceConnected(remoteUid);
                    } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                        callback.onIceDisconnected(remoteUid);
                    } else if (state == PeerConnection.IceConnectionState.FAILED) {
                        callback.onIceFailed(remoteUid);
                    }
                });
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                if (candidate == null || closed.get()) return;
                callback.sendSignal(LiveLockSignaling.LiveLockSignalMessage.candidate(
                        selfUid, remoteUid,
                        candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex));
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {}

            @Override
            public void onAddStream(MediaStream stream) {
                // Plan B legacy; UNIFIED_PLAN uses onTrack
            }

            @Override
            public void onRemoveStream(MediaStream stream) {}

            @Override
            public void onDataChannel(DataChannel dataChannel) {}

            @Override
            public void onRenegotiationNeeded() {
                if (initiator && !closed.get()) {
                    createOffer();
                }
            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                if (receiver == null || receiver.track() == null) return;
                if ("audio".equals(receiver.track().kind())) {
                    AudioTrack remote = (AudioTrack) receiver.track();
                    remote.setEnabled(true);
                    PartyLog.i("LiveLock", "remote audio from " + remoteUid);
                    main.post(() -> callback.onRemoteAudio(remoteUid, remote));
                }
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                if (transceiver == null || transceiver.getReceiver() == null) return;
                if (transceiver.getReceiver().track() != null
                        && "audio".equals(transceiver.getReceiver().track().kind())) {
                    AudioTrack remote = (AudioTrack) transceiver.getReceiver().track();
                    remote.setEnabled(true);
                    main.post(() -> callback.onRemoteAudio(remoteUid, remote));
                }
            }
        };

        pc = factory.factory().createPeerConnection(LiveLockConfig.rtcConfig(), observer);
        if (pc == null) {
            PartyLog.e("LiveLock", "createPeerConnection null for " + remoteUid);
            return;
        }

        if (localTrack != null) {
            // Host: send local audio (and receive if needed)
            List<String> streamIds = new ArrayList<>();
            streamIds.add("livelock");
            pc.addTrack(localTrack, streamIds);
        } else {
            // Guest: receive-only audio m-line
            pc.addTransceiver(
                    org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    new RtpTransceiver.RtpTransceiverInit(
                            RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
        }

        if (initiator) {
            createOffer();
        }
        PartyLog.i("LiveLock", "peer link start remote=" + remoteUid + " initiator=" + initiator);
    }

    private void createOffer() {
        if (pc == null || closed.get()) return;
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        pc.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                if (pc == null || closed.get()) return;
                pc.setLocalDescription(new SimpleSdpObserver("setLocalOffer"), sessionDescription);
                callback.sendSignal(LiveLockSignaling.LiveLockSignalMessage.offer(
                        selfUid, remoteUid, sessionDescription.description, sessionDescription.type.canonicalForm()));
            }

            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String s) {
                PartyLog.e("LiveLock", "createOffer fail: " + s);
            }
            @Override public void onSetFailure(String s) {
                PartyLog.e("LiveLock", "setLocalOffer fail: " + s);
            }
        }, c);
    }

    public void handleRemoteOffer(@NonNull String sdp, @NonNull String type) {
        if (pc == null || closed.get()) return;
        SessionDescription.Type t = SessionDescription.Type.fromCanonicalForm(type);
        SessionDescription desc = new SessionDescription(t, sdp);
        pc.setRemoteDescription(new SimpleSdpObserver("setRemoteOffer") {
            @Override
            public void onSetSuccess() {
                remoteDescriptionSet.set(true);
                flushCandidates();
                createAnswer();
            }
        }, desc);
    }

    public void handleRemoteAnswer(@NonNull String sdp, @NonNull String type) {
        if (pc == null || closed.get()) return;
        SessionDescription.Type t = SessionDescription.Type.fromCanonicalForm(type);
        SessionDescription desc = new SessionDescription(t, sdp);
        pc.setRemoteDescription(new SimpleSdpObserver("setRemoteAnswer") {
            @Override
            public void onSetSuccess() {
                remoteDescriptionSet.set(true);
                flushCandidates();
            }
        }, desc);
    }

    public void handleRemoteCandidate(
            @NonNull String candidate, @Nullable String sdpMid, int mLine) {
        IceCandidate ice = new IceCandidate(sdpMid, mLine, candidate);
        if (!remoteDescriptionSet.get()) {
            pendingRemoteCandidates.add(ice);
            return;
        }
        if (pc != null) {
            pc.addIceCandidate(ice);
        }
    }

    private void flushCandidates() {
        if (pc == null) return;
        for (IceCandidate c : pendingRemoteCandidates) {
            pc.addIceCandidate(c);
        }
        pendingRemoteCandidates.clear();
    }

    private void createAnswer() {
        if (pc == null || closed.get()) return;
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        pc.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                if (pc == null || closed.get()) return;
                pc.setLocalDescription(new SimpleSdpObserver("setLocalAnswer"), sessionDescription);
                callback.sendSignal(LiveLockSignaling.LiveLockSignalMessage.answer(
                        selfUid, remoteUid, sessionDescription.description, sessionDescription.type.canonicalForm()));
            }

            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String s) {
                PartyLog.e("LiveLock", "createAnswer fail: " + s);
            }
            @Override public void onSetFailure(String s) {
                PartyLog.e("LiveLock", "setLocalAnswer fail: " + s);
            }
        }, c);
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            callback.sendSignal(LiveLockSignaling.LiveLockSignalMessage.bye(selfUid, remoteUid));
        } catch (Exception ignored) { }
        if (pc != null) {
            try {
                pc.close();
            } catch (Exception ignored) { }
            try {
                pc.dispose();
            } catch (Exception ignored) { }
            pc = null;
        }
        PartyLog.i("LiveLock", "peer closed remote=" + remoteUid);
    }

    private static class SimpleSdpObserver implements SdpObserver {
        private final String tag;

        SimpleSdpObserver(String tag) {
            this.tag = tag;
        }

        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {
            PartyLog.d("LiveLock", tag + " ok");
        }
        @Override public void onCreateFailure(String s) {
            PartyLog.e("LiveLock", tag + " create fail: " + s);
        }
        @Override public void onSetFailure(String s) {
            PartyLog.e("LiveLock", tag + " set fail: " + s);
        }
    }
}
