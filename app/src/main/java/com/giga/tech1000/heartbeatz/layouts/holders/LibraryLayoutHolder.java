package com.giga.tech1000.heartbeatz.layouts.holders;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.layouts.models.BaseLayoutItem;
import com.giga.tech1000.heartbeatz.layouts.models.LibraryLayoutItem;

public abstract class LibraryLayoutHolder extends BaseLayoutHolder {

    protected final RecyclerView recyclerView;
    protected final View emptyStateContainer;
    protected final ImageView emptyStateIcon;
    protected final TextView emptyStateTitle;
    protected final TextView emptyStateDesc;


    public LibraryLayoutHolder(@NonNull View itemView) {
        super(itemView);
        recyclerView = itemView.findViewById(R.id.recycler_view);
        emptyStateContainer = itemView.findViewById(R.id.empty_state_container);
        emptyStateIcon = itemView.findViewById(R.id.empty_state_icon);
        emptyStateTitle = itemView.findViewById(R.id.empty_state_title);
        emptyStateDesc = itemView.findViewById(R.id.empty_state_desc);
    }

    @Override
    public void bind(BaseLayoutItem item) {
        onBindContent(item);
        updateEmptyState(item);
    }

    protected abstract void onBindContent(BaseLayoutItem item);

    protected void updateEmptyState(BaseLayoutItem item) {
        boolean isEmpty = item.getItems().isEmpty();
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        if (isEmpty) {
            switch (item.getType()) {
                case ALL_SONGS -> {
                    emptyStateTitle.setText(R.string.no_songs_found);
                    emptyStateDesc.setText(R.string.no_songs_desc);
                    emptyStateIcon.setImageResource(com.giga.tech1000.icons_pack.R.drawable.music_note_2_24px);
                }
                case ALBUMS -> {
                    emptyStateTitle.setText(R.string.no_albums_found);
                    emptyStateDesc.setText(R.string.no_albums_desc);
                    emptyStateIcon.setImageResource(com.giga.tech1000.icons_pack.R.drawable.album_24px);
                }
                case ARTISTS -> {
                    emptyStateTitle.setText(R.string.no_artists_found);
                    emptyStateDesc.setText(R.string.no_artists_desc);
                    emptyStateIcon.setImageResource(com.giga.tech1000.icons_pack.R.drawable.person_4_24px);
                }
                case GENRES -> {
                    emptyStateTitle.setText(R.string.no_genres_found);
                    emptyStateDesc.setText(R.string.no_genres_desc);
                    emptyStateIcon.setImageResource(com.giga.tech1000.icons_pack.R.drawable.genres_24px);
                }
                case PLAYLISTS -> {
                    emptyStateTitle.setText(R.string.no_playlists_found);
                    emptyStateDesc.setText(R.string.no_playlists_desc);
                    emptyStateIcon.setImageResource(com.giga.tech1000.icons_pack.R.drawable.playlist_play_24px);
                }
                case FOLDERS -> {
                    emptyStateTitle.setText(R.string.no_folders_found);
                    emptyStateDesc.setText(R.string.no_folders_desc);
                    emptyStateIcon.setImageResource(com.giga.tech1000.icons_pack.R.drawable.folder_24px);
                }
            }
        }
    }


}

