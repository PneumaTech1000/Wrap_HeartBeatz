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

import com.giga.tech1000.party_mode.core.PartyState;

@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerEngine {

    public ExoPlayer createPlayer(Context context, PartyState partyState) {
        // 1. Configure Audio Attributes for Music
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        // 2. Build the Player with robust defaults
        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true) // Handles Audio Focus automatically
                .setHandleAudioBecomingNoisy(true)        // Pauses on headphone unplug
                .setWakeMode(C.WAKE_MODE_NETWORK);        // Prevents CPU/WiFi sleep

        if (partyState == PartyState.JOINED) {
            // Low-latency buffering for Party Client
            // In the new architecture, the client plays a normal HTTP stream.
            // Tuning LoadControl helps with sync stability.
            LoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            2500,  // minBufferMs
                            5000,  // maxBufferMs
                            1000,  // bufferForPlaybackMs
                            1500   // bufferForPlaybackAfterRebufferMs
                    )
                    .build();
            playerBuilder.setLoadControl(loadControl);
        }

        // Standard renderer factory for all modes (Host, Client, Inactive)
        // No longer using TeeAudioProcessor for PCM interception.
        playerBuilder.setRenderersFactory(new DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableAudioFloatOutput(false));

        return playerBuilder.build();
    }
}
