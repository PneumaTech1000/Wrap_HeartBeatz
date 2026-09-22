package com.giga.tech1000.heartbeatz.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.BottomSheetQueueViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BottomSheetQueueViewItem;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.utils.interfaces.BottomSheetQueueAndIndexUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Unified track queue in the media-player bottom sheet.
 * <p>
 * <b>Local playback:</b> {@link #onUpdateQueue} from Media3 / UIThread queue ids.<br>
 * <b>Party live:</b> call {@link #submitPartySongs} (or later Firebase queue) so this same
 * list is the party track queue — one UI surface for both modes.
 */
@UnstableApi
public class FragmentBottomSheetQueue extends Fragment implements BottomSheetQueueAndIndexUpdate {

    private static final String TAG = "BottomSheetQueue";

    private RecyclerView recyclerView;
    private BottomSheetQueueViewAdapter adapter;
    /** Never null — empty until first queue update. */
    @NonNull
    private List<Integer> prevQueue = new ArrayList<>();
    private int prevIndex = -1;
    @Nullable
    private TreeMap<Integer, Song> songTreeMap;
    @Nullable
    private BottomSheetView parentSheetView;
    private PlaybackCacheViewModel playbackViewModel;

    /** When true, list is driven by party queue; local Media3 updates are ignored. */
    private boolean partyMode;

    /** Required for FragmentStateAdapter recreation. */
    public FragmentBottomSheetQueue() {}

    public FragmentBottomSheetQueue(@NonNull BottomSheetView parentSheetView) {
        this.parentSheetView = parentSheetView;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bottom_sheet_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackCacheViewModel.class);

        recyclerView = view.findViewById(R.id.bottom_sheet_queue_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Empty adapter first so observers never touch a null adapter
        adapter = new BottomSheetQueueViewAdapter(new ArrayList<>(), playbackViewModel);
        adapter.setViewType(BaseRecyclerViewAdapter.ViewType.LIST);
        recyclerView.setAdapter(adapter);

        try {
            songTreeMap = SongRepository.getInstance().getCachedSongs();
        } catch (Exception e) {
            Log.w(TAG, "cached songs unavailable", e);
            songTreeMap = new TreeMap<>();
        }
        if (songTreeMap == null) {
            songTreeMap = new TreeMap<>();
        }

        try {
            HeartBeatzApp.container(requireContext())
                    .requireUiThread()
                    .getPlayingCache()
                    .getPlayerCacheInfo()
                    .observe(getViewLifecycleOwner(), playingCache -> {
                        if (adapter != null && playingCache != null) {
                            adapter.setPlayingCacheInfo(playingCache);
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "playing cache observe skipped", e);
        }

        // Safe with empty prevQueue — no NPE
        sortItem();
    }

    public BottomSheetQueueAndIndexUpdate getCallback() {
        return this;
    }

    /**
     * Switch to party queue display. While active, {@link #onUpdateQueue} does not overwrite.
     */
    public void setPartyMode(boolean enabled) {
        this.partyMode = enabled;
    }

    public boolean isPartyMode() {
        return partyMode;
    }

    /**
     * Party live queue: ordered songs already resolved (from cloud/local after approve).
     */
    public void submitPartySongs(@NonNull List<Song> songs, int currentIndex) {
        partyMode = true;
        prevIndex = currentIndex;
        prevQueue = new ArrayList<>();
        for (Song s : songs) {
            if (s != null) prevQueue.add((int) s.getId());
        }
        displayItem(new ArrayList<>(songs));
    }

    /**
     * Exit party mode and show local Media3 queue again (if any).
     */
    public void clearPartyMode() {
        partyMode = false;
        sortItem();
    }

    private void displayItem(@Nullable List<Song> songs) {
        if (recyclerView == null) return;
        List<BaseRecyclerViewItem> newItems = new ArrayList<>();
        if (songs != null) {
            for (Song song : songs) {
                if (song != null) {
                    newItems.add(new BottomSheetQueueViewItem(song));
                }
            }
        }

        if (adapter == null) {
            adapter = new BottomSheetQueueViewAdapter(newItems, playbackViewModel);
            adapter.setViewType(BaseRecyclerViewAdapter.ViewType.LIST);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.setItems(newItems);
        }

        if (prevIndex >= 0 && prevIndex < newItems.size()) {
            final int scrollTo = prevIndex;
            recyclerView.post(() -> {
                if (recyclerView != null && scrollTo < adapter.getItemCount()) {
                    recyclerView.smoothScrollToPosition(scrollTo);
                }
            });
        }
    }

    private void sortItem() {
        List<Song> songs = new ArrayList<>();
        List<Integer> queue = prevQueue;
        if (queue == null) {
            queue = new ArrayList<>();
            prevQueue = queue;
        }

        TreeMap<Integer, Song> map = songTreeMap;
        if (map == null) {
            map = new TreeMap<>();
        }

        for (Integer id : queue) {
            if (id == null) continue;
            Song song = map.get(id);
            if (song != null) {
                songs.add(song);
            }
        }

        Log.d(TAG, "sortItem size=" + queue.size() + " resolved=" + songs.size()
                + " partyMode=" + partyMode);
        displayItem(songs);
    }

    @Override
    public void onUpdateQueue(@Nullable List<Integer> queue, int queueIndex) {
        // Party owns the list while live
        if (partyMode) {
            Log.d(TAG, "onUpdateQueue ignored (partyMode)");
            return;
        }

        if (queueIndex != prevIndex) {
            prevIndex = queueIndex;
        }
        if (queue != null) {
            prevQueue = new ArrayList<>(queue);
        } else {
            prevQueue = new ArrayList<>();
        }

        if (songTreeMap == null) {
            try {
                songTreeMap = SongRepository.getInstance().getCachedSongs();
            } catch (Exception ignored) {
                songTreeMap = new TreeMap<>();
            }
        }
        sortItem();
    }
}
