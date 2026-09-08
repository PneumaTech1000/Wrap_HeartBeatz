package com.giga.tech1000.media_player.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;


@Entity(
        tableName = "folders",
        indices = { @Index(value = {"path"}, unique = true) }
)
public class Folder {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "folderId")
    public long id;
    private  String path;
    private  String name;
    private int songCount;
    public Folder(String name, String path) {
        this.name = name;
        this.path = path;
        this.songCount = 1;
    }

    public Folder() {}



    public String getName() {
        return name;
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    @NonNull
    @Override
    public String toString() {
        return "Folder{" +
                "id=" + id +
                ", path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", songCount=" + songCount +
                '}';
    }
}
