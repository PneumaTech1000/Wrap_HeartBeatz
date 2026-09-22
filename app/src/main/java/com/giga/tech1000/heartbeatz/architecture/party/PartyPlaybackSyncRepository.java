package com.giga.tech1000.heartbeatz.architecture.party;

import android.util.Log;

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

/**
 * Reads/writes host playback sync at {@code parties/{partyId}/sync}.
 * Host writes; guests observe and align Media3.
 */
public final class PartyPlaybackSyncRepository {

    private static final String TAG = "PartyPlaybackSyncRepo";

    private final FirebaseDatabase db;
    private final MutableLiveData<PartyPlaybackSync> syncLive = new MutableLiveData<>(null);

    @Nullable private String activePartyId;
    @Nullable private ValueEventListener listener;
    @Nullable private DatabaseReference syncRef;

    public PartyPlaybackSyncRepository() {
        this.db = FirebaseDatabase.getInstance();
    }

    @NonNull
    public LiveData<PartyPlaybackSync> getSync() {
        return syncLive;
    }

    /** Start listening to a party's sync node (guests + host). */
    public void observeParty(@NonNull String partyId) {
        stopObserving();
        activePartyId = partyId;
        syncRef = db.getReference(PartyFirebasePaths.PARTIES).child(partyId).child(PartyFirebasePaths.SYNC);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                PartyPlaybackSync s = snapshot.getValue(PartyPlaybackSync.class);
                syncLive.postValue(s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "sync observe cancelled: " + error.getMessage());
            }
        };
        syncRef.addValueEventListener(listener);
    }

    public void stopObserving() {
        if (syncRef != null && listener != null) {
            syncRef.removeEventListener(listener);
        }
        syncRef = null;
        listener = null;
        activePartyId = null;
    }

    /**
     * Host publishes a new authoritative snapshot (e.g. after upload or seek/play).
     */
    public void publishHostSync(@NonNull String partyId, @NonNull PartyPlaybackSync sync) {
        Map<String, Object> map = new HashMap<>();
        map.put("objectKey", sync.objectKey);
        map.put("mediaUrl", sync.mediaUrl);
        map.put("trackId", sync.trackId);
        map.put("title", sync.title);
        map.put("artist", sync.artist);
        map.put("positionMs", sync.positionMs);
        map.put("isPlaying", sync.isPlaying);
        map.put("updatedAtClientMs", System.currentTimeMillis());
        map.put("updatedAt", ServerValue.TIMESTAMP);

        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC)
                .updateChildren(map)
                .addOnFailureListener(e -> Log.e(TAG, "publish sync failed", e));
    }

    /** Lightweight position heartbeat while playing (throttle in caller). */
    public void publishPosition(@NonNull String partyId, long positionMs, boolean isPlaying) {
        Map<String, Object> map = new HashMap<>();
        map.put("positionMs", positionMs);
        map.put("isPlaying", isPlaying);
        map.put("updatedAtClientMs", System.currentTimeMillis());
        map.put("updatedAt", ServerValue.TIMESTAMP);
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC)
                .updateChildren(map);
    }

    public void clearSync(@NonNull String partyId) {
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC)
                .removeValue();
    }
}
