package com.giga.tech1000.heartbeatz.ui;

// UIInfoLog is same package

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.legacy.PlaybackStateCompat;

import com.giga.tech1000.heartbeatz.MainActivity;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.architecture.PlaybackStateManager;
import com.giga.tech1000.heartbeatz.architecture.repositories.PlaybackStateRepository;
import com.giga.tech1000.heartbeatz.observers.LibraryObservers;
import com.giga.tech1000.heartbeatz.utils.DoubleBackToExitHandler;
import com.giga.tech1000.heartbeatz.view_models.LibrarySetViewModel;
import com.giga.tech1000.heartbeatz.view_models.SessionIdViewModel;
import com.giga.tech1000.heartbeatz.view_models.extended_models.SettingViewModel;
import com.giga.tech1000.heartbeatz.views.panels.RootMediaPlayerPanel;
import com.giga.tech1000.heartbeatz.views.panels.RootNavigationBarPanel;
import com.giga.tech1000.media_player.SongObserver;
import com.giga.tech1000.media_player.interfaces.IPlaybackCallback;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.media_player.scanners.LocalMediaScannerManager;
import com.giga.tech1000.heartbeatz.observers.CurrentItemPlayerCache;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.giga.tech1000.utils.PermissionManager;
import com.giga.tech1000.utils.interfaces.OnBackPressedHandler;
import com.realgear.multislidinguppanel.MultiSlidingPanelAdapter;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;
import com.realgear.multislidinguppanel.PanelStateListener;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;


@OptIn(markerClass = UnstableApi.class)
public class UIThread implements IPlaybackCallback {

    private static UIThread instance;
    private final MainActivity activity;
    private MultiSlidingUpPanelLayout panelLayout;

    private MediaPlayerThread mediaPlayerThread;
    /**
     * Single shared playback state repository for the whole app (Media3-backed).
     */
    private PlaybackStateRepository playbackStateRepository;

    private boolean uiReady = false;

    private SearchController searchController;
    private SessionIdViewModel sessionIdViewModel;
    private CurrentItemPlayerCache playerCache;
    private DoubleBackToExitHandler exitHandler;
    private SongObserver observer;

    private List<Song> songList = new ArrayList<>();

    private boolean lastIsPlaying;
    private int lastPlaybackState = Player.STATE_IDLE;
    private long lastPosition;

    public UIThread(MainActivity act) {
        instance = this;
        this.activity = act;

        searchController = new SearchController();
        playerCache = new CurrentItemPlayerCache();
        sessionIdViewModel = new SessionIdViewModel();

        act.getOnBackPressedDispatcher().addCallback(act, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (act.isDrawerOpen()) {
                    act.closeDrawer();
                    return;
                }

                if (!uiReady || getNavigationPanel() == null || getMediaPlayerPanel() == null)
                    return;

                if (Boolean.TRUE.equals(getMediaPlayerPanel().getBottomSheetView().isViewVisibility().getValue())) {
                    getMediaPlayerPanel().getBottomSheetView().closeBottomSheet();
                    return;
                }

                if (getMediaPlayerPanel().getPanelState() == MultiSlidingUpPanelLayout.EXPANDED) {
                    getMediaPlayerPanel().collapsePanel();
                    return;
                }

                Fragment activeFragment = getNavigationPanel().getActiveFragment();
                if (activeFragment instanceof OnBackPressedHandler handler) {
                    if (handler.onBackPressed()) return;
                }

                // Party tab → Home tab (show/hide, no fragment destroy)
                if (getNavigationPanel().getCurrentTabId() == R.id.nav_party) {
                    getNavigationPanel().selectTab(R.id.nav_home);
                    return;
                }

                View root = act.findViewById(android.R.id.content);
                exitHandler = new DoubleBackToExitHandler(act, root);
                exitHandler.onBackPressed();
            }
        });
    }

    public void init() {
        mediaPlayerThread = new MediaPlayerThread(activity, this);
        mediaPlayerThread.onStart();
        // One shared PlaybackStateRepository for all ViewModels / UI
        playbackStateRepository = new PlaybackStateManager(mediaPlayerThread);

        onCreate();

        silentObserver();

        uiReady = true;
    }

    public boolean isPlayerBarVisible() {
        boolean v = lastPlaybackState != Player.STATE_IDLE;
        UIInfoLog.d("UIThread.isPlayerBarVisible", "visible=" + v + " lastPlaybackState=" + lastPlaybackState);
        return v;
    }

    // --- Clean IPlaybackCallback Implementation ---

    @Override
    public void onSongChanged(@Nullable Song song) {
        this.lastPosition = 0;
        RootMediaPlayerPanel panel = getMediaPlayerPanel();
        if (panel != null) {
            panel.onSongChanged(song);
        }
        if (song != null) {
            playerCache.cachePlayingSong(song);
        }
        RootNavigationBarPanel navPanel = getNavigationPanel();
        if (navPanel != null) {
            navPanel.updatePaddingWhenWhenBarChanged(isPlayerBarVisible());
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying, int playbackState) {
        this.lastIsPlaying = isPlaying;
        this.lastPlaybackState = playbackState;

        updatePlaybackUI();
        playerCache.cachePlaybackStateChanged(playbackState);

        RootMediaPlayerPanel mediaPanel = getMediaPlayerPanel();
        UIInfoLog.d("UIThread.onPlaybackStateChanged",
                "isPlaying=" + isPlaying + " playbackState=" + playbackState
                        + " mediaPanel=" + (mediaPanel != null)
                        + " song=" + (mediaPanel != null && mediaPanel.getCurrentSong() != null));
        if (mediaPanel != null) {
            // Idle with no active item → hide mini player so bottom nav is flush
            if (playbackState == Player.STATE_IDLE && mediaPanel.getCurrentSong() == null) {
                UIInfoLog.d("UIThread.onPlaybackStateChanged", "-> hideMiniPlayer");
                mediaPanel.hideMiniPlayer();
            } else if (playbackState != Player.STATE_IDLE) {
                UIInfoLog.d("UIThread.onPlaybackStateChanged", "-> showMiniPlayerCollapsed");
                mediaPanel.showMiniPlayerCollapsed();
            }
            UIInfoLog.panelSnapshot("UIThread.afterPlayback", mediaPanel);
        }

        RootNavigationBarPanel navPanel = getNavigationPanel();
        if (navPanel != null) {
            UIInfoLog.panelSnapshot("UIThread.nav", navPanel);
            navPanel.updatePaddingWhenWhenBarChanged(isPlayerBarVisible());
        }
        UIInfoLog.layoutChildren("UIThread.onPlaybackStateChanged", panelLayout);
    }

    @Override
    public void onQueueChanged(List<Integer> queue, int currentIndex) {
        RootMediaPlayerPanel panel = getMediaPlayerPanel();
        if (panel != null) panel.onQueueIndexReady(queue, currentIndex);
    }

    @Override
    public void onSessionIdReady(int sessionId) {
        sessionIdViewModel.setSessionId(sessionId);
        RootMediaPlayerPanel panel = getMediaPlayerPanel();
        if (panel != null) panel.onSessionIdReady(sessionId);
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        RootMediaPlayerPanel panel = getMediaPlayerPanel();
        if (panel != null) panel.onRepeatModeChanged(repeatMode);
    }

    @Override
    public void onShuffleModeChanged(boolean enabled) {
        RootMediaPlayerPanel panel = getMediaPlayerPanel();
        if (panel != null) panel.onShuffleModeChanged(enabled);
    }

    @Override
    public void onProgressUpdate(long position, long duration) {
        this.lastPosition = position;
        updatePlaybackUI();
        if (playerCache != null) {
            playerCache.updateProgress(position);
        }
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {

    }

    @Override
    public void onPartyHostDiscovered(PartyHost host) {
        IPlaybackCallback.super.onPartyHostDiscovered(host);
    }

    @Override
    public void onPartyServiceRegistered(PartyHost host) {
        IPlaybackCallback.super.onPartyServiceRegistered(host);
    }

    @Override
    public void onPartyHostCreated(PartyHost host) {
        IPlaybackCallback.super.onPartyHostCreated(host);
    }

    @Override
    public void onPartyAuthSuccess() {
        IPlaybackCallback.super.onPartyAuthSuccess();
    }

    @Override
    public void onPartyMetadataReceived(SyncPacket sync) {
        IPlaybackCallback.super.onPartyMetadataReceived(sync);
    }

    @Override
    public void onPartyConnectionFailed() {
        IPlaybackCallback.super.onPartyConnectionFailed();
    }

    @Override
    public void onPartyDisconnected() {
        IPlaybackCallback.super.onPartyDisconnected();
    }

    @Override
    public void onPartyGuestsUpdated(List<String> guests) {
        IPlaybackCallback.super.onPartyGuestsUpdated(guests);
    }

    @Override
    public void onPartySetupRequired() {
        IPlaybackCallback.super.onPartySetupRequired();
    }

    private void updatePlaybackUI() {
        RootMediaPlayerPanel panel = getMediaPlayerPanel();
        if (panel == null) return;

        int state;
        if (lastPlaybackState == Player.STATE_BUFFERING) {
            state = PlaybackStateCompat.STATE_BUFFERING;
        } else {
            state = lastIsPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        }

        PlaybackStateCompat compatState = new PlaybackStateCompat.Builder()
                .setState(state, lastPosition, 1.0f, SystemClock.elapsedRealtime())
                .build();

        panel.onPlaybackStateChanged(compatState);
    }

    // --- Rest of the class helpers ---

    public void silentObserver() {
        if (observer != null) {
            observer.stop();
        }
        Handler handler = new Handler(Looper.getMainLooper());
        observer = new SongObserver(activity, handler, activity.getContentResolver(), getScannerManager());
        observer.start();
    }

    public void onDestroy() {
        if (mediaPlayerThread != null) {
            mediaPlayerThread.onDestroy();
        }
        if (observer != null) {
            observer.stop();
        }
        uiReady = false;
        instance = null;
    }

    public TreeMap<Integer, Song> getTreeMapOfSongs() {
        return SongRepository.getInstance().getCachedSongs();
    }

    public MainActivity getActivity() {
        return activity;
    }

    public LocalMediaScannerManager getScannerManager() {
        return activity.getScannerManager();
    }

    public SearchController getSearchController() {
        return searchController;
    }

    public SessionIdViewModel getSessionIdViewModel() {
        return sessionIdViewModel;
    }

    public LibraryObservers getLibraryObservers() {
        return activity.getLibraryObservers();
    }

    public LibrarySetViewModel getLibrarySetViewModel() {
        return activity.getLibrarySetViewModel();
    }

    public SettingViewModel getSettingViewModel() {
        return activity.getSettingViewModel();
    }

    public CurrentItemPlayerCache getPlayingCache() {
        return playerCache;
    }

    public LifecycleOwner getLifecycleOwner() {
        return activity;
    }

    public PermissionManager getPermissionManager() {
        return activity.getPermissionManager();
    }

    @Nullable
    public RootMediaPlayerPanel getMediaPlayerPanel() {
        if (!uiReady || panelLayout == null || panelLayout.getAdapter() == null) return null;
        return panelLayout.getAdapter().getItem(RootMediaPlayerPanel.class);
    }

    @Nullable
    public RootNavigationBarPanel getNavigationPanel() {
        if (!uiReady || panelLayout == null || panelLayout.getAdapter() == null) return null;
        return panelLayout.getAdapter().getItem(RootNavigationBarPanel.class);
    }

    public static UIThread getInstance() {
        return instance;
    }

    public MediaPlayerThread getMediaPlayerThread() {
        return mediaPlayerThread;
    }

    /**
     * Single shared {@link PlaybackStateRepository} backed by Media3 via CorePlayer.
     * ViewModels must use this instead of constructing their own PlaybackStateManager.
     */
    @NonNull
    public PlaybackStateRepository getPlaybackStateRepository() {
        if (playbackStateRepository == null) {
            throw new IllegalStateException("PlaybackStateRepository not ready — call UIThread.init() first");
        }
        return playbackStateRepository;
    }

    public void onCreate() {
        panelLayout = activity.findViewById(R.id.root_multi_sliding_up_panel);
        List<Class<?>> items = new ArrayList<>();
        items.add(RootMediaPlayerPanel.class);
        items.add(RootNavigationBarPanel.class);
        UIInfoLog.d("UIThread.onCreate", "panel order: [0]=RootMediaPlayerPanel [1]=RootNavigationBarPanel");
        panelLayout.setPanelStateListener(new PanelStateListener(panelLayout));
        panelLayout.setAdapter(new MultiSlidingPanelAdapter(activity, items));
        panelLayout.post(() -> UIInfoLog.layoutChildren("UIThread.onCreate.posted", panelLayout));
    }

    public <T extends View> T findViewById(@IdRes int id) {
        return activity.findViewById(id);
    }
}
