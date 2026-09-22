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
import com.giga.tech1000.heartbeatz.architecture.session.PartySession;
import com.giga.tech1000.heartbeatz.ui.adapters.PartySongPickerAdapter;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Party chat inside the media-player bottom sheet.
 * Observes {@link PartySession} so the UI unlocks when host/guest party becomes live.
 */
@UnstableApi
public class FragmentBottomSheetPartyChat extends Fragment {

    @Nullable private BottomSheetView parentSheetView;

    @Nullable private View songPickerLayout;
    @Nullable private MaterialButton btnSongPicker;
    @Nullable private MaterialButton btnSongPickerClose;
    @Nullable private EditText searchField;
    @Nullable private EditText chatInput;
    @Nullable private MaterialButton btnSend;
    @Nullable private RecyclerView pickerRecycler;
    @Nullable private TextView pickerTitle;
    @Nullable private View noPartyOverlay;
    @Nullable private View dockCard;

    @Nullable private TextView headerTitle;
    @Nullable private TextView headerSubtitle;
    @Nullable private TextView headerLive;
    @Nullable private TextView headerGuestCount;

    private PartySongPickerAdapter pickerAdapter;
    private final List<Song> partyTracks = new ArrayList<>();

    private boolean pickerOpen;
    private boolean hostMode = true;
    private boolean inParty;

    @Nullable private PartySession partySession;

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
        dockCard = view.findViewById(R.id.chat_dock_composer_card);
        chatInput = view.findViewById(R.id.chat_input_text_field);
        btnSend = view.findViewById(R.id.btn_send_chat);

        headerTitle = view.findViewById(R.id.chat_header_title);
        headerSubtitle = view.findViewById(R.id.chat_header_subtitle);
        headerLive = view.findViewById(R.id.chat_header_live);
        headerGuestCount = view.findViewById(R.id.chat_header_guest_count);

        setupSongPicker();
        loadLibraryIntoPicker();
        observePartySession();
        refreshPartyUi(null, null);
    }

    private void observePartySession() {
        try {
            partySession = HeartBeatzApp.container(requireContext()).partySession();
        } catch (Exception e) {
            partySession = null;
            return;
        }

        PartySession session = partySession;
        // Host creates party
        session.getHostedParty().observe(getViewLifecycleOwner(), host -> refreshPartyUi(host, null));
        // Guest joins
        session.getConnectedHost().observe(getViewLifecycleOwner(), host -> refreshPartyUi(null, host));
        session.isGuestAuthenticated().observe(getViewLifecycleOwner(), auth -> {
            if (Boolean.TRUE.equals(auth)) {
                refreshPartyUi(null, session.getConnectedHost().getValue());
            } else if (!session.isHosting()) {
                refreshPartyUi(null, null);
            }
        });
        session.getGuestCount().observe(getViewLifecycleOwner(), count -> {
            if (headerGuestCount != null && count != null) {
                headerGuestCount.setText(String.valueOf(count));
            }
        });
        session.getConnectedGuests().observe(getViewLifecycleOwner(), guests -> {
            if (headerGuestCount != null && guests != null) {
                headerGuestCount.setText(String.valueOf(guests.size()));
            }
        });
    }

    /**
     * Recompute in-party / host mode and update chrome.
     * @param hosted preferred host party snapshot (may be null)
     * @param connected preferred guest host snapshot (may be null)
     */
    private void refreshPartyUi(@Nullable PartyHost hosted, @Nullable PartyHost connected) {
        PartySession session = partySession;
        boolean hosting = session != null && session.isHosting();
        boolean guestAuthed = session != null
                && Boolean.TRUE.equals(session.isGuestAuthenticated().getValue());

        // Chat is live only when hosting or fully joined as guest
        inParty = hosting || guestAuthed;
        hostMode = hosting;

        PartyHost active = null;
        if (hosting) {
            active = hosted != null ? hosted
                    : (session != null ? session.getHostedParty().getValue() : null);
        } else if (guestAuthed) {
            active = connected != null ? connected
                    : (session != null ? session.getConnectedHost().getValue() : null);
        }

        applyPartyChrome(active);
    }

    private void applyPartyChrome(@Nullable PartyHost party) {
        if (noPartyOverlay != null) {
            noPartyOverlay.setVisibility(inParty ? View.GONE : View.VISIBLE);
        }

        boolean enableDock = inParty;
        if (dockCard != null) {
            dockCard.setAlpha(enableDock ? 1f : 0.45f);
            dockCard.setEnabled(enableDock);
        }
        if (btnSongPicker != null) btnSongPicker.setEnabled(enableDock);
        if (chatInput != null) {
            chatInput.setEnabled(enableDock);
            chatInput.setFocusable(enableDock);
            chatInput.setFocusableInTouchMode(enableDock);
        }
        if (btnSend != null) btnSend.setEnabled(enableDock);

        if (headerTitle != null) {
            if (inParty && party != null && party.getPartyName() != null) {
                headerTitle.setText(party.getPartyName());
            } else if (inParty) {
                headerTitle.setText(hostMode ? "Your party" : "Party chat");
            } else {
                headerTitle.setText("Party chat");
            }
        }
        if (headerSubtitle != null) {
            if (inParty && party != null && party.getPartyId() != null) {
                String id = party.getPartyId();
                String shortId = id.length() > 8 ? id.substring(id.length() - 8) : id;
                headerSubtitle.setText("#" + shortId);
            } else {
                headerSubtitle.setText(inParty ? (hostMode ? "HOST" : "GUEST") : "—");
            }
        }
        if (headerLive != null) {
            if (!inParty) {
                headerLive.setText("•  Offline");
            } else {
                headerLive.setText(hostMode ? "•  HOST · LIVE" : "•  GUEST · LIVE");
            }
        }
        if (pickerTitle != null) {
            pickerTitle.setText(hostMode ? "Add to party" : "Request a track");
        }

        if (!inParty && pickerOpen) {
            collapseSongPicker();
        }
        if (!inParty) {
            partyTracks.clear();
        }
    }

    private void setupSongPicker() {
        pickerAdapter = new PartySongPickerAdapter();
        if (pickerRecycler != null) {
            pickerRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
            pickerRecycler.setAdapter(pickerAdapter);
        }
        pickerAdapter.setListener(this::onSongPicked);

        if (btnSongPicker != null) {
            btnSongPicker.setOnClickListener(v -> {
                if (!inParty) return;
                toggleSongPicker();
            });
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

    public void expandSongPicker() {
        if (songPickerLayout == null || pickerOpen || !inParty) return;
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
        if (btnSongPicker != null) btnSongPicker.setSelected(true);
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
        if (btnSongPicker != null) btnSongPicker.setSelected(false);
        if (searchField != null) searchField.setText("");
    }

    private void onSongPicked(@NonNull Song song) {
        if (!inParty) return;
        collapseSongPicker();
        if (hostMode) {
            partyTracks.add(song);
            if (parentSheetView != null) {
                parentSheetView.submitPartyQueue(new ArrayList<>(partyTracks), partyTracks.size() - 1);
            }
        }
        // Guest request → chat backend later
    }

    public void setHostMode(boolean host) {
        this.hostMode = host;
        applyPartyChrome(null);
    }

    public boolean isSongPickerOpen() {
        return pickerOpen;
    }

    public void clearPartyTracks() {
        partyTracks.clear();
        if (parentSheetView != null) parentSheetView.clearPartyQueueMode();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (partySession != null) {
            refreshPartyUi(partySession.getHostedParty().getValue(),
                    partySession.getConnectedHost().getValue());
        } else {
            observePartySession();
            if (partySession != null) {
                refreshPartyUi(partySession.getHostedParty().getValue(),
                        partySession.getConnectedHost().getValue());
            }
        }
    }
}
