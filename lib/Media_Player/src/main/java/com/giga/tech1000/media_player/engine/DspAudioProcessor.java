package com.giga.tech1000.media_player.engine;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import com.giga.tech1000.soundengine.SoundEngine;
import com.giga.tech1000.soundengine.SoundEngineHolder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Media3 PCM adapter for the shared native SoundEngine. */
@OptIn(markerClass = UnstableApi.class)
public final class DspAudioProcessor extends BaseAudioProcessor {

    private static final int MAX_BLOCK_SIZE = 4096;

    private SoundEngine soundEngine;
    private float[] sampleScratch = new float[0];
    private int encoding;
    private int channelCount;

    @NonNull
    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw new AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);
        }

        encoding = inputAudioFormat.encoding;
        channelCount = inputAudioFormat.channelCount;
        sampleScratch = new float[MAX_BLOCK_SIZE * channelCount];
        soundEngine = SoundEngineHolder.getInstance(
                inputAudioFormat.sampleRate, MAX_BLOCK_SIZE, channelCount);
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int bytesPerSample = encoding == C.ENCODING_PCM_FLOAT ? Float.BYTES : Short.BYTES;
        int bytesPerFrame = bytesPerSample * channelCount;
        int inputBytes = inputBuffer.remaining() / bytesPerFrame * bytesPerFrame;
        if (inputBytes == 0) {
            return;
        }

        ByteBuffer input = inputBuffer.order(ByteOrder.nativeOrder());
        ByteBuffer output = replaceOutputBuffer(inputBytes)
                .order(ByteOrder.nativeOrder());

        int remainingBytes = inputBytes;
        int maxChunkSamples = sampleScratch.length;
        while (remainingBytes >= bytesPerSample) {
            int chunkSamples = Math.min(
                    remainingBytes / bytesPerSample,
                    maxChunkSamples);

            int alignedSamples = chunkSamples - (chunkSamples % channelCount);
            if (alignedSamples == 0) {
                break;
            }
            chunkSamples = alignedSamples;
            int processedBytes = chunkSamples * bytesPerSample;

            for (int index = 0; index < chunkSamples; index++) {
                sampleScratch[index] = encoding == C.ENCODING_PCM_FLOAT
                        ? input.getFloat()
                        : input.getShort() / 32768.0f;
            }

            // Always use the live shared engine (AudioEngine must not hold a dead handle)
            SoundEngine live = SoundEngineHolder.getCurrent();
            if (live == null) {
                return;
            }
            soundEngine = live;
            soundEngine.process(sampleScratch, chunkSamples);
            for (int index = 0; index < chunkSamples; index++) {
                if (encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(sampleScratch[index]);
                } else {
                    float sample = Math.clamp(sampleScratch[index], -1.0f, 1.0f);
                    output.putShort((short) Math.round(sample * 32767.0f));
                }
            }
            remainingBytes -= processedBytes;
        }
        output.flip();
    }

    @Override
    protected void onFlush() {
        // DSP state intentionally remains continuous across decoder buffer flushes.
    }

    @Override
    protected void onReset() {
        SoundEngineHolder.release();
        soundEngine = null;
        sampleScratch = new float[0];
        encoding = 0;
        channelCount = 0;
    }

    public void release() {
        reset();
    }
}
