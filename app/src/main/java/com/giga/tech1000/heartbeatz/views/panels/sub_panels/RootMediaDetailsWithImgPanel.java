package com.giga.tech1000.heartbeatz.views.panels.sub_panels;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.MediaDetailsWithImgAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.media_details.MediaDetailsWithImgViewItem;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.MediaDetail;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RootMediaDetailsWithImgPanel {

    private final View view;
    private final CoordinatorLayout detailsPage;
    private final LinearLayout emptyPageLayout;
    private final CollapsingToolbarLayout collapsingToolbarLayout;
    private final ImageView itemImageView;
    private MaterialToolbar toolbar;
    private final MaterialToolbar emptyToolbar;
    private final ImageButton searchBtn;
    private final SearchView searchView;
    private final ImageButton shuffleQueue;
    private RecyclerView recyclerView;
    private final MediaDetailsWithImgAdapter adapter;
    private final AtomicBoolean isVisible = new AtomicBoolean(false);
    private final AtomicBoolean isSearchVisible = new AtomicBoolean(false);
    private final PlaybackCacheViewModel playbackViewModel;


    public RootMediaDetailsWithImgPanel(@NonNull FragmentHome context, @NonNull ViewGroup parent,
                                        @NonNull PlaybackCacheViewModel playbackViewModel) {
        this.playbackViewModel = playbackViewModel;
        view = LayoutInflater.from(context.requireContext()).inflate(R.layout.media_details_with_img_root_layout, parent, false);

        detailsPage = view.findViewById(R.id.detail_list_page);
        emptyPageLayout = view.findViewById(R.id.empty_list_page);

        collapsingToolbarLayout = view.findViewById(R.id.collapsing_toolbar);
        itemImageView = view.findViewById(R.id.image);
        toolbar = view.findViewById(R.id.toolbar);
        emptyToolbar = view.findViewById(R.id.empty_tool_bar);

        searchBtn = view.findViewById(R.id.search_queue);
        searchView = view.findViewById(R.id.search_view);
        shuffleQueue = view.findViewById(R.id.shuffle_queue);
        recyclerView = view.findViewById(R.id.recycler_view);

        collapsingToolbarLayout.setCollapsedTitleTextColor(context.requireContext().getResources().getColor(R.color.white, context.requireContext().getTheme()));
        collapsingToolbarLayout.setExpandedTitleColor(context.requireContext().getResources().getColor(R.color.white, context.requireContext().getTheme()));


        adapter = new MediaDetailsWithImgAdapter(new ArrayList<>(), playbackViewModel);
        adapter.setViewType(BaseRecyclerViewAdapter.ViewType.LIST);

        playbackViewModel.getPlayerCacheInfo().observe(context.getViewLifecycleOwner(), adapter::setPlayingCacheInfo);
        recyclerView.setLayoutManager(new LinearLayoutManager(context.requireContext()));
        recyclerView.setAdapter(adapter);

        toolbar.setNavigationOnClickListener(v -> {
            isVisible.set(false);
            context.hideMediaDetailsPanel();
        });
        emptyToolbar.setNavigationOnClickListener(v -> {
            isVisible.set(false);
            context.hideMediaDetailsPanel();
        });

        setupSearch();
    }

    private void setupSearch() {
        searchBtn.setOnClickListener(v -> {
            if (searchView.getVisibility() == View.VISIBLE) {
                collapseSearch();
            } else {
                expandSearch();
            }
        });

        searchView.setOnCloseListener(() -> {
            collapseSearch();
            return false;
        });

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return false;
            }
        });
    }

    private void expandSearch() {
        searchView.setVisibility(View.VISIBLE);
        searchView.setIconified(false);
        searchBtn.setVisibility(View.GONE);
        setSearchIsVisible(true);
        if (shuffleQueue != null) {
            shuffleQueue.setVisibility(View.GONE);
        }
        collapsingToolbarLayout.setTitleEnabled(false);
    }

    private void collapseSearch() {
        searchView.setQuery("", false);
        searchView.setVisibility(View.GONE);
        searchBtn.setVisibility(View.VISIBLE);
        setSearchIsVisible(false);
        if (shuffleQueue != null) {
            shuffleQueue.setVisibility(View.VISIBLE);
        }
        collapsingToolbarLayout.setTitleEnabled(true);
    }


    public void addDetails(MediaDetail mediaDetail) {
        isVisible.set(true);
        List<BaseRecyclerViewItem> items = new ArrayList<>();
        collapsingToolbarLayout.setTitle(mediaDetail.getTitle());
        emptyToolbar.setTitle(mediaDetail.getTitle());
        ImageLoader.load(itemImageView, mediaDetail.getUri());

        for (Song song : mediaDetail.getSongs()) {
            items.add(new MediaDetailsWithImgViewItem(song));
        }

        adapter.setItems(items);

        if (items.isEmpty()) {
            emptyPageLayout.setVisibility(View.VISIBLE);
            detailsPage.setVisibility(View.GONE);
        } else {
            emptyPageLayout.setVisibility(View.GONE);
            detailsPage.setVisibility(View.VISIBLE);
        }
    }

    public AtomicBoolean getIsVisible() {
        return isVisible;
    }

    public AtomicBoolean getIsSearchVisible() {
        return isSearchVisible;
    }

    public void setIsVisible(boolean isVisible) {
        this.isVisible.set(isVisible);
    }

    public void setSearchIsVisible(boolean isVisible) {
        this.isSearchVisible.set(isVisible);
    }

    public View getView() {
        return view;
    }

    public void setBottomPadding(int dimensionPixelSize) {
        detailsPage.setPadding(0, 0, 0, dimensionPixelSize);
    }
}