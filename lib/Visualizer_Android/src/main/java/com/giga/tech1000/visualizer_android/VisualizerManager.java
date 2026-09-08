package com.giga.tech1000.visualizer_android;

import android.media.audiofx.Visualizer;

import java.util.ArrayList;
import java.util.List;

public final class VisualizerManager {

    private static VisualizerManager instance;

    private Visualizer visualizer;
    private final List<BaseVisualizer> consumers = new ArrayList<>();

    private VisualizerManager() {}

    public static synchronized VisualizerManager get() {
        if (instance == null) instance = new VisualizerManager();
        return instance;
    }

    public void attachSession(int audioSessionId) {
        release();

        visualizer = new Visualizer(audioSessionId);
        visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

        visualizer.setDataCaptureListener(
                new Visualizer.OnDataCaptureListener() {
                    @Override
                    public void onWaveFormDataCapture(
                            Visualizer v, byte[] bytes, int rate) {
                        for (BaseVisualizer b : consumers) {
                            b.onAudioData(bytes);
                        }
                    }

                    @Override public void onFftDataCapture(
                            Visualizer v, byte[] bytes, int rate) {}
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
        );

        visualizer.setEnabled(true);
    }

    public void register(BaseVisualizer view) {
        if (!consumers.contains(view)) consumers.add(view);
    }

    public void unregister(BaseVisualizer view) {
        consumers.remove(view);
    }

    public void release() {
        if (visualizer != null) {
            visualizer.setEnabled(false);
            visualizer.release();
            visualizer = null;
        }
    }
}

