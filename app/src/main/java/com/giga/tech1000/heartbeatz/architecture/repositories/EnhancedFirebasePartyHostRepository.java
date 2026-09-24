package com.giga.tech1000.heartbeatz.architecture.repositories;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.utils.PartyIdUtil;

import com.google.firebase.BuildConfig;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.giga.tech1000.party_mode.model.PartyHost;
import com.giga.tech1000.heartbeatz.architecture.party.PartyPresenceStore;
import com.giga.tech1000.heartbeatz.architecture.party.PartyFirebasePaths;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enhanced Firebase-based implementation of PartyHostRepository
 * Replaces the UDP-based discovery with Firebase Realtime Database
 * Includes improved error handling, network awareness, and additional features
 */
public class EnhancedFirebasePartyHostRepository extends FirebaseRepository implements PartyHostRepository {

    private static final String TAG = "EnhancedFirebasePartyHostRepository";
    private static final String PARTIES_NODE = PartyFirebasePaths.PARTIES;
    private static final String PRESENCE_NODE = PartyFirebasePaths.PRESENCE;
    private final PartyPresenceStore presenceStore = new PartyPresenceStore();
    private static final String USERS_NODE = "users";

    // LiveData for UI observation
    private final MutableLiveData<List<PartyHost>> discoveredHostsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<PartyHost> hostedPartyLiveData = new MutableLiveData<>(null);
    /** uid → display name for guest roster UI */
    private final java.util.Map<String, String> memberUidToName = new java.util.concurrent.ConcurrentHashMap<>();
    private final MutableLiveData<List<String>> connectedGuestsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> guestCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<PartyHost> connectedHostLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> guestAuthenticatedLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> networkConnectedLiveData = new MutableLiveData<>(true);
    private final MutableLiveData<String> partyErrorLiveData = new MutableLiveData<>(null);

    // Event listeners
    private ChildEventListener partiesEventListener;
    private ValueEventListener hostedPartyEventListener;
    private ChildEventListener connectedGuestsEventListener;
    private ValueEventListener guestAuthenticatedEventListener;
    private ValueEventListener partyEventListener;
    private ValueEventListener presenceEventListener;
    private ChildEventListener membersEventListener;

    // Current state tracking
    private String currentPartyId;
    private boolean isHosting = false;
    private boolean isDiscovering = false;
    private boolean isNetworkConnected = true;

    // Enhanced features
    private final Map<String, Long> lastSeenTimestamps = new ConcurrentHashMap<>();
    private final List<NetworkChangeListener> networkListeners = new CopyOnWriteArrayList<>();
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    public EnhancedFirebasePartyHostRepository(Context context) {
        super(PARTIES_NODE);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        setupNetworkMonitoring();
        Log.d(TAG, "EnhancedFirebasePartyHostRepository initialized");
    }

    public void release() {
        // super.onCleared();
        tearDownNetworkMonitoring();
        // Clean up all listeners to prevent memory leaks
        stopDiscovery();
        stopListeningToMembers();
        cleanupPartyListener();
        stopPresenceListener();
        if (guestAuthenticatedEventListener != null) {
            if (currentPartyId != null) {
                DatabaseReference authRef = databaseReference.child(currentPartyId)
                        .child("authenticatedUsers")
                        .child(getCurrentUserId());
                authRef.removeEventListener(guestAuthenticatedEventListener);
            }
            guestAuthenticatedEventListener = null;
        }
        if (hostedPartyEventListener != null) {
            if (currentPartyId != null) {
                DatabaseReference partyRef = databaseReference.child(currentPartyId);
                partyRef.removeEventListener(hostedPartyEventListener);
            }
            hostedPartyEventListener = null;
        }
        if (connectedGuestsEventListener != null) {
            if (currentPartyId != null) {
                DatabaseReference guestsRef = databaseReference.child(currentPartyId)
                        .child("connectedGuests");
                guestsRef.removeEventListener(connectedGuestsEventListener);
            }
            connectedGuestsEventListener = null;
        }
    }

    // ============ NETWORK MONITORING ============

    private void setupNetworkMonitoring() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    super.onAvailable(network);
                    setNetworkConnected(true);
                }

                @Override
                public void onLost(android.net.Network network) {
                    super.onLost(network);
                    setNetworkConnected(false);
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            // Legacy approach for older Android versions
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            // Note: In a real implementation, you'd register a BroadcastReceiver here
            // For simplicity, we're showing the concept
        }
    }

    private void tearDownNetworkMonitoring() {
        if (connectivityManager != null && networkCallback != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
            // Unregister broadcast receiver for legacy approach
        }
    }

    private void setNetworkConnected(boolean connected) {
        if (isNetworkConnected != connected) {
            isNetworkConnected = connected;
            networkConnectedLiveData.postValue(connected);
            if (!connected) {
                // Handle disconnection - pause automatic operations
                handleNetworkLoss();
            } else {
                // Handle reconnection - resume operations
                handleNetworkRestore();
            }
            notifyNetworkListeners(connected);
        }
    }

    private void handleNetworkLoss() {
        Log.w(TAG, "Network connection lost");
        // Pause discovery to save battery
        if (isDiscovering) {
            stopDiscoveryInternal();
        }
        // Could optionally pause other operations
    }

    private void handleNetworkRestore() {
        Log.i(TAG, "Network connection restored");
        // Resume discovery if it was active
        if (isDiscovering) {
            startDiscoveryInternal();
        }
        // Could optionally refresh data
    }

    private void notifyNetworkListeners(boolean connected) {
        for (NetworkChangeListener listener : networkListeners) {
            listener.onNetworkChange(connected);
        }
    }

    public interface NetworkChangeListener {
        void onNetworkChange(boolean isConnected);
    }

    public void addNetworkChangeListener(NetworkChangeListener listener) {
        networkListeners.add(listener);
    }

    public void removeNetworkChangeListener(NetworkChangeListener listener) {
        networkListeners.remove(listener);
    }

    @NonNull
    public LiveData<Boolean> isNetworkConnected() {
        return networkConnectedLiveData;
    }

    // ============ PARTY DISCOVERY (enhanced with improved error handling) ============

    @Override
    public void startDiscovery() {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot start discovery: no network connection");
            partyErrorLiveData.postValue("No internet connection");
            return;
        }

        Log.d(TAG, "Starting enhanced party discovery via Firebase");
        isDiscovering = true;

        // Listen for parties in the database with enhanced error handling
        Query partiesQuery = databaseReference
                .orderByChild("timestamp")
                .limitToLast(50); // Get recent parties

        // Clean up any existing listener
        if (partiesEventListener != null) {
            databaseReference.removeEventListener(partiesEventListener);
        }

        partiesEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                try {
                    PartyHost party = snapshot.getValue(PartyHost.class);
                    if (party != null) {
                        // Validate and enhance party data
                        party = validateAndEnhanceParty(party, snapshot.getKey());
                        if (party != null) {
                            updateDiscoveredHostsList(party, true);
                            updateLastSeen(party.getPartyId());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing party onChildAdded", e);
                    partyErrorLiveData.postValue("Error processing party data");
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                try {
                    PartyHost party = snapshot.getValue(PartyHost.class);
                    if (party != null) {
                        // Validate and enhance party data
                        party = validateAndEnhanceParty(party, snapshot.getKey());
                        if (party != null) {
                            updateDiscoveredHostsList(party, true);
                            updateLastSeen(party.getPartyId());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing party onChildChanged", e);
                    partyErrorLiveData.postValue("Error processing party data");
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                try {
                    PartyHost party = snapshot.getValue(PartyHost.class);
                    if (party != null) {
                        updateDiscoveredHostsList(party, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing party onChildRemoved", e);
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Handle ordering changes if needed
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Discovery cancelled: " + error.getMessage());
                partyErrorLiveData.postValue("Discovery failed: " + error.getMessage());
                isDiscovering = false;
            }
        };

        databaseReference.addChildEventListener(partiesEventListener);
    }

    @Override
    public void stopDiscovery() {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot stop discovery: no network connection");
            return;
        }

        Log.d(TAG, "Stopping party discovery");
        isDiscovering = false;

        if (databaseReference != null && partiesEventListener != null) {
            databaseReference.removeEventListener(partiesEventListener);
            partiesEventListener = null;
        }

        // Clear discovered hosts when stopping discovery
        discoveredHostsLiveData.postValue(new ArrayList<>());
    }

    private void startDiscoveryInternal() {
        startDiscovery();
    }

    private void stopDiscoveryInternal() {
        stopDiscovery();
    }

    private PartyHost validateAndEnhanceParty(PartyHost party, String snapshotKey) {
        // Validate required fields
        if (party == null) {
            return null;
        }

        // Ensure partyId is set
        if (party.getPartyId() == null || party.getPartyId().isEmpty()) {
            party.setPartyId(snapshotKey);
        }

        // Ensure timestamp is set
        if (party.getTimestamp() == 0) {
            party.setTimestamp(System.currentTimeMillis());
        }

        // Ensure IP address is set
        if (party.getIpAddress() == null || party.getIpAddress().isEmpty()) {
            party.setIpAddress(getLocalIpAddress());
        }

        // Validate port
        if (party.getPort() <= 0) {
            party.setPort(getSecretPort());
        }

        // Ensure required strings are not null
        if (party.getPartyName() == null) {
            party.setPartyName("Unnamed Party");
        }
        if (party.getOwnerId() == null) {
            party.setOwnerId("unknown");
        }


        return party;
    }

    private void updateDiscoveredHostsList(PartyHost party, boolean addOrUpdate) {
        List<PartyHost> currentList = discoveredHostsLiveData.getValue();
        if (currentList == null) {
            currentList = new ArrayList<>();
        }

        boolean found = false;
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).getPartyId() != null &&
                    currentList.get(i).getPartyId().equals(party.getPartyId())) {
                if (addOrUpdate) {
                    currentList.set(i, party);
                } else {
                    currentList.remove(i);
                }
                found = true;
                break;
            }
        }

        if (!found && addOrUpdate) {
            currentList.add(party);
        }

        // Sort by timestamp (newest first)
        currentList.sort((p1, p2) -> Long.compare(p2.getTimestamp(), p1.getTimestamp()));

        discoveredHostsLiveData.postValue(new ArrayList<>(currentList));
    }

    private void updateLastSeen(String partyId) {
        if (partyId != null) {
            lastSeenTimestamps.put(partyId, System.currentTimeMillis());
        }
    }

    @Override
    public boolean isDiscoveringHosts() {
        return isDiscovering;
    }

    @NonNull
    @Override
    public LiveData<List<PartyHost>> getDiscoveredHosts() {
        return discoveredHostsLiveData;
    }

    // ============ PRESENCE AND AWARENESS FEATURES ============

    /**
     * Update user presence in the system
     * @param userId The user ID
     * @param isOnline Whether the user is online
     */
    public void updateUserPresence(String userId, boolean isOnline) {
        if (!isNetworkConnected || userId == null || userId.isEmpty()) {
            return;
        }
        presenceStore.setOnline(userId, isOnline, isAuthenticated()
                && userId.equals(getCurrentUserId()));
    }

    /**
     * Listen for presence updates from other users
     * @param listener Callback for presence updates
     */
    public void startPresenceListener(PresenceListener listener) {
        if (!isNetworkConnected) {
            return;
        }

        DatabaseReference presenceRef = FirebaseDatabase.getInstance()
                .getReference(PRESENCE_NODE);

        if (presenceEventListener != null) {
            presenceRef.removeEventListener(presenceEventListener);
        }

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

    public void stopPresenceListener() {
        presenceStore.stopListening();
        presenceEventListener = null;
    }

    public interface PresenceListener {
        void onPresenceUpdate(String userId, boolean isOnline, long lastSeen);
    }

    // ============ HOST CREATION & MANAGEMENT (enhanced) ============

    @Override
    public void createParty(@NonNull String partyName, @NonNull String pin) {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot create party: no network connection");
            partyErrorLiveData.postValue("No internet connection");
            return;
        }

        // Check if user is authenticated
        if (!isAuthenticated()) {
            Log.w(TAG, "Cannot create party: user not authenticated");
            partyErrorLiveData.postValue("Please sign in to create a party");
            return;
        }

        Log.d(TAG, "Creating party: " + partyName);

        // Validate inputs
        if (partyName == null || partyName.trim().isEmpty()) {
            partyErrorLiveData.postValue("Party name cannot be empty");
            return;
        }

        if (partyName.length() > 100) {
            partyErrorLiveData.postValue("Party name too long (max 100 characters)");
            return;
        }

        // Generate a new party ID
        String partyId = databaseReference.push().getKey();

        if (partyId == null) {
            Log.e(TAG, "Failed to generate party ID");
            partyErrorLiveData.postValue("Failed to create party");
            return;
        }

        PartyHost host = new PartyHost();
        host.setPartyId(partyId);
        host.setPartyName(partyName.trim());
        host.setPin(pin);
        host.setOwnerId(getCurrentUserId());
        host.setIpAddress(getLocalIpAddress());
        host.setPort(getSecretPort());
        host.setTimestamp(System.currentTimeMillis());
        host.setPasswordProtected(!pin.isEmpty());

        // Save to Firebase with completion listener
        DatabaseReference partyRef = databaseReference.child(partyId);
        partyRef.setValue(host)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Party created successfully: " + partyId);
                    hostedPartyLiveData.postValue(host);
                    isHosting = true;
                    currentPartyId = partyId;

                    // Update presence
                    updateUserPresence(getCurrentUserId(), true);

                    // Register host in members (display name for roster)
                    String hostUid = getCurrentUserId();
                    if (hostUid != null) {
                        DatabaseSession hostSession = new DatabaseSession();
                        hostSession.setUserId(hostUid);
                        hostSession.setDisplayName(resolveDisplayName(hostUid));
                        hostSession.setJoinedAt(System.currentTimeMillis());
                        partyRef.child("members").child(hostUid).setValue(hostSession);
                    }

                    // Start listening to members for this party
                    startListeningToMembers(partyId);
                    // Set up listener for the specific party to monitor changes
                    setupPartyListener(partyId);

                    // Clear any previous errors
                    partyErrorLiveData.postValue(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create party: " + e.getMessage());
                    partyErrorLiveData.postValue("Failed to create party: " + e.getMessage());
                });
    }

    @Override
    public void stopHosting() {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot stop hosting: no network connection");
            partyErrorLiveData.postValue("No internet connection");
            return;
        }

        if (!isHosting || currentPartyId == null) {
            Log.w(TAG, "Not currently hosting a party");
            partyErrorLiveData.postValue("Not hosting a party");
            return;
        }

        Log.d(TAG, "Stopping hosting for party: " + currentPartyId);

        // Stop listening to members
        stopListeningToMembers();
        // Clean up party listener
        cleanupPartyListener();

        DatabaseReference partyRef = databaseReference.child(currentPartyId);
        partyRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Party stopped successfully: " + currentPartyId);
                    hostedPartyLiveData.postValue(null);
                    connectedHostLiveData.postValue(null);
                    connectedGuestsLiveData.postValue(new ArrayList<>());
                    guestCountLiveData.postValue(0);
                    guestAuthenticatedLiveData.postValue(false);
                    isHosting = false;
                    currentPartyId = null;

                    // Update presence
                    updateUserPresence(getCurrentUserId(), false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to stop party: " + e.getMessage());
                    partyErrorLiveData.postValue("Failed to stop party: " + e.getMessage());
                });
    }

    @NonNull
    @Override
    public LiveData<PartyHost> getHostedParty() {
        return hostedPartyLiveData;
    }

    @NonNull
    @Override
    public LiveData<List<String>> getConnectedGuests() {
        return connectedGuestsLiveData;
    }

    @NonNull
    @Override
    public LiveData<Integer> getGuestCount() {
        return guestCountLiveData;
    }

    // ============ GUEST CONNECTION (enhanced) ============

    @Override
    public void joinParty(@NonNull PartyHost host, @NonNull String pin) {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot join party: no network connection");
            partyErrorLiveData.postValue("No internet connection");
            return;
        }

        // Check if user is authenticated
        if (!isAuthenticated()) {
            Log.w(TAG, "Cannot join party: user not authenticated");
            partyErrorLiveData.postValue("Please sign in to join a party");
            return;
        }

        Log.d(TAG, "Joining party: " + host.getPartyName() + " (ID: " + host.getPartyId() + ")");

        // Validate inputs
        if (host == null) {
            partyErrorLiveData.postValue("Invalid party information");
            return;
        }

        if (host.getPartyId() == null || host.getPartyId().isEmpty()) {
            partyErrorLiveData.postValue("Invalid party ID");
            return;
        }

        // Validate PIN first
        if (!host.validatePin(pin)) {
            Log.w(TAG, "Invalid PIN for party: " + host.getPartyName());
            guestAuthenticatedLiveData.postValue(false);
            partyErrorLiveData.postValue("Invalid PIN");
            return;
        }

        currentPartyId = host.getPartyId();
        isHosting = false;

        // Guest connects TO this host — never mark as hosted party (that implies HOSTING)
        connectedHostLiveData.postValue(host);
        hostedPartyLiveData.postValue(null);

        // Mark as attempting to connect
        guestAuthenticatedLiveData.postValue(false);
        partyErrorLiveData.postValue(null);

        // PIN already validated: guest self-registers (no host-side auth gate required)
        DatabaseReference partyRef = databaseReference.child(currentPartyId);
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            partyErrorLiveData.postValue("Please sign in to join a party");
            return;
        }

        DatabaseReference authRef = partyRef.child("authenticatedUsers").child(currentUserId);
        if (guestAuthenticatedEventListener != null) {
            authRef.removeEventListener(guestAuthenticatedEventListener);
            guestAuthenticatedEventListener = null;
        }

        DatabaseSession session = createGuestSession(host);

        // 1) Mark self authenticated under this party (rules: $uid == auth.uid)
        authRef.setValue(true)
                .addOnFailureListener(e -> Log.w(TAG, "authenticatedUsers write: " + e.getMessage()));

        // 2) Join members list
        partyRef.child("members").child(currentUserId).setValue(session)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Joined party as member: " + host.getPartyName());
                    guestAuthenticatedLiveData.postValue(true);
                    connectedHostLiveData.postValue(host);
                    startListeningToMembers(currentPartyId);
                    setupPartyListener(currentPartyId);
                    partyErrorLiveData.postValue(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to join party as member", e);
                    guestAuthenticatedLiveData.postValue(false);
                    partyErrorLiveData.postValue("Failed to join party: " + e.getMessage());
                });
    }

    @NonNull
    private String resolveDisplayName(@Nullable String userId) {
        try {
            com.google.firebase.auth.FirebaseUser u = auth.getCurrentUser();
            if (u != null && u.getDisplayName() != null && !u.getDisplayName().trim().isEmpty()) {
                return u.getDisplayName().trim();
            }
            if (u != null && u.getEmail() != null && !u.getEmail().isEmpty()) {
                String e = u.getEmail();
                int at = e.indexOf('@');
                return at > 0 ? e.substring(0, at) : e;
            }
        } catch (Exception ignored) { }
        if (userId != null && userId.length() > 6) {
            return "Guest " + userId.substring(0, 6);
        }
        return "Guest";
    }

    private DatabaseSession createGuestSession(PartyHost host) {
        long joinTime = System.currentTimeMillis();
        String userId = getCurrentUserId();
        String partyToken = PartyIdUtil.encodePartyId(host.getPartyId(), userId);

        DatabaseSession session = new DatabaseSession();
        session.setUserId(userId);
        session.setPartyToken(partyToken);
        session.setJoinedAt(joinTime);
        session.setUserAgent("HeartBeatz/" + BuildConfig.VERSION_NAME);
        session.setDeviceModel(Build.MODEL);
        session.setOsVersion(String.valueOf(Build.VERSION.SDK_INT));
        session.setDisplayName(resolveDisplayName(userId));

        return session;
    }

    @Override
    public void leaveParty() {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot leave party: no network connection");
            partyErrorLiveData.postValue("No internet connection");
            return;
        }

        if (currentPartyId == null) {
            Log.w(TAG, "Not currently connected to a party");
            partyErrorLiveData.postValue("Not in a party");
            return;
        }

        // Prevent host from leaving without transferring host first
        if (isHosting) {
            Log.w(TAG, "Host must transfer host to a guest before leaving the party");
            partyErrorLiveData.postValue("Transfer host before leaving");
            return;
        }

        Log.d(TAG, "Leaving party: " + currentPartyId);
        String userId = getCurrentUserId();

        if (userId == null) {
            Log.w(TAG, "Cannot leave party: user not authenticated");
            partyErrorLiveData.postValue("Please sign in");
            return;
        }

        // Stop listening to members
        stopListeningToMembers();
        // Clean up party listener
        cleanupPartyListener();

        DatabaseReference partyRef = databaseReference.child(currentPartyId);

        if (userId != null) {
            partyRef.child("members").child(userId).removeValue();
            partyRef.child("authenticatedUsers").child(userId).removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Successfully left party");
                        updateUserPresence(userId, false);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to leave party", e);
                        partyErrorLiveData.postValue("Failed to leave party: " + e.getMessage());
                    });
        }

        // Remove authentication listener
        if (guestAuthenticatedEventListener != null) {
            DatabaseReference authRef = partyRef.child("authenticatedUsers");
            if (authRef != null) {
                authRef.removeEventListener(guestAuthenticatedEventListener);
            }
            guestAuthenticatedEventListener = null;
        }

        // Reset state
        guestAuthenticatedLiveData.postValue(false);
        connectedHostLiveData.postValue(null);
        hostedPartyLiveData.postValue(null);
        currentPartyId = null;
        isHosting = false;
    }

    // ============ MEMBER & PARTY LISTENERS ============

    /**
     * Start listening to members of a specific party
     * @param partyId The ID of the party to listen to
     */
    private void startListeningToMembers(@NonNull String partyId) {
        if (!isNetworkConnected || partyId == null) {
            return;
        }

        // Clean up any existing listener
        stopListeningToMembers();

        DatabaseReference membersRef = databaseReference.child(partyId)
                .child("members");

        membersEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String userId = snapshot.getKey();
                if (userId == null) return;
                // Skip listing the host as a "guest" row when host is also under members
                if (isHosting && userId.equals(getCurrentUserId())) {
                    return;
                }
                String label = userId;
                try {
                    DatabaseSession session = snapshot.getValue(DatabaseSession.class);
                    if (session != null && session.getDisplayName() != null
                            && !session.getDisplayName().trim().isEmpty()) {
                        label = session.getDisplayName().trim();
                    } else {
                        // Fallback: users/{uid}/displayName
                        label = fetchUserDisplayNameSync(userId);
                    }
                } catch (Exception e) {
                    label = "Guest " + userId.substring(0, Math.min(6, userId.length()));
                }
                memberUidToName.put(userId, label);
                publishGuestNames();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String userId = snapshot.getKey();
                if (userId == null) return;
                try {
                    DatabaseSession session = snapshot.getValue(DatabaseSession.class);
                    if (session != null && session.getDisplayName() != null
                            && !session.getDisplayName().trim().isEmpty()) {
                        memberUidToName.put(userId, session.getDisplayName().trim());
                        publishGuestNames();
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String userId = snapshot.getKey();
                if (userId != null) {
                    memberUidToName.remove(userId);
                    publishGuestNames();
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Handle member reordering if needed
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Members listener cancelled: " + error.getMessage());
                partyErrorLiveData.postValue("Failed to listen to party members: " + error.getMessage());
            }
        };

        membersRef.addChildEventListener(membersEventListener);
        Log.d(TAG, "Started listening to members for party: " + partyId);
    }

    /**
     * Stop listening to members of the current party
     */
    private void stopListeningToMembers() {
        if (membersEventListener != null && currentPartyId != null) {
            DatabaseReference membersRef = databaseReference.child(currentPartyId)
                    .child("members");
            membersRef.removeEventListener(membersEventListener);
            membersEventListener = null;
            Log.d(TAG, "Stopped listening to members for party: " + currentPartyId);
        }
        memberUidToName.clear();
    }

    /**
     * Set up listener for the specific party to monitor changes (name, host, etc.)
     * @param partyId The ID of the party to listen to
     */
    private void setupPartyListener(@NonNull String partyId) {
        if (!isNetworkConnected || partyId == null) {
            return;
        }

        // Clean up any existing listener
        cleanupPartyListener();

        DatabaseReference partyRef = databaseReference.child(partyId);

        partyEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Party data has been updated (name, host, etc.)
                PartyHost updatedParty = snapshot.getValue(PartyHost.class);
                if (updatedParty != null) {
                    // Validate and enhance the party data
                    updatedParty = validateAndEnhanceParty(updatedParty, partyId);
                    if (updatedParty != null) {
                        // Update the hosted party info if we're hosting
                        if (isHosting && currentPartyId != null && currentPartyId.equals(partyId)) {
                            hostedPartyLiveData.postValue(updatedParty);
                        }
                        // Update the connected host info if we're a guest
                        else if (!isHosting && connectedHostLiveData.getValue() != null &&
                                connectedHostLiveData.getValue().getPartyId() != null &&
                                connectedHostLiveData.getValue().getPartyId().equals(partyId)) {
                            connectedHostLiveData.postValue(updatedParty);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Party listener cancelled: " + error.getMessage());
                partyErrorLiveData.postValue("Failed to listen to party updates: " + error.getMessage());
            }
        };

        partyRef.addValueEventListener(partyEventListener);
        Log.d(TAG, "Set up party listener for: " + partyId);
    }

    /**
     * Clean up the party listener
     */
    private void cleanupPartyListener() {
        if (partyEventListener != null && currentPartyId != null) {
            DatabaseReference partyRef = databaseReference.child(currentPartyId);
            partyRef.removeEventListener(partyEventListener);
            partyEventListener = null;
            Log.d(TAG, "Cleaned up party listener for: " + currentPartyId);
        }
    }

    /**
     * Update the guest list and count in LiveData
     * @param guestList The updated list of guest user IDs
     */
    private void publishGuestNames() {
        List<String> names = new ArrayList<>(memberUidToName.values());
        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        updateGuestListAndCount(names);
    }

    @NonNull
    private String fetchUserDisplayNameSync(@NonNull String uid) {
        try {
            return "Guest " + uid.substring(0, Math.min(6, uid.length()));
        } catch (Exception e) {
            return "Guest";
        }
    }

    @Nullable
    public String findMemberUidByDisplayName(@Nullable String name) {
        if (name == null) return null;
        for (java.util.Map.Entry<String, String> e : memberUidToName.entrySet()) {
            if (name.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    private void updateGuestListAndCount(List<String> guestList) {
        connectedGuestsLiveData.postValue(new ArrayList<>(guestList));
        guestCountLiveData.postValue(guestList.size());
    }

    // ============ HOST CONTROLS (enhanced) ============

    /**
     * Host-only method to kick a guest from the party
     * You must be the host of the party to call this method.
     *
     * @param userIdToKick The user ID of the guest to kick
     */
    public void kickGuest(String userIdToKick) {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot kick guest: no network connection");
            partyErrorLiveData.postValue("No internet connection");
            return;
        }

        if (!isHosting || currentPartyId == null) {
            Log.w(TAG, "Cannot kick guest: not hosting or no active party");
            partyErrorLiveData.postValue("Not hosting a party");
            return;
        }

        // Verify we are the owner
        String ownerId = getOwnerId();
        if (ownerId == null || !ownerId.equals(getCurrentUserId())) {
            Log.w(TAG, "Cannot kick guest: not the party owner");
            partyErrorLiveData.postValue("Only the host can kick guests");
            return;
        }

        // Validate the user to kick is actually a member
        DatabaseReference membersRef = databaseReference.child(currentPartyId)
                .child("members")
                .child(userIdToKick);

        membersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // User is a member, proceed with kick
                    Log.d(TAG, "Host kicking user: " + userIdToKick);

                    // Remove the user from members
                    membersRef.removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Successfully kicked user: " + userIdToKick);
                                // Optionally send a real-time notification via WebRTC
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to kick user: " + e.getMessage());
                                partyErrorLiveData.postValue("Failed to kick user: " + e.getMessage());
                            });
                } else {
                    Log.w(TAG, "Cannot kick guest: user is not a member of the party");
                    partyErrorLiveData.postValue("User is not in this party");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check membership for kicking: " + error.getMessage());
                partyErrorLiveData.postValue("Error checking membership: " + error.getMessage());
            }
        });
    }

    /**
     * Host-only method to transfer host role to a guest
     * You must be the current host to call this method.
     * After transfer, the original host becomes a guest in the party (if they don't leave).
     *
     * @param newHostUserId The user ID of the guest to transfer host role to
     */
    public void transferHost(String newHostUserId) {
        if (!isNetworkConnected) {
            Log.w(TAG, "Cannot transfer host: no network connection");
            partyErrorLiveData.postValue("No internet connection");
            return;
        }

        if (!isHosting || currentPartyId == null) {
            Log.w(TAG, "Cannot transfer host: not hosting or no active party");
            partyErrorLiveData.postValue("Not hosting a party");
            return;
        }

        // Verify we are the owner
        String ownerId = getOwnerId();
        if (ownerId == null || !ownerId.equals(getCurrentUserId())) {
            Log.w(TAG, "Cannot transfer host: not the party owner");
            partyErrorLiveData.postValue("Only the current host can transfer host");
            return;
        }

        if (newHostUserId == null || newHostUserId.isEmpty()) {
            Log.w(TAG, "Cannot transfer host: invalid user ID");
            partyErrorLiveData.postValue("Invalid user ID");
            return;
        }

        // Verify the newHostUserId is a member of the party
        DatabaseReference membersRef = databaseReference.child(currentPartyId)
                .child("members")
                .child(newHostUserId);

        membersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // User is a member, proceed with transfer
                    Log.d(TAG, "Host transferring host role to user: " + newHostUserId);

                    // Update the ownerId in Firebase
                    DatabaseReference partyRef = databaseReference.child(currentPartyId);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("ownerId", newHostUserId);
                    updates.put("timestamp", ServerValue.TIMESTAMP); // Update timestamp

                    partyRef.updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Host transferred to user: " + newHostUserId);
                                // Update local state: we are no longer the host
                                isHosting = false;
                                // Note: We remain in the party as a guest (if we don't leave)
                                // The hostedPartyLiveData will be updated via the partyEventListener
                                // when the ownerId change is propagated from Firebase.
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to transfer host: " + e.getMessage());
                                partyErrorLiveData.postValue("Failed to transfer host: " + e.getMessage());
                            });
                } else {
                    Log.w(TAG, "Cannot transfer host: user is not a member of the party");
                    partyErrorLiveData.postValue("User is not in this party");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check membership for host transfer: " + error.getMessage());
                partyErrorLiveData.postValue("Error checking membership: " + error.getMessage());
            }
        });
    }

    @NonNull
    @Override
    public LiveData<PartyHost> getConnectedHost() {
        return connectedHostLiveData;
    }

    @NonNull
    @Override
    public LiveData<Boolean> isGuestAuthenticated() {
        return guestAuthenticatedLiveData;
    }

    // ============ ERROR HANDLING ============

    @NonNull
    public LiveData<String> getPartyError() {
        return partyErrorLiveData;
    }

    // ============ STATE CHECKS ============

    @Override
    public boolean isHosting() {
        return isHosting;
    }

    @Override
    public boolean isGuest() {
        // Guest = joined or joining a party we do not host
        return !isHosting && currentPartyId != null;
    }

    @Override
    public boolean isInPartyMode() {
        return isHosting() || isGuest();
    }

    // ============ HELPER METHODS ============

    /**
     * Secret port in the dynamic/private range, derived from the signed-in user id.
     * Uses {@link FirebaseRepository#getCurrentUserId()} (real Firebase Auth uid).
     */
    private int getSecretPort() {
        String userId = getCurrentUserId();
        int hash = (userId != null) ? userId.hashCode() : 0;
        return 49152 + (Math.abs(hash) % 16383); // 49152–65534
    }

    /**
     * Get the local IP address in a way that works across different network configurations
     * @return IPv4 address as a string, or "127.0.0.1" if none found
     */
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Ignore loopback, virtual, and down interfaces
                if (iface.isLoopback() || !iface.isUp() ||
                        iface.getName().contains("vm") ||
                        iface.getName().contains("veth") ||
                        iface.getName().contains("docker") ||
                        iface.getName().contains("lo") ||
                        iface.getName().startsWith("br") || // Bridge interfaces
                        iface.getName().startsWith("vmnet")) { // VMware/VirtualBox interfaces
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Skip loopback addresses
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }

                    // Prefer IPv4 addresses
                    if (addr instanceof Inet4Address) {
                        // Check if it's a private IP (we prefer these for local networking)
                        String hostAddress = addr.getHostAddress();
                        if (isPrivateIPv4Address(hostAddress)) {
                            return hostAddress;
                        }
                        // If we haven't found a private IP yet, keep this as fallback
                        if (hostAddress.equals("127.0.0.1")) {
                            continue; // Skip loopback
                        }
                        // Return first non-loopback IPv4 we find
                        return hostAddress;
                    }
                    // If no IPv4 found, we'll accept IPv6 as fallback (though less ideal for direct connections)
                    else if (addr instanceof Inet6Address) {
                        // For IPv6, we might need to handle scope IDs, but for simplicity we'll return it
                        // In a real app, you might want to filter out link-local or site-local addresses
                        String hostAddress = addr.getHostAddress();
                        // Remove scope ID if present (e.g., %eth0)
                        if (hostAddress.contains("%")) {
                            hostAddress = hostAddress.substring(0, hostAddress.indexOf("%"));
                        }
                        // Only return if we haven't found an IPv4 address yet
                        String ipv4Address = getIPv4Address();
                        if (ipv4Address == null || ipv4Address.equals("127.0.0.1")) {
                            return hostAddress;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local IP address", e);
        }
        return "127.0.0.1"; // Fallback
    }

    /**
     * Check if an IPv4 address is in a private range
     * @param ipAddress IPv4 address as string
     * @return true if private, false otherwise
     */
    private boolean isPrivateIPv4Address(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }
        try {
            String[] parts = ipAddress.split("\\.");
            if (parts.length != 4) {
                return false;
            }

            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // Check for private IP ranges:
            // 10.0.0.0 - 10.255.255.255
            // 172.16.0.0 - 172.31.255.255
            // 192.168.0.0 - 192.168.255.255
            return (first == 10) ||
                    (first == 172 && second >= 16 && second <= 31) ||
                    (first == 192 && second == 168);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Get the first IPv4 address found, or null if none
     * @return IPv4 address as string or null
     */
    @Nullable
    private String getIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Ignore loopback, virtual, and down interfaces
                if (iface.isLoopback() || !iface.isUp() ||
                        iface.getName().contains("vm") ||
                        iface.getName().contains("veth") ||
                        iface.getName().contains("docker") ||
                        iface.getName().contains("lo") ||
                        iface.getName().startsWith("br") ||
                        iface.getName().startsWith("vmnet")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Skip loopback addresses
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }

                    // Return IPv4 addresses
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get IPv4 address", e);
        }
        return null;
    }

    /**
     * Get the owner ID of the current party (if hosting)
     * @return Owner user ID or null if not hosting
     */
    private String getOwnerId() {
        PartyHost hostedParty = hostedPartyLiveData.getValue();
        if (hostedParty != null) {
            return hostedParty.getOwnerId();
        }
        return null;
    }

    /**
     * Check if the current user is the owner of the party they're hosting
     * @return true if the current user is the host and owner
     */
    public boolean isCurrentUserOwner() {
        if (!isHosting) {
            return false;
        }
        String currentUserId = getCurrentUserId();
        String ownerId = getOwnerId();
        return currentUserId != null && ownerId != null && currentUserId.equals(ownerId);
    }

    // ============ INTERFACE IMPLEMENTATIONS (delegating to parent) ============


    public List<String> getConnectedGuestsList() {
        return connectedGuestsLiveData.getValue();
    }

    public int getGuestCountValue() {
        return guestCountLiveData.getValue();
    }

    public PartyHost getHostedPartyValue() {
        return hostedPartyLiveData.getValue();
    }

    public PartyHost getConnectedHostValue() {
        return connectedHostLiveData.getValue();
    }

    // Inner class for session data
    public static class DatabaseSession {
        private String userId;
        private String partyToken;
        private long joinedAt;
        private String userAgent;
        private String deviceModel;
        private String osVersion;
        private String displayName;

        public String getUserId() {
            return userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPartyToken() {
            return partyToken;
        }

        public void setPartyToken(String partyToken) {
            this.partyToken = partyToken;
        }

        public long getJoinedAt() {
            return joinedAt;
        }

        public void setJoinedAt(long joinedAt) {
            this.joinedAt = joinedAt;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getDeviceModel() {
            return deviceModel;
        }

        public void setDeviceModel(String deviceModel) {
            this.deviceModel = deviceModel;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public void setOsVersion(String osVersion) {
            this.osVersion = osVersion;
        }
    }
}