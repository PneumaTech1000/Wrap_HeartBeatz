package com.giga.tech1000.visualizer_android;

import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single process-wide {@link Visualizer} owner.
 * Multiple views register as consumers — only one native Visualizer is created
 * (multiple instances on the same session hang or throw on many devices).
 */
public final class VisualizerManager {

    private static final String TAG = "VisualizerManager";
    private static VisualizerManager instance;

    private Visualizer visualizer;
    private int attachedSessionId = -1;
    private final List<BaseVisualizer> consumers = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private VisualizerManager() {}

    public static synchronized VisualizerManager get() {
        if (instance == null) instance = new VisualizerManager();
        return instance;
    }

    public void register(BaseVisualizer view) {
        if (view == null) return;
        if (!consumers.contains(view)) {
            consumers.add(view);
        }
        // If already capturing, view will receive next frames
    }

    public void unregister(BaseVisualizer view) {
        if (view == null) return;
        consumers.remove(view);
        if (consumers.isEmpty()) {
            // Keep session alive briefly in case another view rebinds; release after idle
            main.postDelayed(() -> {
                if (consumers.isEmpty()) release();
            }, 1500);
        }
    }

    /**
     * Attach (or re-attach) to an ExoPlayer / AudioTrack session.
     * Safe to call repeatedly; no-ops if session unchanged and live.
     */
    public synchronized void attachSession(int audioSessionId) {
        if (audioSessionId <= 0) {
            Log.w(TAG, "attachSession ignored: invalid sessionId=" + audioSessionId);
            return;
        }
        if (visualizer != null && attachedSessionId == audioSessionId) {
            try {
                if (!visualizer.getEnabled()) {
                    visualizer.setEnabled(true);
                }
                return;
            } catch (Exception e) {
                Log.w(TAG, "re-enable failed, recreating: " + e.getMessage());
                releaseInternal();
            }
        }

        releaseInternal();

        try {
            Visualizer v = new Visualizer(audioSessionId);
            int[] range = Visualizer.getCaptureSizeRange();
            int size = range != null && range.length >= 2 ? range[1] : 1024;
            v.setCaptureSize(size);
            int rate = Visualizer.getMaxCaptureRate();
            // Use ~half max rate to avoid overloading main thread
            int captureRate = Math.max(10000, rate / 2);

            v.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                            if (bytes == null) return;
                            for (BaseVisualizer b : consumers) {
                                try {
                                    b.onAudioData(bytes);
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        @Override
                        public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
                            // unused — waveform is enough for Wave/Bar views
                        }
                    },
                    captureRate,
                    true,  // waveform
                    false  // fft
            );

            v.setEnabled(true);
            this.visualizer = v;
            this.attachedSessionId = audioSessionId;
            Log.i(TAG, "Visualizer attached session=" + audioSessionId
                    + " consumers=" + consumers.size());
        } catch (RuntimeException e) {
            // Common: session not ready yet, or Visualizer init failure
            Log.e(TAG, "Failed to attach Visualizer session=" + audioSessionId + ": " + e.getMessage());
            this.visualizer = null;
            this.attachedSessionId = -1;
        }
    }

    public synchronized void release() {
        releaseInternal();
    }

    private void releaseInternal() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
            } catch (Exception ignored) {
            }
            try {
                visualizer.release();
            } catch (Exception ignored) {
            }
            visualizer = null;
        }
        attachedSessionId = -1;
    }

    public int getAttachedSessionId() {
        return attachedSessionId;
    }

    public boolean isLive() {
        return visualizer != null;
    }
}
