package com.giga.tech1000.heartbeatz.layouts.holders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.layouts.models.BaseLayoutItem;

public abstract class BaseLayoutHolder extends RecyclerView.ViewHolder {

    public BaseLayoutHolder(@NonNull View itemView) {
        super(itemView);
    }

    public abstract void bind(BaseLayoutItem item);

    public abstract void onViewAttachedToWindow();
    public abstract void onViewDetachedFromWindow();


}

