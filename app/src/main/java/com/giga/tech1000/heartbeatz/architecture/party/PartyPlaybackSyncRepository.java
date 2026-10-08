package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
/**
 * Firebase delivery for TimeEngine anchors under {@code parties/{id}/sync}.
 */
public class PartyPlaybackSyncRepository {
    private final FirebaseDatabase db = FirebaseDatabase.getInstance();
    private final MutableLiveData<PartyPlaybackSync> syncLive = new MutableLiveData<>(null);

    private DatabaseReference syncRef;
    private ValueEventListener listener;

    @NonNull
    public LiveData<PartyPlaybackSync> getSync() {
        return syncLive;
    }

    public void observeParty(@NonNull String partyId) {
        stopObserving();
        syncRef = db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                PartyPlaybackSync s = snapshot.getValue(PartyPlaybackSync.class);
                if (s != null) {
                    s.receivedAtDeviceMs = System.currentTimeMillis();
                }
                syncLive.postValue(s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                PartyLog.w("PartyPlaybackSyncRepository", "sync observe cancelled: " + error.getMessage());
            }
        };
        syncRef.addValueEventListener(listener);
    }

    public void stopObserving() {
        if (syncRef != null && listener != null) {
            syncRef.removeEventListener(listener);
        }
        listener = null;
        syncRef = null;
    }

    /**
     * Publish TimeEngine-aligned anchor.
     * <p>
     * Null mediaUrl / objectKey are <b>omitted</b> (not written as null). Firebase
     * {@code updateChildren} treats null as delete — that wiped the guest's stream URL
     * on every heartbeat before upload or after host-bridge restart.
     */
    public void publishHostSync(@NonNull String partyId, @NonNull PartyPlaybackSync sync) {
        Map<String, Object> map = new HashMap<>();
        putIfPresent(map, "objectKey", sync.objectKey);
        putIfPresent(map, "mediaUrl", sync.mediaUrl);
        putIfPresent(map, "trackId", sync.trackId);
        putIfPresent(map, "title", sync.title);
        putIfPresent(map, "artist", sync.artist);
        putIfPresent(map, "album", sync.album);
        map.put("scheduleId", sync.scheduleId);
        map.put("positionMs", sync.positionMs);
        map.put("targetPositionMs", sync.targetPositionMs);
        map.put("lookaheadMs", sync.lookaheadMs > 0
                ? sync.lookaheadMs
                : PartyPlaybackSync.DEFAULT_LOOKAHEAD_MS);
        map.put("isPlaying", sync.isPlaying);
        map.put("durationMs", sync.durationMs);
        map.put("hostMonoMs", sync.hostMonoMs);
        map.put("targetHostMonoMs", sync.targetHostMonoMs);
        map.put("updatedAt", ServerValue.TIMESTAMP);

        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC)
                .updateChildren(map)
                .addOnFailureListener(e ->
                        PartyLog.e("PartyPlaybackSyncRepository", "publish sync failed", e));
    }

    private static void putIfPresent(
            @NonNull Map<String, Object> map,
            @NonNull String key,
            @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    public void clearSync(@NonNull String partyId) {
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC)
                .removeValue();
    }
}
