package com.giga.tech1000.heartbeatz.observers;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.ui.adapters.models.AlbumViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.ArtistViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.FolderViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.GenreViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item.CreatePlaylistViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.playlist_item.PlaylistViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.SongViewItem;
import com.giga.tech1000.heartbeatz.ui.adapters.models.BaseRecyclerViewItem;
import com.giga.tech1000.heartbeatz.view_models.AlbumsViewModel;
import com.giga.tech1000.heartbeatz.view_models.ArtistsViewModel;
import com.giga.tech1000.heartbeatz.view_models.FoldersViewModel;
import com.giga.tech1000.heartbeatz.view_models.GenresViewModel;
import com.giga.tech1000.heartbeatz.view_models.PlaylistsViewModel;
import com.giga.tech1000.heartbeatz.view_models.SongsViewModel;
import com.giga.tech1000.media_player.models.Album;
import com.giga.tech1000.media_player.models.Artist;
import com.giga.tech1000.media_player.models.Folder;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.cross_ref.GenreWithCount;
import com.giga.tech1000.media_player.models.cross_ref.PlaylistWithCount;
import com.giga.tech1000.media_player.repository.SongRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class LibraryObservers {

    private final MediatorLiveData<LibraryState> state = new MediatorLiveData<>();
    private LibraryState currentSnapshot = new LibraryState();

    private boolean songsReady;
    private boolean albumsReady;
    private boolean artistsReady;
    private boolean genresReady;
    private boolean playlistsReady;
    private boolean foldersReady;

    private final MutableLiveData<List<Song>> songs = new MutableLiveData<>();
    private PlaylistsViewModel playlistsViewModel;
    public TreeMap<Integer, Song> treeMapOfSongs = new TreeMap<>();

    public LiveData<LibraryState> getState() { return state; }
    public LiveData<List<Song>> getSongs() { return songs; }
    public PlaylistsViewModel getPlaylistViewModel() { return playlistsViewModel; }

    public boolean isReady() {
        return songsReady && albumsReady && artistsReady && genresReady && playlistsReady && foldersReady;
    }

    public void bind(
            SongsViewModel songsVm,
            AlbumsViewModel albumsVm,
            ArtistsViewModel artistsVm,
            GenresViewModel genresVm,
            FoldersViewModel foldersVm,
            PlaylistsViewModel playlistsVm
    ) {
        this.playlistsViewModel = playlistsVm;

        state.addSource(songsVm.getSongs(), songs -> {
            songsReady = true;
            this.songs.setValue(songs);
            SongRepository.getInstance().setSongs(songs);
            
            LibraryState nextState = new LibraryState(currentSnapshot);
            nextState.setSongs(mapSongs(songs));
            emit(nextState);
        });

        state.addSource(albumsVm.getAlbums(), albums -> {
            albumsReady = true;
            LibraryState nextState = new LibraryState(currentSnapshot);
            nextState.setAlbums(mapAlbums(albums));
            emit(nextState);
        });

        state.addSource(artistsVm.getArtists(), artists -> {
            artistsReady = true;
            LibraryState nextState = new LibraryState(currentSnapshot);
            nextState.setArtists(mapArtists(artists));
            emit(nextState);
        });

        state.addSource(genresVm.getGenreWithCount(), genres -> {
            genresReady = true;
            LibraryState nextState = new LibraryState(currentSnapshot);
            nextState.setGenres(mapGenres(genres));
            emit(nextState);
        });

        state.addSource(playlistsVm.getPlaylists(), playlists -> {
            playlistsReady = true;
            LibraryState nextState = new LibraryState(currentSnapshot);
            nextState.setPlaylists(mapPlaylists(playlists));
            emit(nextState);
        });

        state.addSource(foldersVm.getFolders(), folders -> {
            foldersReady = true;
            LibraryState nextState = new LibraryState(currentSnapshot);
            nextState.setFolders(mapFolders(folders));
            emit(nextState);
        });
    }

    private void emit(LibraryState nextState) {
        currentSnapshot = nextState;
        state.setValue(nextState);
    }

    private List<BaseRecyclerViewItem> mapSongs(List<Song> songs) {
        if (songs == null) return Collections.emptyList();
        List<BaseRecyclerViewItem> items = new ArrayList<>(songs.size());
        for (Song song : songs) {
            if (song != null) items.add(new SongViewItem(song));
        }
        return items;
    }

    private List<BaseRecyclerViewItem> mapAlbums(List<Album> albums) {
        if (albums == null) return Collections.emptyList();
        List<BaseRecyclerViewItem> items = new ArrayList<>(albums.size());
        for (Album album : albums) {
            if (album != null) items.add(new AlbumViewItem(album));
        }
        return items;
    }

    private List<BaseRecyclerViewItem> mapArtists(List<Artist> artists) {
        if (artists == null) return Collections.emptyList();
        List<BaseRecyclerViewItem> items = new ArrayList<>(artists.size());
        for (Artist artist : artists) {
            if (artist != null) items.add(new ArtistViewItem(artist));
        }
        return items;
    }

    private List<BaseRecyclerViewItem> mapGenres(List<GenreWithCount> genres) {
        if (genres == null) return Collections.emptyList();
        List<BaseRecyclerViewItem> items = new ArrayList<>(genres.size());
        for (GenreWithCount genre : genres) {
            if (genre != null) items.add(new GenreViewItem(genre));
        }
        return items;
    }

    private List<BaseRecyclerViewItem> mapPlaylists(List<PlaylistWithCount> playlists) {
        List<BaseRecyclerViewItem> items = new ArrayList<>();
        items.add(new CreatePlaylistViewItem());
        if (playlists != null) {
            for (PlaylistWithCount playlist : playlists) {
                if (playlist != null) items.add(new PlaylistViewItem(playlist));
            }
        }
        return items;
    }

    private List<BaseRecyclerViewItem> mapFolders(List<Folder> folders) {
        if (folders == null) return Collections.emptyList();
        List<BaseRecyclerViewItem> items = new ArrayList<>(folders.size());
        for (Folder folder : folders) {
            if (folder != null) items.add(new FolderViewItem(folder));
        }
        return items;
    }

    public TreeMap<Integer, Song> getTreeMapOfSongs() { return SongRepository.getInstance().getCachedSongs(); }
}
