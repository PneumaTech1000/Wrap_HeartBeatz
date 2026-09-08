package com.giga.tech1000.media_player.utils.converters;

import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Converter {

    /* ------------------------------------------------------------
     * SONG → SONG 
     * ------------------------------------------------------------ */

    public static List<Song> toSong(List<Song> songs) {
        List<Song> list = new ArrayList<>();

        for (Song s : songs) {
            Song e = new Song();
            e.setId(s.getId());
            e.setTitle(s.getTitle());
            e.setDisplayName(s.getDisplayName());
            e.setArtist(s.getArtist());
            e.setArtistId(safeLong(s));
            e.album = s.getAlbum();
            e.setDuration(s.getDuration());
            e.setMimeType(s.getMimeType());
            e.setData(s.getData());
            e.setFolder(s.getFolder());
            e.setDateAdded(s.getDateAdded());

            list.add(e);
        }
        return list;
    }

    /* ------------------------------------------------------------
     * ARTIST HASH → ARTIST ID GENERATOR
     * (MediaStore does not provide consistent artist IDs on some devices)
     * ------------------------------------------------------------ */
    private static long safeLong(Song s) {
        String artistString = s.getArtist() + s.getArtistId();
        return Math.abs((long) Double.parseDouble(artistString));
    }

    /* ------------------------------------------------------------
     * BUILD ALBUM  LIST
     * ------------------------------------------------------------ */

    public static Map<Long, Album> buildAlbums(List<Song> songs) {
        Map<Long, Album> albums = new HashMap<>();

        for (Song s : songs) {
            long id = s.getAlbumId();
            if (!albums.containsKey(id)) {
                Album a = new Album();
                a.setId(id);
                a.setName(s.album != null ? s.album : "Unknown Album");
                a.setArtist(s.artist != null ? s.artist : "Unknown Artist");
                a.setSongCount(1);
                albums.put(id, a);
            } else {
                Album a = albums.get(id);
                a.setSongCount(a.getSongCount() + 1);
            }
        }
        return albums;
    }

    /* ------------------------------------------------------------
     * BUILD ARTIST  LIST
     * ------------------------------------------------------------ */

    public static Map<Long, Artist> buildArtists(List<Song> songs) {
        Map<Long, Artist> map = new HashMap<>();

        for (Song s : songs) {
            long id = s.artistId;

            if (!map.containsKey(id)) {
                Artist a = new Artist();
                a.setId(id);
                a.setName(s.artist != null ? s.artist : "Unknown Artist");
                a.setAlbumCount(0);
                a.setTrackCount(1);
                map.put(id, a);
            } else {
                Artist a = map.get(id);
                a.setTrackCount(a.getTrackCount() + 1);
            }
        }

        // Count albums per artist
        Map<String, Set<Long>> artistAlbums = new HashMap<>();
        for (Song s : songs) {
            artistAlbums
                    .computeIfAbsent(s.getArtist(), x -> new HashSet<>())
                    .add(s.getAlbumId());
        }
        for (Artist a : map.values()) {
            Set<Long> list = artistAlbums.get(a.getName());
            if (list != null) {
                a.setAlbumCount(list.size());
            }
        }

        return map;
    }

    /* ------------------------------------------------------------
     * BUILD FOLDER  LIST
     * ------------------------------------------------------------ */

    public static List<Folder> buildFolders(List<Song> songs) {
        Map<String, Folder> map = new HashMap<>();

        for (Song s : songs) {
            String folder = s.getFolder();
            if (folder == null) continue;

            if (!map.containsKey(folder)) {
                Folder f = new Folder();
                f.setId(folder.hashCode());
                f.setPath(folder);
                f.setName(folder.substring(folder.lastIndexOf('/') + 1));
                f.setSongCount(1);
                map.put(folder, f);
            } else {
                Folder f = map.get(folder);
                f.setSongCount(f.getSongCount() + 1);
            }
        }

        return new ArrayList<>(map.values());
    }
}
