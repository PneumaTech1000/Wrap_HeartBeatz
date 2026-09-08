package com.giga.tech1000.heartbeatz.architecture.repositories;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Base Firebase Repository handling common Firebase operations
 */
public abstract class FirebaseRepository {

    protected static final String TAG = "FirebaseRepository";
    protected final FirebaseDatabase database;
    protected final DatabaseReference databaseReference;
    protected final FirebaseAuth auth;

    public FirebaseRepository(@NonNull String node) {
        this.database = FirebaseDatabase.getInstance();
        this.databaseReference = database.getReference(node);
        this.auth = FirebaseAuth.getInstance();
        Log.d(TAG, "Initialized FirebaseRepository for node: " + node);
    }

    protected boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    protected String getCurrentUserId() {
        return isAuthenticated() ? auth.getCurrentUser().getUid() : null;
    }

    protected void addValueEventListener(@NonNull DatabaseReference ref, @NonNull ValueEventListener listener) {
        if (isAuthenticated()) {
            ref.addValueEventListener(listener);
        }
    }

    protected void removeEventListener(@NonNull DatabaseReference ref, @NonNull ValueEventListener listener) {
        ref.removeEventListener(listener);
    }
}