package com.giga.tech1000.heartbeatz.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.adapters.FreeShelfAdapter;
import com.giga.tech1000.heartbeatz.view_models.extended_models.FreeShelfViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

/**
 * P7 — curated legal free music (CC0 / CC-BY / PD). No commercial scraping.
 */
public class FragmentFreeShelf extends Fragment {

    private FreeShelfViewModel viewModel;
    private FreeShelfAdapter adapter;
    private CircularProgressIndicator progress;
    private TextView empty;
    private TextView policy;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_free_shelf, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FreeShelfViewModel.class);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_free_shelf);
        toolbar.setNavigationOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        policy = view.findViewById(R.id.txt_free_shelf_policy);
        progress = view.findViewById(R.id.progress_free_shelf);
        empty = view.findViewById(R.id.txt_free_shelf_empty);
        RecyclerView recycler = view.findViewById(R.id.recycler_free_shelf);
        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_refresh_shelf);

        adapter = new FreeShelfAdapter(track -> viewModel.play(track));
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        fab.setOnClickListener(v -> viewModel.refresh());

        viewModel.getTracks().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            boolean noData = list == null || list.isEmpty();
            empty.setVisibility(noData ? View.VISIBLE : View.GONE);
        });
        viewModel.isLoading().observe(getViewLifecycleOwner(), loading -> {
            progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });
        viewModel.getPolicyText().observe(getViewLifecycleOwner(), text -> {
            if (text != null && !text.isEmpty()) {
                policy.setText(text);
            }
        });
        viewModel.getAttributionEvent().observe(getViewLifecycleOwner(), credit -> {
            if (credit == null || credit.isEmpty()) return;
            Snackbar.make(view, credit, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                Snackbar.make(view, "Shelf: " + err, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
