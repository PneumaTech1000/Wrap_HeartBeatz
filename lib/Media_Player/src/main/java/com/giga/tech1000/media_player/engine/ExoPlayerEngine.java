package com.giga.tech1000.media_player.engine;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

import com.giga.tech1000.party_mode.core.PartyState;

@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerEngine {

    public ExoPlayer createPlayer(
            Context context, PartyState partyState, DspAudioProcessor dspAudioProcessor) {
        return createPlayer(context, partyState, dspAudioProcessor, true);
    }

    /**
     * @param gaplessLocal when true and not a party guest, use larger preload so the next
     *                     playlist item is ready (encoder delay/padding still handled by Media3).
     */
    public ExoPlayer createPlayer(
            Context context,
            PartyState partyState,
            DspAudioProcessor dspAudioProcessor,
            boolean gaplessLocal) {
        // 1. Configure Audio Attributes for Music
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        // 2. Build the Player with robust defaults
        DefaultRenderersFactory renderersFactory = new DspRenderersFactory(
                context, dspAudioProcessor)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableAudioFloatOutput(false);

        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true) // Handles Audio Focus automatically
                .setHandleAudioBecomingNoisy(true)        // Pauses on headphone unplug
                .setWakeMode(C.WAKE_MODE_NETWORK)           // Prevents CPU/WiFi sleep
                .setRenderersFactory(renderersFactory);

        if (partyState == PartyState.JOINED) {
            // Low-latency buffering for Party Client
            LoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            2500,  // minBufferMs
                            5000,  // maxBufferMs
                            1000,  // bufferForPlaybackMs
                            1500   // bufferForPlaybackAfterRebufferMs
                    )
                    .build();
            playerBuilder.setLoadControl(loadControl);
        } else if (gaplessLocal) {
            // P6: keep more of the next item buffered for seamless playlist advances
            LoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            15_000,  // minBufferMs
                            50_000,  // maxBufferMs
                            1_500,   // bufferForPlaybackMs
                            3_000    // bufferForPlaybackAfterRebufferMs
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            playerBuilder.setLoadControl(loadControl);
        }

        ExoPlayer player = playerBuilder.build();
        // Continuity across MediaItems (gapless where container metadata allows)
        player.setPauseAtEndOfMediaItems(false);
        return player;
    }

        private static final class DspRenderersFactory extends DefaultRenderersFactory {

                private final DspAudioProcessor dspAudioProcessor;

                DspRenderersFactory(Context context, DspAudioProcessor dspAudioProcessor) {
                        super(context);
                        this.dspAudioProcessor = dspAudioProcessor;
                }

                @Override
                protected AudioSink buildAudioSink(
                                Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                        return new DefaultAudioSink.Builder(context)
                                        .setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{dspAudioProcessor})
                                        .setEnableFloatOutput(enableFloatOutput)
                                        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                                        .build();
                }
        }
}
