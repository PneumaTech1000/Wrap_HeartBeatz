package com.giga.tech1000.media_player.interfaces;

import com.giga.tech1000.media_player.utils.enums.ItemSource;

import java.util.List;

public interface IPlayerCallback {
    void onClickPlay(int queueIndex, List<Integer> queue, ItemSource source);

    void onClickPlayIndex(int index);

    void onClickPlayNext();

    void onClickPlayPrev();

    void onClickPlayPause();

    void onSetSeekbar(int position);

    void onSetRepeatState(int repeatState);

    void onSetShuffleMode(boolean isShuffleMode);

    void onSetPlaybackSpeed(float speed);

    void onSetPlaybackPitch(float pitch);

    void onCreateParty(String partyName, String pin);

    void onJoinParty(com.giga.tech1000.party_mode.model.PartyHost host, String pin);

    void onJoinAddress(String ip, int port, String pin);

    void onLeaveParty();

    void onAcceptHandover(String pin);

    void onDeclineHandover();

    void onInitiateHandover(String guestName);

    void onStartDiscovery();

    void onStopDiscovery();

    void onUpdateQueue(List<Integer> queue, int queueIndex, ItemSource source);

    void onDestroy();

    void onStart();
}
