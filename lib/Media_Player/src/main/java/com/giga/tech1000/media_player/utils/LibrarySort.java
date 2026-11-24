package com.giga.tech1000.media_player.utils;

import androidx.annotation.NonNull;

import com.giga.tech1000.media_player.models.Song;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Stable library ordering for albums, folders, and queues.
 */
public final class LibrarySort {

    private LibrarySort() {}

    /** Album order: disc → track → title. */
    @NonNull
    public static Comparator<Song> albumTrackOrder() {
        return Comparator
                .comparingInt(Song::getEffectiveDiscOrder)
                .thenComparingInt(Song::getEffectiveTrackOrder)
                .thenComparing(s -> safeTitle(s), String.CASE_INSENSITIVE_ORDER);
    }

    /** Folder / generic list: title only. */
    @NonNull
    public static Comparator<Song> titleOrder() {
        return Comparator.comparing(LibrarySort::safeTitle, String.CASE_INSENSITIVE_ORDER);
    }

    public static void sortAlbumTracks(@NonNull List<Song> songs) {
        songs.sort(albumTrackOrder());
    }

    public static void sortByTitle(@NonNull List<Song> songs) {
        songs.sort(titleOrder());
    }

    @NonNull
    private static String safeTitle(@NonNull Song s) {
        if (s.getTitle() != null && !s.getTitle().isEmpty()) return s.getTitle();
        if (s.getDisplayName() != null) return s.getDisplayName();
        return "";
    }

    @NonNull
    public static String formatTrackLabel(@NonNull Song s) {
        int disc = s.getEffectiveDiscOrder();
        int track = s.getEffectiveTrackOrder();
        if (track >= Integer.MAX_VALUE / 4) return "";
        if (disc > 1) {
            return String.format(Locale.US, "%d-%02d", disc, track);
        }
        return String.format(Locale.US, "%02d", track);
    }
}
