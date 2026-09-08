package com.giga.tech1000.party_mode.sync;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.media3.exoplayer.ExoPlayer;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Host-side synchronization logic. Broadcasts the current playback state.
 */
public class SyncMaster {
    private static final String TAG = "SyncMaster";
    private static final int SYNC_PORT = 8889;
    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;
    private DatagramSocket socket;
    private final ExoPlayer player;
    private String currentMediaId;

    public SyncMaster(ExoPlayer player) {
        this.player = player;
    }

    public void updateMediaId(String mediaId) {
        this.currentMediaId = mediaId;
    }

    public void start() {
        stop();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
        } catch (IOException e) {
            Log.e(TAG, "Failed to setup sync socket", e);
            return;
        }

        scheduler.scheduleWithFixedDelay(() -> {
            if (player == null) return;
            
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    SyncPacket sync = new SyncPacket();
                    sync.state = player.getPlayWhenReady() ? "PLAYING" : "PAUSED";
                    sync.positionMs = player.getCurrentPosition();
                    sync.durationMs = player.getDuration();
                    sync.playbackSpeed = player.getPlaybackParameters().speed;
                    sync.mediaId = currentMediaId;
                    
                    if (player.getCurrentMediaItem() != null && player.getCurrentMediaItem().mediaMetadata != null) {
                        CharSequence title = player.getCurrentMediaItem().mediaMetadata.title;
                        CharSequence artist = player.getCurrentMediaItem().mediaMetadata.artist;
                        CharSequence album = player.getCurrentMediaItem().mediaMetadata.albumTitle;
                        if (title != null) sync.title = title.toString();
                        if (artist != null) sync.artist = artist.toString();
                        if (album != null) sync.album = album.toString();
                    }

                    sync.sentAt = android.os.SystemClock.elapsedRealtime();

                    byte[] data = gson.toJson(sync).getBytes();
                    DatagramPacket datagram = new DatagramPacket(
                            data, data.length,
                            InetAddress.getByName("255.255.255.255"),
                            SYNC_PORT
                    );
                    
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            if (socket != null) socket.send(datagram);
                        } catch (IOException e) {
                            Log.e(TAG, "Sync send error", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Sync preparation error", e);
                }
            });
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}
