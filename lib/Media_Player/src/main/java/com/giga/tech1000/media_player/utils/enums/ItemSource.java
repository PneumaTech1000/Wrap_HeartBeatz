package com.giga.tech1000.media_player.utils.enums;

public enum ItemSource {
    ALL_SONGS("all_songs"),
    ALBUMS("albums"),
    ARTISTS("artists"),
    GENRES("genres"),
    FOLDERS("folders"),
    PLAYLISTS("playlists"),
    SEARCH("search"),
    BOTTOM_SHEET_QUEUE("bottom_sheet_queue"),
    MEDIA_DETAILS_WITHOUT_IMG("media_details_without_img"),
    MEDIA_DETAILS_WITH_IMG("media_details_with_img"),
    NONE("none"),
    SEARCH_RESULTS("search_results");

    private final String value;

    ItemSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // 🔥 Reverse mapping
    public static ItemSource fromValue(String value) {
        for (ItemSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        return NONE; // fallback (important)
    }
}
