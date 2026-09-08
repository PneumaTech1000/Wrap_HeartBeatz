package com.giga.tech1000.heartbeatz.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.database.Cursor;
import android.view.View;
import android.widget.Toast;

import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.utils.enums.FavoriteType;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;

public class FileUtils {

    public static final int DELETE_REQUEST_CODE = 1001;

    public interface AddToInterface {
        void addToPlaylist();
        void addToFavorite();
    }

    // =========================
    // SHARE AUDIO (MediaStore URI ONLY)
    // =========================
    public static void share(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);

        String mime = resolveMimeType(context, uri);
        intent.setType(mime);

        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Critical for cross-app compatibility
        intent.setClipData(ClipData.newRawUri("audio", uri));

        context.startActivity(Intent.createChooser(intent, "Share audio"));
    }

    // =========================
    // SET AS RINGTONE
    // =========================
    public static boolean setAsRingtone(@NonNull Context context, @NonNull Uri uri) {

        if (!Settings.System.canWrite(context)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return false;
        }

        try {
            // Mark existing MediaStore item as ringtone
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
            values.put(MediaStore.Audio.Media.IS_ALARM, false);
            values.put(MediaStore.Audio.Media.IS_MUSIC, true);

            context.getContentResolver().update(uri, values, null, null);

            RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_RINGTONE,
                    uri
            );

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    // =========================
    // ADD TO (PLAYLIST OR FAVORITE)
    // ========================

    public static void addToPlaylist(@NonNull Context context,
                                     @NonNull String[] options,
                                     @NonNull AddToInterface listener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Add to")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                       listener.addToPlaylist();
                    } else {
                        listener.addToFavorite();
                    }
                })
                .show();
    }

    public interface OnDeleteListener {
        void onDeleteSuccess();
        void onDeleteFailed();
    }

    // =========================
    // PERMANENT DELETE FROM DEVICE (SAFE + MODERN)
    // =========================
    public static void deleteAudio(@NonNull FragmentHome activity, @NonNull View view, @NonNull Uri uri, @Nullable OnDeleteListener listener) {
        Context context = activity.requireContext();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                        context.getContentResolver(),
                        Collections.singletonList(uri)
                );

                activity.requestDelete(pendingIntent);
                // Listener will be handled via ActivityResult in FragmentHome

            } else {
                // Legacy delete
                int rows = context.getContentResolver().delete(uri, null, null);

                if (rows > 0) {
                    Snackbar.make(
                            view,
                            "Deleted successfully",
                            Snackbar.LENGTH_SHORT
                    ).show();
                    if (listener != null) listener.onDeleteSuccess();
                } else {
                    Snackbar.make(
                            view,
                            "Delete failed",
                            Snackbar.LENGTH_SHORT
                    ).show();
                    if (listener != null) listener.onDeleteFailed();
                }
            }

        } catch (SecurityException se) {
            se.printStackTrace();
            Snackbar.make(
                    view,
                    "Permission required to delete file",
                    Snackbar.LENGTH_LONG
            ).show();
            if (listener != null) listener.onDeleteFailed();

        } catch (Exception e) {
            e.printStackTrace();
            Snackbar.make(
                    view,
                    "Unexpected error while deleting",
                    Snackbar.LENGTH_SHORT
            ).show();
            if (listener != null) listener.onDeleteFailed();
        }
    }

    // =========================
    // RESOLVE MIME TYPE
    // =========================
    private static String resolveMimeType(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        String type = resolver.getType(uri);
        return type != null ? type : "audio/*";
    }

    // =========================
    // GET DISPLAY NAME (instead of file path)
    // =========================
    public static String getDisplayName(@NonNull Context context, @NonNull Uri uri) {
        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{MediaStore.Audio.Media.DISPLAY_NAME},
                null,
                null,
                null
        );

        if (cursor != null) {
            int index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            if (cursor.moveToFirst()) {
                String name = cursor.getString(index);
                cursor.close();
                return name;
            }
            cursor.close();
        }

        return "Unknown";
    }

    // =========================
    // GET "PATH" (BEST EFFORT)
    // =========================
    public static String getDataColumn(@NonNull Context context, @NonNull Uri uri) {
        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{MediaStore.Audio.Media.DATA},
                null,
                null,
                null
        );

        if (cursor != null) {
            int index = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            if (index != -1 && cursor.moveToFirst()) {
                String path = cursor.getString(index);
                cursor.close();
                return path;
            }
            cursor.close();
        }

        return uri.toString(); // fallback (modern-safe)
    }

    // =========================
    // COPY "PATH" / URI
    // =========================
    public static void copyToClipboard(@NonNull Context context, @NonNull Uri uri, @NonNull View anchor) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        String text = getDataColumn(context, uri);

        ClipData clip = ClipData.newPlainText("Audio", text);
        clipboard.setPrimaryClip(clip);

        Snackbar.make(anchor, "Copied to clipboard", Snackbar.LENGTH_SHORT)
                .setAnchorView(anchor) // safe even if not using CoordinatorLayout
                .show();
    }
}