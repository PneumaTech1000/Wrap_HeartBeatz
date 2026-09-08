package com.giga.tech1000.media_player.models.cross_ref;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.Song;

import java.util.List;

public class FolderWithSongs {
    @Embedded
    public Folder folder;

    @Relation(
            parentColumn = "path",
            entityColumn = "folder"
    )

    public List<Song> songs;

}
