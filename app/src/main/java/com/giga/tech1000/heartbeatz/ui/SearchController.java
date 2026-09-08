package com.giga.tech1000.heartbeatz.ui;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchController {

    private final MutableLiveData<String> query = new MutableLiveData<>("");

    // --- STATE ---

    public LiveData<String> getQuery() {
        return query;
    }

    public void setQuery(@Nullable String q) {
        query.setValue(
                q == null ? "" : q.trim().toLowerCase(Locale.ROOT)
        );
    }

    public void clear() {
        setQuery("");
    }

    public boolean isSearching() {
        String q = query.getValue();
        return q != null && !q.isEmpty();
    }

    // --- FILTERING ---

    public List<BaseRecyclerViewItem> filter(
            List<BaseRecyclerViewItem> source,
            @Nullable String query
    ) {
        if (query == null || query.isEmpty()) return source;

        List<BaseRecyclerViewItem> result = new ArrayList<>();

        for (BaseRecyclerViewItem item : source) {
            for (String token : item.searchTokens()) {
                if (token.toLowerCase(Locale.ROOT).contains(query)) {
                    result.add(item);
                    break;
                }
            }
        }
        return result;
    }

}
