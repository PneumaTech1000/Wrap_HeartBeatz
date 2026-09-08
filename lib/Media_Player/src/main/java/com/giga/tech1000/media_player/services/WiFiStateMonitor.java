package com.giga.tech1000.media_player.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

/**
 * Monitors WiFi state changes and notifies listeners when WiFi is lost/restored.
 * 
 * This is critical for party mode because:
 * - Host needs to detect WiFi loss and gracefully stop hosting
 * - Guest needs to detect WiFi loss and attempt reconnection
 * - Service needs to handle WiFi changes while backgrounded
 */
@OptIn(markerClass = UnstableApi.class)
public class WiFiStateMonitor {
    
    private static final String TAG = "WiFiStateMonitor";
    
    /**
     * Callback interface for WiFi state changes
     */
    public interface WiFiStateListener {
        /**
         * Called when WiFi becomes available and connected
         */
        void onWiFiConnected();
        
        /**
         * Called when WiFi is lost or disconnected
         */
        void onWiFiDisconnected();
    }
    
    private final Context context;
    private final WiFiStateListener listener;
    private boolean isRegistered = false;
    private boolean lastWiFiState = false;
    
    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                handleWiFiStateChange(intent);
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                handleWiFiRadioStateChange(intent);
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                handleConnectivityChange(intent);
            }
        }
    };
    
    public WiFiStateMonitor(@NonNull Context context, @NonNull WiFiStateListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.lastWiFiState = isWiFiConnected();
    }
    
    /**
     * Start monitoring WiFi state changes
     */
    public void startMonitoring() {
        if (isRegistered) {
            Log.w(TAG, "Monitor already started");
            return;
        }
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(wifiReceiver, filter);
            }
            isRegistered = true;
            Log.d(TAG, "WiFi monitoring started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register WiFi receiver", e);
        }
    }
    
    /**
     * Stop monitoring WiFi state changes
     */
    public void stopMonitoring() {
        if (!isRegistered) {
            return;
        }
        
        try {
            context.unregisterReceiver(wifiReceiver);
            isRegistered = false;
            Log.d(TAG, "WiFi monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister WiFi receiver", e);
        }
    }
    
    /**
     * Handle WiFi network state changes
     */
    private void handleWiFiStateChange(Intent intent) {
        boolean isConnected = isWiFiConnected();
        
        if (isConnected != lastWiFiState) {
            lastWiFiState = isConnected;
            
            if (isConnected) {
                Log.d(TAG, "WiFi connected");
                listener.onWiFiConnected();
            } else {
                Log.d(TAG, "WiFi disconnected");
                listener.onWiFiDisconnected();
            }
        }
    }
    
    /**
     * Handle WiFi radio state changes (WiFi turned on/off)
     */
    private void handleWiFiRadioStateChange(Intent intent) {
        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
        
        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                Log.d(TAG, "WiFi radio enabled");
                // WiFi is on, but may not be connected yet
                if (!lastWiFiState && isWiFiConnected()) {
                    lastWiFiState = true;
                    listener.onWiFiConnected();
                }
                break;
                
            case WifiManager.WIFI_STATE_DISABLED:
                Log.d(TAG, "WiFi radio disabled");
                if (lastWiFiState) {
                    lastWiFiState = false;
                    listener.onWiFiDisconnected();
                }
                break;
        }
    }
    
    /**
     * Handle general connectivity changes
     */
    private void handleConnectivityChange(Intent intent) {
        boolean isConnected = isWiFiConnected();
        
        if (isConnected != lastWiFiState) {
            lastWiFiState = isConnected;
            
            if (isConnected) {
                Log.d(TAG, "Connected to WiFi (via ConnectivityManager)");
                listener.onWiFiConnected();
            } else {
                Log.d(TAG, "WiFi lost (via ConnectivityManager)");
                listener.onWiFiDisconnected();
            }
        }
    }
    
    /**
     * Check if device is connected to WiFi
     */
    private boolean isWiFiConnected() {
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            if (cm == null) return false;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                
                return cm.getNetworkCapabilities(network) != null &&
                       cm.getNetworkCapabilities(network).hasTransport(
                               android.net.NetworkCapabilities.TRANSPORT_WIFI
                       );
            } else {
                @SuppressWarnings("deprecation")
                android.net.NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                return info != null && info.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi state", e);
            return false;
        }
    }
    
    /**
     * Get current WiFi connection status
     */
    public boolean isWiFiCurrentlyConnected() {
        return isWiFiConnected();
    }
    
    /**
     * Check if monitor is currently active
     */
    public boolean isMonitoring() {
        return isRegistered;
    }
}
