package com.giga.tech1000.heartbeatz.architecture.media;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Keeps free-tier object storage lean for online party media.
 * During party: files stay. On track remove or party end: deleted from cloud.
 */
public final class PartyMediaLifecycle {

    private static final String TAG = "PartyMediaLifecycle";

    private final PartyMediaStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "party-media-lifecycle");
        t.setDaemon(true);
        return t;
    });

    public PartyMediaLifecycle(@NonNull PartyMediaStore store) {
        this.store = store;
    }

    public void registerUploaded(
            @NonNull String partyId,
            @NonNull String objectKey,
            @Nullable String trackId) {
        if (partyId.isEmpty() || objectKey.isEmpty()) return;
        try {
            DatabaseReference ref = mediaIndexRef(partyId).child(safeFirebaseKey(objectKey));
            Map<String, Object> row = new HashMap<>();
            row.put("objectKey", objectKey);
            if (trackId != null) row.put("trackId", trackId);
            row.put("uploadedAt", System.currentTimeMillis());
            ref.setValue(row);
            Log.d(TAG, "Registered media " + objectKey);
        } catch (Exception e) {
            Log.w(TAG, "registerUploaded failed", e);
        }
    }

    public void deleteTrackObject(@Nullable String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) return;
        io.execute(() -> {
            try {
                store.delete(objectKey);
                Log.i(TAG, "Deleted track object: " + objectKey);
            } catch (Exception e) {
                Log.w(TAG, "deleteTrackObject failed: " + objectKey, e);
            }
        });
        String partyId = partyIdFromKey(objectKey);
        if (partyId != null) {
            try {
                mediaIndexRef(partyId).child(safeFirebaseKey(objectKey)).removeValue();
            } catch (Exception ignored) {
            }
        }
    }

    public void deleteTrackObject(@NonNull String partyId, @Nullable String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) return;
        io.execute(() -> {
            try {
                store.delete(objectKey);
                Log.i(TAG, "Deleted track object: " + objectKey);
            } catch (Exception e) {
                Log.w(TAG, "deleteTrackObject failed: " + objectKey, e);
            }
        });
        try {
            mediaIndexRef(partyId).child(safeFirebaseKey(objectKey)).removeValue();
        } catch (Exception ignored) {
        }
    }

    public void purgeParty(@Nullable String partyId) {
        if (partyId == null || partyId.isEmpty()) return;
        final String id = partyId;
        io.execute(() -> {
            List<String> keys = new ArrayList<>();
            try {
                keys.addAll(readIndexKeysBlocking(id));
            } catch (Exception e) {
                Log.w(TAG, "mediaIndex read failed", e);
            }
            for (String key : keys) {
                try {
                    store.delete(key);
                } catch (Exception e) {
                    Log.w(TAG, "delete key failed: " + key, e);
                }
            }
            try {
                store.deletePartyPrefix(id);
            } catch (Exception e) {
                Log.w(TAG, "deletePartyPrefix failed", e);
            }
            try {
                mediaIndexRef(id).removeValue();
            } catch (Exception e) {
                Log.w(TAG, "mediaIndex clear failed", e);
            }
            Log.i(TAG, "Purged party media for " + id + " (index keys=" + keys.size() + ")");
        });
    }

    @NonNull
    private List<String> readIndexKeysBlocking(@NonNull String partyId) throws Exception {
        List<String> out = new ArrayList<>();
        DataSnapshot snap = com.google.android.gms.tasks.Tasks.await(
                mediaIndexRef(partyId).get(),
                15,
                TimeUnit.SECONDS);
        if (snap == null || !snap.exists()) return out;
        for (DataSnapshot child : snap.getChildren()) {
            Object key = child.child("objectKey").getValue();
            if (key instanceof String && !((String) key).isEmpty()) {
                out.add((String) key);
            } else if (child.getValue() instanceof String) {
                out.add((String) child.getValue());
            }
        }
        return out;
    }

    @NonNull
    private static DatabaseReference mediaIndexRef(@NonNull String partyId) {
        return FirebaseDatabase.getInstance()
                .getReference("parties")
                .child(partyId)
                .child("mediaIndex");
    }

    @NonNull
    private static String safeFirebaseKey(@NonNull String objectKey) {
        return objectKey.replace(".", "_")
                .replace("#", "_")
                .replace("$", "_")
                .replace("[", "_")
                .replace("]", "_")
                .replace("/", "_");
    }

    @Nullable
    private static String partyIdFromKey(@NonNull String objectKey) {
        if (!objectKey.startsWith("parties/")) return null;
        String rest = objectKey.substring("parties/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;
        return rest.substring(0, slash);
    }

    public void shutdown() {
        io.shutdownNow();
    }
}
