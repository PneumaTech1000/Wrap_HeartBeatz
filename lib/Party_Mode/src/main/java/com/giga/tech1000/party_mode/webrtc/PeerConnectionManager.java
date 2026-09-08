package com.giga.tech1000.party_mode.webrtc;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.Map;

/**
 * Manages WebRTC PeerConnection factories and configurations
 */
public class PeerConnectionManager {

    private static final String TAG = "PeerConnectionManager";
    private static final String VIDEO_TRACK_ID = "100";
    private static final String AUDIO_TRACK_ID = "101";

    private PeerConnectionFactory peerConnectionFactory;
    private EglBase rootEglBase;
    private Context context;

    public PeerConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    private void initialize() {
        // Initialize WebRTC
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        // Initialize EGL context
        rootEglBase = EglBase.create();

        // Configure video codec factory
        VideoEncoderFactory videoEncoderFactory =
                new DefaultVideoEncoderFactory(
                        rootEglBase.getEglBaseContext(),
                        true,
                        true);

        VideoDecoderFactory videoDecoderFactory =
                new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        // Create PeerConnectionFactory
        AudioDeviceModule adm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(peerConnectionFactoryOptions())
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory();

        adm.release();


    }


    private PeerConnectionFactory.Options peerConnectionFactoryOptions() {
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = false;
        options.disableNetworkMonitor = false;
        return options;
    }

    /**
     * Creates a new PeerConnection with the given configuration
     */
    public PeerConnection createPeerConnection(
            PeerConnection.RTCConfiguration config,
            PeerConnection.Observer observer) {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory not initialized");
            return null;
        }

        MediaConstraints pcConstraints = new MediaConstraints();
        return peerConnectionFactory.createPeerConnection(
                config, pcConstraints, observer);
    }

    /**
     * Creates a local audio track
     */
    public AudioTrack createAudioTrack(@NonNull String trackId) {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory not initialized");
            return null;
        }

        AudioSource audioSource =
                peerConnectionFactory.createAudioSource(new MediaConstraints());
        return peerConnectionFactory.createAudioTrack(trackId, audioSource);
    }

    /**
     * Creates a local video track from a video capturer
     */
    public VideoTrack createVideoTrack(
            @NonNull String trackId,
            VideoCapturer capturer) {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory not initialized");
            return null;
        }

        VideoSource videoSource =
                peerConnectionFactory.createVideoSource(false, true);
        return peerConnectionFactory.createVideoTrack(trackId, videoSource);
    }

    /**
     * Creates a SurfaceViewRenderer for displaying remote video
     */
    public SurfaceViewRenderer createVideoRenderer() {
        SurfaceViewRenderer renderer = new SurfaceViewRenderer(context);
        renderer.init(rootEglBase.getEglBaseContext(), null);
        return renderer;
    }

    /**
     * Releases resources
     */
    public void dispose() {
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }
    }
}