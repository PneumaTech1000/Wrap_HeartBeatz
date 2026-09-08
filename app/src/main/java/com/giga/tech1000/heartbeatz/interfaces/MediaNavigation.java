package com.giga.tech1000.heartbeatz.interfaces;

import android.net.Uri;

import com.giga.tech1000.media_player.models.Playlist;

public interface MediaNavigation {
    void openAlbum(Uri albumUri, String title, long albumId);
    void openArtist(Uri artistUri, String title, long artistId);
    void openGenre(Uri genreUri, String title, long genreId);
    void openPlaylist(String title, long playlistId);
    void openFolder(String name, String path);

    void openSearchDialog();
    void openMoreInSong(int songId);

    void renamePlaylist(long id, String name);
    void deletePlaylist(Playlist playlist);


}
