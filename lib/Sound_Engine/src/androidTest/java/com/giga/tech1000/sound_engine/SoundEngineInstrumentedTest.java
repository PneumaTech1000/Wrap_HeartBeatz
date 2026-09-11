package com.giga.tech1000.sound_engine;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.giga.tech1000.soundengine.SoundEngine;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SoundEngineInstrumentedTest {

    private SoundEngine soundEngine;

    @After
    public void releaseEngine() {
        if (soundEngine != null) {
            soundEngine.close();
        }
    }

    @Test
    public void disabledChainPreservesInterleavedSamples() {
        soundEngine = new SoundEngine(48000, 256, 2);
        float[] samples = new float[512];
        for (int index = 0; index < samples.length; index++) {
            samples[index] = (index % 17 - 8) / 8.0f;
        }
        float[] original = samples.clone();

        soundEngine.process(samples);

        for (int index = 0; index < samples.length; index++) {
            assertEquals(original[index], samples[index], 0.000001f);
        }
    }

    @Test
    public void spectrumProducesBinsAfterEnoughAudio() {
        soundEngine = new SoundEngine(48000, 256, 2);
        float[] samples = new float[4096];
        for (int frame = 0; frame < samples.length / 2; frame++) {
            float value = (float) Math.sin(2.0 * Math.PI * 1000.0 * frame / 48000.0);
            samples[frame * 2] = value;
            samples[frame * 2 + 1] = value;
        }

        soundEngine.process(samples);

        assertTrue(soundEngine.getSpectrumNumBins() > 0);
        assertTrue(soundEngine.isSpectrumDataReady());
        assertTrue(soundEngine.getSpectrumBinFrequency(1) > 0.0f);
        float[] magnitudes = new float[soundEngine.getSpectrumNumBins()];
        soundEngine.getSpectrumMagnitudes(magnitudes);
        assertTrue(magnitudes.length > 0);
    }
}