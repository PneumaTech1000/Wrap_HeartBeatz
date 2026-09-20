package com.giga.tech1000.heartbeatz.ui;

import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * Coordinates Material bottom sheet (player) with Material bottom navigation.
 *
 * <ul>
 *   <li>Player EXPANDED → hide bottom nav (full immersive player)</li>
 *   <li>Player COLLAPSED (mini) → show bottom nav</li>
 *   <li>Player HIDDEN → show bottom nav</li>
 * </ul>
 */
public final class PlayerChromeController {

    @Nullable
    private static BottomNavigationView bottomNav;

    private PlayerChromeController() {}

    public static void bindBottomNav(@Nullable BottomNavigationView nav) {
        bottomNav = nav;
    }

    public static void onSheetStateChanged(int sheetState) {
        if (bottomNav == null) return;

        boolean fullPlayer = sheetState == BottomSheetBehavior.STATE_EXPANDED
                || sheetState == BottomSheetBehavior.STATE_HALF_EXPANDED;

        UIInfoLog.d("PlayerChrome", "sheetState=" + sheetState + " hideNav=" + fullPlayer);

        if (fullPlayer) {
            if (bottomNav.getVisibility() != View.GONE) {
                bottomNav.animate().cancel();
                bottomNav.animate()
                        .translationY(bottomNav.getHeight())
                        .alpha(0f)
                        .setDuration(180)
                        .withEndAction(() -> {
                            bottomNav.setVisibility(View.GONE);
                            bottomNav.setTranslationY(0f);
                            bottomNav.setAlpha(1f);
                        })
                        .start();
            }
        } else {
            if (bottomNav.getVisibility() != View.VISIBLE) {
                bottomNav.setVisibility(View.VISIBLE);
                bottomNav.setAlpha(0f);
                bottomNav.setTranslationY(bottomNav.getHeight() > 0 ? bottomNav.getHeight() : 80f);
                bottomNav.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(180)
                        .start();
            }
        }

        // Content padding: notify active tab fragment via nav controller if available
        if (UIThreadBridge.getNav() != null) {
            boolean miniVisible = sheetState == BottomSheetBehavior.STATE_COLLAPSED;
            UIThreadBridge.getNav().updatePaddingWhenWhenBarChanged(miniVisible);
        }
    }
}
