package com.giga.tech1000.heartbeatz.architecture.repositories;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.giga.tech1000.heartbeatz.utils.PartyIdUtil;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.giga.tech1000.party_mode.model.PartyHost;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Firebase-based implementation of PartyHostRepository
 * Replaces the UDP-based discovery with Firebase Realtime Database
 */
public class FirebasePartyHostRepository extends FirebaseRepository implements PartyHostRepository {

    private static final String TAG = "FirebasePartyHostRepository";
    private static final String PARTIES_NODE = "parties";

    // LiveData for UI observation
    private final MutableLiveData<List<PartyHost>> discoveredHostsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<PartyHost> hostedPartyLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<List<String>> connectedGuestsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> guestCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<PartyHost> connectedHostLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> guestAuthenticatedLiveData = new MutableLiveData<>(false);
    /** Error / auth prompts for UI (e.g. "Please sign in to create a party"). */
    private final MutableLiveData<String> partyErrorLiveData = new MutableLiveData<>(null);

    // Event listeners
    private ChildEventListener partiesEventListener;
    private ValueEventListener hostedPartyEventListener; // Kept for backward compatibility, but we'll use partyEventListener for specific party
    private ChildEventListener connectedGuestsEventListener;
    private ValueEventListener guestAuthenticatedEventListener;
    private ValueEventListener partyEventListener; // Listens to the specific party we are in (for ownerId changes, etc.)

    // Current state tracking
    private String currentPartyId;
    private boolean isHosting = false;
    private boolean isDiscovering = false;

    public FirebasePartyHostRepository() {
        super(PARTIES_NODE);
        Log.d(TAG, "FirebasePartyHostRepository initialized");
    }

    // ============ PARTY DISCOVERY (replaces UDP Scanner/Broadcaster) ============

    @Override
    public void startDiscovery() {
        Log.d(TAG, "Starting party discovery via Firebase");
        isDiscovering = true;

        // Listen for parties in the database
        Query partiesQuery = databaseReference
                .orderByChild("timestamp")
                .limitToLast(50); // Get recent parties

        if (partiesEventListener != null) {
            databaseReference.removeEventListener(partiesEventListener);
        }

        partiesEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                PartyHost party = snapshot.getValue(PartyHost.class);
                if (party != null) {
                    // Ensure partyId is set
                    if (party.getPartyId() == null || party.getPartyId().isEmpty()) {
                        party.setPartyId(snapshot.getKey());
                    }
                    updateDiscoveredHostsList(party, true);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                PartyHost party = snapshot.getValue(PartyHost.class);
                if (party != null) {
                    // Ensure partyId is set
                    if (party.getPartyId() == null || party.getPartyId().isEmpty()) {
                        party.setPartyId(snapshot.getKey());
                    }
                    updateDiscoveredHostsList(party, true);
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                PartyHost party = snapshot.getValue(PartyHost.class);
                if (party != null) {
                    updateDiscoveredHostsList(party, false);
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Handle ordering changes if needed
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Discovery cancelled: " + error.getMessage());
            }
        };

        databaseReference.addChildEventListener(partiesEventListener);
    }

    @Override
    public void stopDiscovery() {
        Log.d(TAG, "Stopping party discovery");
        isDiscovering = false;

        if (databaseReference != null && partiesEventListener != null) {
            databaseReference.removeEventListener(partiesEventListener);
            partiesEventListener = null;
        }

        // Clear discovered hosts when stopping discovery
        discoveredHostsLiveData.postValue(new ArrayList<>());
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

        discoveredHostsLiveData.postValue(new ArrayList<>(currentList));
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

    // ============ HOST CREATION & MANAGEMENT ============

    @Override
    public void createParty(@NonNull String partyName, @NonNull String pin) {
        // Check if user is authenticated
        if (!isAuthenticated()) {
            Log.w(TAG, "Cannot create party: user not authenticated");
            partyErrorLiveData.postValue("Please sign in to create a party");
            return;
        }
        partyErrorLiveData.postValue(null);

        Log.d(TAG, "Creating party: " + partyName);

        // Generate a new party ID
        String partyId = databaseReference.push().getKey();

        if (partyId == null) {
            Log.e(TAG, "Failed to generate party ID");
            return;
        }

        PartyHost host = new PartyHost();
        host.setPartyId(partyId);
        host.setPartyName(partyName);
        host.setPin(pin);
        host.setOwnerId(getCurrentUserId()); // This will be non-null due to isAuthenticated check above
        host.setIpAddress(getLocalIpAddress());
        host.setPort(getSecretPort()); // Use secret port instead of hardcoded 8080
        host.setTimestamp(System.currentTimeMillis());
        host.setPasswordProtected(!pin.isEmpty());

        // Save to Firebase
        DatabaseReference partyRef = databaseReference.child(partyId);
        partyRef.setValue(host)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Party created successfully: " + partyId);
                    hostedPartyLiveData.postValue(host);
                    isHosting = true;
                    currentPartyId = partyId;

                    // Start listening to members for this party
                    startListeningToMembers(partyId);
                    // Set up listener for the specific party to monitor changes (e.g., ownerId)
                    setupPartyListener(partyId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create party: " + e.getMessage());
                });
    }

    @Override
    public void stopHosting() {
        if (!isHosting || currentPartyId == null) {
            Log.w(TAG, "Not currently hosting a party");
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
                    connectedGuestsLiveData.postValue(new ArrayList<>());
                    guestCountLiveData.postValue(0);
                    isHosting = false;
                    currentPartyId = null;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to stop party: " + e.getMessage());
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

    // ============ GUEST CONNECTION ============

    @Override
    public void joinParty(@NonNull PartyHost host, @NonNull String pin) {
        // Check if user is authenticated
        if (!isAuthenticated()) {
            Log.w(TAG, "Cannot join party: user not authenticated");
            partyErrorLiveData.postValue("Please sign in to join a party");
            guestAuthenticatedLiveData.postValue(false);
            return;
        }
        partyErrorLiveData.postValue(null);

        Log.d(TAG, "Joining party: " + host.getPartyName() + " (ID: " + host.getPartyId() + ")");

        // Validate PIN first
        if (!host.validatePin(pin)) {
            Log.w(TAG, "Invalid PIN for party: " + host.getPartyName());
            guestAuthenticatedLiveData.postValue(false);
            return;
        }

        currentPartyId = host.getPartyId();
        isHosting = false;

        // Update hosted party info (what we're connecting to)
        hostedPartyLiveData.postValue(host);

        // Mark as attempting to connect
        guestAuthenticatedLiveData.postValue(false);

        // Listen for authentication success from the host
        DatabaseReference partyRef = databaseReference.child(currentPartyId);
        String currentUserId = getCurrentUserId();
        DatabaseReference authRef = partyRef.child("authenticatedUsers").child(currentUserId);

        if (guestAuthenticatedEventListener != null) {
            authRef.removeEventListener(guestAuthenticatedEventListener);
        }

        guestAuthenticatedEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean authenticated = snapshot.getValue(Boolean.class);
                if (Boolean.TRUE.equals(authenticated)) {
                    Log.d(TAG, "Successfully authenticated to party: " + host.getPartyName());
                    guestAuthenticatedLiveData.postValue(true);
                    connectedHostLiveData.postValue(host);

                    // Start listening to members for this party
                    startListeningToMembers(currentPartyId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Auth listener cancelled: " + error.getMessage());
                guestAuthenticatedLiveData.postValue(false);
            }
        };

        authRef.addValueEventListener(guestAuthenticatedEventListener);

        // Add user to members list
        // Store the encoded token in the value for client-side obfuscation
        // Use the user ID as the key for simple security rules
        DatabaseReference membersRef = partyRef.child("members");
        String userId = currentUserId;
        String partyToken = PartyIdUtil.encodePartyId(currentPartyId, userId);

        Map<String, Object> memberData = new HashMap<>();
        memberData.put("userId", userId); // Store actual user ID for verification
        memberData.put("partyToken", partyToken); // Store encoded token for client use
        memberData.put("joinedAt", System.currentTimeMillis());
        membersRef.child(userId).setValue(memberData);

        // Set up listener for the specific party to monitor changes (e.g., ownerId)
        setupPartyListener(currentPartyId);
    }

    @Override
    public void leaveParty() {
        if (currentPartyId == null) {
            Log.w(TAG, "Not currently connected to a party");
            return;
        }

        // Prevent host from leaving without transferring host first
        if (isHosting) {
            Log.w(TAG, "Host must transfer host to a guest before leaving the party");
            return;
        }

        Log.d(TAG, "Leaving party: " + currentPartyId);
        String userId = getCurrentUserId();

        if (userId == null) {
            Log.w(TAG, "Cannot leave party: user not authenticated");
            return;
        }

        // Stop listening to members
        stopListeningToMembers();
        // Clean up party listener
        cleanupPartyListener();

        DatabaseReference partyRef = databaseReference.child(currentPartyId);

        // Remove user from members list using their user ID as key
        // String userId = getCurrentUserId();
        if (userId != null) {
            DatabaseReference memberRef = partyRef.child("members").child(userId);
            memberRef.removeValue();
        }

        // Remove authentication listener
        if (guestAuthenticatedEventListener != null) {
            DatabaseReference authRef = partyRef.child("authenticatedUsers").child(getCurrentUserId());
            authRef.removeEventListener(guestAuthenticatedEventListener);
            guestAuthenticatedEventListener = null;
        }

        // Reset state
        guestAuthenticatedLiveData.postValue(false);
        connectedHostLiveData.postValue(null);
        hostedPartyLiveData.postValue(null);
        currentPartyId = null;
        isHosting = false;
    }

    // ============ HOST CONTROLS ============

    /**
     * Host-only method to kick a guest from the party
     * You must be the host of the party to call this method.
     *
     * @param userIdToKick The user ID of the guest to kick
     */
    public void kickGuest(String userIdToKick) {
        if (!isHosting || currentPartyId == null) {
            Log.w(TAG, "Cannot kick guest: not hosting or no active party");
            return;
        }

        // Verify we are the owner
        String ownerId = getOwnerId();
        if (ownerId == null || !ownerId.equals(getCurrentUserId())) {
            Log.w(TAG, "Cannot kick guest: not the party owner");
            return;
        }

        Log.d(TAG, "Host kicking user: " + userIdToKick);

        // Remove the user from members
        DatabaseReference memberRef = databaseReference.child(PARTIES_NODE)
                .child(currentPartyId)
                .child("members")
                .child(userIdToKick);
        memberRef.removeValue();
    }

    /**
     * Host-only method to transfer host role to a guest
     * You must be the current host to call this method.
     * After transfer, the original host becomes a guest in the party (if they don't leave).
     *
     * @param newHostUserId The user ID of the guest to transfer host role to
     */
    public void transferHost(String newHostUserId) {
        if (!isHosting || currentPartyId == null) {
            Log.w(TAG, "Cannot transfer host: not hosting or no active party");
            return;
        }

        // Verify we are the owner
        String ownerId = getOwnerId();
        if (ownerId == null || !ownerId.equals(getCurrentUserId())) {
            Log.w(TAG, "Cannot transfer host: not the party owner");
            return;
        }

        // Verify the newHostUserId is a member of the party
        DatabaseReference membersRef = databaseReference.child(PARTIES_NODE).child(currentPartyId).child("members");
        membersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean isMember = snapshot.hasChild(newHostUserId);
                if (!isMember) {
                    Log.w(TAG, "Cannot transfer host: user " + newHostUserId + " is not a member of the party");
                    return;
                }

                // Update the ownerId in Firebase
                DatabaseReference partyRef = databaseReference.child(PARTIES_NODE).child(currentPartyId);
                Map<String, Object> updates = new HashMap<>();
                updates.put("ownerId", newHostUserId);
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
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check membership for host transfer: " + error.getMessage());
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

    /**
     * Observes party operation errors (including auth-required prompts for the UI).
     */
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
        return connectedHostLiveData.getValue() != null &&
                guestAuthenticatedLiveData.getValue() != null &&
                guestAuthenticatedLiveData.getValue();
    }

    @Override
    public boolean isInPartyMode() {
        return isHosting() || isGuest();
    }

    // ============ HELPER METHODS ============

    /**
     * Get a secret port to avoid conflicts with other apps
     * In a production app, this could be configured remotely or derived from a seed
     * For now, we'll use a port in the dynamic/private range (49152-65535)
     */
    private int getSecretPort() {
        // Using a fixed port in the private range for consistency
        // In production, consider making this configurable or deriving from user/device ID
        return 50000 + Math.abs(("HeartBeatzParty" + getCurrentUserId()).hashCode() % 15000);
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

                Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();

                    // Skip loopback addresses
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }

                    // Prefer IPv4 addresses
                    if (addr instanceof java.net.Inet4Address) {
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
                    else if (addr instanceof java.net.Inet6Address) {
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

                Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();

                    // Skip loopback addresses
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }

                    // Return IPv4 addresses
                    if (addr instanceof java.net.Inet4Address) {
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
     * Set up a listener for the specific party we are in to monitor for changes (e.g., ownerId)
     * @param partyId The ID of the party to listen to
     */
    private void setupPartyListener(String partyId) {
        cleanupPartyListener(); // Clean up any existing listener first

        if (partyId == null) {
            return;
        }

        DatabaseReference partyRef = databaseReference.child(PARTIES_NODE).child(partyId);

        partyEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Update the hostedPartyLiveData with the latest party data
                PartyHost party = snapshot.getValue(PartyHost.class);
                if (party != null) {
                    // Ensure partyId is set
                    if (party.getPartyId() == null || party.getPartyId().isEmpty()) {
                        party.setPartyId(snapshot.getKey());
                    }
                    hostedPartyLiveData.postValue(party);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Party listener cancelled: " + error.getMessage());
            }
        };

        partyRef.addValueEventListener(partyEventListener);
    }

    /**
     * Clean up the party listener
     */
    private void cleanupPartyListener() {
        if (partyEventListener != null && currentPartyId != null) {
            DatabaseReference partyRef = databaseReference.child(PARTIES_NODE).child(currentPartyId);
            partyRef.removeEventListener(partyEventListener);
            partyEventListener = null;
        }
    }

    /**
     * Start listening to members changes for the current party
     */
    private void startListeningToMembers(String partyId) {
        stopListeningToMembers(); // Stop any existing listener first

        if (partyId == null) {
            return;
        }

        DatabaseReference membersRef = databaseReference.child(PARTIES_NODE).child(partyId).child("members");

        if (connectedGuestsEventListener != null) {
            membersRef.removeEventListener(connectedGuestsEventListener);
        }

        connectedGuestsEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // The key is the user ID
                String userId = snapshot.getKey();
                if (userId != null) {
                    List<String> currentList = connectedGuestsLiveData.getValue();
                    if (currentList == null) {
                        currentList = new ArrayList<>();
                    }
                    if (!currentList.contains(userId)) {
                        currentList.add(userId);
                        connectedGuestsLiveData.postValue(new ArrayList<>(currentList));
                        guestCountLiveData.postValue(currentList.size());
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Handle member data changes if needed
                // Could update last seen time, etc.
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                // Remove the member from our list
                String userId = snapshot.getKey();
                if (userId != null) {
                    List<String> currentList = connectedGuestsLiveData.getValue();
                    if (currentList != null) {
                        currentList.remove(userId);
                        if (currentList.isEmpty()) {
                            connectedGuestsLiveData.postValue(new ArrayList<>());
                        } else {
                            connectedGuestsLiveData.postValue(new ArrayList<>(currentList));
                        }
                        guestCountLiveData.postValue(currentList.size());
                    }
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Not used
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Members listener cancelled: " + error.getMessage());
            }
        };

        membersRef.addChildEventListener(connectedGuestsEventListener);
    }

    /**
     * Stop listening to members changes
     */
    private void stopListeningToMembers() {
        if (connectedGuestsEventListener != null && currentPartyId != null) {
            DatabaseReference membersRef = databaseReference.child(PARTIES_NODE).child(currentPartyId).child("members");
            membersRef.removeEventListener(connectedGuestsEventListener);
            connectedGuestsEventListener = null;
        }
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

    /**
     * Clean up resources
     */
    public void release() {
        Log.d(TAG, "Releasing FirebasePartyHostRepository resources");
        stopDiscovery();
        stopListeningToMembers();
        cleanupPartyListener(); // Clean up the party listener

        // Remove all listeners
        if (databaseReference != null) {
            if (partiesEventListener != null) {
                databaseReference.removeEventListener(partiesEventListener);
                partiesEventListener = null;
            }
            if (hostedPartyEventListener != null) {
                databaseReference.removeEventListener(hostedPartyEventListener);
                hostedPartyEventListener = null;
            }
            if (connectedGuestsEventListener != null) {
                databaseReference.removeEventListener(connectedGuestsEventListener);
                connectedGuestsEventListener = null;
            }
            if (guestAuthenticatedEventListener != null) {
                databaseReference.removeEventListener(guestAuthenticatedEventListener);
                guestAuthenticatedEventListener = null;
            }
        }
    }
}