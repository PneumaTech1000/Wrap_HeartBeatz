package com.giga.tech1000.heartbeatz.views.panels.sub_panels;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.ui.fragments.FragmentHome;
import com.giga.tech1000.heartbeatz.utils.DialogUtil;
import com.giga.tech1000.heartbeatz.utils.FileUtils;
import com.giga.tech1000.heartbeatz.view_models.SongInfoPanelViewModel;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.utils.AudioInfoResolver;
import com.giga.tech1000.media_player.utils.enums.FavoriteType;
import com.giga.tech1000.utils.TimeConverter;
import com.giga.tech1000.utils.statics.AudioTechInfo;
import com.google.android.material.snackbar.Snackbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

@OptIn(markerClass = UnstableApi.class)
public class SongInfoPanel {
    private final FragmentHome fragmentHome;
    private final Context context;
    private final SongInfoPanelViewModel songViewModel;

    private final View root;
    private final AtomicBoolean isVisible = new AtomicBoolean(false);

    private LinearLayout parentView;
    private ImageView albumView;
    private TextView bsTitle, bsArtist, bsAlbum;
    private TextView dirView, bitrateView, sizeView, lengthView, formatView, sampleRateView;
    private ImageButton bsFavorite, copyIcon;
    private ImageButton shareIcon, artistIcon, deleteIcon, ringtoneIcon, addToPlaylistIcon, hideIcon;
    private View bsEditIcon;

    private final TreeMap<Integer, Song> treeMapOfSongs;
    private Song currentSong;

    public SongInfoPanel(@NonNull FragmentHome fragment, @NonNull ViewGroup parent,
                         @NonNull SongInfoPanelViewModel songViewModel) {
        this.fragmentHome = fragment;
        this.context = fragment.requireContext();
        this.songViewModel = songViewModel;
        this.currentSong = null;

        this.treeMapOfSongs = songViewModel.getTreeMapOfSongs();

        root = LayoutInflater.from(context).inflate(R.layout.media_song_info_root_layout, parent, false);

        init(root);
    }

    private void init(@NonNull View root) {
        parentView = root.findViewById(R.id.bottomSheetContainer);
        albumView = root.findViewById(R.id.ivAlbum);
        bsTitle = root.findViewById(R.id.tvTitle);
        bsArtist = root.findViewById(R.id.tvArtist);
        bsAlbum = root.findViewById(R.id.tvAlbum);

        dirView = root.findViewById(R.id.txt_dir);
        bitrateView = root.findViewById(R.id.txt_bitrate);
        sizeView = root.findViewById(R.id.txt_size);
        lengthView = root.findViewById(R.id.txt_length);
        formatView = root.findViewById(R.id.txt_format);
        sampleRateView = root.findViewById(R.id.txt_sample_rate);

        bsFavorite = root.findViewById(R.id.bs_favorite);

        shareIcon = root.findViewById(R.id.share_icon);
        artistIcon = root.findViewById(R.id.artist_icon);
        deleteIcon = root.findViewById(R.id.delete_icon);
        ringtoneIcon = root.findViewById(R.id.ringtone_icon);
        addToPlaylistIcon = root.findViewById(R.id.add_to_playlist_icon);
        hideIcon = root.findViewById(R.id.hide_icon);

        copyIcon = root.findViewById(R.id.copy_icon);

        bsEditIcon = root.findViewById(R.id.bs_edit_icon);

        bsEditIcon.setOnClickListener(v -> {
            fragmentHome.hideMediaDetailsPanel();
            fragmentHome.displayEditSongInfoPanel(currentSong);
        });
        shareIcon.setOnClickListener(v -> FileUtils.share(context, currentSong.uri));
        artistIcon.setOnClickListener(v -> {
        });
        deleteIcon.setOnClickListener(v -> {
            DialogUtil.showDeleteDialog(context, currentSong.getTitle(), (deleteFromDevice) -> {
                if (deleteFromDevice) {
                    FileUtils.deleteAudio(fragmentHome, root, currentSong.uri, new FileUtils.OnDeleteListener() {
                        @Override
                        public void onDeleteSuccess() {
                            HeartBeatzApp.container(getContext()).requireUiThread().getScannerManager().runIncrementalMediaRefresh();
                        }

                        @Override
                        public void onDeleteFailed() {
                            // Already handled by Snackbar in FileUtils
                        }
                    });
                } else {
                    songViewModel.tempDeleteSongById(currentSong.getId());
                }
                setIsVisible(false);
                fragmentHome.hideMediaDetailsPanel();
            });
        });

        hideIcon.setOnClickListener(v -> songViewModel.hideSongById(currentSong.getId()));

        ringtoneIcon.setOnClickListener(v -> {
            if (FileUtils.setAsRingtone(context, currentSong.uri)) {
                Snackbar.make(root, R.string.success_ringtone_set, Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(root, R.string.error_ringtone_failed, Snackbar.LENGTH_SHORT).show();
            }
        });
        addToPlaylistIcon.setOnClickListener(v -> {
            String[] options = {"Playlist", "Favorite"};
            FileUtils.addToPlaylist(context, options, new FileUtils.AddToInterface() {
                @Override
                public void addToPlaylist() {
                    // Playlist selection
                    songViewModel.getPlaylists().observe(fragmentHome.getViewLifecycleOwner(), playlists -> {
                        if (playlists == null || playlists.isEmpty()) return;

                        String[] playlistNames = new String[playlists.size()];
                        for (int i = 0; i < playlists.size(); i++) {
                            playlistNames[i] = playlists.get(i).playlist.getName();
                        }

                        new MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.title_select_playlist)
                                .setItems(playlistNames, (playlistDialog, playlistIndex) -> {
                                    long playlistId = playlists.get(playlistIndex).playlist.getPlaylistId();
                                    songViewModel.addSongToPlaylist(playlistId, currentSong.id, new LibraryRepository.OnMetadataUpdateListener() {
                                        @Override
                                        public void onUpdateSuccess() {
                                            Snackbar.make(root, context.getString(R.string.msg_added_to_playlist, playlistNames[playlistIndex]), Snackbar.LENGTH_SHORT).show();
                                        }

                                        @Override
                                        public void onPermissionRequired(android.app.PendingIntent pendingIntent) {
                                            fragmentHome.updateLauncher.launch(new IntentSenderRequest.Builder(pendingIntent).build());
                                        }

                                        @Override
                                        public void onUpdateError(Exception e) {
                                            Snackbar.make(root, R.string.error_add_to_playlist_failed, Snackbar.LENGTH_SHORT).show();
                                        }
                                    });
                                })
                                .show();
                    });

                }

                @Override
                public void addToFavorite() {
                    // Favorite
                    songViewModel.toggleFavorite(String.valueOf(currentSong.id), false);
                    Snackbar.make(root, R.string.msg_added_to_favorites, Snackbar.LENGTH_SHORT).show();
                }

            });
        });
        copyIcon.setOnClickListener(v -> FileUtils.copyToClipboard(context, currentSong.uri, root));

    }

    public void show(int songId) {
        if (treeMapOfSongs == null) return;
        currentSong = treeMapOfSongs.get(songId);
        if (currentSong == null) return;

        setIsVisible(true);
        AudioTechInfo info = AudioInfoResolver.resolve(context, currentSong);
        ImageLoader.load(albumView, currentSong.getAlbumArt());

        bsTitle.setText(currentSong.getTitle());
        bsAlbum.setText(currentSong.getAlbum());
        bsArtist.setText(currentSong.getArtist());

        dirView.setText(currentSong.getFolder());
        bitrateView.setText(formatBitrate(info.bitrate, info.mime, info.estimatedBitrate));
        sizeView.setText(formatFileSize(currentSong.getSize()));
        lengthView.setText(TimeConverter.formatTime(currentSong.getDuration()));
        formatView.setText(info.mime);
        sampleRateView.setText(formatSampleRate(info.sampleRate));

        String songIdString = String.valueOf(currentSong.id);
        songViewModel.isFavoriteSong(songIdString)
                .observe(fragmentHome.getViewLifecycleOwner(), isFav -> {
                    if (isFav) {
                        bsFavorite.setImageResource(com.giga.tech1000.icons_pack.R.drawable.favorite_outline_24px);
                        bsFavorite.setColorFilter(
                                ContextCompat.getColor(context, R.color.favorite_red),
                                PorterDuff.Mode.SRC_IN
                        );
                    } else {
                        bsFavorite.setImageResource(com.giga.tech1000.icons_pack.R.drawable.favorite_24px);
                        bsFavorite.clearColorFilter();
                    }

                    bsFavorite.setOnClickListener(v ->
                            songViewModel.toggleFavorite(songIdString, isFav)
                    );
                });
    }

    public void addDetails(int songId) {
        show(songId);
    }


    public AtomicBoolean getIsVisible() {
        return isVisible;
    }

    public View getView() {
        return root;
    }

    public void setIsVisible(boolean visible) {
        isVisible.set(visible);
    }

    public void setBottomPadding(int padding) {
        parentView.setPadding(0, 0, 0, padding);
    }

    public static String formatBitrate(int bitrateBps, String mime, boolean estimated) {
        if (bitrateBps <= 0) return "Unknown";
        int kbps = bitrateBps / 1000;
        if (mime != null) {
            mime = mime.toLowerCase(Locale.US);
            if (mime.contains("flac") || mime.contains("wav") || mime.contains("aiff") || mime.contains("alac")) {
                return "Lossless";
            }
        }
        String label = (kbps >= 256) ? "320 kbps" : (kbps >= 192) ? "192 kbps" : (kbps >= 128) ? "128 kbps" : "<128 kbps";
        return estimated ? label + " (est.)" : label;
    }

    public static String formatSampleRate(int sampleRate) {
        if (sampleRate <= 0) return "—";
        return (sampleRate % 1000 == 0) ? (sampleRate / 1000) + " kHz" : String.format(Locale.US, "%.1f kHz", sampleRate / 1000f);
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "—";
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }
}
