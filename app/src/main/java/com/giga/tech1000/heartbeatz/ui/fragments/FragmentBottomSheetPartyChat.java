package com.giga.tech1000.heartbeatz.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;
import com.giga.tech1000.heartbeatz.architecture.di.AppContainer;
import com.giga.tech1000.heartbeatz.architecture.session.PartySession;
import com.giga.tech1000.heartbeatz.ui.adapters.PartyQueueAdapter;
import com.giga.tech1000.heartbeatz.ui.adapters.PartySongPickerAdapter;
import com.giga.tech1000.heartbeatz.ui.party.PartyQueueItem;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Party chat room UI inside the media-player custom bottom sheet.
 * <p>
 * Song picker expands upward from {@code chat_dock_composer_card} over the chat stream.
 * Queue list is host-editable; guests see a faded read-only list.
 * Backend (upload / approve / Firebase chat) is wired in a later pass.
 */
@UnstableApi
public class FragmentBottomSheetPartyChat extends Fragment {

    @Nullable
    private BottomSheetView parentSheetView;

    private MaterialCardView songPickerPanel;
    private MaterialButton btnSongPicker;
    private MaterialButton btnSongPickerClose;
    private EditText searchField;
    private RecyclerView pickerRecycler;
    private RecyclerView queueRecycler;
    private TextView queueCount;
    private TextView queueEmpty;
    private TextView queueGuestHint;
    private View queueSection;

    private PartySongPickerAdapter pickerAdapter;
    private PartyQueueAdapter queueAdapter;

    private boolean pickerOpen;
    private boolean hostMode = true;

    /** Required by {@link com.giga.tech1000.heartbeatz.ui.adapters.StateFragmentAdapter}. */
    public FragmentBottomSheetPartyChat() {}

    public FragmentBottomSheetPartyChat(@NonNull BottomSheetView parent) {
        this.parentSheetView = parent;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bottom_sheet_party_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        songPickerPanel = view.findViewById(R.id.party_song_picker_panel);
        btnSongPicker = view.findViewById(R.id.btn_party_song_picker);
        btnSongPickerClose = view.findViewById(R.id.btn_party_song_picker_close);
        searchField = view.findViewById(R.id.party_song_picker_search);
        pickerRecycler = view.findViewById(R.id.party_song_picker_recycler);
        queueRecycler = view.findViewById(R.id.party_queue_recycler);
        queueCount = view.findViewById(R.id.party_queue_count);
        queueEmpty = view.findViewById(R.id.party_queue_empty);
        queueGuestHint = view.findViewById(R.id.party_queue_guest_hint);
        queueSection = view.findViewById(R.id.party_queue_section);

        setupQueue();
        setupSongPicker();
        resolveHostMode();
        applyHostGuestChrome();
        loadLibraryIntoPicker();
        // Placeholder queue until backend
        queueAdapter.submit(new ArrayList<>());
        refreshQueueEmptyState();
    }

    private void setupQueue() {
        queueAdapter = new PartyQueueAdapter();
        queueRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        queueRecycler.setAdapter(queueAdapter);
        queueRecycler.setNestedScrollingEnabled(false);
        queueAdapter.setListener(new PartyQueueAdapter.Listener() {
            @Override
            public void onRemove(@NonNull PartyQueueItem item, int position) {
                if (!hostMode || position < 0) return;
                List<PartyQueueItem> next = queueAdapter.getItems();
                if (position < next.size()) {
                    next.remove(position);
                    queueAdapter.submit(next);
                    refreshQueueEmptyState();
                }
                // Backend: remove from Firebase queue later
            }

            @Override
            public void onPlay(@NonNull PartyQueueItem item, int position) {
                if (!hostMode) return;
                // Backend: host forces this track → sync publish later
            }
        });
    }

    private void setupSongPicker() {
        pickerAdapter = new PartySongPickerAdapter();
        pickerRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        pickerRecycler.setAdapter(pickerAdapter);
        pickerAdapter.setListener(song -> onSongPicked(song));

        if (btnSongPicker != null) {
            btnSongPicker.setOnClickListener(v -> toggleSongPicker());
        }
        if (btnSongPickerClose != null) {
            btnSongPickerClose.setOnClickListener(v -> collapseSongPicker());
        }
        if (searchField != null) {
            searchField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (pickerAdapter != null) pickerAdapter.filter(s != null ? s.toString() : "");
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        if (songPickerPanel != null) {
            songPickerPanel.setVisibility(View.GONE);
            songPickerPanel.setAlpha(0f);
            songPickerPanel.setTranslationY(80f);
        }
    }

    private void resolveHostMode() {
        try {
            AppContainer c = HeartBeatzApp.container(requireContext());
            PartySession session = c.partySession();
            hostMode = session.isHosting();
            // Guests who are in party → not host
            if (session.isGuest()) hostMode = false;
            // If not in party yet, treat as host chrome for layout preview
            if (!session.isInParty()) hostMode = true;
        } catch (Exception ignored) {
            hostMode = true;
        }
    }

    private void applyHostGuestChrome() {
        queueAdapter.setHostMode(hostMode);
        if (queueGuestHint != null) {
            queueGuestHint.setVisibility(hostMode ? View.GONE : View.VISIBLE);
        }
        if (songPickerPanel != null) {
            TextView title = songPickerPanel.findViewById(R.id.party_song_picker_title);
            if (title != null) {
                title.setText(hostMode ? "Add to party" : "Request a track");
            }
        }
    }

    private void loadLibraryIntoPicker() {
        try {
            SongRepository.getInstance().getSongs().observe(getViewLifecycleOwner(), tree -> {
                List<Song> songs = new ArrayList<>();
                if (tree != null) songs.addAll(tree.values());
                if (pickerAdapter != null) pickerAdapter.submit(songs);
            });
        } catch (Exception e) {
            if (pickerAdapter != null) pickerAdapter.submit(new ArrayList<>());
        }
    }

    private void toggleSongPicker() {
        if (pickerOpen) collapseSongPicker();
        else expandSongPicker();
    }

    /** Expands picker upward over chat from the composer dock. */
    public void expandSongPicker() {
        if (songPickerPanel == null || pickerOpen) return;
        pickerOpen = true;
        songPickerPanel.setVisibility(View.VISIBLE);
        songPickerPanel.setAlpha(0f);
        songPickerPanel.setTranslationY(songPickerPanel.getHeight() > 0
                ? songPickerPanel.getHeight() * 0.15f
                : 120f);
        songPickerPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(null)
                .start();
        if (btnSongPicker != null) {
            btnSongPicker.setSelected(true);
        }
    }

    public void collapseSongPicker() {
        if (songPickerPanel == null || !pickerOpen) return;
        pickerOpen = false;
        songPickerPanel.animate()
                .alpha(0f)
                .translationY(80f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        songPickerPanel.setVisibility(View.GONE);
                        songPickerPanel.animate().setListener(null);
                    }
                })
                .start();
        if (btnSongPicker != null) {
            btnSongPicker.setSelected(false);
        }
        if (searchField != null) {
            searchField.setText("");
        }
    }

    private void onSongPicked(@NonNull Song song) {
        collapseSongPicker();
        if (hostMode) {
            // Host: add to party-chat strip + shared Queue tab (party mode)
            List<PartyQueueItem> next = queueAdapter.getItems();
            next.add(new PartyQueueItem(
                    UUID.randomUUID().toString(),
                    song.getTitle() != null ? song.getTitle() : "Unknown",
                    song.getArtist(),
                    "You",
                    song.getId(),
                    null,
                    null));
            queueAdapter.submit(next);
            refreshQueueEmptyState();
            pushSharedPartyQueue(next);
            // Backend: PartyTrackUploader + publishHostSync
        } else {
            // Guest: request only — host approves later (chat backend)
        }
    }

    /** Mirror party queue into FragmentBottomSheetQueue (one surface when party is live). */
    private void pushSharedPartyQueue(@NonNull List<PartyQueueItem> items) {
        if (parentSheetView == null) return;
        List<Song> songs = new ArrayList<>();
        try {
            var tree = SongRepository.getInstance().getCachedSongs();
            for (PartyQueueItem qi : items) {
                Song s = tree != null ? tree.get((int) qi.localSongId) : null;
                if (s != null) songs.add(s);
            }
        } catch (Exception ignored) { }
        parentSheetView.submitPartyQueue(songs, 0);
    }

    private void refreshQueueEmptyState() {
        boolean empty = queueAdapter.getItemCount() == 0;
        if (queueEmpty != null) queueEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (queueRecycler != null) queueRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (queueCount != null) {
            int n = queueAdapter.getItemCount();
            queueCount.setText(n == 1 ? "1 track" : n + " tracks");
        }
    }

    /** Call when party role changes (host/guest). */
    public void setHostMode(boolean host) {
        this.hostMode = host;
        if (queueAdapter != null) applyHostGuestChrome();
    }

    public boolean isSongPickerOpen() {
        return pickerOpen;
    }
}
