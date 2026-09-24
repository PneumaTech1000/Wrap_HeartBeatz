package com.giga.tech1000.heartbeatz.ui.fragments;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.giga.tech1000.heartbeatz.CustomScannerActivity;
import com.giga.tech1000.heartbeatz.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;
import com.giga.tech1000.heartbeatz.ui.adapters.GuestListAdapter;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PartyViewModel;
import com.giga.tech1000.heartbeatz.views.knobs.DiscoveryIndicatorManager;
import com.giga.tech1000.heartbeatz.views.knobs.PartyPulseView;
import com.giga.tech1000.heartbeatz.utils.QrCodeUtil;
import com.giga.tech1000.heartbeatz.ui.PartyConnectionStatus;
import com.giga.tech1000.heartbeatz.utils.PartyAnalytics;
import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.utils.interfaces.DisplayMarginCallback;
import com.giga.tech1000.utils.interfaces.OnBackPressedHandler;
import com.giga.tech1000.utils.interfaces.PartyModeUICallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.List;

public class FragmentParty extends Fragment implements PartyModeUICallback, OnBackPressedHandler, DisplayMarginCallback {

    private PartyViewModel viewModel;
    private PartyState currentState = PartyState.IDLE;
    /** Prevent QR tab from re-selecting on every LiveData emission (causes tab bounce / heat). */
    private boolean hostQrAutoOpened;


    private ConstraintLayout partyRoot;

    private View idleView;
    private View searchingView;
    private View setupRequiredView;
    private View connectedView;
    private TabLayout partyTabs;
    private View qrContainer;
    private RecyclerView guestsRecycler;
    private GuestListAdapter guestListAdapter;
    private LinearLayout tvNoGuests;
    private ImageView ivHostQrCode;
    private TextView tvPartyPin;
    private android.widget.ImageButton btnTogglePartyPin;
    private boolean pinRevealed;
    private boolean guestMetaBannerShown;
    @Nullable private androidx.appcompat.app.AlertDialog partyListDialog;
    @Nullable private androidx.appcompat.app.AlertDialog pinEntryDialog;
    @Nullable private androidx.appcompat.app.AlertDialog guestLoadingDialog;
    private boolean guestPlayerExpanded;


    private TextView partyTitle;
    private TextView deviceCount;
    private Chip partyStatus;
    private Chip partyBadge;
    private MaterialButton btnShareParty;
    private TextView tvPartyLink;
    private View partySnackbar;
    private TextView tvPartySnackbarMessage;
    private MaterialButton btnPartySnackbarAction;
    private TextView tvTrackProgress;

    // for idle view page
    private MaterialCardView cardCreateParty, cardJoinParty;

    // for searching view page
    private PartyPulseView pulseView;
    private TextView pulseInfoText;

    // for floating party found
    private FrameLayout discoveryOverlay;

    // From FragmentParty (Now Playing Card)
    private View cardNowPlaying;
    private LinearProgressIndicator progressSync;
    private TextView tvPartySongTitle;
    private TextView tvPartyArtist;
    private ImageView ivPartyAlbumArt;

    private DiscoveryIndicatorManager discoveryIndicatorManager;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if (result.getContents() == null) {
                    // Check if we came back with a manual join intent
                    if (result.getOriginalIntent() != null && "MANUAL_JOIN".equals(result.getOriginalIntent().getStringExtra("ACTION"))) {
                        if (!isWifiEnabled()) {
                            Toast.makeText(requireContext(), "Please enable Wi-Fi to search for parties", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!requirePartyAuth("search for parties")) return;
                        viewModel.startDiscovery();
                    } else {
                        Toast.makeText(requireContext(), "Cancelled", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    handleScannedData(result.getContents());
                }
            });

    public FragmentParty() {
        // Required empty public constructor
    }

    public PartyModeUICallback getCallback() {
        return this;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_party, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PartyViewModel.class);

        // Initialize Views
        partyRoot = view.findViewById(R.id.party_root);
        partySnackbar = view.findViewById(R.id.partySnackbar);
        tvPartySnackbarMessage = view.findViewById(R.id.tvPartySnackbarMessage);
        btnPartySnackbarAction = view.findViewById(R.id.btnPartySnackbarAction);
        idleView = view.findViewById(R.id.welcome_page);
        cardCreateParty = view.findViewById(R.id.card_create_party);
        cardJoinParty = view.findViewById(R.id.card_join_party);

        searchingView = view.findViewById(R.id.searching_page);
        setupRequiredView = view.findViewById(R.id.setup_required_page);
        pulseView = view.findViewById(R.id.partyPulse);
        pulseInfoText = view.findViewById(R.id.pulse_info_text);

        discoveryOverlay = view.findViewById(R.id.discovery_overlay);
        connectedView = view.findViewById(R.id.connected_party);

        progressSync = view.findViewById(R.id.progressSync);

        // Connected View Components
        partyTabs = connectedView.findViewById(R.id.partyTabs);
        qrContainer = connectedView.findViewById(R.id.qrContainer);
        guestsRecycler = connectedView.findViewById(R.id.guestsRecycler);
        guestListAdapter = new GuestListAdapter();
        guestsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        guestsRecycler.setAdapter(guestListAdapter);

        guestListAdapter.setOnGuestClickListener(name -> {
            // name is display name; ViewModel/repo resolve uid when kicking

            if (viewModel.getPartyState().getValue() == PartyState.HOSTING) {
                showHostActionDialog(name);
            }
        });

        tvNoGuests = connectedView.findViewById(R.id.tvNoGuests);
        ivHostQrCode = connectedView.findViewById(R.id.ivHostQrCode);
        tvPartyPin = connectedView.findViewById(R.id.tvPartyPin);
        btnTogglePartyPin = connectedView.findViewById(R.id.btnTogglePartyPin);
        if (btnTogglePartyPin != null) {
            btnTogglePartyPin.setOnClickListener(v -> {
                pinRevealed = !pinRevealed;
                refreshPinLabel();
            });
        }

        partyTitle = connectedView.findViewById(R.id.partyTitle);
        deviceCount = connectedView.findViewById(R.id.deviceCount);
        partyStatus = connectedView.findViewById(R.id.partyStatus);
        partyBadge = connectedView.findViewById(R.id.partyBadgeChip);
        btnShareParty = connectedView.findViewById(R.id.btnShareParty);
        tvPartyLink = connectedView.findViewById(R.id.tvPartyLink);

        setupPartyTabs();

        cardNowPlaying = view.findViewById(R.id.cardNowPlaying);
        tvPartySongTitle = view.findViewById(R.id.tvPartySongTitle);
        tvPartyArtist = view.findViewById(R.id.tvPartyArtist);
        ivPartyAlbumArt = view.findViewById(R.id.ivPartyAlbumArt);
        tvTrackProgress = view.findViewById(R.id.tvTrackProgress);

        discoveryIndicatorManager = new DiscoveryIndicatorManager(discoveryOverlay);

        // Set Click Listeners
        cardCreateParty.setOnClickListener(v -> handleCreateClick());
        cardJoinParty.setOnClickListener(v -> handleJoinClick());

        // Host QR code button is inside the connected_party layout
        View btnInvite = view.findViewById(R.id.btnInviteContainer);
        if (btnInvite != null) {
            btnInvite.setOnClickListener(v -> {
                if (partyTabs != null) {
                    TabLayout.Tab qrTab = partyTabs.getTabAt(1);
                    if (qrTab != null) qrTab.select();
                }
            });
        }

        if (btnShareParty != null) {
            btnShareParty.setOnClickListener(v -> {
                String partyId = viewModel.getPartyId();
                String partyName = viewModel.getPartyName();
                String partyPin = viewModel.getPartyPin();
                if (partyId == null || partyId.isEmpty()) {
                    Toast.makeText(requireContext(), "Party not ready to share yet", Toast.LENGTH_SHORT).show();
                    return;
                }
                String inviteText = QrCodeUtil.formatPartyShareText(partyId, partyName, partyPin);
                PartyAnalytics.inviteShared();
                sharePartyLink(inviteText);
            });
        }

        View btnCloseParty = view.findViewById(R.id.btnCloseParty);
        if (btnCloseParty != null) {
            btnCloseParty.setOnClickListener(v -> {
                String title = viewModel.getPartyState().getValue() == PartyState.HOSTING ? "End Party?" : "Leave Party?";
                String message = viewModel.getPartyState().getValue() == PartyState.HOSTING ?
                        "This will disconnect all guests and stop the stream." :
                        "You will stop receiving the audio stream.";

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("Confirm", (dialog, which) -> viewModel.leaveParty())
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // Setup Required Listeners
        View btnOpenHotspot = view.findViewById(R.id.btn_open_hotspot);
        if (btnOpenHotspot != null) {
            btnOpenHotspot.setOnClickListener(v -> openHotspotSettings());
        }

        View btnOpenWifi = view.findViewById(R.id.btn_open_wifi);
        if (btnOpenWifi != null) {
            btnOpenWifi.setOnClickListener(v -> {
                WifiManager wifi = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startActivity(new android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI));
                    } else {
                        wifi.setWifiEnabled(true);
                    }
                }
            });
        }

        View btnSetupBack = view.findViewById(R.id.btn_setup_back);
        if (btnSetupBack != null) {
            btnSetupBack.setOnClickListener(v -> viewModel.leaveParty());
        }

        observeViewModel();
        viewModel.setUiCallback(this);
        consumePendingPartyInvite();

        // Initial State
        renderState(viewModel.getPartyState().getValue() != null ? viewModel.getPartyState().getValue() : PartyState.IDLE);
    }

    private void observeViewModel() {
        viewModel.getPartyState().observe(getViewLifecycleOwner(), state -> {
            if (state == PartyState.IDLE) {
                hostQrAutoOpened = false;
                guestPlayerExpanded = false;
                dismissPartyDialogs();
                dismissGuestLoadingDialog();
            }
            if (state == PartyState.CONNECTING || state == PartyState.JOINED) {
                dismissPartyDialogs();
            }
            if (state == PartyState.IDLE && currentState == PartyState.CONNECTING) {
                Toast.makeText(requireContext(), "Connection failed or host disconnected", Toast.LENGTH_SHORT).show();
            }
            renderState(state);
            if (state == PartyState.JOINED) {
                onGuestJoinedUi();
            }
        });

        // This ensures the host-side UI (QR code, tab switch) updates when the session is ready
        viewModel.getHostedParty().observe(getViewLifecycleOwner(), host -> {
            if (host != null) {
                onPartyCreated(host);
                updateQrDisplay();
            }
        });

        // Also observe PlaybackState's host to ensure fast reactions if available
        viewModel.getPlaybackState().getPartyHost().observe(getViewLifecycleOwner(), host -> {
            if (host != null && currentState != PartyState.HOSTING) {
                onPartyCreated(host);
                updateQrDisplay();
            }
        });

        viewModel.getDiscoveredParties().observe(getViewLifecycleOwner(), hosts -> {
            if (hosts != null && !hosts.isEmpty()) {
                PartyState state = viewModel.getPartyState().getValue();
                if (state == PartyState.SEARCHING || state == PartyState.FOUND) {
                    showPartySelectionDialog(hosts);
                }
            }
        });

        viewModel.getCurrentSong().observe(getViewLifecycleOwner(), metadata -> {
            if (metadata != null) {
                if (tvPartySongTitle != null) tvPartySongTitle.setText(metadata.title);
                if (tvPartyArtist != null) tvPartyArtist.setText(metadata.artist);
                if (ivPartyAlbumArt != null && metadata.albumArt != null) {
                    Glide.with(this)
                            .load(metadata.albumArt)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .placeholder(com.giga.tech1000.icons_pack.R.drawable.music_note_2_24px)
                            .into(ivPartyAlbumArt);
                }
            }
        });

        viewModel.isPlaying().observe(getViewLifecycleOwner(), playing -> {
            updatePlaybackStatus();
        });

        viewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> updatePlaybackProgress());
        viewModel.getCurrentDuration().observe(getViewLifecycleOwner(), duration -> updatePlaybackProgress());

        viewModel.isGuestAuthenticated().observe(getViewLifecycleOwner(), authenticated -> {
            if (Boolean.TRUE.equals(authenticated)) {
                PartyAnalytics.partyJoinSuccess();
            }
            if (authenticated != null && (currentState == PartyState.JOINED || currentState == PartyState.HOSTING)) {
                updateUiContent(currentState);
            }
        });

        viewModel.getGuestCount().observe(getViewLifecycleOwner(), count -> {
            if (deviceCount != null) {
                String deviceCountText = count + (count == 1 ? " Device" : " Devices");
                deviceCount.setText(deviceCountText);
            }
            if (tvNoGuests != null) {
                tvNoGuests.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getGuestList().observe(getViewLifecycleOwner(), names -> {
            if (guestListAdapter != null) {
                guestListAdapter.setGuests(names);
            }
        });

        viewModel.getHandoverRequest().observe(getViewLifecycleOwner(), from -> {
            if (from != null) {
                showHandoverRequestDialog(from);
            }
        });

        viewModel.getPartyError().observe(getViewLifecycleOwner(), error -> {
            if (error == null || error.isEmpty()) return;
            showInlineMessage(error, null, null);
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
        });
    }

    private void openHotspotSettings() {
        Intent intent = new android.content.Intent();
        intent.setClassName("com.android.settings", "com.android.settings.Settings$TetherSettingsActivity");
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            // Fallback to more generic tethering/hotspot actions
            try {
                startActivity(new android.content.Intent("android.settings.WIFI_TETHER_SETTINGS"));
            } catch (Exception e2) {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
            }
        }
    }

    private void setupPartyTabs() {
        if (partyTabs == null) return;
        partyTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    guestsRecycler.setVisibility(View.VISIBLE);
                    qrContainer.setVisibility(View.GONE);
                } else {
                    guestsRecycler.setVisibility(View.GONE);
                    qrContainer.setVisibility(View.VISIBLE);
                    updateQrDisplay();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void updateQrDisplay() {
        String partyId = viewModel.getPartyId();
        String partyName = viewModel.getPartyName();
        String partyPin = viewModel.getPartyPin();
        if (partyId == null || partyId.isEmpty()) return;

        // Always refresh PIN label on main thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::refreshPinLabel);
        } else {
            refreshPinLabel();
        }

        new Thread(() -> {
            String qrContent = QrCodeUtil.formatPartyInvite(partyId, partyName, partyPin);
            Bitmap qrBitmap = QrCodeUtil.generateQrCode(qrContent, 512);
            if (qrBitmap != null && isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (ivHostQrCode != null) {
                        ivHostQrCode.setImageBitmap(qrBitmap);
                    }
                    refreshPinLabel();
                });
            }
        }).start();
    }

    private void refreshPinLabel() {
        if (tvPartyPin == null || viewModel == null) return;
        String pin = viewModel.getPartyPin();
        if (pin == null || pin.isEmpty()) {
            tvPartyPin.setText("PIN: —");
            return;
        }
        if (pinRevealed) {
            tvPartyPin.setText("PIN: " + pin);
        } else {
            // Mask: one bullet per digit
            StringBuilder masked = new StringBuilder("PIN: ");
            for (int i = 0; i < pin.length(); i++) masked.append('•');
            tvPartyPin.setText(masked.toString());
        }
    }

    private void showHostActionDialog(String guestName) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Guest: " + guestName)
                .setItems(new String[]{"Handover Host Control", "Disconnect Guest"}, (dialog, which) -> {
                    if (which == 0) {
                        viewModel.initiateHandover(guestName);
                        Toast.makeText(requireContext(), "Handover request sent to " + guestName, Toast.LENGTH_SHORT).show();
                    } else {
                        // Logic for disconnecting specific guest could be added to viewModel
                        Toast.makeText(requireContext(), "Disconnecting " + guestName, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showHandoverRequestDialog(String from) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Host Handover Request")
                .setMessage("The current host wants you to take over control of the party. You will become the new host.")
                .setPositiveButton("Accept", (dialog, which) -> viewModel.acceptHandover())
                .setNegativeButton("Decline", (dialog, which) -> viewModel.declineHandover())
                .setCancelable(false)
                .show();
    }

    private void handleJoinClick() {
        if (!requirePartyAuth("join a party")) return;
        if (!hasNearbyPermissions()) {
            Toast.makeText(requireContext(), "Nearby devices permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        startQrScanner();
    }

    private void startQrScanner() {
        ScanOptions scanOptions = new ScanOptions();
        scanOptions.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        scanOptions.setPrompt("Scan a HeartBeatz Party QR Code\n\nTap 'Manual Join' to search nearby");
        scanOptions.setBeepEnabled(false);
        scanOptions.setBarcodeImageEnabled(true);
        scanOptions.setOrientationLocked(false);
        // We use a custom activity to add the "Manual Join" button
        scanOptions.setCaptureActivity(CustomScannerActivity.class);
        barcodeLauncher.launch(scanOptions);
    }

    private void handleScannedData(String data) {
        if (data == null) return;

        if (data.startsWith("WIFI:")) {
            Toast.makeText(requireContext(), "Wi-Fi QR detected. Please connect to the Wi-Fi manually and try again.", Toast.LENGTH_LONG).show();
            return;
        }

        QrCodeUtil.PartyInvite invite = QrCodeUtil.parseInvite(data);
        if (invite == null) {
            Toast.makeText(requireContext(), "Invalid QR Code", Toast.LENGTH_SHORT).show();
            return;
        }

        PartyAnalytics.partyJoinAttempt(false);
        PartyHost host = new PartyHost();
        host.setPartyId(invite.partyId);
        host.setPartyName(invite.partyName != null ? invite.partyName : "Party");
        if (invite.ipAddress != null) host.setIpAddress(invite.ipAddress);
        if (invite.port > 0) host.setPort(invite.port);
        host.setPin(invite.pin != null ? invite.pin : "");
        host.setPasswordProtected(invite.pin != null && !invite.pin.isEmpty());

        String pin = invite.pin != null ? invite.pin : "";
        if (pin.isEmpty() && !invite.legacyLan) {
            showPinEntryDialog(host);
        } else {
            viewModel.joinParty(host, pin);
        }
    }

    private void showHostQrCode() {
        String partyId = viewModel.getPartyId();
        String partyName = viewModel.getPartyName();
        String partyPin = viewModel.getPartyPin();
        if (partyId == null || partyId.isEmpty()) {
            Toast.makeText(requireContext(), "Host information not available yet", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            String qrContent = QrCodeUtil.formatPartyInvite(partyId, partyName, partyPin);
            Bitmap qrBitmap = QrCodeUtil.generateQrCode(qrContent, 512);
            String qrString = QrCodeUtil.formatPartyInvite(partyId, partyName, null);
            if (qrBitmap != null && isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    ImageView imageView = new ImageView(requireContext());
                    imageView.setImageBitmap(qrBitmap);
                    int padding = (int) (16 * getResources().getDisplayMetrics().density);
                    imageView.setPadding(padding, padding, padding, padding);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Party QR Code")
                            .setMessage("Guests scan this to join " + qrString)
                                            .setView(imageView)
                                            .setPositiveButton("Close", null)
                                            .show();
                });
            }
        }).start();
    }


    /**
     * Local library playback does not require an account.
     * Party host/join/discover require Firebase Auth ({@code auth != null} on RTDB rules).
     */
    private void consumePendingPartyInvite() {
        if (getActivity() == null) return;
        android.content.Intent intent = requireActivity().getIntent();
        if (intent == null) return;
        String partyId = intent.getStringExtra("party_invite_id");
        if (partyId == null || partyId.isEmpty()) return;
        String pin = intent.getStringExtra("party_invite_pin");
        String name = intent.getStringExtra("party_invite_name");
        intent.removeExtra("party_invite_id");
        intent.removeExtra("party_invite_pin");
        intent.removeExtra("party_invite_name");

        if (!requirePartyAuth("join a party")) return;

        PartyHost host = new PartyHost();
        host.setPartyId(partyId);
        host.setPartyName(name != null ? name : "Party");
        host.setPin(pin != null ? pin : "");
        host.setPasswordProtected(pin != null && !pin.isEmpty());
        if (pin == null || pin.isEmpty()) {
            showPinEntryDialog(host);
        } else {
            viewModel.joinParty(host, pin);
        }
    }

    private boolean requirePartyAuth(@NonNull String actionLabel) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return true;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign in required")
                .setMessage("Sign in to " + actionLabel + ". Playing music from your library does not need an account.")
                .setPositiveButton("Sign in", (d, w) ->
                        startActivity(new Intent(requireContext(), LoginActivity.class)))
                .setNegativeButton("Cancel", null)
                .show();
        return false;
    }

    private void handleCreateClick() {
        if (!requirePartyAuth("create a party")) return;
        if (!hasNearbyPermissions()) {
            Toast.makeText(requireContext(), "Nearby devices permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        showCreatePartyDialog();
    }

    private void showCreatePartyDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_party_name, null);
        TextInputEditText nameEditText = dialogView.findViewById(R.id.partyNameEditText);
        TextInputEditText pinEditText = dialogView.findViewById(R.id.partyPinEditText);
        nameEditText.setText(R.string.heartbeatz_party);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Name your Party")
                .setView(dialogView)
                .setPositiveButton("Create", (dialog, which) -> {
                    String nameText = nameEditText.getText().toString();
                    String partyName = (!nameText.isEmpty()) ? nameText.trim() : "HeartBeatz Party";
                    String pin = pinEditText.getText().toString();
                    PartyAnalytics.partyCreated(pin != null && !pin.isEmpty());
                    viewModel.createParty(partyName, pin);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void dismissPartyDialogs() {
        try {
            if (partyListDialog != null && partyListDialog.isShowing()) partyListDialog.dismiss();
        } catch (Exception ignored) { }
        partyListDialog = null;
        try {
            if (pinEntryDialog != null && pinEntryDialog.isShowing()) pinEntryDialog.dismiss();
        } catch (Exception ignored) { }
        pinEntryDialog = null;
    }

    private void showPartySelectionDialog(List<PartyHost> hosts) {
        if (hosts == null || hosts.isEmpty()) return;
        dismissPartyDialogs();

        String[] names = new String[hosts.size()];
        for (int i = 0; i < hosts.size(); i++) {
            names[i] = hosts.get(i).partyName != null ? hosts.get(i).partyName : "Party";
        }

        partyListDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select a Party")
                .setItems(names, (dialog, which) -> {
                    dialog.dismiss();
                    partyListDialog = null;
                    showPinEntryDialog(hosts.get(which));
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    partyListDialog = null;
                    viewModel.leaveParty();
                })
                .setOnCancelListener(d -> {
                    partyListDialog = null;
                    viewModel.leaveParty();
                })
                .setCancelable(true)
                .create();
        partyListDialog.show();
    }

    private void showPinEntryDialog(PartyHost host) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_party_name, null);
        TextInputLayout nameLayout = dialogView.findViewById(R.id.partyNameInputLayout);
        nameLayout.setVisibility(View.GONE);
        TextInputEditText pinEditText = dialogView.findViewById(R.id.partyPinEditText);

        pinEntryDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Enter Party PIN")
                .setView(dialogView)
                .setPositiveButton("Join", (dialog, which) -> {
                    String pin = pinEditText.getText() != null
                            ? pinEditText.getText().toString() : "";
                    dialog.dismiss();
                    pinEntryDialog = null;
                    dismissPartyDialogs();
                    viewModel.joinParty(host, pin);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    pinEntryDialog = null;
                })
                .setOnDismissListener(d -> pinEntryDialog = null)
                .create();
        pinEntryDialog.show();
    }

    private void renderState(PartyState newState) {
        if (newState == null) return;
        if (newState == currentState) {
            updateUiContent(newState);
            return;
        }

        View out = getViewForState(currentState);
        View in = getViewForState(newState);

        if (out != in) {
            animateStateTransition(out, in);
        }

        if (newState == PartyState.JOINED || newState == PartyState.HOSTING) {
            if (cardNowPlaying != null) cardNowPlaying.setVisibility(View.VISIBLE);
        } else {
            if (cardNowPlaying != null) cardNowPlaying.setVisibility(View.GONE);
        }

        currentState = newState;
        updateUiContent(newState);
    }

    private void updateUiContent(PartyState state) {
        switch (state) {
            case SEARCHING:
            case CREATING:
            case CONNECTING:
                pulseView.start();
                if (state == PartyState.SEARCHING)
                    pulseInfoText.setText(R.string.searching_for_parties);
                else if (state == PartyState.CREATING)
                    pulseInfoText.setText(R.string.creating_party);
                else pulseInfoText.setText(R.string.connecting_to_party);
                break;
            case FOUND:
                pulseView.start();
                pulseInfoText.setText(R.string.party_found);
                break;
            case JOINED:
            case HOSTING:
                pulseView.stop();
                if (state == PartyState.JOINED) {
                    pulseInfoText.setText(R.string.connected_to_party);
                    if (partyTitle != null) partyTitle.setText(R.string.party_joined);
                    // Do not keep "Streaming audio" forever — real metadata comes from sync
                    if (tvPartySongTitle != null)
                        tvPartySongTitle.setText(R.string.waiting_for_music);
                    if (tvPartyArtist != null)
                        tvPartyArtist.setText(R.string.connected_to_party);
                    if (progressSync != null) progressSync.setVisibility(View.GONE);

                    if (partyTabs != null) {
                        partyTabs.setVisibility(View.GONE); // guests: no host QR/guests tabs
                    }
                } else {
                    String partyName = getString(R.string.hosting_party) + viewModel.getPartyName();
                    pulseInfoText.setText(partyName);
                    if (partyTitle != null) partyTitle.setText(R.string.your_party);
                    if (partyTabs != null) partyTabs.setVisibility(View.VISIBLE);
                    if (tvPartySongTitle != null)
                        tvPartySongTitle.setText(R.string.streaming_active);
                    if (tvPartyArtist != null) tvPartyArtist.setText(R.string.you_are_the_host);
                    if (progressSync != null) progressSync.setVisibility(View.GONE);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (currentState == PartyState.HOSTING || currentState == PartyState.JOINED) {
                            if (cardNowPlaying != null) cardNowPlaying.setVisibility(View.GONE);
                        }
                    }, 3000);
                }

                updatePartyHeader(state);
                updatePlaybackStatus();
                updatePlaybackProgress();
                updateQrDisplay();
                break;
            case IDLE:
                pulseView.stop();
                break;
            case ERROR:
                pulseView.stop();
                pulseInfoText.setText(R.string.something_went_wrong_reconnecting);
                break;
        }
    }

    private View getViewForState(PartyState state) {
        return switch (state) {
            case SEARCHING, CREATING, FOUND, CONNECTING -> searchingView;
            case SETUP_REQUIRED -> setupRequiredView;
            case JOINED, HOSTING -> connectedView;
            default -> idleView;
        };
    }

    private void animateStateTransition(View out, View in) {
        if (out != null) {
            out.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                out.setVisibility(View.GONE);
                if (in != null) {
                    in.setAlpha(0f);
                    in.setVisibility(View.VISIBLE);
                    in.animate().alpha(1f).setDuration(220).start();
                }
            }).start();
        } else if (in != null) {
            in.setAlpha(0f);
            in.setVisibility(View.VISIBLE);
            in.animate().alpha(1f).setDuration(220).start();
        }
    }

    private void updatePartyHeader(PartyState state) {
        if (partyStatus != null) {
            switch (state) {
                case HOSTING -> partyStatus.setText(R.string.party_status_hosting);
                case JOINED -> {
                    Boolean playing = viewModel.isPlaying().getValue();
                    if (Boolean.TRUE.equals(playing)) {
                        partyStatus.setText(R.string.party_status_playing);
                    } else {
                        partyStatus.setText(R.string.party_status_syncing);
                    }
                }
                default -> partyStatus.setText(R.string.party_status_offline);
            }
        }
        if (partyBadge != null) {
            switch (state) {
                case HOSTING -> partyBadge.setText("HOST");
                case JOINED -> partyBadge.setText("CONNECTED");
                default -> partyBadge.setText("IDLE");
            }
        }
    }

    private void updatePlaybackStatus() {
        if (tvPartyArtist == null || progressSync == null || tvTrackProgress == null) return;
        Boolean playing = viewModel.isPlaying().getValue();
        PartyState state = currentState;
        if (state == PartyState.JOINED) {
            if (Boolean.TRUE.equals(playing)) {
                tvPartyArtist.setText(R.string.party_status_playing);
            } else {
                tvPartyArtist.setText(R.string.party_status_syncing);
            }
        } else if (state == PartyState.HOSTING) {
            tvPartyArtist.setText(R.string.you_are_the_host);
        }

        Long duration = viewModel.getCurrentDuration().getValue();
        Long position = viewModel.getCurrentPosition().getValue();
        if (duration != null && duration > 0 && position != null) {
            progressSync.setIndeterminate(false);
            int progress = (int) Math.min(100, (position * 100) / duration);
            progressSync.setProgress(progress);
            tvTrackProgress.setVisibility(View.VISIBLE);
        } else {
            progressSync.setIndeterminate(true);
            tvTrackProgress.setVisibility(View.GONE);
        }
    }

    private void updatePlaybackProgress() {
        if (tvTrackProgress == null || progressSync == null) return;
        Long position = viewModel.getCurrentPosition().getValue();
        Long duration = viewModel.getCurrentDuration().getValue();
        if (position == null) position = 0L;
        if (duration == null) duration = 0L;
        if (duration > 0) {
            tvTrackProgress.setText(formatTime(position) + " / " + formatTime(duration));
            progressSync.setIndeterminate(false);
            progressSync.setProgress((int) Math.min(100, (position * 100) / duration));
            tvTrackProgress.setVisibility(View.VISIBLE);
        } else {
            tvTrackProgress.setText(formatTime(position) + " / " + formatTime(duration));
            progressSync.setIndeterminate(true);
            tvTrackProgress.setVisibility(View.GONE);
        }
    }

    private void showInlineMessage(String message, String actionText, Runnable action) {
        if (partySnackbar == null || tvPartySnackbarMessage == null || btnPartySnackbarAction == null)
            return;
        tvPartySnackbarMessage.setText(message);
        if (actionText != null && action != null) {
            btnPartySnackbarAction.setText(actionText);
            btnPartySnackbarAction.setVisibility(View.VISIBLE);
            btnPartySnackbarAction.setOnClickListener(v -> {
                action.run();
                hideInlineMessage();
            });
        } else {
            btnPartySnackbarAction.setVisibility(View.GONE);
        }
        partySnackbar.setVisibility(View.VISIBLE);
        partySnackbar.setAlpha(0f);
        partySnackbar.animate().alpha(1f).setDuration(220).start();
        if (actionText == null) {
            partySnackbar.postDelayed(this::hideInlineMessage, 4500);
        }
    }

    private void hideInlineMessage() {
        if (partySnackbar == null) return;
        partySnackbar.animate().alpha(0f).setDuration(180).withEndAction(() -> partySnackbar.setVisibility(View.GONE)).start();
    }

    private void sharePartyLink(String inviteText) {
        if (inviteText == null || inviteText.isEmpty()) return;
        Context context = requireContext();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("HeartBeatz Party", inviteText);
            clipboard.setPrimaryClip(clip);
        }
        if (tvPartyLink != null) {
            tvPartyLink.setText(R.string.party_link_copied);
            tvPartyLink.setVisibility(View.VISIBLE);
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Join my HeartBeatz party");
        shareIntent.putExtra(Intent.EXTRA_TEXT, inviteText);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_party_message)));
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private boolean hasNearbyPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    @Override
    public void onPartyFoundByClient(PartyHost host) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (discoveryIndicatorManager != null) {
                    discoveryIndicatorManager.showDeviceFound(host.partyName);
                }
            });
        }
    }

    @Override
    public void onPartyCreatedByHost(PartyHost host) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                showInlineMessage(getString(R.string.your_party) + " created and ready to invite guests.", null, null);
            });
        }
    }

    @Override
    public void onConnectionFailed() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                showInlineMessage(getString(R.string.connection_failed_inline), getString(R.string.retry), () -> viewModel.startDiscovery());
            });
        }
    }

    @Override
    public void onDisconnected() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                showInlineMessage("Disconnected from party", getString(R.string.retry), () -> viewModel.startDiscovery());
            });
        }
    }

    private void onPartyCreated(PartyHost host) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (currentState != PartyState.HOSTING) {
                    renderState(PartyState.HOSTING);
                }
                // Only open QR once per party — do not yank user back from Guests tab
                if (!hostQrAutoOpened && partyTabs != null) {
                    hostQrAutoOpened = true;
                    TabLayout.Tab qrTab = partyTabs.getTabAt(1);
                    if (qrTab != null) qrTab.select();
                }
            });
        }
    }

    private void onGuestJoinedUi() {
        guestMetaBannerShown = false;
        guestPlayerExpanded = false;
        dismissPartyDialogs();

        if (cardNowPlaying != null) cardNowPlaying.setVisibility(android.view.View.VISIBLE);
        if (progressSync != null) progressSync.setVisibility(android.view.View.VISIBLE);
        if (tvPartySongTitle != null) tvPartySongTitle.setText(R.string.streaming_audio);
        if (tvPartyArtist != null) tvPartyArtist.setText(R.string.syncing_with_host);

        showGuestLoadingDialog("Preparing party track…");

        try {
            com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp.container(requireContext())
                    .partyLiveBridge()
                    .getLatestSync()
                    .observe(getViewLifecycleOwner(), sync -> {
                        if (sync == null) return;
                        if (tvPartySongTitle != null && sync.title != null) {
                            tvPartySongTitle.setText(sync.title);
                        }
                        if (tvPartyArtist != null) {
                            String artist = sync.artist != null ? sync.artist : "";
                            if (sync.album != null && !sync.album.isEmpty()) {
                                artist = artist.isEmpty() ? sync.album : artist + " · " + sync.album;
                            }
                            if (sync.durationMs > 0) {
                                long sec = (sync.durationMs / 1000) % 60;
                                long min = (sync.durationMs / 1000) / 60;
                                artist = (artist.isEmpty() ? "" : artist + " · ")
                                        + min + ":" + String.format("%02d", sec);
                            }
                            tvPartyArtist.setText(artist);
                        }

                        boolean metaReady = sync.title != null && !sync.title.isEmpty()
                                && sync.mediaUrl != null && !sync.mediaUrl.isEmpty();
                        if (metaReady) {
                            updateGuestLoadingMessage("Buffering “" + sync.title + "”…");
                        }

                        // Expand only once when metadata is ready (title + url)
                        if (metaReady && !guestPlayerExpanded) {
                            guestPlayerExpanded = true;
                            // Short delay so Media3 can start buffering
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                if (!isAdded()) return;
                                dismissGuestLoadingDialog();
                                if (cardNowPlaying != null) cardNowPlaying.setVisibility(android.view.View.GONE);
                                if (progressSync != null) progressSync.setVisibility(android.view.View.GONE);
                                expandFullPlayerForParty();
                            }, 600);
                        }
                    });

            // Also observe song LiveData for duration/position UI readiness
            viewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
                if (song == null) return;
                if (tvPartySongTitle != null && song.getTitle() != null) {
                    tvPartySongTitle.setText(song.getTitle());
                }
            });
        } catch (Exception e) {
            android.util.Log.w("FragmentParty", "guest sync UI observe failed", e);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    this::dismissGuestLoadingDialog, 4000);
        }
    }

    private void showGuestLoadingDialog(@NonNull String message) {
        if (!isAdded()) return;
        dismissGuestLoadingDialog();
        android.widget.LinearLayout box = new android.widget.LinearLayout(requireContext());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        android.widget.ProgressBar bar = new android.widget.ProgressBar(requireContext());
        bar.setIndeterminate(true);
        android.widget.TextView msg = new android.widget.TextView(requireContext());
        msg.setText(message);
        msg.setId(android.view.View.generateViewId());
        msg.setPadding(0, pad / 2, 0, 0);
        box.addView(bar);
        box.addView(msg);
        guestLoadingDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Party stream")
                .setView(box)
                .setCancelable(false)
                .create();
        guestLoadingDialog.show();
        guestLoadingDialog.setOnDismissListener(d -> guestLoadingDialog = null);
        // stash message view tag
        guestLoadingDialog.setTitle("Party stream");
        box.setTag(msg);
        if (guestLoadingDialog.getWindow() != null) {
            guestLoadingDialog.getWindow().getDecorView().setTag(msg);
        }
    }

    private void updateGuestLoadingMessage(@NonNull String message) {
        if (guestLoadingDialog == null || !guestLoadingDialog.isShowing()) return;
        try {
            android.view.View decor = guestLoadingDialog.getWindow() != null
                    ? guestLoadingDialog.getWindow().getDecorView() : null;
            Object tag = decor != null ? decor.getTag() : null;
            if (tag instanceof android.widget.TextView) {
                ((android.widget.TextView) tag).setText(message);
            }
        } catch (Exception ignored) { }
    }

    private void dismissGuestLoadingDialog() {
        try {
            if (guestLoadingDialog != null && guestLoadingDialog.isShowing()) {
                guestLoadingDialog.dismiss();
            }
        } catch (Exception ignored) { }
        guestLoadingDialog = null;
    }

    private void expandFullPlayerForParty() {
        try {
            var panel = com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp.container(requireContext())
                    .requireUiThread()
                    .getMediaPlayerPanel();
            if (panel != null) {
                panel.expandPlayer();
            }
        } catch (Exception e) {
            android.util.Log.w("FragmentParty", "expand player skipped", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissPartyDialogs();
        dismissGuestLoadingDialog();
        viewModel.setUiCallback(null);
    }

    public boolean onBackPressed() {
        PartyState state = viewModel.getPartyState().getValue();
        if (state != null && state != PartyState.IDLE) {
            // Searching / scanner / connected: leave party flow and return to Home tab
            viewModel.leaveParty();
            navigateToHomeTab();
            return true;
        }
        return false;
    }

    private void navigateToHomeTab() {
        try {
            var nav = HeartBeatzApp.container(requireContext()).requireUiThread().getNavigationPanel();
            if (nav != null) {
                nav.selectTab(R.id.nav_home);
            }
        } catch (Exception e) {
            // Activity may not have UIThread ready
        }
    }

    @Override
    public void onDisplayBarPlayerChanged(boolean isDisplaying) {
        int paddingHeight = (isDisplaying) ? getResources().getDimensionPixelSize(R.dimen.bar_and_navigation_height) : getResources().getDimensionPixelSize(R.dimen.navigation_bar_height);
        partyRoot.setPadding(0, 0, 0, paddingHeight);
    }
}
