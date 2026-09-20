package com.giga.tech1000.heartbeatz.architecture.di;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.architecture.session.FirebasePartySession;
import com.giga.tech1000.heartbeatz.architecture.session.PartySession;
import com.giga.tech1000.heartbeatz.observers.LibraryObservers;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.view_models.LibrarySetViewModel;
import com.giga.tech1000.heartbeatz.view_models.SessionIdViewModel;
import com.giga.tech1000.media_player.scanners.LocalMediaScannerManager;

/**
 * Application-scoped dependency graph. Created once in {@link com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp}.
 * Prefer constructor injection into ViewModels/fragments via this container — not static service lookups.
 *
 * <p>UIThread is attached later from MainActivity because it needs the Activity instance.
 */
public final class AppContainer {

    private final Context appContext;
    private final PartySession partySession;

    @Nullable private UIThread uiThread;
    @Nullable private PlaybackStateRepository playbackStateRepository;
    @Nullable private LibraryObservers libraryObservers;
    @Nullable private LocalMediaScannerManager scannerManager;
    @Nullable private SessionIdViewModel sessionIdViewModel;
    @Nullable private LibrarySetViewModel librarySetViewModel;

    public AppContainer(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.partySession = new FirebasePartySession(appContext);
    }

    @NonNull
    public Context getAppContext() {
        return appContext;
    }

    @NonNull
    public PartySession partySession() {
        return partySession;
    }

    /**
     * Called from MainActivity after {@code new UIThread(activity)} and {@code uiThread.init()}.
     */
    public void attachUiThread(@NonNull UIThread thread) {
        this.uiThread = thread;
        this.playbackStateRepository = thread.getPlaybackStateRepository();
        this.libraryObservers = thread.getLibraryObservers();
        this.scannerManager = thread.getScannerManager();
        this.sessionIdViewModel = thread.getSessionIdViewModel();
        this.librarySetViewModel = thread.getLibrarySetViewModel();
    }

    public void detachUiThread() {
        this.uiThread = null;
        this.playbackStateRepository = null;
        this.libraryObservers = null;
        this.scannerManager = null;
        this.sessionIdViewModel = null;
        this.librarySetViewModel = null;
    }

    @Nullable
    public UIThread uiThreadOrNull() {
        return uiThread;
    }

    /**
     * @throws IllegalStateException if MainActivity has not attached UIThread yet
     */
    @NonNull
    public UIThread requireUiThread() {
        if (uiThread == null) {
            throw new IllegalStateException("UIThread not attached — call only after MainActivity init");
        }
        return uiThread;
    }

    @Nullable
    public PlaybackStateRepository playbackRepositoryOrNull() {
        return playbackStateRepository;
    }

    @Nullable
    public LibraryObservers libraryObserversOrNull() {
        return libraryObservers;
    }

    @Nullable
    public LocalMediaScannerManager scannerManagerOrNull() {
        return scannerManager;
    }

    @Nullable
    public SessionIdViewModel sessionIdViewModelOrNull() {
        return sessionIdViewModel;
    }

    @Nullable
    public LibrarySetViewModel librarySetViewModelOrNull() {
        return librarySetViewModel;
    }

    public void release() {
        partySession.release();
        detachUiThread();
    }
}
