package com.giga.tech1000.heartbeatz.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;

import java.util.ArrayList;
import java.util.List;

public class GuestListAdapter extends RecyclerView.Adapter<GuestListAdapter.GuestViewHolder> {

    private final List<String> guestNames = new ArrayList<>();

    public void setGuests(List<String> guests) {
        guestNames.clear();
        guestNames.addAll(guests);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GuestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_party_guest, parent, false);
        return new GuestViewHolder(view);
    }

    public interface OnGuestClickListener {
        void onGuestClick(String name);
    }

    private OnGuestClickListener listener;

    public void setOnGuestClickListener(OnGuestClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull GuestViewHolder holder, int position) {
        String name = guestNames.get(position);
        holder.tvName.setText(name);
        holder.tvDevice.setText("Connected Device");
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGuestClick(name);
            }
        });
    }

    @Override
    public int getItemCount() {
        return guestNames.size();
    }

    static class GuestViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDevice;
        ImageView ivAvatar;

        public GuestViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.name);
            tvDevice = itemView.findViewById(R.id.device);
            ivAvatar = itemView.findViewById(R.id.avatar);
            ivAvatar.setImageResource(com.giga.tech1000.icons_pack.R.drawable.person_add_24px);
        }
    }
}
