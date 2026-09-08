package com.giga.tech1000.heartbeatz.views.panels.sub_panels;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.MediaDetailsWithoutImgAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.media_details.MediaDetailsWithoutImgViewItem;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.MediaDetail;
import com.giga.tech1000.utils.statics.Converters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.android.material.appbar.MaterialToolbar;

public class RootMediaDetailsWithoutImgPanel {

    private final ConstraintLayout fullPageLayout;
    private final LinearLayout emptyPageLayout;
    private final View view;
    private final MaterialToolbar toolbar;
    private final MaterialToolbar emptyToolbar;
    private RecyclerView recyclerView;
    private final MediaDetailsWithoutImgAdapter adapter;

    private AppCompatButton btnAddToPlaylist;
    private long playlistId;
    private final AtomicBoolean isVisible = new AtomicBoolean(false);
    private final AtomicBoolean isSearchVisible = new AtomicBoolean(false);
    private final PlaybackCacheViewModel playbackViewModel;



    public RootMediaDetailsWithoutImgPanel(@NonNull FragmentHome fragment, @NonNull ViewGroup parent,
                                          @NonNull PlaybackCacheViewModel playbackViewModel) {
        this.playbackViewModel = playbackViewModel;
        view = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.media_details_without_img_root_layout, parent, false);

        emptyPageLayout = view.findViewById(R.id.empty_list_page);
        fullPageLayout = view.findViewById(R.id.detail_list_page);
        btnAddToPlaylist = view.findViewById(R.id.btn_add_to_playlist);

        toolbar = view.findViewById(R.id.tool_bar);
        emptyToolbar = view.findViewById(R.id.empty_tool_bar);

        recyclerView = view.findViewById(R.id.recycler_view);

        btnAddToPlaylist.setOnClickListener(v -> {
            fragment.getLibraryObservers().getSongs().observe(fragment.getViewLifecycleOwner(), songs -> {
                if (songs != null) {
                    fragment.getSongSelectionPanel().addDetails(songs, playlistId);
                    fragment.displaySelectionPanel();
                }
            });
        });

        adapter = new MediaDetailsWithoutImgAdapter(new ArrayList<>(), playbackViewModel);
        adapter.setViewType(BaseRecyclerViewAdapter.ViewType.LIST);
        playbackViewModel.getPlayerCacheInfo().observe(fragment.getViewLifecycleOwner(), adapter::setPlayingCacheInfo);
        recyclerView.setLayoutManager(new LinearLayoutManager(fragment.requireContext()));
        recyclerView.setAdapter(adapter);

        toolbar.setNavigationOnClickListener(v -> {
            isVisible.set(false);
            fragment.hideMediaDetailsPanel();
        });

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                hookSearchView(searchView);
            }
        }

        emptyToolbar.setNavigationOnClickListener(v -> {
            isVisible.set(false);
            fragment.hideMediaDetailsPanel();
        });

    }


    public void addDetails(MediaDetail mediaDetail) {
        isVisible.set(true);
        List<BaseRecyclerViewItem> items = new ArrayList<>();
        toolbar.setTitle(mediaDetail.getTitle());
        emptyToolbar.setTitle(mediaDetail.getTitle());
        if (mediaDetail.getType() == MediaDetail.Type.PLAYLIST_SONG) playlistId = Converters.uriToPlaylistId(mediaDetail.getUri());

        for (Song song : mediaDetail.getSongs()) {
            items.add(new MediaDetailsWithoutImgViewItem(song));
        }

        adapter.setItems(items);

        if (items.isEmpty()) {
            emptyPageLayout.setVisibility(View.VISIBLE);
            fullPageLayout.setVisibility(View.GONE);
        } else {
            emptyPageLayout.setVisibility(View.GONE);
            fullPageLayout.setVisibility(View.VISIBLE);
        }
    }

    private void hookSearchView(SearchView searchView) {
        searchView.setQueryHint("Search songs...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return true;
            }
        });
    }

    public AtomicBoolean getIsVisible() { return isVisible; }
    public AtomicBoolean getIsSearchVisible() { return  isSearchVisible; }
    public void setIsVisible(boolean isVisible) { this.isVisible.set(isVisible); }
    public void setSearchIsVisible(boolean isVisible) { this.isSearchVisible.set(isVisible); }

    public void setBottomPadding(int dimensionPixelSize) {
       // backgroundBlur.setPadding(0, 0,0, dimensionPixelSize);
    }

    public View getView() {
        return view;
    }
}