package com.giga.tech1000.heartbeatz.view_models.extended_models;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;
import com.giga.tech1000.heartbeatz.architecture.freeshelf.FreeShelfPlayer;
import com.giga.tech1000.heartbeatz.architecture.freeshelf.FreeShelfRepository;
import com.giga.tech1000.heartbeatz.architecture.freeshelf.FreeShelfTrack;
import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.ui.UIThread;

import java.util.List;

public class FreeShelfViewModel extends AndroidViewModel {

    private final FreeShelfRepository repository;
    private final MutableLiveData<String> attributionEvent = new MutableLiveData<>(null);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>(null);

    @Nullable private FreeShelfPlayer player;

    public FreeShelfViewModel(@NonNull Application application) {
        super(application);
        this.repository = HeartBeatzApp.container(application).freeShelfRepository();
        bindPlayer();
        repository.loadAsync();
    }

    private void bindPlayer() {
        try {
            UIThread ui = HeartBeatzApp.container(getApplication()).uiThreadOrNull();
            if (ui != null) {
                PlaybackStateRepository repo = ui.getPlaybackStateRepository();
                if (repo != null) {
                    player = new FreeShelfPlayer(repo);
                }
            }
        } catch (Exception ignored) { }
    }

    @NonNull
    public LiveData<List<FreeShelfTrack>> getTracks() {
        return repository.getTracks();
    }

    @NonNull
    public LiveData<Boolean> isLoading() {
        return repository.isLoading();
    }

    @NonNull
    public LiveData<String> getError() {
        return repository.getError();
    }

    @NonNull
    public LiveData<String> getPolicyText() {
        return repository.getPolicyText();
    }

    /** One-shot attribution line for snackbar / dialog. */
    @NonNull
    public LiveData<String> getAttributionEvent() {
        return attributionEvent;
    }

    @NonNull
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public void refresh() {
        repository.refreshRemoteAsync();
        statusMessage.postValue("Refreshing catalog…");
    }

    public void play(@NonNull FreeShelfTrack track) {
        if (player == null) {
            bindPlayer();
        }
        if (player == null) {
            statusMessage.postValue("Playback not ready — open library first");
            return;
        }
        if (!track.isPlayable()) {
            statusMessage.postValue("This entry is informational only");
            return;
        }
        String credit = player.play(track);
        if (credit != null) {
            attributionEvent.postValue(credit);
        }
        statusMessage.postValue("Playing · " + track.license.label);
    }
}
