package com.giga.tech1000.heartbeatz.architecture.party.venue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD;

/**
 * Lightweight host-side HTTP server for Venue mode.
 * Serves party tracks with HTTP Range so Media3 can seek on LAN.
 * <p>
 * Paths:
 * <ul>
 *   <li>{@code GET /party/health} → 200 ok</li>
 *   <li>{@code GET /party/track/{trackId}} → audio file</li>
 * </ul>
 */
public final class VenueHttpServer extends NanoHTTPD {

    private final ConcurrentHashMap<String, File> tracks = new ConcurrentHashMap<>();

    public VenueHttpServer(int port) {
        super(port);
    }

    public void putTrack(@NonNull String trackId, @NonNull File file) {
        if (file.exists() && file.canRead()) {
            tracks.put(trackId, file);
            PartyLog.d("VenueHttp", "mapped track=" + trackId + " file=" + file.getName()
                    + " bytes=" + file.length());
        }
    }

    public void removeTrack(@NonNull String trackId) {
        tracks.remove(trackId);
    }

    public void clearTracks() {
        tracks.clear();
    }

    public boolean hasTrack(@NonNull String trackId) {
        File f = tracks.get(trackId);
        return f != null && f.exists();
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String uri = session.getUri();
            if (uri == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found");
            }
            // Strip query
            int q = uri.indexOf('?');
            if (q >= 0) uri = uri.substring(0, q);

            if ("/party/health".equals(uri) || "/party/health/".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok");
            }

            if (uri.startsWith("/party/track/")) {
                String trackId = uri.substring("/party/track/".length());
                if (trackId.endsWith("/")) trackId = trackId.substring(0, trackId.length() - 1);
                File file = tracks.get(trackId);
                if (file == null || !file.exists()) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "track missing");
                }
                return serveFileWithRange(session, file, guessMime(file));
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found");
        } catch (Exception e) {
            PartyLog.w("VenueHttp", "serve error: " + e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "error");
        }
    }

    @NonNull
    private Response serveFileWithRange(
            @NonNull IHTTPSession session,
            @NonNull File file,
            @NonNull String mime) throws IOException {
        long fileLen = file.length();
        String rangeHeader = session.getHeaders().get("range");
        if (rangeHeader == null) {
            rangeHeader = session.getHeaders().get("Range");
        }

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length()).trim();
            long start = 0;
            long end = fileLen - 1;
            int dash = spec.indexOf('-');
            if (dash >= 0) {
                String startS = spec.substring(0, dash).trim();
                String endS = spec.substring(dash + 1).trim();
                if (!startS.isEmpty()) start = Long.parseLong(startS);
                if (!endS.isEmpty()) end = Long.parseLong(endS);
            }
            if (start < 0) start = 0;
            if (end >= fileLen) end = fileLen - 1;
            if (start > end) {
                Response r = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
                r.addHeader("Content-Range", "bytes */" + fileLen);
                return r;
            }
            long len = end - start + 1;
            FileInputStream fis = new FileInputStream(file);
            long skipped = fis.skip(start);
            if (skipped < start) {
                // fall through to full file
                fis.close();
                FileInputStream full = new FileInputStream(file);
                Response r = newFixedLengthResponse(Response.Status.OK, mime, full, fileLen);
                r.addHeader("Accept-Ranges", "bytes");
                return r;
            }
            Response r = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, len);
            r.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLen);
            r.addHeader("Accept-Ranges", "bytes");
            r.addHeader("Content-Length", String.valueOf(len));
            return r;
        }

        FileInputStream fis = new FileInputStream(file);
        Response r = newFixedLengthResponse(Response.Status.OK, mime, fis, fileLen);
        r.addHeader("Accept-Ranges", "bytes");
        r.addHeader("Content-Length", String.valueOf(fileLen));
        return r;
    }

    @NonNull
    private static String guessMime(@NonNull File file) {
        String n = file.getName().toLowerCase();
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".m4a") || n.endsWith(".mp4")) return "audio/mp4";
        if (n.endsWith(".aac")) return "audio/aac";
        if (n.endsWith(".ogg") || n.endsWith(".opus")) return "audio/ogg";
        if (n.endsWith(".flac")) return "audio/flac";
        if (n.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }
}
