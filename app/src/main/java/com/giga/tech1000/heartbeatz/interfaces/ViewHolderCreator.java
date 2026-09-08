package com.giga.tech1000.heartbeatz.interfaces;

import android.view.View;

import com.giga.tech1000.heartbeatz.ui.adapters.view_holders.BaseRecyclerViewHolder;

public interface ViewHolderCreator {
    BaseRecyclerViewHolder create(View view);
}

