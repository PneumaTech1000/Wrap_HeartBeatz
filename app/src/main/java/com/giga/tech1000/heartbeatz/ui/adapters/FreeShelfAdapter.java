package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.architecture.freeshelf.FreeShelfTrack;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

public final class FreeShelfAdapter extends ListAdapter<FreeShelfTrack, FreeShelfAdapter.VH> {

    public interface Listener {
        void onPlay(@NonNull FreeShelfTrack track);
    }

    private final Listener listener;

    public FreeShelfAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<FreeShelfTrack> DIFF =
            new DiffUtil.ItemCallback<FreeShelfTrack>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull FreeShelfTrack a, @NonNull FreeShelfTrack b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull FreeShelfTrack a, @NonNull FreeShelfTrack b) {
                    return a.title.equals(b.title)
                            && a.artist.equals(b.artist)
                            && a.streamUrl.equals(b.streamUrl)
                            && a.license == b.license;
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_free_shelf_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FreeShelfTrack t = getItem(position);
        h.title.setText(t.title);
        h.artist.setText(t.artist);
        h.license.setText(t.license.label);
        String credit = t.sourceName != null
                ? t.sourceName + " · " + t.attribution
                : t.attribution;
        h.attribution.setText(credit);
        h.play.setOnClickListener(v -> listener.onPlay(t));
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView artist;
        final TextView attribution;
        final Chip license;
        final MaterialButton play;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txt_track_title);
            artist = itemView.findViewById(R.id.txt_track_artist);
            attribution = itemView.findViewById(R.id.txt_track_attribution);
            license = itemView.findViewById(R.id.chip_license);
            play = itemView.findViewById(R.id.btn_play_track);
        }
    }
}
