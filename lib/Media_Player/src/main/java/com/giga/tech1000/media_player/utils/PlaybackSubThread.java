package com.giga.tech1000.media_player.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/**
 * PlaybackSubThread: periodically executes a task on the MAIN thread.
 * Used for progress polling and state updates.
 */
public class PlaybackSubThread implements Runnable {

    private final int interval;
    private final Runnable updateTask;

    private final HandlerThread workerThread;
    private final Handler workerHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isRunning = false;

    public PlaybackSubThread(Runnable updateTask) {
        this(1000, updateTask);
    }

    public PlaybackSubThread(int interval, Runnable updateTask) {
        this.interval = interval;
        this.updateTask = updateTask;

        workerThread = new HandlerThread("PlaybackSubThread");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    @Override
    public void run() {
        if (!isRunning) return;

        mainHandler.post(updateTask);

        workerHandler.postDelayed(this, interval);
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        workerHandler.post(this);
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        workerHandler.removeCallbacks(this);
    }

    public void release() {
        stop();
        workerThread.quitSafely();
    }

    public boolean isRunning() {
        return isRunning;
    }
}
