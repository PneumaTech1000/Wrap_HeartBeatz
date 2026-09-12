package com.giga.tech1000.heartbeatz.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.BottomSheetQueueViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.SongViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BottomSheetQueueViewItem;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.media_player.SongObserver;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.media_player.scanners.LibraryScanner;
import com.giga.tech1000.utils.interfaces.BottomSheetQueueAndIndexUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@UnstableApi
public class FragmentBottomSheetQueue extends Fragment implements BottomSheetQueueAndIndexUpdate {

    private RecyclerView recyclerView;
    private BottomSheetQueueViewAdapter adapter;
    private List<Integer> prevQueue;
    private int prevIndex;
    private TreeMap<Integer, Song> songTreeMap;
    private BottomSheetView parentSheetView;
    private PlaybackCacheViewModel playbackViewModel;

    public FragmentBottomSheetQueue(BottomSheetView parentSheetView) {
        this.parentSheetView = parentSheetView;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bottom_sheet_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playbackViewModel = new androidx.lifecycle.ViewModelProvider(requireActivity()).get(PlaybackCacheViewModel.class);

        recyclerView = view.findViewById(R.id.bottom_sheet_queue_list);
        recyclerView.setHasFixedSize(true);


        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        this.songTreeMap = SongRepository.getInstance().getCachedSongs();

        UIThread.getInstance().getPlayingCache().getPlayerCacheInfo().observe(getViewLifecycleOwner(), playingCache -> adapter.setPlayingCacheInfo(playingCache));
        sortItem();
    }

    public BottomSheetQueueAndIndexUpdate getCallback() {
        return this;
    }


    private void displayItem(List<Song> songs) {
        List<BaseRecyclerViewItem> newItems = new ArrayList<>();
        if (songs == null) return;

        for (Song song : songs) {
            newItems.add(new BottomSheetQueueViewItem(song));
        }

        if (adapter == null) {
            adapter = new BottomSheetQueueViewAdapter(newItems, playbackViewModel);
            adapter.setViewType(BaseRecyclerViewAdapter.ViewType.LIST);
        }

        recyclerView.setAdapter(adapter);

        adapter.setItems(newItems); // Fresh list every time
        if (prevIndex >= 0 && prevIndex < prevQueue.size()) {
            recyclerView.post(() -> recyclerView.smoothScrollToPosition(prevIndex));
        }
    }

    private void sortItem() {
        List<Song> songs = new ArrayList<>();
        Log.i("HHHVHVHVHVH", prevQueue.size() + " Song");

        for (Integer id : prevQueue) {
            Song song = songTreeMap.get(id);
            if (song != null) songs.add(song);
        }

        displayItem(songs);
    }



    @Override
    public void onUpdateQueue(List<Integer> queue, int queueIndex) {
         if (queueIndex != prevIndex) {
            prevIndex = queueIndex;
        }
        if (queue != null && queue != prevQueue) {
            prevQueue = queue;
        }

        if (songTreeMap != null) sortItem();
    }


}
