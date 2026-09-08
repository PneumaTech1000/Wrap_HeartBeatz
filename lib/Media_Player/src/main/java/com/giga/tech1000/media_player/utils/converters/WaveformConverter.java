package com.giga.tech1000.media_player.utils.converters;

import androidx.room.TypeConverter;

public class WaveformConverter {

    @TypeConverter
    public static byte[] fromFloatArray(float[] waveform) {
        if (waveform == null) return null;
        byte[] bytes = new byte[waveform.length];
        for (int i = 0; i < waveform.length; i++) {
            bytes[i] = (byte) (Math.min(1f, waveform[i]) * 255);
        }
        return bytes;
    }

    @TypeConverter
    public static float[] toFloatArray(byte[] bytes) {
        if (bytes == null) return null;
        float[] waveform = new float[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            waveform[i] = (bytes[i] & 0xFF) / 255f;
        }
        return waveform;
    }
}

