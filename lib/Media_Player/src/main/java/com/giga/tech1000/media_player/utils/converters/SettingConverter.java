package com.giga.tech1000.media_player.utils.converters;

import androidx.room.TypeConverter;

import com.giga.tech1000.media_player.utils.enums.ItemSource;
import com.giga.tech1000.media_player.utils.enums.RepeatMode;
import com.giga.tech1000.media_player.utils.enums.ShuffleMode;
import com.giga.tech1000.media_player.utils.enums.ThemeMode;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import androidx.room.TypeConverter;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class SettingConverter {

    private static final Gson gson = new Gson();
    private static final Type INT_LIST_TYPE =
            new TypeToken<List<Integer>>() {}.getType();

    // ===== List<Integer> =====

    @TypeConverter
    public static String fromIntList(List<Integer> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return gson.toJson(list, INT_LIST_TYPE);
    }

    @TypeConverter
    public static List<Integer> toIntList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        return gson.fromJson(json, INT_LIST_TYPE);
    }

    // ===== RepeatMode =====
    @TypeConverter
    public static int fromRepeatMode(RepeatMode mode) {
        return mode.toMedia3();
    }

    @TypeConverter
    public static RepeatMode toRepeatMode(int value) {
        return RepeatMode.fromMedia3(value);
    }

    // ===== ThemeMode stays string-based =====
    @TypeConverter
    public static String fromThemeMode(ThemeMode mode) {
        return mode.name();
    }

    @TypeConverter
    public static ThemeMode toThemeMode(String value) {
        return ThemeMode.valueOf(value);
    }

    // ===== ShuffleMode =====
    @TypeConverter
    public static boolean fromShuffleMode(ShuffleMode mode) {
        return mode.toBoolean();
    }

    @TypeConverter
    public static ShuffleMode toShuffleMode(boolean value) {
        return ShuffleMode.fromBoolean(value);
    }

    // ===== ItemSource =====
    @TypeConverter
    public static String fromItemSource(ItemSource source) {
        return source.getValue();
    }

    @TypeConverter
    public static ItemSource toItemSource(String value) {
        return ItemSource.fromValue(value);
    }
}
