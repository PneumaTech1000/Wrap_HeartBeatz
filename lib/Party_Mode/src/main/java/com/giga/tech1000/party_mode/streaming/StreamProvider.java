package com.giga.tech1000.party_mode.streaming;

import android.net.Uri;
import java.io.InputStream;
import java.io.IOException;

/**
 * Interface for providing the current audio stream.
 */
public interface StreamProvider {
    Uri getCurrentUri();
    InputStream getStream() throws IOException;
    boolean isProtected();
    boolean verifyPin(String pin);
    void onGuestConnected(String remoteAddress);
}
