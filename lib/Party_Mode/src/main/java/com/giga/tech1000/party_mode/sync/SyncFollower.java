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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side synchronization logic. Aligns playback with the host.
 */
public class SyncFollower {
    private static final String TAG = "SyncFollower";
    private static final int SYNC_PORT = 8889;
    private static final long SYNC_THRESHOLD_MS = 500;

    private final Gson gson = new Gson();
    private final ExoPlayer player;
    private DatagramSocket socket;
    private ExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public SyncFollower(ExoPlayer player) {
        this.player = player;
    }

    public void start() {
        if (isRunning.getAndSet(true)) return;
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                socket = new DatagramSocket(SYNC_PORT);
                byte[] buffer = new byte[2048];
                while (isRunning.get()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength());
                    SyncPacket sync = gson.fromJson(json, SyncPacket.class);
                    handleSync(sync);
                }
            } catch (IOException e) {
                if (isRunning.get()) Log.e(TAG, "Sync follower error", e);
            }
        });
    }

    private void handleSync(SyncPacket sync) {
        if (player == null) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            // 1. State Sync
            boolean hostPlaying = "PLAYING".equals(sync.state);
            if (player.getPlayWhenReady() != hostPlaying) {
                player.setPlayWhenReady(hostPlaying);
            }

            // 2. Position Sync
            long now = android.os.SystemClock.elapsedRealtime();
            long latency = (now - sync.sentAt) / 2; // Rough estimate
            long targetPos = sync.positionMs + latency;
            long drift = Math.abs(targetPos - player.getCurrentPosition());

            if (drift > SYNC_THRESHOLD_MS) {
                Log.w(TAG, "Large drift detected (" + drift + "ms). Seeking.");
                player.seekTo(targetPos);
            } else if (drift > 50) {
                // Future: Smooth adjustment via playback speed
            }
        });
    }

    public void stop() {
        isRunning.set(false);
        if (socket != null) socket.close();
        if (executor != null) executor.shutdownNow();
    }
}
