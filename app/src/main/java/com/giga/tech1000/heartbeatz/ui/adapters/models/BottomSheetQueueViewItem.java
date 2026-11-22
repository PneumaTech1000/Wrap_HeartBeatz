package com.giga.tech1000.heartbeatz.ui.adapters.models;

import android.net.Uri;

import com.giga.tech1000.media_player.models.Song;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BottomSheetQueueViewItem extends BaseRecyclerViewItem {

    private Song song;
    /** When non-null, row is from shared party queue (DJ mode). */
    private com.giga.tech1000.heartbeatz.architecture.party.PartyQueueItem partyItem;

    public BottomSheetQueueViewItem(Song song) {
        super(song.getTitle(), ItemType.BOTTOM_SHEET_SONG);
        this.song = song;
    }

    public BottomSheetQueueViewItem(
            Song song,
            com.giga.tech1000.heartbeatz.architecture.party.PartyQueueItem partyItem) {
        super(song.getTitle(), ItemType.BOTTOM_SHEET_SONG);
        this.song = song;
        this.partyItem = partyItem;
    }

    @androidx.annotation.Nullable
    public com.giga.tech1000.heartbeatz.architecture.party.PartyQueueItem getPartyItem() {
        return partyItem;
    }

    public Uri getUri() {
        return song.getUri();
    }


    public Song getSong() {
        return song;
    }

    @Override
    public List<String> searchTokens() {
        return Arrays.asList(
                song.getTitle(),
                song.getArtist(),
                song.getAlbum(),
                song.getDisplayName()
        );
    }

    @Override
    public int hashcode() {
        return getUri().hashCode() + getTitle().hashCode();
    }

    @Override
    public int getId() {
        return (int) song.getId();
    }

}
