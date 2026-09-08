package com.giga.tech1000.heartbeatz.layouts.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.layouts.LibraryLayoutDiffCallback;
import com.giga.tech1000.heartbeatz.layouts.holders.AlbumsLayoutHolder;
import com.giga.tech1000.heartbeatz.layouts.holders.AllSongsLayoutHolder;
import com.giga.tech1000.heartbeatz.layouts.holders.ArtistsLayoutHolder;
import com.giga.tech1000.heartbeatz.layouts.holders.BaseLayoutHolder;
import com.giga.tech1000.heartbeatz.layouts.holders.FolderLayoutHolder;
import com.giga.tech1000.heartbeatz.layouts.holders.GenresLayoutHolder;
import com.giga.tech1000.heartbeatz.layouts.holders.PlaylistsLayoutHolder;
import com.giga.tech1000.heartbeatz.layouts.models.BaseLayoutItem;
import com.giga.tech1000.heartbeatz.layouts.models.LibraryLayoutItem;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;

public class LibraryLayoutAdapter
        extends ListAdapter<LibraryLayoutItem, BaseLayoutHolder> {

    private final LayoutInflater inflater;
    private final MediaNavigation mediaNavigation;
    private final PlaybackCacheViewModel playbackViewModel;

    public LibraryLayoutAdapter(
            @NonNull Context context,
            @NonNull MediaNavigation navigation,
            @NonNull PlaybackCacheViewModel playbackViewModel
    ) {
        super(new LibraryLayoutDiffCallback());
        this.inflater = LayoutInflater.from(context);
        this.mediaNavigation = navigation;
        this.playbackViewModel = playbackViewModel;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public BaseLayoutHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        BaseLayoutItem.LayoutType type = BaseLayoutItem.LayoutType.values()[viewType];

        View view = inflater.inflate(
                R.layout.local_library_layout,
                parent,
                false
        );

        return switch (type) {
            case ALL_SONGS -> new AllSongsLayoutHolder(view, mediaNavigation, playbackViewModel);
            case ALBUMS -> new AlbumsLayoutHolder(view, mediaNavigation);
            case ARTISTS -> new ArtistsLayoutHolder(view, mediaNavigation);
            case GENRES -> new GenresLayoutHolder(view, mediaNavigation);
            case FOLDERS -> new FolderLayoutHolder(view, mediaNavigation);
            case PLAYLISTS -> new PlaylistsLayoutHolder(view, mediaNavigation);
        };
    }

    @Override
    public void onBindViewHolder(
            @NonNull BaseLayoutHolder holder,
            int position
    ) {
        holder.bind(getItem(position));
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType().ordinal();
    }


    @Override
    public long getItemId(int position) {
        return getItem(position).getStableId();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull BaseLayoutHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.onViewAttachedToWindow();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull BaseLayoutHolder holder) {
        holder.onViewDetachedFromWindow();
        super.onViewDetachedFromWindow(holder);
    }

}
