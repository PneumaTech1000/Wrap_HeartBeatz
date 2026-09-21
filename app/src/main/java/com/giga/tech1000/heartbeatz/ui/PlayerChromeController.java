package com.giga.tech1000.heartbeatz.ui;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * Coordinates player expand fraction with bottom navigation visibility.
 * <p>
 * expandFraction: 0 = mini (or hidden), 1 = full player.
 * Nav alpha / translation track the fraction continuously during drag.
 */
public final class PlayerChromeController {

    @Nullable
    private static BottomNavigationView bottomNav;

    private PlayerChromeController() {}

    public static void bindBottomNav(@Nullable BottomNavigationView nav) {
        bottomNav = nav;
    }

    /**
     * Continuous chrome update while user slides the player.
     * @param expandFraction 0 = mini, 1 = full
     */
    public static void onSlide(float expandFraction) {
        if (bottomNav == null) return;
        float e = MathUtils.clamp(expandFraction, 0f, 1f);

        if (bottomNav.getVisibility() != View.VISIBLE) {
            bottomNav.setVisibility(View.VISIBLE);
        }
        bottomNav.animate().cancel();
        float h = bottomNav.getHeight() > 0 ? bottomNav.getHeight() : 80f;
        bottomNav.setTranslationY(h * e);
        bottomNav.setAlpha(1f - e);
        // Fully expanded → hide for touch-through; fully collapsed → ensure interactive
        if (e >= 0.98f) {
            bottomNav.setVisibility(View.INVISIBLE);
            bottomNav.setTranslationY(h);
            bottomNav.setAlpha(0f);
        } else if (e <= 0.02f) {
            bottomNav.setVisibility(View.VISIBLE);
            bottomNav.setTranslationY(0f);
            bottomNav.setAlpha(1f);
        }

        if (UIThreadBridge.getNav() != null) {
            // Mini visible when not fully expanded
            boolean miniOrHidden = e < 0.5f;
            UIThreadBridge.getNav().updatePaddingWhenWhenBarChanged(
                    miniOrHidden || UIThreadBridgePad.isPlayerBarVisible());
        }
    }

    public static void onSheetStateChanged(int sheetState) {
        if (bottomNav == null) return;

        boolean fullPlayer = sheetState == BottomSheetBehavior.STATE_EXPANDED
                || sheetState == BottomSheetBehavior.STATE_HALF_EXPANDED;

        UIInfoLog.d("PlayerChrome", "sheetState=" + sheetState + " hideNav=" + fullPlayer);

        if (fullPlayer) {
            onSlide(1f);
            if (UIThreadBridge.getNav() != null) {
                UIThreadBridge.getNav().updatePaddingWhenWhenBarChanged(false);
            }
        } else if (sheetState == BottomSheetBehavior.STATE_COLLAPSED) {
            onSlide(0f);
            if (UIThreadBridge.getNav() != null) {
                UIThreadBridge.getNav().updatePaddingWhenWhenBarChanged(true);
            }
        } else if (sheetState == BottomSheetBehavior.STATE_HIDDEN) {
            onSlide(0f);
            if (UIThreadBridge.getNav() != null) {
                UIThreadBridge.getNav().updatePaddingWhenWhenBarChanged(false);
            }
        }
    }
}
