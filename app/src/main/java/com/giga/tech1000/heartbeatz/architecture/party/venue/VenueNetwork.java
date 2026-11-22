package com.giga.tech1000.heartbeatz.architecture.party.venue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * LAN helpers for Venue mode (same Wi‑Fi private IPv4).
 */
public final class VenueNetwork {

    private VenueNetwork() {}

    /**
     * Prefer wlan/eth private IPv4; skip loopback and cellular-like interfaces when possible.
     */
    @Nullable
    public static String localIpv4() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return null;
            String fallback = null;
            for (NetworkInterface nif : Collections.list(ifaces)) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String name = nif.getName() != null ? nif.getName().toLowerCase() : "";
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null || host.startsWith("169.254.")) continue; // link-local
                    if (isPrivateLan(host)) {
                        if (name.contains("wlan") || name.contains("wifi") || name.contains("eth")
                                || name.contains("ap") || name.contains("swlan")) {
                            return host;
                        }
                        if (fallback == null) fallback = host;
                    }
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isPrivateLan(@NonNull String host) {
        return host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    /**
     * Rough same-/24 check for two IPv4 strings (Venue eligibility).
     */
    public static boolean likelySameSubnet(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return false;
        int ia = a.lastIndexOf('.');
        int ib = b.lastIndexOf('.');
        if (ia <= 0 || ib <= 0) return false;
        return a.substring(0, ia).equals(b.substring(0, ib));
    }
}
