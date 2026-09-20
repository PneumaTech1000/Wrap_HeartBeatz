package com.giga.tech1000.party_mode.streaming;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * NanoHTTPD wrapper for streaming audio streams.
 */
/** @deprecated Frozen local HTTP streamer. Prefer shared media URL after §7. */
@Deprecated
public class StreamServer extends NanoHTTPD {
    private static final String TAG = "StreamServer";
    private final StreamProvider provider;

    public StreamServer(int port, StreamProvider provider) {
        super(port);
        this.provider = provider;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (provider.isProtected()) {
            Map<String, String> params = session.getParms();
            String requestPin = params.get("pin");
            if (!provider.verifyPin(requestPin)) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid party PIN");
            }
        }

        String remoteAddress = null;
        try {
            remoteAddress = session.getRemoteIpAddress();
        } catch (Throwable ignored) {
        }
        if (remoteAddress == null && session.getHeaders() != null) {
            remoteAddress = session.getHeaders().get("remote-addr");
        }
        if (remoteAddress != null) {
            provider.onGuestConnected(remoteAddress);
        }

        try {
            InputStream is = provider.getStream();
            if (is == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Stream not available");
            }
            // NanoHTTPD's newChunkedResponse takes the stream and will close it when the response is finished.
            return newChunkedResponse(Response.Status.OK, "audio/mpeg", is);
        } catch (IOException e) {
            Log.e(TAG, "Error accessing stream", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error opening stream");
        }
    }

    public void startServer() {
        try {
            start(5000, false);
            Log.i(TAG, "Streaming server started on port " + getListeningPort());
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
        }
    }

    public void stopServer() {
        stop();
        Log.i(TAG, "Streaming server stopped");
    }
}
