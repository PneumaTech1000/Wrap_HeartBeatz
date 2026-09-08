package com.giga.tech1000.heartbeatz.layouts;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.giga.tech1000.heartbeatz.layouts.models.LibraryLayoutItem;

public class LibraryLayoutDiffCallback extends DiffUtil.ItemCallback<LibraryLayoutItem> {

    @Override
    public boolean areItemsTheSame(
            @NonNull LibraryLayoutItem oldItem,
            @NonNull LibraryLayoutItem newItem
    ) {
        // One page per type → this is stable
        return oldItem.getType() == newItem.getType();
    }

    @Override
    public boolean areContentsTheSame(
            @NonNull LibraryLayoutItem oldItem,
            @NonNull LibraryLayoutItem newItem
    ) {
        // Compare filtered list sizes + hash
        return oldItem.getItems().size()
                == newItem.getItems().size()
                && oldItem.getItems()
                .equals(newItem.getItems());
    }
}

