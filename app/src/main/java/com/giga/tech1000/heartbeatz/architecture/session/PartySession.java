package com.giga.tech1000.heartbeatz.architecture.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.giga.tech1000.party_mode.model.PartyHost;

import java.util.List;

/**
 * Application-facing party API. UI / ViewModels depend on this only —
 * not on Firebase paths, WebRTC, or UIThread.
 */
public interface PartySession {

    void startDiscovery();
    void stopDiscovery();
    boolean isDiscovering();

    @NonNull LiveData<List<PartyHost>> getDiscoveredParties();

    void createParty(@NonNull String partyName, @NonNull String pin);
    void stopHosting();
    @NonNull LiveData<PartyHost> getHostedParty();
    @NonNull LiveData<List<String>> getConnectedGuests();
    @NonNull LiveData<Integer> getGuestCount();

    void joinParty(@NonNull PartyHost host, @NonNull String pin);
    void leaveParty();
    @NonNull LiveData<PartyHost> getConnectedHost();
    @NonNull LiveData<Boolean> isGuestAuthenticated();

    boolean isHosting();
    boolean isGuest();
    boolean isInParty();

    @NonNull LiveData<String> getError();
    @NonNull LiveData<Boolean> isNetworkConnected();

    /** Release listeners; call from ViewModel.onCleared or Activity destroy. */
    void release();
}
