package com.giga.tech1000.heartbeatz.architecture.media;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Host-side: upload local file → object storage → report progress on main thread.
 */
public final class PartyTrackUploader {

    private static final String TAG = "PartyTrackUploader";

    public enum State { IDLE, UPLOADING, SUCCESS, ERROR }

    public static final class Status {
        public final State state;
        public final float progress01;
        @Nullable public final PartyMediaObject result;
        @Nullable public final String errorMessage;

        public Status(State state, float progress01,
                      @Nullable PartyMediaObject result,
                      @Nullable String errorMessage) {
            this.state = state;
            this.progress01 = progress01;
            this.result = result;
            this.errorMessage = errorMessage;
        }

        public static Status idle() {
            return new Status(State.IDLE, 0f, null, null);
        }
    }

    private final PartyMediaStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "party-media-upload");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Status> statusLive = new MutableLiveData<>(Status.idle());

    public PartyTrackUploader(@NonNull PartyMediaStore store) {
        this.store = store;
    }

    @NonNull
    public LiveData<Status> getStatus() {
        return statusLive;
    }

    public void uploadAsync(
            @NonNull String partyId,
            @NonNull String trackId,
            @NonNull File file,
            @NonNull String mimeType,
            @Nullable String contentHash) {
        post(new Status(State.UPLOADING, 0f, null, null));
        io.execute(() -> {
            try {
                PartyMediaObject obj = store.upload(
                        partyId, trackId, file, mimeType, contentHash,
                        fraction -> post(new Status(State.UPLOADING, fraction, null, null)));
                post(new Status(State.SUCCESS, 1f, obj, null));
            } catch (Exception e) {
                Log.e(TAG, "upload failed", e);
                post(new Status(State.ERROR, 0f, null, e.getMessage() != null ? e.getMessage() : "upload failed"));
            }
        });
    }

    @MainThread
    public void reset() {
        statusLive.setValue(Status.idle());
    }

    private void post(@NonNull Status s) {
        main.post(() -> statusLive.setValue(s));
    }

    public void shutdown() {
        io.shutdownNow();
    }
}
