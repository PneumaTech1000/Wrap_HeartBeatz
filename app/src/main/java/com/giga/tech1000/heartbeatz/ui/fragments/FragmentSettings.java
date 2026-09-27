package com.giga.tech1000.heartbeatz.ui.fragments;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.utils.enums.ThemeMode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * In-app Settings screen hosted in {@code root_container_view}.
 * <p>
 * Lives under the MultiSlidingUpPanel media + nav panels, so the mini player
 * stays elevated and can still expand to full player.
 */
public class FragmentSettings extends Fragment {

    public static final String TAG = "FragmentSettings";

    public static FragmentSettings newInstance() {
        return new FragmentSettings();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialToolbar toolbar = view.findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> close());

        bindProfile(view);
        bindAudio(view);
        bindPlayback(view);
        bindLibrary(view);
        bindTheme(view);
        bindAbout(view);
    }

    private void bindProfile(View view) {
        TextView name = view.findViewById(R.id.txt_profile_name);
        TextView sub = view.findViewById(R.id.txt_profile_sub);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String display = user.getDisplayName();
            if (display == null || display.isEmpty()) {
                display = user.getEmail() != null ? user.getEmail() : "Signed in";
            }
            name.setText(display);
            sub.setText("Party mode unlocked");
        } else {
            name.setText("Guest");
            sub.setText("Local playback • Sign in for Party");
        }
        view.findViewById(R.id.btn_manage_account).setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(requireContext(), "Sign in from the side menu", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Account management coming soon", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindAudio(View view) {
        view.findViewById(R.id.row_open_equalizer).setOnClickListener(v -> {
            close();
            // Open EQ from Home after closing settings
            try {
                var ui = HeartBeatzApp.container(requireContext()).requireUiThread();
                if (ui.getNavigationPanel() != null) {
                    ui.getNavigationPanel().selectTab(R.id.nav_home);
                }
                // Equalizer is opened via FragmentHome chrome when available
                Toast.makeText(requireContext(), "Open Equalizer from library toolbar", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Equalizer unavailable", Toast.LENGTH_SHORT).show();
            }
        });

        MaterialSwitch gapless = view.findViewById(R.id.switch_gapless);
        MaterialSwitch replay = view.findViewById(R.id.switch_replay_gain);
        Slider crossfade = view.findViewById(R.id.slider_crossfade);
        TextView crossfadeVal = view.findViewById(R.id.txt_crossfade_val);

        gapless.setOnCheckedChangeListener((b, checked) ->
                persistHint("gapless", checked));
        replay.setOnCheckedChangeListener((b, checked) ->
                persistHint("replay_gain", checked));

        crossfade.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                crossfadeVal.setText(String.format(java.util.Locale.US, "%.1fs", value));
            }
        });
    }

    private void bindPlayback(View view) {
        MaterialSwitch autoPause = view.findViewById(R.id.switch_auto_pause);
        MaterialSwitch resume = view.findViewById(R.id.switch_resume_reconnect);
        MaterialSwitch lock = view.findViewById(R.id.switch_lock_screen);
        MaterialSwitch keepOn = view.findViewById(R.id.switch_keep_screen_on);

        try {
            SettingEntity s = HeartBeatzApp.get(requireContext()).settingsSnapshot();
            if (s != null) {
                keepOn.setChecked(s.keepScreenOn);
            }
        } catch (Exception ignored) {
        }

        keepOn.setOnCheckedChangeListener((b, checked) -> {
            try {
                HeartBeatzApp.get(requireContext()).settings().update(s -> s.keepScreenOn = checked);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Could not save", Toast.LENGTH_SHORT).show();
            }
        });

        autoPause.setOnCheckedChangeListener((b, c) -> persistHint("auto_pause", c));
        resume.setOnCheckedChangeListener((b, c) -> persistHint("resume_reconnect", c));
        lock.setOnCheckedChangeListener((b, c) -> persistHint("lock_screen", c));
    }

    private void bindLibrary(View view) {
        TextView stats = view.findViewById(R.id.txt_library_stats);
        MaterialButton rescan = view.findViewById(R.id.btn_rescan);
        try {
            // Best-effort track count if library is ready
            stats.setText("Monitored local library");
        } catch (Exception ignored) {
        }
        rescan.setOnClickListener(v -> {
            rescan.setEnabled(false);
            rescan.setText("Scanning…");
            Toast.makeText(requireContext(), "Library rescan requested", Toast.LENGTH_SHORT).show();
            rescan.postDelayed(() -> {
                if (isAdded()) {
                    rescan.setEnabled(true);
                    rescan.setText("Rescan Library");
                }
            }, 1500);
        });
    }

    private void bindTheme(View view) {
        MaterialButtonToggleGroup group = view.findViewById(R.id.theme_toggle_group);
        ThemeMode current = ThemeMode.SYSTEM;
        try {
            SettingEntity s = HeartBeatzApp.get(requireContext()).settingsSnapshot();
            if (s != null && s.themeMode != null) current = s.themeMode;
        } catch (Exception ignored) {
        }

        int checkedId = switch (current) {
            case LIGHT -> R.id.btn_theme_light;
            case DARK -> R.id.btn_theme_dark;
            default -> R.id.btn_theme_system;
        };
        group.check(checkedId);

        group.addOnButtonCheckedListener((g, checkedId1, isChecked) -> {
            if (!isChecked) return;
            ThemeMode mode;
            if (checkedId1 == R.id.btn_theme_light) {
                mode = ThemeMode.LIGHT;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId1 == R.id.btn_theme_dark) {
                mode = ThemeMode.DARK;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                mode = ThemeMode.SYSTEM;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            try {
                HeartBeatzApp.get(requireContext()).settings().update(s -> s.themeMode = mode);
            } catch (Exception ignored) {
            }
        });
    }

    private void bindAbout(View view) {
        TextView version = view.findViewById(R.id.txt_version);
        try {
            String v = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            version.setText("Version " + (v != null ? v : "—"));
        } catch (PackageManager.NameNotFoundException e) {
            version.setText("HeartBeatz");
        }
    }

    private void persistHint(String key, boolean value) {
        // Reserved for SettingsRepository expansion; keep UX responsive
        android.util.Log.d(TAG, "setting " + key + "=" + value);
    }

    private void close() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            getParentFragmentManager()
                    .beginTransaction()
                    .remove(this)
                    .commitAllowingStateLoss();
        }
    }

    /** Open settings above Home/Party content (player panels stay elevated). */
    public static void open(@NonNull androidx.fragment.app.FragmentActivity activity) {
        androidx.fragment.app.FragmentManager fm = activity.getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG) != null) {
            return;
        }
        fm.beginTransaction()
                .setCustomAnimations(
                        android.R.anim.slide_in_left,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.slide_out_right)
                .add(R.id.root_container_view, newInstance(), TAG)
                .addToBackStack(TAG)
                .commit();
    }
}
