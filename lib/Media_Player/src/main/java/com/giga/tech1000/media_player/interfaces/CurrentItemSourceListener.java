package com.giga.tech1000.media_player.interfaces;

import com.giga.tech1000.media_player.utils.enums.ItemSource;

public interface CurrentItemSourceListener {
    void onItemPositionPlaying(ItemSource source, int queueIndex, boolean isPlaying);
}
