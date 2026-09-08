package com.giga.tech1000.heartbeatz.ui.adapters.helpers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.util.Pair;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.interfaces.ViewHolderCreator;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.AlbumViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.ArtistViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BottomSheetQueueViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.CreatePlaylistViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.FolderViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.GenreViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.MediaDetailsWithImgViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.MediaDetailsWithoutImgViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.PlaylistViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.SelectSongViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.SongViewHolder;

import java.util.HashMap;
import java.util.Map;

public class BaseViewHelper {

    // --- UPGRADED MAP ---
    // The outer map key is the ViewHolder class.
    // The inner map key is the ItemType enum, and the value is the Layout ID.
    public static final Map<Class<?>, Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>>> registry = new HashMap<>();


    static {
        // --- MAPPING FOR SongViewHolder ---
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> song = new HashMap<>();
        song.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_layout_list_rectangle, SongViewHolder::new));
        song.put(BaseRecyclerViewAdapter.ViewType.GRID, new Pair<>(R.layout.item_library_layout_grid_rectangle, SongViewHolder::new));
        registry.put(SongViewHolder.class, song);

        // ===== mapping for bottom sheet queue view holder =====
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> bottomSheetQueueLayouts = new HashMap<>();
        bottomSheetQueueLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_bottom_sheet_queue_list, BottomSheetQueueViewHolder::new)); // Your existing list layout
        registry.put(BottomSheetQueueViewHolder.class, bottomSheetQueueLayouts);

        // ==== Mapping for AlbumViewHolder ====
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> albumLayouts = new HashMap<>();
        albumLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_layout_list_rectangle, AlbumViewHolder::new));
        albumLayouts.put(BaseRecyclerViewAdapter.ViewType.GRID, new Pair<>(R.layout.item_library_layout_grid_rectangle, AlbumViewHolder::new));
        registry.put(AlbumViewHolder.class, albumLayouts);

        // ==== Mapping for ArtistViewHolder ====
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> artistLayouts = new HashMap<>();
        artistLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_layout_list_circle, ArtistViewHolder::new));
        artistLayouts.put(BaseRecyclerViewAdapter.ViewType.GRID, new Pair<>(R.layout.item_library_layout_grid_circle, ArtistViewHolder::new));
        registry.put(ArtistViewHolder.class, artistLayouts);

        // ==== Mapping for FolderViewHolder ====
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> folderLayouts = new HashMap<>();
        folderLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_layout_list_rectangle, FolderViewHolder::new));
        folderLayouts.put(BaseRecyclerViewAdapter.ViewType.GRID, new Pair<>(R.layout.item_library_layout_grid_rectangle, FolderViewHolder::new));
        registry.put(FolderViewHolder.class, folderLayouts);

        // ===== Mapping for GenreViewHolder =====
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> genreLayouts = new HashMap<>();
        genreLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_layout_list_circle, GenreViewHolder::new));
        genreLayouts.put(BaseRecyclerViewAdapter.ViewType.GRID, new Pair<>(R.layout.item_library_layout_grid_circle, GenreViewHolder::new));
        registry.put(GenreViewHolder.class, genreLayouts);

        // ===== Mapping for PlaylistViewHolder =====
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> playlistLayouts = new HashMap<>();
        playlistLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_layout_list_circle, null));
        playlistLayouts.put(BaseRecyclerViewAdapter.ViewType.GRID, new Pair<>(R.layout.item_library_layout_grid_circle, null));
        registry.put(PlaylistViewHolder.class, playlistLayouts);

        // ===== Mapping for CreatePlaylistViewHolder =====
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> createPlaylistLayouts = new HashMap<>();
        createPlaylistLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_create_playlist_layout_list_circle, CreatePlaylistViewHolder::new));
        createPlaylistLayouts.put(BaseRecyclerViewAdapter.ViewType.GRID, new Pair<>(R.layout.item_create_playlist_layout_grid_circle, CreatePlaylistViewHolder::new));
        registry.put(CreatePlaylistViewHolder.class, createPlaylistLayouts);

        // ======== Mapping for MediaDetailsViewHolder ========
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> mediaDetailsImgLayouts = new HashMap<>();
        mediaDetailsImgLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_media_details_list, MediaDetailsWithImgViewHolder::new));
        registry.put(MediaDetailsWithImgViewHolder.class, mediaDetailsImgLayouts);

        // ======== Mapping for MediaDetailsViewHolder ========
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> mediaDetailsLayouts = new HashMap<>();
        mediaDetailsLayouts.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_layout_list_circle, MediaDetailsWithoutImgViewHolder::new));
        registry.put(MediaDetailsWithoutImgViewHolder.class, mediaDetailsLayouts);

        // ======== Mapping for SelectSongViewHolder ========
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> selectionLayout = new HashMap<>();
        selectionLayout.put(BaseRecyclerViewAdapter.ViewType.LIST, new Pair<>(R.layout.item_library_selection_list_circle, SelectSongViewHolder::new));
        registry.put(SelectSongViewHolder.class, selectionLayout);

    }

    public static View inflate(ViewGroup parent, Class<?> vhClass, BaseRecyclerViewAdapter.ViewType viewType) {
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> map = registry.get(vhClass);
        if (map == null) throw new IllegalStateException("No ViewHolder registered for " + vhClass.getSimpleName());
        Pair<Integer, ViewHolderCreator> entry = map.get(viewType);
        if (entry == null) throw new IllegalStateException("No layout for " + vhClass.getSimpleName() + " type " + viewType);
        return LayoutInflater.from(parent.getContext()).inflate(entry.first, parent, false);
    }

    public static BaseRecyclerViewHolder onCreateViewHolder(
            Class<?> vhClass,
            ViewGroup parent,
            BaseRecyclerViewAdapter.ViewType viewType
    ) {
        Map<BaseRecyclerViewAdapter.ViewType, Pair<Integer, ViewHolderCreator>> map =
                registry.get(vhClass);

        if (map == null) {
            throw new IllegalStateException("No ViewHolder registered for " + vhClass.getSimpleName());
        }

        Pair<Integer, ViewHolderCreator> entry = map.get(viewType);
        if (entry == null) {
            throw new IllegalStateException("No layout for " + vhClass.getSimpleName() + " type " + viewType);
        }

        View v = LayoutInflater.from(parent.getContext())
                .inflate(entry.first, parent, false);

        return entry.second.create(v);
    }

}
