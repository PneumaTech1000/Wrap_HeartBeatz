package com.giga.tech1000.utils.interfaces;

import com.giga.tech1000.party_mode.model.PartyHost;

public interface PartyModeUICallback {
    void onPartyFoundByClient(PartyHost host);
    void onPartyCreatedByHost(PartyHost host);
    void onConnectionFailed();
    void onDisconnected();
}
