package com.giga.tech1000.heartbeatz.architecture.library;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.media_player.scanners.LocalMediaScannerManager;

/**
 * UI-facing scan state for Settings / library chrome.
 * Single-flight: concurrent rescan requests are ignored while one is running.
 */
public final class LibraryScanCoordinator {

    private static final String TAG = "LibraryScan";

    public enum State {
        IDLE,
        RUNNING,
        SUCCESS,
        FAILED
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final MutableLiveData<String> message = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> songCount = new MutableLiveData<>(0);

    private volatile boolean running;

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    @NonNull
    public LiveData<String> getMessage() {
        return message;
    }

    @NonNull
    public LiveData<Integer> getSongCount() {
        return songCount;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * @return false if a scan is already in progress
     */
    public boolean requestFullRescan(@Nullable LocalMediaScannerManager scanner) {
        if (scanner == null) {
            post(State.FAILED, "Scanner not ready", 0);
            return false;
        }
        if (running) {
            Log.i(TAG, "rescan ignored — already running");
            return false;
        }
        running = true;
        post(State.RUNNING, "Scanning library…", 0);

        scanner.forceFullRescan((phase, count, error) -> main.post(() -> {
            switch (phase) {
                case STARTED:
                    post(State.RUNNING, "Scanning library…", 0);
                    break;
                case SCANNING:
                    post(State.RUNNING, "Found " + count + " tracks…", count);
                    break;
                case SUCCESS:
                    running = false;
                    post(State.SUCCESS, "Library updated · " + count + " tracks", count);
                    break;
                case FAILED:
                    running = false;
                    post(State.FAILED,
                            error != null ? error : "Library scan failed",
                            0);
                    break;
            }
        }));
        return true;
    }

    private void post(@NonNull State s, @Nullable String msg, int count) {
        state.setValue(s);
        message.setValue(msg);
        songCount.setValue(count);
        Log.i(TAG, s + " " + msg);
    }
}
