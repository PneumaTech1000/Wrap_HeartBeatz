package com.giga.tech1000.heartbeatz.utils;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

/**
 * Party invites: primary format is partyId + PIN (works across networks once cloud sync ships).
 * Legacy {@code HB_PARTY:ip:port:...} still parsed for older QR codes.
 */
public final class QrCodeUtil {

    /** App deep link host (manifest intent-filter). */
    public static final String DEEP_LINK_SCHEME = "heartbeatz";
    public static final String DEEP_LINK_HOST = "party";

    /** HTTPS app-link style (optional; same path parsing). */
    public static final String HTTPS_HOST = "heartbeatz.app";

    private QrCodeUtil() {}

    public static Bitmap generateQrCode(String content, int size) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            return barcodeEncoder.encodeBitmap(content, BarcodeFormat.QR_CODE, size, size);
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates a Wi-Fi connection QR code.
     * Format: WIFI:S:SSID;T:WPA;P:PASSWORD;;
     */
    public static String formatWifiQr(String ssid, String password, String type) {
        return "WIFI:S:" + ssid + ";T:" + type + ";P:" + password + ";;";
    }

    /**
     * @deprecated Prefer {@link #formatPartyInvite(String, String, String)} (partyId-based).
     */
    @Deprecated
    public static String formatPartyQr(String ip, int port, String name, String pin) {
        return "HB_PARTY:" + ip + ":" + port + ":" + name + ":" + (pin != null ? pin : "");
    }

    /** Share / QR payload: deep link with partyId and optional PIN. */
    @NonNull
    public static String formatPartyInvite(
            @NonNull String partyId,
            @Nullable String partyName,
            @Nullable String pin) {
        Uri.Builder b = new Uri.Builder()
                .scheme(DEEP_LINK_SCHEME)
                .authority(DEEP_LINK_HOST)
                .appendPath(partyId);
        if (partyName != null && !partyName.isEmpty()) {
            b.appendQueryParameter("name", partyName);
        }
        if (pin != null && !pin.isEmpty()) {
            b.appendQueryParameter("pin", pin);
        }
        return b.build().toString();
    }

    /** Human-readable share text. */
    @NonNull
    public static String formatPartyShareText(
            @NonNull String partyId,
            @Nullable String partyName,
            @Nullable String pin) {
        String title = (partyName != null && !partyName.isEmpty()) ? partyName : "HeartBeatz Party";
        String link = formatPartyInvite(partyId, partyName, pin);
        StringBuilder sb = new StringBuilder();
        sb.append("Join my HeartBeatz party: ").append(title).append('\n');
        sb.append(link);
        if (pin != null && !pin.isEmpty()) {
            sb.append("\nPIN: ").append(pin);
        }
        return sb.toString();
    }

    @Nullable
    public static PartyInvite parseInvite(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String data = raw.trim();

        // New deep link: heartbeatz://party/{partyId}?pin=&name=
        if (data.startsWith(DEEP_LINK_SCHEME + "://") || data.startsWith("https://")) {
            try {
                Uri uri = Uri.parse(data);
                if (DEEP_LINK_HOST.equals(uri.getHost())
                        || HTTPS_HOST.equals(uri.getHost())
                        || (uri.getPath() != null && uri.getPath().contains("party"))) {
                    String partyId = null;
                    if (uri.getPathSegments() != null && !uri.getPathSegments().isEmpty()) {
                        // heartbeatz://party/{id} → host=party, path=/{id}
                        if (DEEP_LINK_HOST.equals(uri.getHost())) {
                            partyId = uri.getLastPathSegment();
                        } else if (uri.getPathSegments().size() >= 2
                                && "party".equals(uri.getPathSegments().get(0))) {
                            partyId = uri.getPathSegments().get(1);
                        } else {
                            partyId = uri.getLastPathSegment();
                        }
                    }
                    if (partyId == null || partyId.isEmpty()) return null;
                    return new PartyInvite(
                            partyId,
                            uri.getQueryParameter("name"),
                            uri.getQueryParameter("pin"),
                            null,
                            0,
                            false);
                }
            } catch (Exception ignored) {
            }
        }

        // Legacy LAN QR
        if (data.startsWith("HB_PARTY:")) {
            try {
                String[] parts = data.split(":");
                if (parts.length >= 4) {
                    String ip = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String name = parts[3];
                    String pin = parts.length > 4 ? parts[4] : "";
                    String partyId = name + "_" + ip;
                    return new PartyInvite(partyId, name, pin, ip, port, true);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Parsed invite payload. */
    public static final class PartyInvite {
        public final String partyId;
        @Nullable public final String partyName;
        @Nullable public final String pin;
        @Nullable public final String ipAddress;
        public final int port;
        public final boolean legacyLan;

        public PartyInvite(
                String partyId,
                @Nullable String partyName,
                @Nullable String pin,
                @Nullable String ipAddress,
                int port,
                boolean legacyLan) {
            this.partyId = partyId;
            this.partyName = partyName;
            this.pin = pin;
            this.ipAddress = ipAddress;
            this.port = port;
            this.legacyLan = legacyLan;
        }
    }
}
