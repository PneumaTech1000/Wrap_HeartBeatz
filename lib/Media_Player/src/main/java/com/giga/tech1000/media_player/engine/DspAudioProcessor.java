package com.giga.tech1000.media_player.engine;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.soundengine.SoundEngine;
import com.giga.tech1000.soundengine.SoundEngineHolder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Media3 PCM adapter for shared native DSPark SoundEngine. No Android audiofx. */
@OptIn(markerClass = UnstableApi.class)
public final class DspAudioProcessor extends BaseAudioProcessor {

    private static final int MAX_BLOCK_SIZE = 4096;

    private SoundEngine soundEngine;
    private float[] sampleScratch = new float[0];
    private int encoding;
    private int channelCount;
    private boolean configured;

    @NonNull
    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw new AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);
        }
        encoding = inputAudioFormat.encoding;
        channelCount = Math.max(1, inputAudioFormat.channelCount);
        sampleScratch = new float[MAX_BLOCK_SIZE * channelCount];
        soundEngine = SoundEngineHolder.configure(
                inputAudioFormat.sampleRate, MAX_BLOCK_SIZE, channelCount);
        configured = true;
        return inputAudioFormat;
    }

    @Override
    public boolean isActive() {
        return configured && SoundEngineHolder.isReady();
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!configured) return;
        SoundEngine live = SoundEngineHolder.getCurrent();
        if (live == null) {
            int remaining = inputBuffer.remaining();
            if (remaining > 0) {
                ByteBuffer out = replaceOutputBuffer(remaining);
                out.put(inputBuffer);
                out.flip();
            }
            return;
        }
        soundEngine = live;

        int bytesPerSample = encoding == C.ENCODING_PCM_FLOAT ? Float.BYTES : Short.BYTES;
        int bytesPerFrame = bytesPerSample * channelCount;
        int inputBytes = inputBuffer.remaining() / bytesPerFrame * bytesPerFrame;
        if (inputBytes == 0) return;

        ByteBuffer input = inputBuffer.order(ByteOrder.nativeOrder());
        ByteBuffer output = replaceOutputBuffer(inputBytes).order(ByteOrder.nativeOrder());

        int remainingBytes = inputBytes;
        int maxChunkSamples = sampleScratch.length;
        while (remainingBytes >= bytesPerSample) {
            int chunkSamples = Math.min(remainingBytes / bytesPerSample, maxChunkSamples);
            int aligned = chunkSamples - (chunkSamples % channelCount);
            if (aligned == 0) break;
            chunkSamples = aligned;
            int processedBytes = chunkSamples * bytesPerSample;

            for (int i = 0; i < chunkSamples; i++) {
                sampleScratch[i] = encoding == C.ENCODING_PCM_FLOAT
                        ? input.getFloat()
                        : input.getShort() / 32768.0f;
            }
            soundEngine.process(sampleScratch, chunkSamples);
            for (int i = 0; i < chunkSamples; i++) {
                if (encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(sampleScratch[i]);
                } else {
                    float s = Math.max(-1f, Math.min(1f, sampleScratch[i]));
                    output.putShort((short) Math.round(s * 32767f));
                }
            }
            remainingBytes -= processedBytes;
        }
        output.flip();
    }

    @Override
    protected void onFlush() { }

    @Override
    protected void onReset() {
        configured = false;
        soundEngine = null;
    }

    public void release() {
        configured = false;
        soundEngine = null;
    }
}
