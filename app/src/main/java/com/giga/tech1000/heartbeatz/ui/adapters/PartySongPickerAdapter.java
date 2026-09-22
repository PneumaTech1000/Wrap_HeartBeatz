package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.media_player.models.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PartySongPickerAdapter extends RecyclerView.Adapter<PartySongPickerAdapter.VH> {

    public interface Listener {
        void onSongChosen(@NonNull Song song);
    }

    private final List<Song> all = new ArrayList<>();
    private final List<Song> visible = new ArrayList<>();
    @Nullable private Listener listener;

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<Song> songs) {
        all.clear();
        all.addAll(songs);
        visible.clear();
        visible.addAll(songs);
        notifyDataSetChanged();
    }

    public void filter(@Nullable String query) {
        visible.clear();
        if (query == null || query.trim().isEmpty()) {
            visible.addAll(all);
        } else {
            String q = query.trim().toLowerCase(Locale.US);
            for (Song s : all) {
                String t = s.getTitle() != null ? s.getTitle().toLowerCase(Locale.US) : "";
                String a = s.getArtist() != null ? s.getArtist().toLowerCase(Locale.US) : "";
                if (t.contains(q) || a.contains(q)) visible.add(s);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_party_song_picker_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Song song = visible.get(position);
        h.title.setText(song.getTitle() != null ? song.getTitle() : "Unknown");
        h.artist.setText(song.getArtist() != null ? song.getArtist() : "");
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSongChosen(song);
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title, artist;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.picker_title);
            artist = itemView.findViewById(R.id.picker_artist);
        }
    }
}
