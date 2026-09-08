package com.giga.tech1000.heartbeatz.ui;

import android.net.Uri;

import androidx.activity.result.IntentSenderRequest;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.media3.common.util.Log;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.ui.fragments.dialogs.CreatePlaylistDialogFragment;
import com.giga.tech1000.heartbeatz.ui.fragments.dialogs.RenamePlaylistDialogFragment;
import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Playlist;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.MediaDetail;
import com.giga.tech1000.heartbeatz.interfaces.MediaNavigation;
import com.giga.tech1000.utils.statics.Converters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class MediaNavigationManager implements MediaNavigation {

    private final FragmentHome fragment;
    private final MotionLayout motionLayout;
    private int paddingHeight;
    public MediaNavigationManager(FragmentHome fragment, MotionLayout layout) {
        this.fragment = fragment;
        this.motionLayout = layout;
    }

    public void setBottomPadding(int padding) {
        this.paddingHeight = padding;
    }

    private void showCreatePlaylistDialog() {

        CreatePlaylistDialogFragment dialog =
                CreatePlaylistDialogFragment.newInstance();

        dialog.setCallback(name -> {
            UIThread.getInstance()
                    .getScannerManager()
                    .getRepository()
                    .createPlaylist(name, playlistId -> this.openPlaylist(name, playlistId));

        });

        dialog.show(
                fragment.getChildFragmentManager(),
                "create_playlist_dialog"
        );
    }


    public void getSongsFromAlbumId(long albumId, Consumer<List<Song>> callback) {
        UIThread.getInstance().getScannerManager().getRepository()
                .getAlbumWithSongs(albumId)
                .observe(fragment.getViewLifecycleOwner(), albumWithSongs -> {
                    if (albumWithSongs != null) {
                        callback.accept(new ArrayList<>(albumWithSongs.getSongs()));
                    } else {
                        callback.accept(Collections.emptyList());
                    }
                });
    }

    public void getSongsFromArtistId(long artistId, Consumer<List<Song>> callback) {
        UIThread.getInstance().getScannerManager().getRepository()
                .getArtistWithSongs(artistId)
                .observe(fragment.getViewLifecycleOwner(), artistWithSongs -> {
                    if (artistWithSongs != null) {
                        callback.accept(new ArrayList<>(artistWithSongs.getSongs()));
                    } else {
                        callback.accept(Collections.emptyList());
                    }
                });
    }

    public void getSongsFromGenreId(long genreId, Consumer<List<Song>> callback) {
        UIThread.getInstance().getScannerManager().getRepository()
                .getGenreWithSongs(genreId)
                .observe(fragment.getViewLifecycleOwner(), genreWithSongs -> {
                    if (genreWithSongs != null) {
                        callback.accept(new ArrayList<>(genreWithSongs.getSongs()));
                    } else {
                        callback.accept(Collections.emptyList());
                    }
                });
    }

    public void getSongsFromPlaylistId(long playlistId, Consumer<List<Song>> callback) {
        UIThread.getInstance().getScannerManager().getRepository()
                .getPlaylistWithSongs(playlistId)
                .observe(fragment.getViewLifecycleOwner(), playlistWithSongs -> {
                    if (playlistWithSongs != null) {
                        callback.accept(new ArrayList<>(playlistWithSongs.getSongs()));
                    } else {
                        callback.accept(Collections.emptyList());
                    }
                });
    }

    public void getSongsFromFolderPath(String folder, Consumer<List<Song>> callback) {
        UIThread.getInstance().getScannerManager().getRepository()
                .getSongsByFolder(folder)
                .observe(fragment.getViewLifecycleOwner(), folderWithSongs -> {
                    if (folderWithSongs != null) {
                        callback.accept(new ArrayList<>(folderWithSongs.songs));
                    } else {
                        callback.accept(Collections.emptyList());
                    }
                });
    }

    private void displayMediaDetailsWithImgPanel() {
        Log.d("TAG", "displayMediaDetailsWithImgPanel: IS NULL");
        if (motionLayout == null) return;
        if (fragment.getMediaDetailsWithImgPanel() != null)
            fragment.getMediaDetailsWithImgPanel().setBottomPadding(paddingHeight);
        motionLayout.transitionToState(R.id.with_image);
    }

    private void displayMediaDetailsWithoutImgPanel() {
        if (motionLayout == null) return;
        if (fragment.getMediaDetailsWithoutImgPanel() != null)
            fragment.getMediaDetailsWithoutImgPanel().setBottomPadding(paddingHeight);
        motionLayout.transitionToState(R.id.without_image);
    }

    public void displaySongInfoPanel() {
        if (motionLayout == null) return;
        if (fragment.getSongInfoPanel() != null) fragment.getSongInfoPanel().setBottomPadding(paddingHeight);
        motionLayout.transitionToState(R.id.song_info_page);
    }

    @Override
    public void openAlbum(Uri albumUri, String title, long albumId) {
        this.getSongsFromAlbumId(albumId, songs -> {
            MediaDetail mediaDetail = new MediaDetail(MediaDetail.Type.ALBUM_SONG, albumUri, title, songs);
            fragment.getMediaDetailsWithImgPanel().addDetails(mediaDetail);
            displayMediaDetailsWithImgPanel();
        });
    }

    @Override
    public void openArtist(Uri artistUri, String title, long artistId) {

        this.getSongsFromArtistId(artistId, songs -> {
            MediaDetail mediaDetail = new MediaDetail(MediaDetail.Type.ARTIST_SONG, artistUri, title, songs);
            fragment.getMediaDetailsWithImgPanel().addDetails(mediaDetail);
            displayMediaDetailsWithImgPanel();
        });
    }

    @Override
    public void openGenre(Uri genreUri, String title, long genreId) {

        this.getSongsFromGenreId(genreId, songs -> {
            MediaDetail mediaDetail = new MediaDetail(MediaDetail.Type.GENRE_SONG, genreUri, title, songs);
            fragment.getMediaDetailsWithImgPanel().addDetails(mediaDetail);
            displayMediaDetailsWithImgPanel();
        });
    }

    @Override
    public void openPlaylist(String title, long playlistId) {
        this.getSongsFromPlaylistId(playlistId, songs -> {
            MediaDetail mediaDetail = new MediaDetail(MediaDetail.Type.PLAYLIST_SONG, Converters.playlistIdToUri(playlistId), title, songs);
            fragment.getMediaDetailsWithoutImgPanel().addDetails(mediaDetail);
            displayMediaDetailsWithoutImgPanel();
        });
    }

    @Override
    public void openFolder(String name, String path) {
        this.getSongsFromFolderPath(path, songs -> {
            MediaDetail mediaDetail = new MediaDetail(MediaDetail.Type.FOLDER_SONG, Uri.parse(path), name, songs);
            fragment.getMediaDetailsWithoutImgPanel().addDetails(mediaDetail);
            displayMediaDetailsWithoutImgPanel();
        });
    }

    @Override
    public void openSearchDialog() {
        showCreatePlaylistDialog();
    }

    @Override
    public void openMoreInSong(int songId) {
        fragment.getSongInfoPanel().addDetails(songId);
        displaySongInfoPanel();
    }

    @Override
    public void renamePlaylist(long id, String name) {
        RenamePlaylistDialogFragment dialog = RenamePlaylistDialogFragment.newInstance(id, name);
        dialog.setCallback((playlistId, newName) -> {
            UIThread.getInstance().getScannerManager().getRepository().renamePlaylist(playlistId, newName, new LibraryRepository.OnMetadataUpdateListener() {
                @Override
                public void onUpdateSuccess() {
                    // Refresh if needed, though LiveData should handle it
                }

                @Override
                public void onPermissionRequired(android.app.PendingIntent pendingIntent) {
                    fragment.updateLauncher.launch(new IntentSenderRequest.Builder(pendingIntent).build());
                }

                @Override
                public void onUpdateError(Exception e) {
                    // Toast error
                }
            });
        });
        dialog.show(fragment.getChildFragmentManager(), "rename_playlist_dialog");
    }

    @Override
    public void deletePlaylist(Playlist playlist) {
        UIThread.getInstance().getScannerManager().getRepository().deletePlaylist(playlist, new LibraryRepository.OnMetadataUpdateListener() {
            @Override
            public void onUpdateSuccess() {
            }

            @Override
            public void onPermissionRequired(android.app.PendingIntent pendingIntent) {
                fragment.updateLauncher.launch(new IntentSenderRequest.Builder(pendingIntent).build());
            }

            @Override
            public void onUpdateError(Exception e) {
            }
        });
    }
}
