package com.giga.tech1000.heartbeatz.views;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.SystemClock;
import androidx.media3.session.legacy.PlaybackStateCompat;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.math.MathUtils;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.theme.GradientImageView;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.view_models.extended_models.PlaybackCacheViewModel;
import com.giga.tech1000.heartbeatz.views.panels.RootMediaPlayerPanel;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.visualizer_android.visualizer.BarVisualizer;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.giga.tech1000.heartbeatz.utils.ImageLoader;

@UnstableApi
public class MediaPlayerBarView {

    public static final int STATE_NORMAL = 0;
    public static final int STATE_PARTIAL = 1;

    private PlaybackStateCompat prevPlaybackState;

    private final LinearLayout backgroundView;
    private final LinearProgressIndicator progressIndicator;
    private final LinearLayout constraintLayout;

    private final GradientImageView albumImageView;
    private final TextView titleText;
    private final AppCompatImageButton playPauseButton;
    private final CircularProgressIndicator playPauseProgressIndicator;

    private final BarVisualizer audioVisualizerBar;

    private final View rootView;
    private final RootMediaPlayerPanel panel;
    private final PlaybackCacheViewModel playbackViewModel;

    private boolean isPartyClient = false;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (prevPlaybackState != null && (prevPlaybackState.getState() == PlaybackStateCompat.STATE_PLAYING || prevPlaybackState.getState() == PlaybackStateCompat.STATE_BUFFERING)) {
                long currentPos = prevPlaybackState.getPosition();
                long timeDiff = SystemClock.elapsedRealtime() - prevPlaybackState.getLastPositionUpdateTime();
                long playbackPos = currentPos + (long) (timeDiff * prevPlaybackState.getPlaybackSpeed());

                progressIndicator.setProgressCompat((int) playbackPos, true);

                rootView.postDelayed(this, 1000);
            }
        }
    };

    public MediaPlayerBarView(View mRoot, RootMediaPlayerPanel p) {
        this.rootView = mRoot;
        this.panel = p;

        this.backgroundView = findViewById(R.id.background_view);
        progressIndicator = findViewById(R.id.progress_bar_indicator);
        this.constraintLayout = findViewById(R.id.mini_player_controls_wrapper);

        albumImageView = findViewById(R.id.bar_album_art);
        titleText = findViewById(R.id.bar_song_title);
        playPauseButton = findViewById(R.id.bar_play_pause_btn);
        playPauseProgressIndicator = findViewById(R.id.bar_play_pause_progress_indicator);

        audioVisualizerBar = findViewById(R.id.audio_visualizer_bar);

        this.rootView.setAlpha(1.0F);
        this.rootView.setVisibility(View.VISIBLE);

        this.playbackViewModel = new ViewModelProvider(HeartBeatzApp.container(getContext()).requireUiThread().getActivity()).get(PlaybackCacheViewModel.class);

        onInitView();
    }

    private void onInitView() {
        rootView.setOnClickListener(v -> {
            if (panel.getPanelState() == RootMediaPlayerPanel.STATE_COLLAPSED) panel.expandPlayer();
        });

        playPauseButton.setOnClickListener(v -> playbackViewModel.togglePlayPause());
    }


    public void onSliding(float slidingOffset, int state) {
        float fadeStart = 0.25F;
        float alpha = (slidingOffset / fadeStart);
        alpha = MathUtils.clamp(alpha, 0.0F, 1.0F);

        if (state == STATE_NORMAL) {
            float barAlpha = 1F - alpha;
            this.rootView.setAlpha(barAlpha);
            this.rootView.setVisibility(barAlpha > 0 ? View.VISIBLE : View.GONE);

            this.backgroundView.setAlpha(barAlpha);
            this.progressIndicator.setAlpha(1F);
            this.constraintLayout.setAlpha(1F);
        } else {
            this.rootView.setAlpha(alpha);
            this.rootView.setVisibility(alpha > 0 ? View.VISIBLE : View.GONE);

            this.backgroundView.setAlpha(alpha);
            this.progressIndicator.setAlpha(alpha);
            this.constraintLayout.setAlpha(alpha);
        }
    }

    public <T extends View> T findViewById(@IdRes int id) {
        return this.rootView.findViewById(id);
    }

    public void onPlaybackStateChanged(PlaybackStateCompat state) {
        prevPlaybackState = state;
        progressIndicator.setProgressCompat((int) state.getPosition(), true);

        rootView.removeCallbacks(progressUpdater);
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING || state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
            rootView.post(progressUpdater);
        }

        if (state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
            playPauseProgressIndicator.setVisibility(View.VISIBLE);
            playPauseButton.setVisibility(View.INVISIBLE);
        } else {
            playPauseProgressIndicator.setVisibility(View.GONE);
            playPauseButton.setVisibility(View.VISIBLE);

            if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                playPauseButton.setImageIcon(Icon.createWithResource(rootView.getContext(), com.giga.tech1000.icons_pack.R.drawable.pause_24px));
                titleText.setSelected(true);
            } else {
                playPauseButton.setImageIcon(Icon.createWithResource(rootView.getContext(), com.giga.tech1000.icons_pack.R.drawable.play_arrow_fill));
                titleText.setSelected(false);
            }
        }
    }

    public void onSongChanged(Song song) {
        titleText.setText(song.getTitle());
        ((TextView) findViewById(R.id.bar_song_artist)).setText(song.getArtist());

        Uri album_art = song.getAlbumArt();

        if (album_art != null) {
            ImageLoader.load(albumImageView, album_art);
        } else {
            albumImageView.setImageDrawable(ResourcesCompat.getDrawable(rootView.getResources(), R.drawable.album_launcher, rootView.getContext().getTheme()));
        }
        progressIndicator.setMax((int) song.getDuration());
    }

    public void setPartyClientMode(boolean enabled) {
        this.isPartyClient = enabled;
        playPauseButton.setEnabled(!enabled);
        playPauseButton.setAlpha(enabled ? 0.5f : 1.0f);
    }

    public BarVisualizer getPlayerBarVisualizer() {
        return audioVisualizerBar;
    }

    public void onVibrantLightColorChanged(int vibrantLightColor) {
        int surfaceColor = MaterialColors.getColor(rootView.getContext(), R.attr.kv_primaryColor, Color.WHITE);
        audioVisualizerBar.setColor(withAlpha(surfaceColor, 0.9f));
    }

    private int withAlpha(int color, float alphaPercent) {
        int alpha = Math.round(255 * alphaPercent);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
