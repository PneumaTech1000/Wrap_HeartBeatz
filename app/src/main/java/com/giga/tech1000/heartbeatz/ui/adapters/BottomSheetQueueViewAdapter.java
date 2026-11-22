package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.architecture.party.PartyQueueItem;
import com.giga.tech1000.heartbeatz.ui.adapters.helpers.BaseViewHelper;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BottomSheetQueueViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;
import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BottomSheetQueueViewHolder;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.ArrayList;
import java.util.List;

public class BottomSheetQueueViewAdapter extends BaseRecyclerViewAdapter {

    public interface PartyQueueActions {
        void onApprove(@NonNull PartyQueueItem item);
        void onReject(@NonNull PartyQueueItem item);
        void onRemove(@NonNull PartyQueueItem item);
        void onPlayReady(@NonNull PartyQueueItem item);
    }

    private final PlaybackCacheViewModel playbackViewModel;
    private boolean partyMode;
    private boolean hostControls;
    @Nullable private PartyQueueActions partyActions;

    public BottomSheetQueueViewAdapter(List<BaseRecyclerViewItem> i, PlaybackCacheViewModel playbackViewModel) {
        super(i);
        this.playbackViewModel = playbackViewModel;
    }

    public void setPartyMode(boolean partyMode, boolean hostControls) {
        this.partyMode = partyMode;
        this.hostControls = hostControls;
        notifyDataSetChanged();
    }

    public void setPartyActions(@Nullable PartyQueueActions partyActions) {
        this.partyActions = partyActions;
    }

    @NonNull
    @Override
    public BaseRecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BaseRecyclerViewItem.ItemType itemType = BaseRecyclerViewItem.ItemType.values()[viewType];

        return switch (itemType) {
            case BOTTOM_SHEET_SONG ->
                    BaseViewHelper.onCreateViewHolder(BottomSheetQueueViewHolder.class, parent, getViewType());
            default -> throw new IllegalStateException(
                    "Unsupported viewType: " + viewType
            );
        };
    }

    @Override
    protected void bind(@NonNull BaseRecyclerViewHolder holder, @NonNull BaseRecyclerViewItem item) {
        holder.onInitializeView(getViewType());
        holder.onBindViewHolder(item);
        holder.onPlayingStateViewHolder(getPlayingCacheInfo(), item);

        BottomSheetQueueViewItem qItem = (item instanceof BottomSheetQueueViewItem)
                ? (BottomSheetQueueViewItem) item : null;
        PartyQueueItem party = qItem != null ? qItem.getPartyItem() : null;

        View approve = holder.itemView.findViewById(R.id.bottom_sheet_queue_approve);
        View reject = holder.itemView.findViewById(R.id.bottom_sheet_queue_reject);
        View remove = holder.itemView.findViewById(R.id.bottom_sheet_queue_remove);

        if (partyMode && party != null && hostControls) {
            boolean pending = party.isPending();
            if (approve != null) {
                approve.setVisibility(pending ? View.VISIBLE : View.GONE);
                approve.setOnClickListener(v -> {
                    if (partyActions != null) partyActions.onApprove(party);
                });
            }
            if (reject != null) {
                reject.setVisibility(pending ? View.VISIBLE : View.GONE);
                reject.setOnClickListener(v -> {
                    if (partyActions != null) partyActions.onReject(party);
                });
            }
            if (remove != null) {
                remove.setVisibility(pending ? View.GONE : View.VISIBLE);
                remove.setOnClickListener(v -> {
                    if (partyActions != null) partyActions.onRemove(party);
                });
            }
        } else {
            if (approve != null) approve.setVisibility(View.GONE);
            if (reject != null) reject.setVisibility(View.GONE);
            if (remove != null) remove.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            if (partyMode) {
                if (party != null && hostControls && party.isReadyOrPlaying() && partyActions != null) {
                    partyActions.onPlayReady(party);
                }
                // Guests cannot jump the DJ queue
                return;
            }

            if (playbackViewModel == null) return;
            var cache = getPlayingCacheInfo();
            var source = cache != null ? cache.getSource() : ItemSource.ALL_SONGS;
            List<Integer> q = getQueue();
            if (q == null) q = new ArrayList<>();
            playbackViewModel.play(pos, q, source);
        });
    }
}
