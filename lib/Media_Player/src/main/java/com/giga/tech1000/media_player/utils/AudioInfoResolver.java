package com.giga.tech1000.media_player.utils;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.utils.statics.AudioTechInfo;

public final class AudioInfoResolver {

    public static AudioTechInfo resolve(Context context, Song song) {
        AudioTechInfo info = new AudioTechInfo();
        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(context, song.getUri(), null);

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if (mime != null && mime.startsWith("audio/")) {
                    info.mime = mime;

                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        info.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }

                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        info.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }

                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        info.bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                        info.estimatedBitrate = false;
                    }
                    break;
                }
            }

            // 🔹 Fallback bitrate estimation
            if (info.bitrate <= 0) {
                info.bitrate = estimateBitrate(song);
                info.estimatedBitrate = true;
            }

        } catch (Exception ignored) {
            // Absolute safety: nothing crashes
        } finally {
            extractor.release();
        }

        return info;
    }

    private static int estimateBitrate(Song song) {
        long durationMs = song.getDuration(); // from MediaStore
        if (durationMs <= 0) return -1;

        long fileSize = song.getSize();
        if (fileSize <= 0) return -1;

        // bits per second
        return (int) ((fileSize * 8L * 1000L) / durationMs);
    }

}