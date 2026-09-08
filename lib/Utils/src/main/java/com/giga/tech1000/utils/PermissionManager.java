package com.giga.tech1000.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A robust, production-ready manager for handling runtime permissions in a music player app.
 * It uses the modern Activity Result APIs and handles all relevant Android versions.
 */
public class PermissionManager {

    private final ComponentActivity activity;
    private final PermissionCallback callback;
    private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher;

    /**
     * The callback interface to notify the calling Activity/Fragment about permission results.
     */
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
        void onPermissionsDeniedPermanently();
    }

    public PermissionManager(ComponentActivity activity, PermissionCallback callback) {
        this.activity = activity;
        this.callback = callback;
        this.requestMultiplePermissionsLauncher = registerPermissionLauncher();
    }

    public PermissionManager() {
        this.activity = null;
        this.callback = null;
        this.requestMultiplePermissionsLauncher = null;
    }

    private ActivityResultLauncher<String[]> registerPermissionLauncher() {
        return activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
            boolean allGranted = true;
            for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                if (!entry.getValue()) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                callback.onPermissionsGranted();
            } else {
                // Check if any permission was permanently denied ("Don't ask again").
                boolean permanentlyDenied = false;
                for (String perm : permissions.keySet()) {
                    if (!activity.shouldShowRequestPermissionRationale(perm)) {
                        permanentlyDenied = true;
                        break;
                    }
                }

                if (permanentlyDenied) {
                    callback.onPermissionsDeniedPermanently();
                } else {
                    callback.onPermissionsDenied();
                }
            }
        });
    }

    /**
     * Checks if all required permissions for the app to function are granted.
     * @return true if all permissions are granted, false otherwise.
     */
    public boolean hasRequiredPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Requests the necessary permissions based on the Android API level.
     */
    public void requestRequiredPermissions() {
        requestMultiplePermissionsLauncher.launch(getRequiredPermissions());
    }

    /**
     * A helper method to create an intent that opens the app's specific settings screen,
     * allowing the user to grant permissions manually.
     * @param context The application context.
     * @return An Intent to the app's settings screen.
     */
    public static Intent getAppSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        return intent;
    }

    /**
     * Determines which permissions are required based on the device's Android version.
     * @return An array of permission strings to be requested.
     */
    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // For Android 13 (TIRAMISU) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            // On Android 13+, READ_EXTERNAL_STORAGE is replaced by READ_MEDIA_*
            // but we still might need it for some legacy file access if targeting lower APIs.
            // However, READ_MEDIA_AUDIO is the primary one for music.
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            permissions.add(Manifest.permission.RECORD_AUDIO);
            permissions.add(Manifest.permission.CAMERA);
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        // For Android 6 (M) up to Android 12
        else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.RECORD_AUDIO);
            permissions.add(Manifest.permission.CAMERA);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        return permissions.toArray(new String[0]);
    }
}
