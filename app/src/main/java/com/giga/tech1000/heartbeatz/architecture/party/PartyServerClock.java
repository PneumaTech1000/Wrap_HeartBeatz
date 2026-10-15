package com.giga.tech1000.heartbeatz.architecture.party;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.giga.tech1000.heartbeatz.architecture.party.PartyLog;
/**
 * Firebase {@code .info/serverTimeOffset}: difference between server UTC and device clock.
 * {@code serverNow = System.currentTimeMillis() + offset}.
 */
public final class PartyServerClock {
    private static final PartyServerClock INSTANCE = new PartyServerClock();

    private volatile long offsetMs;
    private volatile boolean ready;
    @Nullable private ValueEventListener listener;

    private PartyServerClock() {}

    @NonNull
    public static PartyServerClock get() {
        return INSTANCE;
    }

    public void start() {
        if (listener != null) return;
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long v = snapshot.getValue(Long.class);
                if (v != null) {
                    long prev = offsetMs;
                    offsetMs = v;
                    boolean first = !ready;
                    ready = true;
                    // SET diagnostics: clock offset is critical for multi-device ideal
                    if (first || Math.abs(prev - offsetMs) > 5) {
                        PartyLog.i("SET", "clock role=any offsetMs=" + offsetMs
                                + " serverNow=" + (System.currentTimeMillis() + offsetMs)
                                + " deviceWall=" + System.currentTimeMillis()
                                + " ready=true");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                PartyLog.w("PartyServerClock", "serverTimeOffset cancelled: " + error.getMessage());
            }
        };
        FirebaseDatabase.getInstance()
                .getReference(".info/serverTimeOffset")
                .addValueEventListener(listener);
    }

    public void stop() {
        if (listener != null) {
            FirebaseDatabase.getInstance()
                    .getReference(".info/serverTimeOffset")
                    .removeEventListener(listener);
            listener = null;
        }
    }

    public boolean isReady() {
        return ready;
    }

    /** Estimated Firebase server time (ms). */
    public long serverNowMs() {
        return System.currentTimeMillis() + offsetMs;
    }

    public long getOffsetMs() {
        return offsetMs;
    }
}
