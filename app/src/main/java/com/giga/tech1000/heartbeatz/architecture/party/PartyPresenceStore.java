package com.giga.tech1000.heartbeatz.architecture.party;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns /presence/{uid} reads and writes only.
 * Callers must pass the authenticated uid that matches Firebase Auth.
 */
public final class PartyPresenceStore {

    private static final String TAG = "PartyPresenceStore";

    public interface Listener {
        void onPresenceUpdate(@NonNull String userId, boolean online, long lastSeen);
    }

    @Nullable
    private ValueEventListener presenceEventListener;

    public void setOnline(@NonNull String userId, boolean isOnline, boolean authenticated) {
        if (!authenticated || userId.isEmpty()) {
            Log.w(TAG, "Skip presence: not authenticated or empty uid");
            return;
        }

        DatabaseReference presenceRef = FirebaseDatabase.getInstance()
                .getReference(PartyFirebasePaths.PRESENCE)
                .child(userId);

        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("online", isOnline);
        presenceData.put("lastSeen", ServerValue.TIMESTAMP);
        presenceData.put("version", Build.VERSION.SDK_INT);

        if (isOnline) {
            Map<String, Object> offline = new HashMap<>();
            offline.put("online", false);
            offline.put("lastSeen", ServerValue.TIMESTAMP);
            presenceRef.onDisconnect().setValue(offline);
        }

        presenceRef.setValue(presenceData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Presence updated uid=" + userId))
                .addOnFailureListener(e ->
                        Log.w(TAG, "Presence write failed (check firebase.rules /presence): "
                                + e.getMessage()));
    }

    public void startListening(@NonNull Listener listener) {
        stopListening();
        DatabaseReference presenceRef = FirebaseDatabase.getInstance()
                .getReference(PartyFirebasePaths.PRESENCE);

        presenceEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    Boolean online = userSnapshot.child("online").getValue(Boolean.class);
                    Long lastSeen = userSnapshot.child("lastSeen").getValue(Long.class);
                    if (userId != null && online != null) {
                        listener.onPresenceUpdate(userId, online, lastSeen != null ? lastSeen : 0L);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Presence listener cancelled: " + error.getMessage());
            }
        };
        presenceRef.addValueEventListener(presenceEventListener);
    }

    public void stopListening() {
        if (presenceEventListener == null) return;
        FirebaseDatabase.getInstance()
                .getReference(PartyFirebasePaths.PRESENCE)
                .removeEventListener(presenceEventListener);
        presenceEventListener = null;
    }
}
