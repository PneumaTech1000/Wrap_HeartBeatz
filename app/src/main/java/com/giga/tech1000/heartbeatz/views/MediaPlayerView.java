package com.giga.tech1000.heartbeatz.views;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;
import androidx.media3.common.util.UnstableApi;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.theme.GradientImageView;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.utils.ImageLoader;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.heartbeatz.view_models.extended_models.SettingViewModel;
import com.giga.tech1000.heartbeatz.views.panels.RootMediaPlayerPanel;
import com.giga.tech1000.media_player.database.library.LibraryRepository;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.SettingEntity;
import com.giga.tech1000.media_player.utils.enums.FavoriteType;
import com.giga.tech1000.media_player.utils.enums.RepeatMode;
import com.giga.tech1000.media_player.utils.enums.ShuffleMode;
import com.giga.tech1000.utils.TimeConverter;
import com.giga.tech1000.visualizer_android.visualizer.WaveVisualizer;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.realgear.multislidinguppanel.MultiSlidingUpPanelLayout;

import androidx.lifecycle.ViewModelProvider;

@UnstableApi
public class MediaPlayerView {

    public static final int STATE_NORMAL = 0;
    public static final int STATE_PARTIAL = 1;

    private final ConstraintLayout constraintLayout;
    private final TextView songName;
    private final TextView artistName;
    private final TextView currentTimeText;
    private final TextView totalTimeText;
    private final ImageButton playPauseButtonView;
    private final CircularProgressIndicator playPauseProgressIndicator;
    private final ImageButton shuffleButton;
    private final ImageButton previousButton;
    private final ImageButton nextButton;
    private final ImageButton playListButton;
    private final ImageButton btnBackPanel;
    private final ImageButton btnLyrics;
    private final ImageButton btnParty;
    private final ImageButton btnFavorite;
    private final ImageButton btnRepeat;
    private final GradientImageView albumImageView;
    private final AppCompatSeekBar seekBar;
    private PlaybackStateCompat prevPlaybackState;

    private final WaveVisualizer audioVisualizer;

    private final View rootView;

    private boolean isPartyClient = false;

    private final SettingViewModel settingViewModel;
    private final PlaybackCacheViewModel playbackViewModel;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (prevPlaybackState != null && (prevPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING || prevPlaybackState.getState() == PlaybackStateCompat.STATE_BUFFERING)) {
                long currentPos = prevPlaybackState.getPosition();
                long timeDiff = SystemClock.elapsedRealtime() - prevPlaybackState.getLastPositionUpdateTime();
                long playbackPos = currentPos + (long) (timeDiff * prevPlaybackState.getPlaybackSpeed());

                updateProgressUI(playbackPos);

                rootView.postDelayed(this, 1000);
            }
        }
    };

    public MediaPlayerView(View mRoot, RootMediaPlayerPanel g) {
        this.rootView = mRoot;
        this.constraintLayout = findViewById(R.id.media_player_controls_container);

        this.songName = findViewById(R.id.tv_song_name);
        this.artistName = findViewById(R.id.tv_artist_name);
        this.currentTimeText = findViewById(R.id.tv_current_time);
        this.totalTimeText = findViewById(R.id.tv_total_time);

        this.playPauseButtonView = findViewById(R.id.btn_play_pause);
        this.playPauseProgressIndicator = findViewById(R.id.play_pause_progress_indicator);
        this.shuffleButton = findViewById(R.id.btn_shuffle);
        this.previousButton = findViewById(R.id.btn_previous);
        this.nextButton = findViewById(R.id.btn_next);
        this.playListButton = findViewById(R.id.btn_play_list);
        this.btnRepeat = findViewById(R.id.btn_repeat);
        this.btnLyrics = findViewById(R.id.btn_lyric);
        this.btnParty = findViewById(R.id.btn_party);
        this.btnFavorite = findViewById(R.id.btn_favorite);

        this.seekBar = findViewById(R.id.seek_bar);
        this.albumImageView = findViewById(R.id.album_art);
        this.btnBackPanel = findViewById(R.id.btn_back);

        this.audioVisualizer = findViewById(R.id.audio_visualizer);

        this.rootView.setAlpha(0.0F);
        this.rootView.setVisibility(View.GONE);

        this.settingViewModel = UIThread.getInstance().getSettingViewModel();
        this.playbackViewModel = new ViewModelProvider(UIThread.getInstance().getActivity()).get(PlaybackCacheViewModel.class);
        SettingEntity setting = settingViewModel.getCached();
        if (setting == null) setting = new SettingEntity();

        init();

        onInitView(g, setting);
        initializer(setting);
    }

    private void initializer(SettingEntity s) {
        btnRepeat.setImageResource(setImageByCheckingMode(s.repeatMode.toMedia3()));
        shuffleButton.setImageResource((s.getShuffleValue() == ShuffleMode.ON) ?
                com.giga.tech1000.icons_pack.R.drawable.shuffle_24px : com.giga.tech1000.icons_pack.R.drawable.shuffle_off);

        seekBar.setProgress((int) s.getLastPlayedSongPosition(), true);
    }


    private void init() {
        playPauseButtonView.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        albumImageView.setPaletteColor(R.attr.kv_colorSurface);

        int surfaceColor = MaterialColors.getColor(rootView.getContext(), R.attr.kv_primaryColor, Color.WHITE);
        audioVisualizer.setColor(withAlpha(surfaceColor, 0.7f));
    }

    private void onInitView(RootMediaPlayerPanel panel, SettingEntity s) {
        //btnParty.setOnClickListener(v -> {});
        btnLyrics.setOnClickListener(v -> panel.getBottomSheetView().openLyricsFragment());
        playListButton.setOnClickListener(v -> panel.getBottomSheetView().openQueueFragment());
        playPauseButtonView.setOnClickListener(v -> playbackViewModel.togglePlayPause());
        previousButton.setOnClickListener(v -> playbackViewModel.previous());
        nextButton.setOnClickListener(v -> playbackViewModel.next());
        btnRepeat.setOnClickListener(v -> {
            RepeatMode next = nextRepeat(s.getRepeatMode());
            playbackViewModel.setRepeatMode(next.toMedia3());
        });

        shuffleButton.setOnClickListener(v -> playbackViewModel.toggleShuffle());

        findViewById(R.id.btn_equalizer).setOnClickListener(v -> panel.collapsePanel());

        btnBackPanel.setOnClickListener(v -> {
            if (panel.getPanelState() == MultiSlidingUpPanelLayout.EXPANDED) panel.collapsePanel();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean isMediaSeeking = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isMediaSeeking) {
                    playbackViewModel.seekTo(progress);
                    currentTimeText.setText(TimeConverter.formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isMediaSeeking = true;
                rootView.removeCallbacks(progressUpdater);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isMediaSeeking = false;
                if (prevPlaybackState != null && (prevPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING || prevPlaybackState.getState() == PlaybackStateCompat.STATE_BUFFERING)) {
                    rootView.post(progressUpdater);
                }
            }
        });

    }

    public void onSliding(float slidingOffset, int state) {
        float fadeStart = 0.25F;
        float alpha = (slidingOffset - fadeStart) * (1F / (1F - fadeStart));
        alpha = MathUtils.clamp(alpha, 0.0F, 1.0F);

        if (state == STATE_NORMAL) {
            this.rootView.setAlpha(alpha);
            this.rootView.setVisibility(alpha > 0 ? View.VISIBLE : View.GONE);
            this.constraintLayout.setAlpha(1F);
        } else {
            this.constraintLayout.setAlpha(1F - alpha);
        }
    }

    public <T extends View> T findViewById(@IdRes int id) {
        return this.rootView.findViewById(id);
    }

    @SuppressLint("WrongConstant")
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
        int oldState = (prevPlaybackState != null) ? prevPlaybackState.getState() : -1;
        prevPlaybackState = state;
        
        updateProgressUI(state.getPosition());

        rootView.removeCallbacks(progressUpdater);
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING || state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
            rootView.post(progressUpdater);
        }

        if (state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
            playPauseProgressIndicator.setVisibility(View.VISIBLE);
            playPauseButtonView.setVisibility(View.INVISIBLE);
        } else {
            playPauseProgressIndicator.setVisibility(View.GONE);
            playPauseButtonView.setVisibility(View.VISIBLE);
            
            if (oldState != state.getState()) {
                switch (state.getState()) {
                    case PlaybackStateCompat.STATE_PLAYING -> playPauseButtonView.setImageIcon(Icon.createWithResource(rootView.getContext(), com.giga.tech1000.icons_pack.R.drawable.pause_24px));
                    case PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> playPauseButtonView.setImageIcon(Icon.createWithResource(rootView.getContext(), com.giga.tech1000.icons_pack.R.drawable.play_arrow_fill));
                }
            }
        }
    }

    private void updateProgressUI(long position) {
        seekBar.setProgress((int) position);
        currentTimeText.setText(TimeConverter.formatTime(position));
    }

    public void onSongChanged(Song song) {
        songName.setText(song.getTitle());
        artistName.setText(song.getArtist());

        ImageLoader.load(albumImageView, song.getAlbumArt());


        long duration = song.getDuration();
        seekBar.setMax((int) duration);
        totalTimeText.setText(TimeConverter.formatTime(duration));

        LibraryRepository repository = UIThread.getInstance().getLibrarySetViewModel().getRepo();
        String songId = String.valueOf(song.getId());
        repository.isFavoriteSync(songId, FavoriteType.SONG)
                .observe(UIThread.getInstance().getLifecycleOwner(), isFav -> {
                    if (isFav) {
                        btnFavorite.setImageResource(com.giga.tech1000.icons_pack.R.drawable.favorite_outline_24px);
                        btnFavorite.setColorFilter(
                                ContextCompat.getColor(rootView.getContext(), R.color.favorite_red),
                                PorterDuff.Mode.SRC_IN
                        );
                    } else {
                        btnFavorite.setImageResource(com.giga.tech1000.icons_pack.R.drawable.favorite_24px);
                        btnFavorite.clearColorFilter();
                    }

                    btnFavorite.setOnClickListener(v ->
                            repository.toggle(songId, FavoriteType.SONG, isFav)
                    );
                });
    }

    public void onRepeatModeChanged(int repeatMode) {
        btnRepeat.setImageResource(setImageByCheckingMode(repeatMode));
    }

    public void onShuffleModeChanged(boolean isShuffleMode) {
        shuffleButton.setImageResource((isShuffleMode) ?
                com.giga.tech1000.icons_pack.R.drawable.shuffle_24px : com.giga.tech1000.icons_pack.R.drawable.shuffle_off);
    }

    public void setPartyClientMode(boolean enabled) {
        this.isPartyClient = enabled;
        
        // Disable and blur/fade controls
        float alpha = enabled ? 0.5f : 1.0f;
        boolean interactive = !enabled;

        playPauseButtonView.setEnabled(interactive);
        playPauseButtonView.setAlpha(alpha);
        
        previousButton.setEnabled(interactive);
        previousButton.setAlpha(alpha);
        
        nextButton.setEnabled(interactive);
        nextButton.setAlpha(alpha);
        
        shuffleButton.setEnabled(interactive);
        shuffleButton.setAlpha(alpha);
        
        btnRepeat.setEnabled(interactive);
        btnRepeat.setAlpha(alpha);
        
        seekBar.setEnabled(interactive);
        // seekBar alpha is handled by its container usually or direct
        seekBar.setAlpha(alpha);

        // Keep playlist and back buttons enabled as they are for navigation
    }

    public WaveVisualizer getPlayerWaveVisualizer() {
        return audioVisualizer;
    }

    private int setImageByCheckingMode(int repeatMode) {
        return switch (repeatMode) {
            case PlaybackStateCompat.REPEAT_MODE_NONE -> com.giga.tech1000.icons_pack.R.drawable.repeat_off_24px;
            case PlaybackStateCompat.REPEAT_MODE_ONE -> com.giga.tech1000.icons_pack.R.drawable.repeat_one_24px;
            default -> com.giga.tech1000.icons_pack.R.drawable.repeat_24px;
        };
    }

    private RepeatMode nextRepeat(RepeatMode current) {
        return switch (current) {
            case OFF -> RepeatMode.ONE;
            case ONE -> RepeatMode.ALL;
            case ALL -> RepeatMode.OFF;
        };
    }

    private ShuffleMode nextShuffle(ShuffleMode current) {
        return (current == ShuffleMode.OFF)
                ? ShuffleMode.ON
                : ShuffleMode.OFF;
    }


    private int withAlpha(int color, float alphaPercent) {
        int alpha = Math.round(255 * alphaPercent);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
