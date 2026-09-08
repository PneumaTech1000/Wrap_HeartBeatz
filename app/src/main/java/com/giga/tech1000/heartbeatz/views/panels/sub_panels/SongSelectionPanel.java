package com.giga.tech1000.heartbeatz.views.panels.sub_panels;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.SelectSongViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.SelectSongViewItem;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.media_player.models.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.android.material.appbar.MaterialToolbar;

public class SongSelectionPanel {
    private final View view;
    private MaterialToolbar toolbar;
    private final ConstraintLayout backgroundBlur;
    private AppCompatButton btnPlaylistAdd;
    private RecyclerView recyclerView;

    private final SelectSongViewAdapter adapter;
    private long playlistId;
    private final AtomicBoolean isVisible = new AtomicBoolean(false);
    private final AtomicBoolean isSearchVisible = new AtomicBoolean(false);

    public SongSelectionPanel(@NonNull FragmentHome fragment, @NonNull ViewGroup parent) {
        view = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.media_selection_root_layout, parent, false);

        toolbar = view.findViewById(R.id.tool_bar);

        backgroundBlur = view.findViewById(R.id.detail_list_page);

        this.btnPlaylistAdd = view.findViewById(R.id.btn_playlist_add);
        this.recyclerView = view.findViewById(R.id.recycler_view);

        adapter = new SelectSongViewAdapter(new ArrayList<>());
        adapter.setViewType(BaseRecyclerViewAdapter.ViewType.LIST);
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

        btnPlaylistAdd.setOnClickListener(v -> {
            List<Long> selectedIds = adapter.getSelectedSongsId();
            fragment.addSelectedToPlaylist(playlistId, selectedIds);
        });



    }

    public void addDetails(List<Song> songs, long playlistId) {
        isVisible.set(true);
        List<BaseRecyclerViewItem> items = new ArrayList<>();
        this.playlistId = playlistId;

        for (Song song : songs) {
            items.add(new SelectSongViewItem(song));
        }

        adapter.setItems(items);
    }

    private void hookSearchView(SearchView searchView) {
        searchView.setOnSearchClickListener(view -> setSearchIsVisible(true));
        searchView.setOnCloseListener(() -> {
            setSearchIsVisible(false);
            return true;
        });

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

    public AtomicBoolean getIsVisible() {
        return isVisible;
    }
    public AtomicBoolean getIsSearchVisible() { return isSearchVisible; }

    public void setIsVisible(boolean isVisible) {
        this.isVisible.set(isVisible);
    }
    public void setSearchIsVisible(boolean isVisible) { this.isSearchVisible.set(isVisible); }


    public void setBottomPadding(int dimensionPixelSize) {
        backgroundBlur.setPadding(0, 0, 0, dimensionPixelSize);
    }

    public View getView() {
        return view;
    }
}
