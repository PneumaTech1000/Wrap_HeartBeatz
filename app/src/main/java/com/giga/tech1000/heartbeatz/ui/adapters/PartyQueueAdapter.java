package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.party.PartyQueueItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Party queue list. Host: remove enabled. Guest: faded, remove hidden.
 */
public final class PartyQueueAdapter extends RecyclerView.Adapter<PartyQueueAdapter.VH> {

    public interface Listener {
        void onRemove(@NonNull PartyQueueItem item, int position);
        void onPlay(@NonNull PartyQueueItem item, int position);
    }

    private final List<PartyQueueItem> items = new ArrayList<>();
    private boolean hostMode = true;
    @Nullable private Listener listener;

    public void setHostMode(boolean hostMode) {
        this.hostMode = hostMode;
        notifyDataSetChanged();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<PartyQueueItem> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    public List<PartyQueueItem> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_party_queue_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PartyQueueItem item = items.get(position);
        h.index.setText(String.valueOf(position + 1));
        h.title.setText(item.title);
        h.subtitle.setText(item.subtitle());
        h.itemView.setAlpha(hostMode ? 1f : 0.55f);
        h.remove.setVisibility(hostMode ? View.VISIBLE : View.GONE);
        h.remove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(item, h.getBindingAdapterPosition());
        });
        h.itemView.setOnClickListener(v -> {
            if (hostMode && listener != null) {
                int pos = h.getBindingAdapterPosition();
                if (pos >= 0) listener.onPlay(item, pos);
            }
        });
        h.itemView.setClickable(hostMode);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView index, title, subtitle;
        final ImageButton remove;

        VH(@NonNull View itemView) {
            super(itemView);
            index = itemView.findViewById(R.id.party_queue_index);
            title = itemView.findViewById(R.id.party_queue_track_title);
            subtitle = itemView.findViewById(R.id.party_queue_track_subtitle);
            remove = itemView.findViewById(R.id.party_queue_btn_remove);
        }
    }
}
