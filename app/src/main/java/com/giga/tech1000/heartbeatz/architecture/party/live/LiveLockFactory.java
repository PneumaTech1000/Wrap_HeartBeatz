package com.giga.tech1000.heartbeatz.architecture.party.live;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

/**
 * Shared {@link PeerConnectionFactory} + host outbound audio track.
 * Process-wide singleton; released only on explicit shutdown.
 */
public final class LiveLockFactory {

    private static final Object LOCK = new Object();
    @Nullable private static LiveLockFactory instance;

    private final PeerConnectionFactory factory;
    private final EglBase eglBase;
    private final AudioDeviceModule adm;
    @Nullable private AudioSource audioSource;
    @Nullable private AudioTrack localAudioTrack;

    private LiveLockFactory(@NonNull Context context) {
        Context app = context.getApplicationContext();
        PeerConnectionFactory.InitializationOptions init =
                PeerConnectionFactory.InitializationOptions.builder(app)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(init);

        eglBase = EglBase.create();
        adm = JavaAudioDeviceModule.builder(app)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(
                        new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(
                        new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        PartyLog.i("LiveLock", "PeerConnectionFactory ready");
    }

    @NonNull
    public static LiveLockFactory get(@NonNull Context context) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new LiveLockFactory(context);
            }
            return instance;
        }
    }

    @NonNull
    public PeerConnectionFactory factory() {
        return factory;
    }

    /**
     * Host mic track (shared across all peer links).
     * True MediaProjection loopback can replace this later via {@link #replaceLocalAudioTrack}.
     */
    @NonNull
    public synchronized AudioTrack ensureLocalAudioTrack() {
        if (localAudioTrack != null) {
            return localAudioTrack;
        }
        MediaConstraints constraints = new MediaConstraints();
        // Prefer continuous capture for party
        constraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        constraints.mandatory.add(
                new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioSource = factory.createAudioSource(constraints);
        localAudioTrack = factory.createAudioTrack("livelock_audio", audioSource);
        localAudioTrack.setEnabled(true);
        PartyLog.i("LiveLock", "local audio track created");
        return localAudioTrack;
    }

    public synchronized void replaceLocalAudioTrack(@Nullable AudioTrack track) {
        if (localAudioTrack != null && localAudioTrack != track) {
            try {
                localAudioTrack.setEnabled(false);
                localAudioTrack.dispose();
            } catch (Exception ignored) { }
        }
        localAudioTrack = track;
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(true);
        }
    }

    public synchronized void releaseLocalAudio() {
        if (localAudioTrack != null) {
            try {
                localAudioTrack.setEnabled(false);
                localAudioTrack.dispose();
            } catch (Exception ignored) { }
            localAudioTrack = null;
        }
        if (audioSource != null) {
            try {
                audioSource.dispose();
            } catch (Exception ignored) { }
            audioSource = null;
        }
    }
}
