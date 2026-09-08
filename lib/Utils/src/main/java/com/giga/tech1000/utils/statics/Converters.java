package com.giga.tech1000.utils.statics;

import android.net.Uri;

import androidx.annotation.NonNull;

public class Converters {

    public static Uri playlistIdToUri(long playlistId) {
        return new Uri.Builder()
                .scheme("app")
                .authority("heartbeatz")
                .appendPath("playlist")
                .appendPath(String.valueOf(playlistId))
                .build();
    }

    public static long uriToPlaylistId(@NonNull Uri uri) {
        if (uri.getLastPathSegment() == null) return -1;
        return Long.parseLong(uri.getLastPathSegment());
    }


}
