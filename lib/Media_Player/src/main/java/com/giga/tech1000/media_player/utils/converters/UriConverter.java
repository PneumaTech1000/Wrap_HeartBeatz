package com.giga.tech1000.media_player.utils.converters;

import android.net.Uri;

import androidx.room.TypeConverter;

public class UriConverter {

    @TypeConverter
    public static Uri toUri(String value) {
        if (value == null) return null;
        return Uri.parse(value);
    }

    @TypeConverter
    public static String fromUri(Uri uri) {
        if (uri == null) return null;
        return uri.toString();
    }
}

