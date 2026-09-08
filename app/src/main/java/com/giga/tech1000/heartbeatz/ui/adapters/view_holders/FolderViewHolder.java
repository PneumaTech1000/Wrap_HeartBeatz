package com.giga.tech1000.heartbeatz.ui.adapters.view_holders;

import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.FolderViewItem;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;

public class FolderViewHolder extends BaseRecyclerViewHolder {


    public FolderViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    @Override
    public void onInitializeView(BaseRecyclerViewAdapter.ViewType viewType) {

    }

    @Override
    public void onBindViewHolder(BaseRecyclerViewItem viewItem) {
        FolderViewItem item = (FolderViewItem) viewItem;

        TextView title = findViewById(R.id.item_library_title);
        TextView artist = findViewById(R.id.item_library_subtitle);
        ImageView art = findViewById(R.id.item_library_art);

        String single = item.getFolder().getSongCount() > 1 ? " Songs " : " Song ";
        String info = item.getFolder().getSongCount() + single + item.getFolder().getPath();

        title.setText(item.getFolder().getName());
        artist.setText(info);

        art.setImageResource(com.giga.tech1000.icons_pack.R.drawable.folder_48px);
        art.setColorFilter(R.color.hb_primary, PorterDuff.Mode.SRC_IN);

    }

    @Override
    public void onPlayingStateViewHolder(PlayerCacheModel cacheModel, BaseRecyclerViewItem viewItem) {

    }
}
