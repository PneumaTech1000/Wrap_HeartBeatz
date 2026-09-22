package com.giga.tech1000.heartbeatz.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import com.giga.tech1000.heartbeatz.ui.adapters.PartySongPickerAdapter;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Party chat UI inside the media-player bottom sheet.
 * <p>
 * Song picker expands inside {@code chat_dock_composer_card} above the composer row.
 * Party track queue lives in {@link FragmentBottomSheetQueue} (Queue tab), not here.
 */
@UnstableApi
public class FragmentBottomSheetPartyChat extends Fragment {

    @Nullable
    private BottomSheetView parentSheetView;

    /** Collapsible picker block inside the dock card. */
    @Nullable private View songPickerLayout;
    @Nullable private MaterialButton btnSongPicker;
    @Nullable private MaterialButton btnSongPickerClose;
    @Nullable private EditText searchField;
    @Nullable private RecyclerView pickerRecycler;
    @Nullable private TextView pickerTitle;

    private PartySongPickerAdapter pickerAdapter;

    /** Host-side ordered list pushed to the shared Queue tab (UI until Firebase). */
    private final List<Song> partyTracks = new ArrayList<>();

    private boolean pickerOpen;
    private boolean hostMode = true;
    @Nullable private View noPartyOverlay;

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

        songPickerLayout = view.findViewById(R.id.party_chat_song_picker_layout);
        btnSongPicker = view.findViewById(R.id.btn_party_song_picker);
        btnSongPickerClose = view.findViewById(R.id.btn_party_song_picker_close);
        searchField = view.findViewById(R.id.party_song_picker_search);
        pickerRecycler = view.findViewById(R.id.party_song_picker_recycler);
        pickerTitle = view.findViewById(R.id.party_song_picker_title);
        noPartyOverlay = view.findViewById(R.id.party_chat_no_party);

        setupSongPicker();
        updateNoPartyOverlay();

        resolveHostMode();
        applyHostGuestChrome();
        loadLibraryIntoPicker();
    }

    private void setupSongPicker() {
        pickerAdapter = new PartySongPickerAdapter();
        if (pickerRecycler != null) {
            pickerRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
            pickerRecycler.setAdapter(pickerAdapter);
        }
        pickerAdapter.setListener(this::onSongPicked);

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
                    if (pickerAdapter != null) {
                        pickerAdapter.filter(s != null ? s.toString() : "");
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        if (songPickerLayout != null) {
            songPickerLayout.setVisibility(View.GONE);
            songPickerLayout.setAlpha(0f);
        }
    }

    private void updateNoPartyOverlay() {
        boolean inParty = false;
        try {
            PartySession session = HeartBeatzApp.container(requireContext()).partySession();
            inParty = session.isInParty();
        } catch (Exception ignored) { }
        if (noPartyOverlay != null) {
            noPartyOverlay.setVisibility(inParty ? View.GONE : View.VISIBLE);
        }
        // Disable dock while not in party
        if (btnSongPicker != null) btnSongPicker.setEnabled(inParty);
        View dock = getView() != null ? getView().findViewById(R.id.chat_dock_composer_card) : null;
        if (dock != null) dock.setAlpha(inParty ? 1f : 0.4f);
        if (dock != null) dock.setEnabled(inParty);
    }

    private void resolveHostMode() {
        try {
            AppContainer c = HeartBeatzApp.container(requireContext());
            PartySession session = c.partySession();
            if (session.isGuest()) {
                hostMode = false;
            } else if (session.isHosting()) {
                hostMode = true;
            } else {
                // Not in party — preview as host chrome
                hostMode = true;
            }
        } catch (Exception ignored) {
            hostMode = true;
        }
    }

    private void applyHostGuestChrome() {
        if (pickerTitle != null) {
            pickerTitle.setText(hostMode ? "Add to party" : "Request a track");
        }
    }

    private void loadLibraryIntoPicker() {
        try {
            SongRepository.getInstance().getSongs().observe(getViewLifecycleOwner(), tree -> {
                List<Song> songs = new ArrayList<>();
                if (tree != null) {
                    songs.addAll(tree.values());
                }
                if (pickerAdapter != null) {
                    pickerAdapter.submit(songs);
                }
            });
        } catch (Exception e) {
            if (pickerAdapter != null) {
                pickerAdapter.submit(new ArrayList<>());
            }
        }
    }

    private void toggleSongPicker() {
        if (pickerOpen) {
            collapseSongPicker();
        } else {
            expandSongPicker();
        }
    }

    /** Expands picker inside the dock card, above the composer row. */
    public void expandSongPicker() {
        if (songPickerLayout == null || pickerOpen) return;
        pickerOpen = true;
        songPickerLayout.setVisibility(View.VISIBLE);
        songPickerLayout.setAlpha(0f);
        songPickerLayout.setTranslationY(40f);
        songPickerLayout.animate()
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
        if (songPickerLayout == null || !pickerOpen) return;
        pickerOpen = false;
        songPickerLayout.animate()
                .alpha(0f)
                .translationY(40f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (songPickerLayout != null) {
                            songPickerLayout.setVisibility(View.GONE);
                            songPickerLayout.animate().setListener(null);
                        }
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
            // Host: append to party track list → shared Queue tab
            partyTracks.add(song);
            if (parentSheetView != null) {
                parentSheetView.submitPartyQueue(new ArrayList<>(partyTracks), partyTracks.size() - 1);
            }
            // Backend later: upload + Firebase queue/sync
        }
        // Guest: song request via chat backend later
    }

    /** Call when party role changes (host/guest). */
    public void setHostMode(boolean host) {
        this.hostMode = host;
        applyHostGuestChrome();
    }

    public boolean isSongPickerOpen() {
        return pickerOpen;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNoPartyOverlay();
        resolveHostMode();
        applyHostGuestChrome();
    }

    /** Clear local party track buffer when leaving party. */
    public void clearPartyTracks() {
        partyTracks.clear();
        if (parentSheetView != null) {
            parentSheetView.clearPartyQueueMode();
        }
    }
}
