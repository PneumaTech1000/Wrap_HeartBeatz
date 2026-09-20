package com.giga.tech1000.heartbeatz.architecture.session;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.architecture.repositories.EnhancedFirebasePartyHostRepository;
import com.giga.tech1000.heartbeatz.architecture.repositories.PartyHostRepository;
import com.giga.tech1000.party_mode.model.PartyHost;

import java.util.List;

/**
 * Default {@link PartySession}: Firebase discovery/membership via {@link PartyHostRepository}.
 * Construct once in {@link com.giga.tech1000.heartbeatz.architecture.di.AppContainer}.
 */
public final class FirebasePartySession implements PartySession {

    private final EnhancedFirebasePartyHostRepository hostRepo;
    private final MutableLiveData<String> emptyError = new MutableLiveData<>(null);

    public FirebasePartySession(@NonNull Context appContext) {
        this.hostRepo = new EnhancedFirebasePartyHostRepository(appContext.getApplicationContext());
    }

    /** Testing / alternate backend */
    public FirebasePartySession(@NonNull EnhancedFirebasePartyHostRepository hostRepo) {
        this.hostRepo = hostRepo;
    }

    @NonNull
    public PartyHostRepository asHostRepository() {
        return hostRepo;
    }

    @Override public void startDiscovery() { hostRepo.startDiscovery(); }
    @Override public void stopDiscovery() { hostRepo.stopDiscovery(); }
    @Override public boolean isDiscovering() { return hostRepo.isDiscoveringHosts(); }

    @NonNull @Override
    public LiveData<List<PartyHost>> getDiscoveredParties() {
        return hostRepo.getDiscoveredHosts();
    }

    @Override public void createParty(@NonNull String partyName, @NonNull String pin) {
        hostRepo.createParty(partyName, pin);
    }
    @Override public void stopHosting() { hostRepo.stopHosting(); }

    @NonNull @Override public LiveData<PartyHost> getHostedParty() {
        return hostRepo.getHostedParty();
    }
    @NonNull @Override public LiveData<List<String>> getConnectedGuests() {
        return hostRepo.getConnectedGuests();
    }
    @NonNull @Override public LiveData<Integer> getGuestCount() {
        return hostRepo.getGuestCount();
    }

    @Override public void joinParty(@NonNull PartyHost host, @NonNull String pin) {
        hostRepo.joinParty(host, pin);
    }
    @Override public void leaveParty() { hostRepo.leaveParty(); }

    @NonNull @Override public LiveData<PartyHost> getConnectedHost() {
        return hostRepo.getConnectedHost();
    }
    @NonNull @Override public LiveData<Boolean> isGuestAuthenticated() {
        return hostRepo.isGuestAuthenticated();
    }

    @Override public boolean isHosting() { return hostRepo.isHosting(); }
    @Override public boolean isGuest() { return hostRepo.isGuest(); }
    @Override public boolean isInParty() { return hostRepo.isInPartyMode(); }

    @NonNull @Override
    public LiveData<String> getError() {
        LiveData<String> err = hostRepo.getPartyError();
        return err != null ? err : emptyError;
    }

    @NonNull @Override
    public LiveData<Boolean> isNetworkConnected() {
        return hostRepo.isNetworkConnected();
    }

    @Override
    public void release() {
        hostRepo.stopDiscovery();
        hostRepo.stopPresenceListener();
    }
}
