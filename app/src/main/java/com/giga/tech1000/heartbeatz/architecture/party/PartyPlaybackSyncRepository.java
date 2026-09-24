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
 * Host writes / guests observe {@code parties/{partyId}/sync}.
 * {@code updatedAt} is always {@link ServerValue#TIMESTAMP} (Firebase server UTC ms).
 */
public final class PartyPlaybackSyncRepository {

    private static final String TAG = "PartyPlaybackSyncRepo";

    private final FirebaseDatabase db = FirebaseDatabase.getInstance();
    private final MutableLiveData<PartyPlaybackSync> syncLive = new MutableLiveData<>(null);

    @Nullable private DatabaseReference syncRef;
    @Nullable private ValueEventListener listener;

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
                Log.w(TAG, "sync observe cancelled: " + error.getMessage());
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

    /** Full host packet including 5s lookahead fields + server timestamp. */
    public void publishHostSync(@NonNull String partyId, @NonNull PartyPlaybackSync sync) {
        Map<String, Object> map = new HashMap<>();
        map.put("objectKey", sync.objectKey);
        map.put("mediaUrl", sync.mediaUrl);
        map.put("trackId", sync.trackId);
        map.put("title", sync.title);
        map.put("artist", sync.artist);
        map.put("positionMs", sync.positionMs);
        map.put("targetPositionMs", sync.targetPositionMs);
        map.put("lookaheadMs", sync.lookaheadMs > 0
                ? sync.lookaheadMs
                : PartyPlaybackSync.DEFAULT_LOOKAHEAD_MS);
        map.put("isPlaying", sync.isPlaying);
        map.put("durationMs", sync.durationMs);
        map.put("updatedAt", ServerValue.TIMESTAMP);

        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC)
                .updateChildren(map)
                .addOnFailureListener(e -> Log.e(TAG, "publish sync failed", e));
    }

    public void clearSync(@NonNull String partyId) {
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.SYNC)
                .removeValue();
    }
}
