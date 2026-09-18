package com.giga.tech1000.heartbeatz.interfaces;

import android.net.Uri;

import androidx.annotation.Nullable;

/**
 * Activity-owned drawer API. Fragments open/close the drawer and register for clicks.
 * Keeps DrawerLayout in {@link com.giga.tech1000.heartbeatz.MainActivity} so it stacks
 * above MultiSlidingUpPanel chrome (bottom nav + mini player).
 */
public interface DrawerController {

    void openDrawer();

    void closeDrawer();

    boolean isDrawerOpen();

    void setDrawerListener(@Nullable DrawerListener listener);

    /** Update header + auth footer for guest vs signed-in. */
    void updateDrawerAccount(
            @Nullable String displayName,
            @Nullable String email,
            @Nullable Uri photoUri,
            boolean isGuest
    );

    interface DrawerListener {
        /** @return true if handled (drawer will close). */
        boolean onDrawerNavigationItem(int itemId);

        void onDrawerLoginClicked();

        void onDrawerSignupClicked();

        void onDrawerLogoutClicked();
    }
}
