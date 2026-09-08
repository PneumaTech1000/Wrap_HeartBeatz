package com.giga.tech1000.media_player;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.repository.SongRepository;
import com.giga.tech1000.media_player.scanners.LocalMediaScannerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class SongObserver extends ContentObserver {

    private final ContentResolver resolver;
    private final LocalMediaScannerManager scanner;
    private final Context context;

    private List<Song> songList = new ArrayList<>();

    public SongObserver(@NonNull Context ctx,
                        @NonNull Handler handler,
                        @NonNull ContentResolver resolver,
                        @NonNull LocalMediaScannerManager scanner) {
        super(handler);
        this.context = ctx;
        this.resolver = resolver;
        this.scanner = scanner;
    }

    // Register the observer
    public void start() {
        resolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                this
        );

    }

    // Unregister observer
    public void stop() {
        resolver.unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        if (scanner != null) {
            scanner.runIncrementalMediaRefresh();
        }
    }

    public List<Song> getSongs() {
        return scanner != null ? scanner.getSongs() : new ArrayList<>();
    }

    /**
     * Refresh songs in database with incremental diff
     */
    private void refreshSongs() {
        // 1️⃣ reloading all items (for temporal use, will be updated later)
        if (scanner != null) {
            scanner.runIncrementalMediaRefresh();
        }

    }


    public TreeMap<Integer, Song> getTreeMapOfSongs(@Nullable List<Song> songs) {
        return SongRepository.getInstance().getCachedSongs();
    }

}
