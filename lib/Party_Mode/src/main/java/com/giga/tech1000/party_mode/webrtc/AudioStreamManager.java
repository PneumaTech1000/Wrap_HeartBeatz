package com.giga.tech1000.party_mode.webrtc;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.webrtc.DataChannel;
import org.webrtc.PeerConnection;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Manages audio streaming via WebRTC data channels
 * Handles sending and receiving audio packets between peers
 */
public class AudioStreamManager {

    private static final String TAG = "AudioStreamManager";
    public static final String AUDIO_DATA_CHANNEL_LABEL = "audioData";

    private Context context;
    private PeerConnection peerConnection;
    private DataChannel audioDataChannel;
    private AudioDataReceiver audioDataReceiver;
    private boolean isInitialized = false;

    public interface AudioDataReceiver {
        void onAudioDataReceived(byte[] audioData);
        void onAudioDataChannelStateChange(DataChannel.State state);
    }

    public AudioStreamManager(Context context) {
        this.context = context;
    }

    /**
     * Initializes the audio stream manager with a peer connection
     */
    public void initialize(PeerConnection pc, AudioDataReceiver receiver) {
        this.peerConnection = pc;
        this.audioDataReceiver = receiver;
        createDataChannel();
        isInitialized = true;
    }

    /**
     * Creates the audio data channel for sending/receiving audio data
     */
    private void createDataChannel() {
        if (peerConnection == null) {
            Log.e(TAG, "Cannot create data channel: peer connection is null");
            return;
        }

        DataChannel.Init options = new DataChannel.Init();
        options.ordered = true; // Ensure ordered delivery for audio sync
        options.maxRetransmitTimeMs = 500; // Limit retransmission time
        options.maxRetransmits = 0; // No retransmits for real-time audio

        audioDataChannel = peerConnection.createDataChannel(AUDIO_DATA_CHANNEL_LABEL, options);
        audioDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long previousAmount) {
                // Not used for audio streaming
            }

            @Override
            public void onStateChange() {
                Log.d(TAG, "Data channel state changed: " + audioDataChannel.state());
                if (audioDataReceiver != null) {
                    audioDataReceiver.onAudioDataChannelStateChange(audioDataChannel.state());
                }
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                if (buffer.data != null) {
                    byte[] data = new byte[buffer.data.capacity()];
                    buffer.data.get(data);
                    if (audioDataReceiver != null) {
                        audioDataReceiver.onAudioDataReceived(data);
                    }
                }
            }
        });

        Log.d(TAG, "Audio data channel created: " + AUDIO_DATA_CHANNEL_LABEL);
    }

    /**
     * Sends audio data to the remote peer
     */
    public void sendAudioData(byte[] audioData) {
        if (!isInitialized || audioDataChannel == null) {
            Log.w(TAG, "Cannot send audio data: not initialized");
            return;
        }

        if (audioDataChannel.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "Cannot send audio data: channel not open");
            return;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(audioData);
            DataChannel.Buffer dataBuffer = new DataChannel.Buffer(buffer, false);
            audioDataChannel.send(dataBuffer);
            Log.d(TAG, "Sent audio data: " + audioData.length + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "Error sending audio data", e);
        }
    }

    /**
     * Closes the audio data channel
     */
    public void close() {
        if (audioDataChannel != null) {
            audioDataChannel.dispose();
            audioDataChannel = null;
        }
        isInitialized = false;
        Log.d(TAG, "Audio data channel closed");
    }

    /**
     * Returns the data channel label
     */
    public static String getAudioDataChannelLabel() {
        return AUDIO_DATA_CHANNEL_LABEL;
    }
}