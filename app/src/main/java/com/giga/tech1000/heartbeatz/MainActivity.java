package com.giga.tech1000.heartbeatz;

import android.content.Intent;
import android.util.Log;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;
import com.giga.tech1000.heartbeatz.utils.QrCodeUtil;
import com.giga.tech1000.heartbeatz.utils.PartyAnalytics;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.heartbeatz.interfaces.DrawerController;
import com.google.android.material.navigation.NavigationView;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;


import com.giga.tech1000.heartbeatz.observers.LibraryObservers;
import com.giga.tech1000.heartbeatz.ui.UIInfoLog;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PartyViewModel;
import com.giga.tech1000.heartbeatz.view_models.extended_models.SettingViewModel;
import com.giga.tech1000.media_player.scanners.LocalMediaScannerManager;
import com.giga.tech1000.utils.PermissionManager;
import com.giga.tech1000.heartbeatz.view_models.AlbumsViewModel;
import com.giga.tech1000.heartbeatz.view_models.ArtistsViewModel;
import com.giga.tech1000.heartbeatz.view_models.FoldersViewModel;
import com.giga.tech1000.heartbeatz.view_models.GenresViewModel;
import com.giga.tech1000.heartbeatz.view_models.LibrarySetViewModel;
import com.giga.tech1000.heartbeatz.view_models.PlaylistsViewModel;
import com.giga.tech1000.heartbeatz.view_models.SongsViewModel;
import com.giga.tech1000.party_mode.core.PartyState;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.party_mode.model.SyncPacket;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Main Activity: DrawerLayout + CoordinatorLayout with Material bottom nav and player sheet.
 * Individual fragments (Home, Party) contain their own toolbars.
 */

@OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends AppCompatActivity implements DrawerController {

    private PermissionManager permissionManager;
    private UIThread uiThread;

    // Using a volatile boolean is safer for checks across different threads/callbacks.
    private volatile boolean isAppReady = false;
    private volatile boolean isDataReady = false;

    private LibraryObservers libraryObservers;
    private LocalMediaScannerManager scannerManager;
    private LibrarySetViewModel librarySetViewModel;

    private SettingViewModel settingViewModel;

    // Firebase Auth
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    // UI Components

    // App-level drawer
    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private View authButtonsContainer;
    private View btnLogin;
    private View btnSignup;
    private View btnLogout;
    private DrawerListener drawerListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // --- STEP 1: INSTALL SPLASH SCREEN ---
        androidx.core.splashscreen.SplashScreen splashScreen = androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        uiThread = new UIThread(this);
        libraryObservers = new LibraryObservers();

        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        initCoreComponents();

        // --- STEP 3: KEEP SPLASH VISIBLE UNTIL DATA IS READY ---
        splashScreen.setKeepOnScreenCondition(() -> !isDataReady);

        setContentView(R.layout.activity_main);
        setupAppDrawer();

        // Edge-to-edge: pad content for nav/gesture bars; leave status-bar insets for toolbars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, 0, systemBars.right, bottom);
            Log.d("UIInfo", "[MainActivity.insets] content pad L=" + systemBars.left
                    + " R=" + systemBars.right + " B=" + bottom
                    + " statusTop kept for children=" + systemBars.top
                    + " contentH=" + v.getHeight());

            // Consume left/right/bottom so children do not double-pad; keep status bar for FragmentHome.
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(),
                            Insets.of(0, systemBars.top, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .build();
        });


        // --- STEP 2: SETUP PERMISSIONS ---
        // Initialize the manager with a callback that handles all outcomes.
        setupPermissions();

        // --- STEP 4: CHECK PERMISSIONS ---
        // This kicks off the entire process.
        checkAndRequestPermissions();


        // Observe Party state to update UI
        PartyViewModel partyViewModel = new ViewModelProvider(this).get(PartyViewModel.class);
        partyViewModel.getPartyState().observe(this, state -> {
            boolean isClient = (state == PartyState.JOINED);
            if (uiThread != null && uiThread.getMediaPlayerPanel() != null) {
                uiThread.getMediaPlayerPanel().setPartyClientMode(isClient);
            }
        });

        // Update UI with metadata from Party Mode when in client mode
        partyViewModel.getCurrentSync().observe(this, sync -> {
            if (sync != null && partyViewModel.getPartyState().getValue() == PartyState.JOINED) {
                Song song = convertSyncToSong(sync);
                if (uiThread != null && uiThread.getMediaPlayerPanel() != null) {
                    uiThread.getMediaPlayerPanel().onSongChanged(song);
                }
            }
        });

        // PartyViewModel needs UIThread.init() first — wired in setupPartyObservers()

        // Set up Firebase Auth listener to check if user is signed in
        setupFirebaseAuthListener();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // Note: Toolbar menus are now handled in individual fragments
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        // Note: Toolbar item clicks are now handled in individual fragments
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    private Song convertSyncToSong(SyncPacket sync) {
        // Find the actual song in repository by mediaId if possible, or create a stub
        Song song = new Song();
        try {
            int id = Integer.parseInt(sync.mediaId);
            java.util.TreeMap<Integer, Song> cached = com.giga.tech1000.media_player.repository.SongRepository.getInstance().getCachedSongs();
            if (cached != null && cached.containsKey(id)) {
                return cached.get(id);
            }
            song.id = id;
        } catch (Exception e) {
            song.id = -1;
        }
        song.title = (sync.title != null) ? sync.title : "Streaming Audio";
        song.artist = (sync.artist != null) ? sync.artist : "Party Mode";
        return song;
    }

    /*
    // Optional: Set up panel slide listeners if needed
        // multiSlidingUpPanelLayout.addPanelSlideListener(new PanelSlideListener() { ... });
    */

    private void initCoreComponents() {
        isAppReady = true;

        SongsViewModel songsVm =
                new androidx.lifecycle.ViewModelProvider(this).get(SongsViewModel.class);
        AlbumsViewModel albumsVm =
                new androidx.lifecycle.ViewModelProvider(this).get(AlbumsViewModel.class);
        ArtistsViewModel artistsVm =
                new androidx.lifecycle.ViewModelProvider(this).get(ArtistsViewModel.class);
        GenresViewModel genresVm =
                new androidx.lifecycle.ViewModelProvider(this).get(GenresViewModel.class);
        FoldersViewModel foldersVm =
                new androidx.lifecycle.ViewModelProvider(this).get(FoldersViewModel.class);
        PlaylistsViewModel playlistsVm =
                new androidx.lifecycle.ViewModelProvider(this).get(PlaylistsViewModel.class);

        librarySetViewModel = new androidx.lifecycle.ViewModelProvider(this).get(LibrarySetViewModel.class);
        settingViewModel = new androidx.lifecycle.ViewModelProvider(this).get(SettingViewModel.class);


        libraryObservers.bind(songsVm, albumsVm, artistsVm, genresVm, foldersVm, playlistsVm);

        // 🔥 This activates everything
        libraryObservers.getState().observe(this, state -> {
            if (libraryObservers.isReady()) {
                setDataReady(); // splash disappears here
            }
        });
    }

    /**
     * Configures the PermissionManager and defines what happens on each permission result.
     */
    private void setupPermissions() {
        permissionManager = new PermissionManager(this, new PermissionManager.PermissionCallback() {
            @Override
            public void onPermissionsGranted() {
                Toast.makeText(MainActivity.this, "Permissions Granted! Loading music...", Toast.LENGTH_SHORT).show();
                // Permissions are granted, now we can load the data.
                loadAudioFiles();
            }

            @Override
            public void onPermissionsDenied() {
                com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content),
                                "Storage permission is required to play music.",
                                com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                        .setAction("RETRY", v -> checkAndRequestPermissions())
                        .show();
                // The app is "ready" to show the UI (even if it's just a Snackbar).
                setDataReady();
            }

            @Override
            public void onPermissionsDeniedPermanently() {
                com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content),
                                "Permission permanently denied. Go to settings to enable it.",
                                com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                        .setAction("SETTINGS", v ->
                                startActivity(PermissionManager.getAppSettingsIntent(MainActivity.this)))
                        .show();
                // The app is "ready" to show the UI.
                setDataReady();
            }
        });
    }

    /**
     * Checks if permissions are granted. If so, proceeds. If not, requests them.
     */
    private void checkAndRequestPermissions() {
        if (permissionManager.hasRequiredPermissions()) {
            // If we already have permission, go straight to loading audio.
            loadAudioFiles();
        } else {
            // Otherwise, request the permissions. The result will be handled by the callback.
            permissionManager.requestRequiredPermissions();
        }
    }

    /**
     * The single entry point for loading music and preparing the main UI.
     * This is where you would call your ViewModel or background scanner.
     */
    private void loadAudioFiles() {
        if (scannerManager == null) {
            scannerManager = new LocalMediaScannerManager(this);
            scannerManager.init();
        }

        // Avoid double-init (permission callback can fire after UI already built).
        if (uiThread != null && uiThread.getNavigationPanel() != null) {
            UIInfoLog.d("MainActivity.loadAudioFiles", "UI already initialized — skip re-init");
            HeartBeatzApp.container(this).attachUiThread(uiThread);
            return;
        }

        // Attach so requireUiThread() works during init; re-attach after init to bind PlaybackStateRepository.
        HeartBeatzApp.container(this).attachUiThread(uiThread);
        uiThread.init();
        HeartBeatzApp.container(this).attachUiThread(uiThread);
        setupPartyObservers();
    }

    private void setupPartyObservers() {
        PartyViewModel partyViewModel =
                new androidx.lifecycle.ViewModelProvider(this).get(PartyViewModel.class);
        // Bind shared Media3 playback repo now that UIThread.init() has run
        partyViewModel.attachPlaybackRepository(uiThread.getPlaybackStateRepository());

        partyViewModel.getPartyState().observe(this, state -> {
            boolean isClient = (state == PartyState.JOINED);
            if (uiThread != null && uiThread.getMediaPlayerPanel() != null) {
                uiThread.getMediaPlayerPanel().setPartyClientMode(isClient);
            }
        });

        partyViewModel.getCurrentSync().observe(this, sync -> {
            if (sync != null && partyViewModel.getPartyState().getValue() == PartyState.JOINED) {
                Song song = convertSyncToSong(sync);
                if (uiThread != null && uiThread.getMediaPlayerPanel() != null) {
                    uiThread.getMediaPlayerPanel().onSongChanged(song);
                }
            }
        });
    }


    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }


    @Override
    protected void onDestroy() {
        if (uiThread != null) {
            uiThread.onDestroy();
        }
        if (scannerManager != null) {
            scannerManager.release();
        }
        HeartBeatzApp.container(this).detachUiThread();
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        if ("com.heartbeatz.party.SHOW_PARTY".equals(intent.getAction())) {
            if (uiThread != null && uiThread.getNavigationPanel() != null) {
                uiThread.getNavigationPanel().selectTab(R.id.nav_party);
            }
            return;
        }

        // Deep link: heartbeatz://party/{id}?pin=  or https://heartbeatz.app/party/{id}
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            QrCodeUtil.PartyInvite invite = QrCodeUtil.parseInvite(intent.getData().toString());
            if (invite != null && invite.partyId != null) {
                if (uiThread != null && uiThread.getNavigationPanel() != null) {
                    uiThread.getNavigationPanel().selectTab(R.id.nav_party);
                }
                PartyAnalytics.partyJoinAttempt(true);
                intent.putExtra("party_invite_id", invite.partyId);
                if (invite.pin != null) intent.putExtra("party_invite_pin", invite.pin);
                if (invite.partyName != null) intent.putExtra("party_invite_name", invite.partyName);
                // FragmentParty reads pending extras via activity intent on resume
                setIntent(intent);
                Toast.makeText(this, "Opening party invite…", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public SettingViewModel getSettingViewModel() {
        return settingViewModel;
    }

    public LibraryObservers getLibraryObservers() {
        return libraryObservers;
    }

    public LocalMediaScannerManager getScannerManager() {
        return scannerManager;
    }

    public LibrarySetViewModel getLibrarySetViewModel() {
        return librarySetViewModel;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public void setDataReady() {
        isDataReady = true;
    }

    // Firebase Auth listener setup
    private void setupFirebaseAuthListener() {
        mAuthListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                // User is signed in, proceed with app initialization
                // The app initialization is already happening in onCreate
            }
            /*
            else {
                // User is signed out, send to log in screen
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
            */
        };
    }

    // region DrawerController
    private void setupAppDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navView = findViewById(R.id.nav_view);
        authButtonsContainer = findViewById(R.id.auth_buttons_container);
        btnLogin = findViewById(R.id.btn_login);
        btnSignup = findViewById(R.id.btn_signup);
        btnLogout = findViewById(R.id.btn_logout);

        if (navView != null) {
            navView.setNavigationItemSelectedListener(item -> {
                boolean handled = drawerListener != null
                        && drawerListener.onDrawerNavigationItem(item.getItemId());
                closeDrawer();
                return handled || true;
            });
        }
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                if (drawerListener != null) drawerListener.onDrawerLoginClicked();
                closeDrawer();
            });
        }
        if (btnSignup != null) {
            btnSignup.setOnClickListener(v -> {
                if (drawerListener != null) drawerListener.onDrawerSignupClicked();
                closeDrawer();
            });
        }
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                if (drawerListener != null) drawerListener.onDrawerLogoutClicked();
                closeDrawer();
            });
        }

        // Status bar padding on drawer panel
        View drawerContainer = findViewById(R.id.nav_drawer_container);
        if (drawerContainer != null && drawerLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(drawerContainer, (v, insets) -> {
                int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                if (top == 0) {
                    int resId = this.getResources().getIdentifier("status_bar_height", "dimen", "android");
                    if (resId > 0) top = getResources().getDimensionPixelSize(resId);
                }
                v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
        android.util.Log.d("UIInfo", "[MainActivity.setupAppDrawer] drawer ready");
    }

    @Override
    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    @Override
    public void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    @Override
    public boolean isDrawerOpen() {
        return drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START);
    }

    @Override
    public void setDrawerListener(DrawerListener listener) {
        this.drawerListener = listener;
    }

    @Override
    public void updateDrawerAccount(String displayName, String email, android.net.Uri photoUri, boolean isGuest) {
        if (navView != null && navView.getHeaderView(0) != null) {
            View header = navView.getHeaderView(0);
            android.widget.TextView txtName = header.findViewById(R.id.nav_header_name);
            android.widget.TextView txtEmail = header.findViewById(R.id.nav_header_email);
            android.widget.ImageView img = header.findViewById(R.id.nav_header_photo);
            if (txtName != null) {
                txtName.setText(isGuest
                        ? getString(R.string.placeholder_guest)
                        : (displayName != null ? displayName : "User"));
            }
            if (txtEmail != null) {
                txtEmail.setText(isGuest
                        ? getString(R.string.not_signed_in)
                        : (email != null ? email : ""));
            }
            if (img != null) {
                if (!isGuest && photoUri != null) {
                    com.bumptech.glide.Glide.with(this)
                            .load(photoUri)
                            .circleCrop()
                            .placeholder(R.drawable.profile_pic)
                            .into(img);
                } else {
                    img.setImageResource(R.drawable.profile_pic);
                }
            }
        }
        if (authButtonsContainer != null) {
            authButtonsContainer.setVisibility(isGuest ? View.VISIBLE : View.GONE);
        }
        if (btnLogout != null) {
            btnLogout.setVisibility(isGuest ? View.GONE : View.VISIBLE);
        }
    }


    /** Keep screen awake while media is playing (party + local). */
    public void setKeepScreenOnWhilePlaying(boolean playing) {
        runOnUiThread(() -> {
            if (playing) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

}
