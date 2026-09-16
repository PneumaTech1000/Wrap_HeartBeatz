package com.giga.tech1000.heartbeatz.ui.fragments;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;

import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.view.GravityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.transition.TransitionManager;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.layouts.adapters.LibraryLayoutAdapter;
import com.giga.tech1000.heartbeatz.layouts.models.BaseLayoutItem;
import com.giga.tech1000.heartbeatz.layouts.models.LibraryLayoutItem;
import com.giga.tech1000.heartbeatz.observers.LibraryObservers;
import com.giga.tech1000.heartbeatz.observers.LibraryState;
import com.giga.tech1000.heartbeatz.ui.SearchController;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.MediaNavigationManager;
import com.giga.tech1000.heartbeatz.view_models.LibrarySetViewModel;
import com.giga.tech1000.heartbeatz.view_models.SongInfoPanelViewModel;
import com.giga.tech1000.heartbeatz.view_models.extended_models.EqualizerViewModel;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.heartbeatz.views.panels.sub_panels.EditSongInfoPanel;
import com.giga.tech1000.heartbeatz.views.panels.sub_panels.EqualizerViewPanel;
import com.giga.tech1000.heartbeatz.views.panels.sub_panels.RootMediaDetailsWithImgPanel;
import com.giga.tech1000.heartbeatz.views.panels.sub_panels.RootMediaDetailsWithoutImgPanel;
import com.giga.tech1000.heartbeatz.views.panels.sub_panels.SongInfoPanel;
import com.giga.tech1000.heartbeatz.views.panels.sub_panels.SongSelectionPanel;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.utils.interfaces.DisplayMarginCallback;
import com.giga.tech1000.utils.interfaces.OnBackPressedHandler;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButton;

import android.content.Intent;

import com.giga.tech1000.heartbeatz.LoginActivity;
import com.giga.tech1000.heartbeatz.SignUpActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@OptIn(markerClass = UnstableApi.class)
public class FragmentHome extends Fragment implements DisplayMarginCallback, OnBackPressedHandler {

    private MotionLayout motionLayout;
    private FrameLayout pagerWrapper;
    private TabLayout tabLayout;
    private ViewPager2 viewPager2;
    private LibraryLayoutAdapter pagerAdapter;
    private RootMediaDetailsWithImgPanel mediaDetailsWithImgPanel;
    private RootMediaDetailsWithoutImgPanel mediaDetailsWithoutImgPanel;
    private EqualizerViewPanel equalizerViewPanel;
    private SongSelectionPanel songSelectionPanel;
    private SongInfoPanel songInfoPanel;
    private EditSongInfoPanel editSongInfoPanel;
    private int paddingHeight;

    private SongInfoPanelViewModel songInfoPanelViewModel;
    private EqualizerViewModel equalizerViewModel;
    private PlaybackCacheViewModel playbackCacheViewModel;

    private FrameLayout equalizerPanelView, songSelectionPanelView, songInfoPanelView, editSongInfoPanelView, mediaDetailsWithImgPanelView, mediaDetailsWithoutImgPanelView;

    private View navDrawerScrollContent;

    private LibraryObservers libraryObservers;
    private SearchController search;

    private List<LibraryLayoutItem> pages = new ArrayList<>();
    private LibrarySetViewModel librarySetViewModel;

    private AppCompatImageButton menuButton, searchButton;
    private AppCompatImageButton btnPanel;
    private TextView toolbarTitle;
    private SearchView searchView;
    private ViewGroup container;


    private DrawerLayout drawerLayout;
    private NavigationView navView;

    // Drawer Auth UI
    private View authButtonsContainer;
    private MaterialButton btnLogin;
    private MaterialButton btnSignup;
    private MaterialButton btnLogout;

    private Observer<LibraryState> libraryStateObserver;
    private Observer<String> searchQueryObserver;
    private LibraryState lastState;
    private String lastQuery;

    private MediaNavigationManager mediaNavigationManager;
    public final ActivityResultLauncher<PickVisualMediaRequest> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (editSongInfoPanel != null) {
                            editSongInfoPanel.handleImagePickerResult(uri);
                        }
                    });

    public final ActivityResultLauncher<String> legacyPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (editSongInfoPanel != null)
                            editSongInfoPanel.handleImagePickerResult(uri);
                    }
            );


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize all views here
        motionLayout = view.findViewById(R.id.library_root);
        pagerWrapper = view.findViewById(R.id.fragment_local_view_pager_wrapper);
        tabLayout = view.findViewById(R.id.fragment_local_tab_layout);
        viewPager2 = view.findViewById(R.id.fragment_local_view_pager);

        menuButton = view.findViewById(R.id.btn_menu);
        searchButton = view.findViewById(R.id.btn_search);
        btnPanel = view.findViewById(R.id.btn_panel);
        toolbarTitle = view.findViewById(R.id.toolbar_title);
        searchView = view.findViewById(R.id.toolbar_search_view);

        equalizerPanelView = view.findViewById(R.id.media_equalizer_root_container);
        songSelectionPanelView = view.findViewById(R.id.media_selection_root_container);
        songInfoPanelView = view.findViewById(R.id.media_song_info_root_container);
        editSongInfoPanelView = view.findViewById(R.id.media_edit_song_info_root_container);
        mediaDetailsWithImgPanelView = view.findViewById(R.id.media_details_with_img_container);
        mediaDetailsWithoutImgPanelView = view.findViewById(R.id.media_details_without_img_container);

        drawerLayout = view.findViewById(R.id.drawer_layout);
        navView = view.findViewById(R.id.nav_view);
        navDrawerScrollContent = view.findViewById(R.id.nav_drawer_scroll_content);

        authButtonsContainer = view.findViewById(R.id.auth_buttons_container);
        btnLogin = view.findViewById(R.id.btn_login);
        btnSignup = view.findViewById(R.id.btn_signup);
        btnLogout = view.findViewById(R.id.btn_logout);

        return view;
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        libraryObservers = UIThread.getInstance().getLibraryObservers();
        search = UIThread.getInstance().getSearchController();
        librarySetViewModel = UIThread.getInstance().getLibrarySetViewModel();

        // Initialize ViewModels for UI panels
        songInfoPanelViewModel = new ViewModelProvider(this).get(SongInfoPanelViewModel.class);
        equalizerViewModel = new ViewModelProvider(this).get(EqualizerViewModel.class);
        playbackCacheViewModel = new ViewModelProvider(this).get(PlaybackCacheViewModel.class);

        mediaNavigationManager = new MediaNavigationManager(this, motionLayout);

        UIThread.getInstance().getSessionIdViewModel().getSessionId()
                .observe(getViewLifecycleOwner(), id -> {
                    if (equalizerViewPanel != null) {
                        equalizerViewPanel.setSessionId(id != null ? id : -1);
                    }
                });

        setupViewPager();
        setupMenu();
        setupDrawerAuth();
        setupEdgeToEdgeInsets(view);
        setupMotionLayoutTransitions();

        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                observeData();
                return true;
            }
        });

        viewPager2.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        librarySetViewModel.selectedTab.setValue(position);
                    }
                }
        );

        librarySetViewModel.selectedTab.observe(getViewLifecycleOwner(), pos -> {
            if (pos != null && pos != viewPager2.getCurrentItem()) {
                viewPager2.setCurrentItem(pos, false);
            }
        });
    }

    public final ActivityResultLauncher<IntentSenderRequest> updateLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(requireContext(), "Permission granted, changes saved", Toast.LENGTH_SHORT).show();
                    // After permission is granted, we usually need to re-trigger the update
                    // or the OS might have already applied it depending on how the intent was built.
                    // For safety, refresh the UI state.
                    UIThread.getInstance().getScannerManager().runIncrementalMediaRefresh();
                } else {
                    Toast.makeText(requireContext(), "Permission denied, could not save changes", Toast.LENGTH_SHORT).show();
                }
            }
    );

    public ActivityResultLauncher<IntentSenderRequest> deleteLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Snackbar.make(requireView(), "Deleted successfully", Snackbar.LENGTH_SHORT).show();
                            // Trigger incremental refresh to remove the deleted item from DB and UI
                            UIThread.getInstance().getScannerManager().runIncrementalMediaRefresh();
                        } else {
                            Snackbar.make(requireView(), "Delete cancelled", Snackbar.LENGTH_SHORT).show();
                        }
                    }
            );


    @Override
    public void onResume() {
        super.onResume();
        observeData();
    }

    @Override
    public void onPause() {
        removeObservers();
        super.onPause();
    }

    @Override
    public void onDisplayBarPlayerChanged(boolean isDisplaying) {
        paddingHeight = (isDisplaying) ? getResources().getDimensionPixelSize(R.dimen.bar_and_navigation_height) : getResources().getDimensionPixelSize(R.dimen.navigation_bar_height);

        mediaNavigationManager.setBottomPadding(paddingHeight);
        pagerWrapper.setPadding(0, 0, 0, paddingHeight);

        if (navDrawerScrollContent != null) {
            navDrawerScrollContent.setPadding(0, navDrawerScrollContent.getPaddingTop(), 0, paddingHeight);
        }

        if (getSongInfoPanel().getIsVisible().get())
            getSongInfoPanel().setBottomPadding(paddingHeight);
        if (getEqualizerViewPanel().getIsVisible().get())
            getEqualizerViewPanel().setBottomPadding(paddingHeight);
        if (getMediaDetailsWithImgPanel().getIsVisible().get())
            getMediaDetailsWithImgPanel().setBottomPadding(paddingHeight);
        if (getMediaDetailsWithoutImgPanel().getIsVisible().get())
            getMediaDetailsWithoutImgPanel().setBottomPadding(paddingHeight);
        if (getSongSelectionPanel().getIsVisible().get())
            getSongSelectionPanel().setBottomPadding(paddingHeight);
        if (getEditSongInfoPanel().getIsVisible().get())
            getEditSongInfoPanel().setBottomPadding(paddingHeight);

    }

    private void setupDrawerAuth() {
        // Set up drawer toggle with btn_panel (hamburger icon)
        if (btnPanel != null) {
            btnPanel.setOnClickListener(v -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // Navigation item selection
        navView.setNavigationItemSelectedListener(menuItem -> {
            int id = menuItem.getItemId();
            NavController navController = Navigation.findNavController(requireActivity(), R.id.root_container_view);
            if (id == R.id.nav_home) {
                // Already here
            } else if (id == R.id.nav_party) {
                navController.navigate(R.id.nav_party);
            } else if (id == R.id.nav_settings) {
                Toast.makeText(requireContext(), "Settings clicked", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), LoginActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        //if (btnSignup != null) {
        btnSignup.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), SignUpActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        // }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                drawerLayout.closeDrawer(GravityCompat.START);
                Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show();
            });
        }

        // Observe Firebase Auth user to update header and buttons
        FirebaseAuth.getInstance().addAuthStateListener(authState -> {
            FirebaseUser user = authState.getCurrentUser();
            boolean isLoggedIn = user != null;

            // Update Header
            View headerView = navView.getHeaderView(0);
            if (headerView != null) {
                ImageView imgHeader = headerView.findViewById(R.id.nav_header_photo);
                TextView txtName = headerView.findViewById(R.id.nav_header_name);
                TextView txtEmail = headerView.findViewById(R.id.nav_header_email);

                if (isLoggedIn) {
                    txtName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
                    txtEmail.setText(user.getEmail() != null ? user.getEmail() : "");
                    if (user.getPhotoUrl() != null) {
                        Glide.with(this)
                                .load(user.getPhotoUrl())
                                .circleCrop()
                                .placeholder(com.giga.tech1000.icons_pack.R.drawable.person_4_24px)
                                .into(imgHeader);
                    } else {
                        imgHeader.setImageResource(R.drawable.profile_pic);
                    }
                } else {
                    txtName.setText(R.string.placeholder_guest);
                    txtEmail.setText(R.string.not_signed_in);
                    imgHeader.setImageResource(R.drawable.profile_pic);
                }
            }

            // Update Button Visibility
            if (authButtonsContainer != null) {
                authButtonsContainer.setVisibility(isLoggedIn ? View.GONE : View.VISIBLE);
            }

            if (btnLogout != null) {
                btnLogout.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void setupMenu() {
        searchButton.setOnClickListener(v -> toggleSearchView(true));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                search.setQuery(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                search.setQuery(newText);
                tabLayout.setVisibility(search.isSearching() ? View.GONE : View.VISIBLE);
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            toggleSearchView(false);
            UIThread.getInstance().getSearchController().clear();
            return true;
        });

        menuButton.setOnClickListener(this::showPopupMenu);
    }


    private void showPopupMenu(View anchor) {
        // 1. Initialize the PopupMenu with the Context and the Anchor View
        // Using the 3rd argument style attr ensures Material3 styling
        PopupMenu popup = new PopupMenu(requireContext(), anchor, Gravity.END);

        // 2. Inflate your menu resource
        popup.getMenuInflater().inflate(R.menu.local_menu, popup.getMenu());

        // 3. Handle Item Clicks
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_equalizer) {
                // Handle Equalizer
                openEqualizer();
                return true;
            } else if (id == R.id.menu_settings) {
                // Handle Settings
                return true;
            } else if (id == R.id.menu_about) {
                // Handle About
                return true;
            } else if (id == R.id.menu_exit) {
                // Handle Exit
                return true;
            }
            return false;
        });

        // 4. Show it
        popup.show();
    }


    private void toggleSearchView(boolean showSearch) {
        if (searchView.getVisibility() == (showSearch ? View.VISIBLE : View.GONE)) return;
        // MODERN: Use TransitionManager for a smooth fade/slide effect
        if (container != null) {
            TransitionManager.beginDelayedTransition(container);
        }

        if (showSearch) {
            toolbarTitle.setVisibility(View.GONE);
            searchButton.setVisibility(View.GONE);
            searchView.setVisibility(View.VISIBLE);

            searchView.setIconified(false); // Open it
            searchView.requestFocus();

            // Show Keyboard
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchView.findFocus(), InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchView.setVisibility(View.GONE);
            toolbarTitle.setVisibility(View.VISIBLE);
            searchButton.setVisibility(View.VISIBLE);

            // Clear search query and focus
            searchView.setQuery("", false);
            searchView.clearFocus();
        }

    }

    // panel implementation if not implemented
    private EqualizerViewPanel getEqualizerViewPanel() {
        if (equalizerViewPanel == null) {
            equalizerViewPanel = new EqualizerViewPanel(this, equalizerPanelView, equalizerViewModel);
            equalizerPanelView.addView(equalizerViewPanel.getView());

            // Apply insets to the newly created panel
            ViewCompat.requestApplyInsets(requireView());

            // Immediately sync current session ID if available
            Integer currentId = UIThread.getInstance().getSessionIdViewModel().getSessionId().getValue();
            equalizerViewPanel.setSessionId(currentId != null ? currentId : -1);
        }
        return equalizerViewPanel;
    }

    public RootMediaDetailsWithImgPanel getMediaDetailsWithImgPanel() {
        if (mediaDetailsWithImgPanel == null) {
            mediaDetailsWithImgPanel = new RootMediaDetailsWithImgPanel(this, mediaDetailsWithImgPanelView, playbackCacheViewModel);
            mediaDetailsWithImgPanelView.addView(mediaDetailsWithImgPanel.getView());
        }
        return mediaDetailsWithImgPanel;
    }

    public RootMediaDetailsWithoutImgPanel getMediaDetailsWithoutImgPanel() {
        if (mediaDetailsWithoutImgPanel == null) {
            mediaDetailsWithoutImgPanel = new RootMediaDetailsWithoutImgPanel(this, mediaDetailsWithoutImgPanelView, playbackCacheViewModel);
            mediaDetailsWithoutImgPanelView.addView(mediaDetailsWithoutImgPanel.getView());
        }
        return mediaDetailsWithoutImgPanel;
    }

    public EditSongInfoPanel getEditSongInfoPanel() {
        if (editSongInfoPanel == null) {
            editSongInfoPanel = new EditSongInfoPanel(this, editSongInfoPanelView);
            editSongInfoPanelView.addView(editSongInfoPanel.getView());
        }

        return editSongInfoPanel;
    }

    public SongInfoPanel getSongInfoPanel() {
        if (songInfoPanel == null) {
            songInfoPanel = new SongInfoPanel(this, songInfoPanelView, songInfoPanelViewModel);
            songInfoPanelView.addView(songInfoPanel.getView());
        }
        return songInfoPanel;
    }

    public SongSelectionPanel getSongSelectionPanel() {
        if (songSelectionPanel == null) {
            songSelectionPanel = new SongSelectionPanel(this, songSelectionPanelView);
            songSelectionPanelView.addView(songSelectionPanel.getView());
        }
        return songSelectionPanel;
    }


    public MotionLayout getMotionLayout() {
        return motionLayout;
    }

    public void addSelectedToPlaylist(long playlistId, List<Long> songIds) {
        libraryObservers.getPlaylistViewModel().addSongs(playlistId, songIds);
    }

    public LibraryObservers getLibraryObservers() {
        return libraryObservers;
    }

    private void observeData() {
        if (libraryStateObserver == null) {
            libraryStateObserver = state -> {
                if (state != null) {
                    rebuildPages(state, search.getQuery().getValue());
                }
            };
        }

        if (searchQueryObserver == null) {
            searchQueryObserver = q -> {
                rebuildPages(libraryObservers.getState().getValue(), q);
            };
        }

        libraryObservers.getState()
                .observe(getViewLifecycleOwner(), libraryStateObserver);

        search.getQuery()
                .observe(getViewLifecycleOwner(), searchQueryObserver);
    }

    private void removeObservers() {

        if (libraryStateObserver != null) {
            libraryObservers.getState()
                    .removeObserver(libraryStateObserver);
        }

        if (searchQueryObserver != null) {
            search.getQuery()
                    .removeObserver(searchQueryObserver);
        }
    }


    private void rebuildPages(
            @Nullable LibraryState state,
            @Nullable String query
    ) {
        if (state == null) return;

        // Force rebuild if view was just recreated
        if (state == lastState && Objects.equals(query, lastQuery)) {
            // If we already have pages in the adapter, don't submit again
            if (!pages.isEmpty() && pagerAdapter.getItemCount() > 0) return;
        }

        lastState = state;
        lastQuery = query;

        List<LibraryLayoutItem> newPages = List.of(LibraryLayoutItem.filtered(BaseLayoutItem.LayoutType.ALL_SONGS, state.getSongs(), query, search),
                LibraryLayoutItem.filtered(BaseLayoutItem.LayoutType.ALBUMS, state.getAlbums(), query, search),
                LibraryLayoutItem.filtered(BaseLayoutItem.LayoutType.ARTISTS, state.getArtists(), query, search),
                LibraryLayoutItem.filtered(BaseLayoutItem.LayoutType.GENRES, state.getGenres(), query, search),
                LibraryLayoutItem.filtered(BaseLayoutItem.LayoutType.PLAYLISTS, state.getPlaylists(), query, search),
                LibraryLayoutItem.filtered(BaseLayoutItem.LayoutType.FOLDERS, state.getFolders(), query, search)
        );

        pages = new ArrayList<>(newPages);
        pagerAdapter.submitList(pages, this::setupTabs);
    }


    private void setupViewPager() {
        pagerAdapter = new LibraryLayoutAdapter(
                requireContext(),
                mediaNavigationManager,
                playbackCacheViewModel
        );

        viewPager2.setAdapter(pagerAdapter);
        viewPager2.setOffscreenPageLimit(6); // Keep all pages in memory to prevent disappearing

        setupTabs();
    }


    private void setupTabs() {
        if (pages.isEmpty()) return;

        new TabLayoutMediator(tabLayout, viewPager2,
                (tab, position) -> {
                    if (position >= pages.size()) return;
                    LibraryLayoutItem item = pages.get(position);
                    switch (item.getType()) {
                        case ALL_SONGS -> tab.setText(R.string.tab_songs);
                        case ALBUMS -> tab.setText(R.string.tab_albums);
                        case ARTISTS -> tab.setText(R.string.tab_artists);
                        case GENRES -> tab.setText(R.string.tab_genres);
                        case FOLDERS -> tab.setText(R.string.tab_folders);
                        case PLAYLISTS -> tab.setText(R.string.tab_playlists);
                    }
                }
        ).attach();
    }

    private void setupEdgeToEdgeInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());

            // Handle Toolbar
            View toolbarWrapper = v.findViewById(R.id.tool_bar_wrapper);
            if (toolbarWrapper != null) {
                toolbarWrapper.setPadding(toolbarWrapper.getPaddingLeft(), statusBars.top,
                        toolbarWrapper.getPaddingRight(), toolbarWrapper.getPaddingBottom());

                // Adjust its height to include status bar
                ViewGroup.LayoutParams params = toolbarWrapper.getLayoutParams();
                params.height = getResources().getDimensionPixelSize(com.google.android.material.R.dimen.m3_appbar_size_compact) + statusBars.top;
                toolbarWrapper.setLayoutParams(params);
            }

            // Handle Drawer Header (if separate) or NavView
            if (navView != null) {
                View header = navView.getHeaderView(0);
                if (header != null) {
                    header.setPadding(header.getPaddingLeft(), statusBars.top,
                            header.getPaddingRight(), header.getPaddingBottom());
                }
            }

            // Handle Equalizer Top Padding
            if (equalizerViewPanel != null && equalizerViewPanel.getView() != null) {
                View eqHeader = (View) equalizerViewPanel.getView().findViewById(R.id.equalizer_view_close).getParent().getParent();
                if (eqHeader instanceof AppBarLayout) {
                    eqHeader.setPadding(eqHeader.getPaddingLeft(), statusBars.top,
                            eqHeader.getPaddingRight(), eqHeader.getPaddingBottom());
                }
            }

            return insets;
        });
    }

    private void setupMotionLayoutTransitions() {
        if (motionLayout == null) return;

        motionLayout.setTransitionListener(new MotionLayout.TransitionListener() {
            @Override
            public void onTransitionStarted(MotionLayout motionLayout, int startId, int endId) {
            }

            @Override
            public void onTransitionChange(MotionLayout motionLayout, int startId, int endId, float progress) {
                // Determine if we are transitioning to a full-screen state
                boolean isEnteringFullScreen = endId == R.id.equalizer_page ||
                        endId == R.id.with_image ||
                        endId == R.id.without_image ||
                        endId == R.id.song_info_page ||
                        endId == R.id.edit_song_info_page;

                updateStatusBarAppearance(isEnteringFullScreen, progress);
            }

            @Override
            public void onTransitionCompleted(MotionLayout motionLayout, int currentId) {
                boolean isFullScreen = currentId == R.id.equalizer_page ||
                        currentId == R.id.with_image ||
                        currentId == R.id.without_image ||
                        currentId == R.id.song_info_page ||
                        currentId == R.id.edit_song_info_page;

                if (isFullScreen) {
                    // Optionally hide status bar completely when settled
                    // WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(motionLayout);
                    // if (controller != null) controller.hide(WindowInsetsCompat.Type.statusBars());
                }
            }

            @Override
            public void onTransitionTrigger(MotionLayout motionLayout, int triggerId, boolean positive, float progress) {
            }
        });
    }

    private void updateStatusBarAppearance(boolean isFullScreen, float progress) {
        Activity activity = getActivity();
        if (activity == null) return;

        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(activity.getWindow().getDecorView());
        if (controller == null) return;

        // Modern approach: Update light/dark status bar icons based on background
        // For Equalizer, it's usually dark or matches surface color.
        // If surface is dark, set isAppearanceLightStatusBars(false)
        if (isFullScreen && progress > 0.5f) {
            controller.setAppearanceLightStatusBars(false); // White icons
        } else {
            // Revert to system/default (usually dark icons on light theme)
            // check theme...
            controller.setAppearanceLightStatusBars(true);
        }
    }

    private void openEqualizer() {
        displayEqualizerPanel();
    }


    public void displayEditSongInfoPanel(Song song) {
        if (motionLayout == null) return;
        if (getEditSongInfoPanel() != null)
            getEditSongInfoPanel().setBottomPadding(paddingHeight);
        getEditSongInfoPanel().setCurrentSong(song);
        motionLayout.transitionToState(R.id.edit_song_info_page);
    }

    public void displaySelectionPanel() {
        if (motionLayout == null) return;
        if (getSongSelectionPanel() != null)
            getSongSelectionPanel().setBottomPadding(paddingHeight);
        getSongSelectionPanel().setIsVisible(true);
        motionLayout.transitionToState(R.id.selection_page);
    }

    public void displayEqualizerPanel() {
        if (motionLayout == null) return;
        if (getEqualizerViewPanel() != null) {
            getEqualizerViewPanel().setBottomPadding(paddingHeight);
            getEqualizerViewPanel().setIsVisible(true);
        }
        motionLayout.transitionToState(R.id.equalizer_page);
    }


    public void hideMediaDetailsPanel() {
        if (motionLayout == null) return;
        motionLayout.transitionToState(R.id.base_state);
        getMediaDetailsWithoutImgPanel().setIsVisible(false);
        getMediaDetailsWithImgPanel().setIsVisible(false);
        getSongSelectionPanel().setIsVisible(false);
        getEqualizerViewPanel().setIsVisible(false);
        getSongInfoPanel().setIsVisible(false);
        getEditSongInfoPanel().setIsVisible(false);
    }

    public void hideMediaSearchView() {
        getMediaDetailsWithoutImgPanel().setSearchIsVisible(false);
        getMediaDetailsWithImgPanel().setSearchIsVisible(false);
        getSongSelectionPanel().setSearchIsVisible(false);
    }

    public void requestUpdate(PendingIntent pendingIntent) {
        IntentSenderRequest request = new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build();
        updateLauncher.launch(request);
    }

    public void requestDelete(PendingIntent pendingIntent) {
        IntentSenderRequest request =
                new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build();

        deleteLauncher.launch(request);
    }


    @Override
    public void onDestroyView() {
        if (equalizerViewPanel != null) equalizerViewPanel.onDestroy();
        equalizerViewPanel = null;
        mediaDetailsWithImgPanel = null;
        mediaDetailsWithoutImgPanel = null;
        songSelectionPanel = null;
        songInfoPanel = null;

        // Clean up view references to prevent memory leaks and stale data
        motionLayout = null;
        equalizerPanelView = null;
        songSelectionPanelView = null;
        songInfoPanelView = null;
        mediaDetailsWithImgPanelView = null;
        mediaDetailsWithoutImgPanelView = null;
        pagerWrapper = null;
        tabLayout = null;
        viewPager2 = null;
        menuButton = null;
        searchButton = null;
        toolbarTitle = null;
        searchView = null;
        container = null;
        drawerLayout = null;
        navView = null;
        // DO NOT reset lastState and lastQuery here if you want to preserve UI state
        // across fragment switches within the same activity lifecycle.
        super.onDestroyView();
    }


    @Override
    public boolean onBackPressed() {
        // Close drawer if open
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        // viewpager managements, with their panels (withImgPanel, withoutImgPanel etc.)
        if (getMediaDetailsWithoutImgPanel().getIsSearchVisible().get() ||
                getMediaDetailsWithImgPanel().getIsSearchVisible().get() ||
                getSongSelectionPanel().getIsSearchVisible().get()) {
            hideMediaSearchView();
            return true;
        }

        if (getMediaDetailsWithImgPanel().getIsVisible().get() ||
                getMediaDetailsWithoutImgPanel().getIsVisible().get() ||
                getSongSelectionPanel().getIsVisible().get() ||
                getEqualizerViewPanel().getIsVisible().get() ||
                getSongInfoPanel().getIsVisible().get() ||
                getEditSongInfoPanel().getIsVisible().get()) {
            hideMediaDetailsPanel();
            return true;
        }

        if (tabLayout.getTabAt(0) != null) {
            if (!tabLayout.getTabAt(0).isSelected()) {
                tabLayout.selectTab(tabLayout.getTabAt(0));
                return true;
            }
        }

        return false;
    }

}