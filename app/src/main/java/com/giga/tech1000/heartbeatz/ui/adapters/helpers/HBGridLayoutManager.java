package com.giga.tech1000.heartbeatz.ui.adapters.helpers;

import android.content.Context;
import androidx.recyclerview.widget.GridLayoutManager;

import com.giga.tech1000.heartbeatz.ui.adapters.BaseRecyclerViewAdapter;

public class HBGridLayoutManager extends GridLayoutManager {

    private static final int MAX_SPAN_COUNT = 2;

    public HBGridLayoutManager(Context context, BaseRecyclerViewAdapter adapter) {
        super(context, MAX_SPAN_COUNT);
        setSpanSizeLookup(new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter.getViewType() == BaseRecyclerViewAdapter.ViewType.LIST) {
                    return MAX_SPAN_COUNT; // Takes full width (1 item per row)
                } else {
                    return 1; // Takes 1/2 width (2 items per row)
                }
            }
        });
    }
}
