package com.giga.tech1000.heartbeatz.architecture.party.venue;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.architecture.party.PartyFirebasePaths;
import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host: starts LAN HTTP server, publishes {@code parties/{id}/venue}, maps tracks.
 * Guest: observes venue meta, builds LAN track URLs, probes reachability.
 */
public final class VenueCoordinator {

    public static final int DEFAULT_PORT = 18765;
    private static final AtomicInteger PORT_BUMP = new AtomicInteger(0);

    private final Context app;
    private final MutableLiveData<VenueMeta> venueLive = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> venueActive = new MutableLiveData<>(false);

    @Nullable private VenueHttpServer server;
    @Nullable private String hostPartyId;
    private boolean hosting;
    private int boundPort = DEFAULT_PORT;
    @Nullable private String boundIp;

    @Nullable private DatabaseReference venueRef;
    @Nullable private ValueEventListener venueListener;

    @Nullable private WifiManager.WifiLock wifiLock;
    @Nullable private PowerManager.WakeLock wakeLock;

    public VenueCoordinator(@NonNull Context context) {
        this.app = context.getApplicationContext();
    }

    @NonNull
    public LiveData<VenueMeta> getVenueMeta() {
        return venueLive;
    }

    @NonNull
    public LiveData<Boolean> isVenueActive() {
        return venueActive;
    }

    public boolean isHostingVenue() {
        return hosting && server != null;
    }

    /**
     * Start host venue: bind HTTP, publish Firebase venue node.
     */
    public synchronized void startHost(@NonNull String partyId) {
        stopHost();
        this.hostPartyId = partyId;
        this.hosting = true;

        String ip = VenueNetwork.localIpv4();
        if (ip == null) {
            PartyLog.w("Venue", "no private IPv4 — venue disabled (cloud-only)");
            hosting = false;
            publishVenueDisabled(partyId);
            venueActive.postValue(false);
            return;
        }

        int port = DEFAULT_PORT + (PORT_BUMP.getAndIncrement() % 20);
        VenueHttpServer s = new VenueHttpServer(port);
        try {
            s.start(NanoTimeouts.SOCKET_READ_TIMEOUT, false);
            this.server = s;
            this.boundPort = port;
            this.boundIp = ip;
            acquireLocks();
            publishVenueEnabled(partyId, ip, port);
            venueActive.postValue(true);
            PartyLog.i("Venue", "host server up http://" + ip + ":" + port + "/party/");
        } catch (Exception e) {
            PartyLog.e("Venue", "failed to start HTTP server", e);
            try { s.stop(); } catch (Exception ignored) { }
            this.server = null;
            this.hosting = false;
            publishVenueDisabled(partyId);
            venueActive.postValue(false);
        }
    }

    public synchronized void stopHost() {
        if (server != null) {
            try {
                server.clearTracks();
                server.stop();
            } catch (Exception ignored) { }
            server = null;
        }
        if (hostPartyId != null) {
            publishVenueDisabled(hostPartyId);
        }
        releaseLocks();
        hosting = false;
        hostPartyId = null;
        boundIp = null;
        venueActive.postValue(false);
    }

    /**
     * Map a local file for LAN guests. Call after host selects/uploads a track.
     */
    public void registerLocalTrack(@NonNull String trackId, @Nullable File file) {
        if (server == null || file == null) return;
        server.putTrack(trackId, file);
    }

    @Nullable
    public String lanTrackUrl(@Nullable String trackId) {
        if (trackId == null || trackId.isEmpty()) return null;
        VenueMeta meta = venueLive.getValue();
        if (meta != null && meta.enabled && meta.baseUrl != null) {
            return meta.baseUrl + "/party/track/" + trackId;
        }
        if (hosting && boundIp != null) {
            return "http://" + boundIp + ":" + boundPort + "/party/track/" + trackId;
        }
        return null;
    }

    /** Guest: observe host venue advertisement. */
    public void observeGuest(@NonNull String partyId) {
        stopObservingGuest();
        venueRef = FirebaseDatabase.getInstance()
                .getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.VENUE);
        venueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                VenueMeta meta = snapshot.getValue(VenueMeta.class);
                if (meta == null) {
                    meta = new VenueMeta();
                    meta.enabled = false;
                }
                if (meta.enabled && meta.ip != null && meta.port > 0 && meta.baseUrl == null) {
                    meta.baseUrl = "http://" + meta.ip + ":" + meta.port;
                }
                venueLive.postValue(meta);
                venueActive.postValue(meta.enabled);
                PartyLog.d("Venue", "guest meta enabled=" + meta.enabled
                        + " base=" + meta.baseUrl);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                PartyLog.w("Venue", "guest observe cancelled: " + error.getMessage());
            }
        };
        venueRef.addValueEventListener(venueListener);
    }

    public void stopObservingGuest() {
        if (venueRef != null && venueListener != null) {
            venueRef.removeEventListener(venueListener);
        }
        venueRef = null;
        venueListener = null;
        if (!hosting) {
            venueLive.postValue(null);
            venueActive.postValue(false);
        }
    }

    public void shutdown() {
        stopObservingGuest();
        stopHost();
    }

    private void publishVenueEnabled(@NonNull String partyId, @NonNull String ip, int port) {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", true);
        map.put("ip", ip);
        map.put("port", port);
        map.put("baseUrl", "http://" + ip + ":" + port);
        map.put("updatedAt", ServerValue.TIMESTAMP);
        FirebaseDatabase.getInstance()
                .getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.VENUE)
                .updateChildren(map)
                .addOnFailureListener(e -> PartyLog.e("Venue", "publish enabled failed", e));
        VenueMeta meta = new VenueMeta();
        meta.enabled = true;
        meta.ip = ip;
        meta.port = port;
        meta.baseUrl = "http://" + ip + ":" + port;
        venueLive.postValue(meta);
    }

    private void publishVenueDisabled(@NonNull String partyId) {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", false);
        map.put("updatedAt", ServerValue.TIMESTAMP);
        FirebaseDatabase.getInstance()
                .getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.VENUE)
                .updateChildren(map);
    }

    private void acquireLocks() {
        try {
            WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wifiLock == null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HeartBeatz:Venue");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Exception e) {
            PartyLog.w("Venue", "wifi lock: " + e.getMessage());
        }
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartBeatz:Venue");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(3 * 60 * 60 * 1000L); // max 3h party
            }
        } catch (Exception e) {
            PartyLog.w("Venue", "wake lock: " + e.getMessage());
        }
    }

    private void releaseLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) { }
        wifiLock = null;
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) { }
        wakeLock = null;
    }

    /** Firebase POJO for {@code parties/{id}/venue}. */
    public static class VenueMeta {
        public boolean enabled;
        @Nullable public String ip;
        public int port;
        @Nullable public String baseUrl;
        @Nullable public Object updatedAt;

        public VenueMeta() {}
    }

    /** NanoHTTPD start timeout constant holder. */
    private static final class NanoTimeouts {
        static final int SOCKET_READ_TIMEOUT = 30_000;
    }
}
