package com.giga.tech1000.heartbeatz.interfaces;

import android.net.Uri;

import com.giga.tech1000.media_player.models.Playlist;

public interface MediaNavigation {
    void openAlbum(Uri albumUri, String title, long albumId);
    void openArtist(Uri artistUri, String title, long artistId);
    void openGenre(Uri genreUri, String title, long genreId);
    void openPlaylist(String title, long playlistId);
    void openFolder(String name, String path);

    /** B4: start album in disc/track order without opening the detail panel. */
    void playAlbumNow(long albumId);

    /** B4: start folder queue (title order). */
    void playFolderNow(long folderId);

    void openSearchDialog();
    void openMoreInSong(int songId);

    void renamePlaylist(long id, String name);
    void deletePlaylist(Playlist playlist);


}
