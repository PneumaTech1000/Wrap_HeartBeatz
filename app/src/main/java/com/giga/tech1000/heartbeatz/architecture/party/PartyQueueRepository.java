package com.giga.tech1000.heartbeatz.architecture.party;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase RTDB queue under {@code parties/{partyId}/queue}.
 * <p>
 * Host owns approve / reject / remove / reorder / ready metadata.
 * Guests may only create {@link PartyQueueItem#STATUS_PENDING} rows attributed to themselves.
 */
public final class PartyQueueRepository {

    private final FirebaseDatabase db = FirebaseDatabase.getInstance();
    private final MutableLiveData<List<PartyQueueItem>> queueLive =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Integer> pendingCountLive = new MutableLiveData<>(0);

    @Nullable private DatabaseReference queueRef;
    @Nullable private ValueEventListener listener;
    @Nullable private String activePartyId;

    @NonNull
    public LiveData<List<PartyQueueItem>> getQueue() {
        return queueLive;
    }

    /** Host badge: number of pending suggestions. */
    @NonNull
    public LiveData<Integer> getPendingCount() {
        return pendingCountLive;
    }

    @Nullable
    public String getActivePartyId() {
        return activePartyId;
    }

    public void observeParty(@NonNull String partyId) {
        if (partyId.equals(activePartyId) && listener != null) {
            return;
        }
        stopObserving();
        activePartyId = partyId;
        queueRef = db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.QUEUE);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<PartyQueueItem> items = new ArrayList<>();
                int pending = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    PartyQueueItem item = child.getValue(PartyQueueItem.class);
                    if (item == null) continue;
                    item.itemId = child.getKey();
                    if (!item.isVisibleInQueue()) continue;
                    items.add(item);
                    if (item.isPending()) pending++;
                }
                items.sort(Comparator
                        .comparingLong((PartyQueueItem i) -> i.orderIndex)
                        .thenComparingLong(i -> i.createdAt));
                queueLive.postValue(items);
                pendingCountLive.postValue(pending);
                PartyLog.d("PartyQueue", "snapshot size=" + items.size() + " pending=" + pending);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                PartyLog.w("PartyQueue", "observe cancelled: " + error.getMessage());
            }
        };
        queueRef.addValueEventListener(listener);
        PartyLog.i("PartyQueue", "observing party=" + partyId);
    }

    public void stopObserving() {
        if (queueRef != null && listener != null) {
            queueRef.removeEventListener(listener);
        }
        listener = null;
        queueRef = null;
        activePartyId = null;
        queueLive.postValue(Collections.emptyList());
        pendingCountLive.postValue(0);
    }

    /**
     * Guest suggests a local library track (metadata only until host approves / has media).
     */
    public void suggestTrack(
            @NonNull String partyId,
            @NonNull PartyQueueItem draft,
            @Nullable Runnable onOk,
            @Nullable java.util.function.Consumer<String> onError) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (onError != null) onError.accept("Sign in required to suggest a track");
            return;
        }
        draft.suggestedByUid = user.getUid();
        if (draft.suggestedByName == null || draft.suggestedByName.isEmpty()) {
            draft.suggestedByName = user.getDisplayName() != null
                    ? user.getDisplayName()
                    : "Guest";
        }
        draft.status = PartyQueueItem.STATUS_PENDING;
        draft.mediaUrl = null;
        draft.objectKey = null;
        if (draft.orderIndex <= 0) {
            draft.orderIndex = System.currentTimeMillis();
        }
        draft.createdAt = System.currentTimeMillis();
        draft.updatedAt = draft.createdAt;

        DatabaseReference ref = db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.QUEUE)
                .push();
        draft.itemId = ref.getKey();
        Map<String, Object> map = toMap(draft, true);
        ref.updateChildren(map)
                .addOnSuccessListener(v -> {
                    PartyLog.i("PartyQueue", "suggested item=" + draft.itemId
                            + " title=" + draft.title);
                    if (onOk != null) onOk.run();
                })
                .addOnFailureListener(e -> {
                    PartyLog.e("PartyQueue", "suggest failed", e);
                    if (onError != null) onError.accept(e.getMessage());
                });
    }

    /**
     * Host adds a track as ready (or approved if URL not yet known).
     */
    public void hostAddTrack(
            @NonNull String partyId,
            @NonNull PartyQueueItem item,
            @Nullable Runnable onOk,
            @Nullable java.util.function.Consumer<String> onError) {
        if (item.status == null || item.status.isEmpty()) {
            item.status = (item.mediaUrl != null && !item.mediaUrl.isEmpty())
                    ? PartyQueueItem.STATUS_READY
                    : PartyQueueItem.STATUS_APPROVED;
        }
        if (item.orderIndex <= 0) {
            item.orderIndex = System.currentTimeMillis();
        }
        item.updatedAt = System.currentTimeMillis();
        if (item.createdAt <= 0) item.createdAt = item.updatedAt;

        DatabaseReference ref = db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.QUEUE)
                .push();
        item.itemId = ref.getKey();
        ref.updateChildren(toMap(item, true))
                .addOnSuccessListener(v -> {
                    PartyLog.i("PartyQueue", "hostAdd item=" + item.itemId
                            + " status=" + item.status);
                    if (onOk != null) onOk.run();
                })
                .addOnFailureListener(e -> {
                    PartyLog.e("PartyQueue", "hostAdd failed", e);
                    if (onError != null) onError.accept(e.getMessage());
                });
    }

    /**
     * Upsert by trackId when host finishes cloud upload — attaches mediaUrl and marks READY.
     */
    public void hostMarkTrackReady(
            @NonNull String partyId,
            @Nullable String trackId,
            @Nullable String mediaUrl,
            @Nullable String objectKey,
            @Nullable String title,
            @Nullable String artist,
            @Nullable String album,
            long durationMs) {
        if (trackId == null || trackId.isEmpty()) return;
        if (mediaUrl == null || mediaUrl.isEmpty()) return;

        DatabaseReference root = db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.QUEUE);
        root.orderByChild("trackId").equalTo(trackId).limitToFirst(5)
                .get()
                .addOnSuccessListener(snap -> {
                    boolean updated = false;
                    for (DataSnapshot child : snap.getChildren()) {
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("mediaUrl", mediaUrl);
                        if (objectKey != null) patch.put("objectKey", objectKey);
                        patch.put("status", PartyQueueItem.STATUS_READY);
                        patch.put("updatedAt", ServerValue.TIMESTAMP);
                        if (title != null) patch.put("title", title);
                        if (artist != null) patch.put("artist", artist);
                        if (album != null) patch.put("album", album);
                        if (durationMs > 0) patch.put("durationMs", durationMs);
                        child.getRef().updateChildren(patch);
                        updated = true;
                    }
                    if (!updated) {
                        PartyQueueItem item = new PartyQueueItem();
                        item.trackId = trackId;
                        item.title = title;
                        item.artist = artist;
                        item.album = album;
                        item.mediaUrl = mediaUrl;
                        item.objectKey = objectKey;
                        item.durationMs = durationMs;
                        item.status = PartyQueueItem.STATUS_READY;
                        item.orderIndex = System.currentTimeMillis();
                        item.suggestedByName = "Host";
                        hostAddTrack(partyId, item, null, null);
                    }
                })
                .addOnFailureListener(e ->
                        PartyLog.w("PartyQueue", "hostMarkTrackReady query failed: " + e.getMessage()));
    }

    public void approve(@NonNull String partyId, @NonNull String itemId) {
        patchStatus(partyId, itemId, PartyQueueItem.STATUS_APPROVED);
    }

    public void reject(@NonNull String partyId, @NonNull String itemId) {
        patchStatus(partyId, itemId, PartyQueueItem.STATUS_REJECTED);
    }

    public void remove(@NonNull String partyId, @NonNull String itemId) {
        patchStatus(partyId, itemId, PartyQueueItem.STATUS_REMOVED);
    }

    public void markPlaying(@NonNull String partyId, @NonNull String itemId) {
        patchStatus(partyId, itemId, PartyQueueItem.STATUS_PLAYING);
    }

    private void patchStatus(
            @NonNull String partyId,
            @NonNull String itemId,
            @NonNull String status) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("status", status);
        patch.put("updatedAt", ServerValue.TIMESTAMP);
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.QUEUE)
                .child(itemId)
                .updateChildren(patch)
                .addOnFailureListener(e ->
                        PartyLog.e("PartyQueue", "patchStatus " + status + " failed", e));
    }

    /**
     * Host reorder: write contiguous orderIndex 0..n-1 for visible items.
     */
    public void reorder(@NonNull String partyId, @NonNull List<String> orderedItemIds) {
        Map<String, Object> updates = new HashMap<>();
        for (int i = 0; i < orderedItemIds.size(); i++) {
            String id = orderedItemIds.get(i);
            if (id == null) continue;
            updates.put(id + "/orderIndex", (long) i);
            updates.put(id + "/updatedAt", ServerValue.TIMESTAMP);
        }
        if (updates.isEmpty()) return;
        db.getReference(PartyFirebasePaths.PARTIES)
                .child(partyId)
                .child(PartyFirebasePaths.QUEUE)
                .updateChildren(updates)
                .addOnFailureListener(e ->
                        PartyLog.e("PartyQueue", "reorder failed", e));
    }

    @NonNull
    private static Map<String, Object> toMap(@NonNull PartyQueueItem item, boolean includeTimestamps) {
        Map<String, Object> map = new HashMap<>();
        put(map, "trackId", item.trackId);
        put(map, "title", item.title);
        put(map, "artist", item.artist);
        put(map, "album", item.album);
        put(map, "status", item.status);
        put(map, "suggestedByUid", item.suggestedByUid);
        put(map, "suggestedByName", item.suggestedByName);
        map.put("orderIndex", item.orderIndex);
        map.put("durationMs", item.durationMs);
        put(map, "mediaUrl", item.mediaUrl);
        put(map, "objectKey", item.objectKey);
        if (includeTimestamps) {
            map.put("createdAt", item.createdAt > 0 ? item.createdAt : ServerValue.TIMESTAMP);
            map.put("updatedAt", ServerValue.TIMESTAMP);
        }
        return map;
    }

    private static void put(Map<String, Object> map, String key, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }
}
