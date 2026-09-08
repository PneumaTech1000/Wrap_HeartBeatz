package com.giga.tech1000.media_player.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaLibraryService;

import com.giga.tech1000.media_player.R;


/**
 * Manages foreground service notifications for party mode.
 * 
 * Ensures the service stays alive in the background by:
 * - Creating persistent notifications during party hosting/joining
 * - Handling notification channel setup (Android 8+)
 * - Updating notification state in real-time
 * - Providing quick actions for party control
 */
@OptIn(markerClass = UnstableApi.class)
public class ForegroundServiceManager {
    
    private static final String TAG = "ForegroundServiceManager";
    private static final int PARTY_NOTIFICATION_ID = 42001;
    private static final int PLAYBACK_NOTIFICATION_ID = 42002;
    
    private static final String CHANNEL_PARTY_MODE = "party_mode_foreground";
    private static final String CHANNEL_PLAYBACK = "playback_foreground";
    
    private final Context context;
    private final MediaLibraryService mediaService;
    private final NotificationManager notificationManager;
    
    private boolean isPartyActive = false;
    private String currentPartyName = "";
    private int guestCount = 0;
    private boolean isHosting = false;
    
    public ForegroundServiceManager(@NonNull Context context, @NonNull MediaLibraryService service) {
        this.context = context;
        this.mediaService = service;
        this.notificationManager = context.getSystemService(NotificationManager.class);
        createNotificationChannels();
    }
    
    /**
     * Creates notification channels for Android 8+
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Party Mode Channel
            NotificationChannel partyChannel = new NotificationChannel(
                    CHANNEL_PARTY_MODE,
                    "Party Mode",
                    NotificationManager.IMPORTANCE_LOW
            );
            partyChannel.setDescription("Keeps HeartBeatz running while hosting or joining parties");
            partyChannel.enableVibration(false);
            partyChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(partyChannel);
            
            // Playback Channel (for normal playback)
            NotificationChannel playbackChannel = new NotificationChannel(
                    CHANNEL_PLAYBACK,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            playbackChannel.setDescription("Shows current playback status");
            playbackChannel.enableVibration(false);
            playbackChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(playbackChannel);
        }
    }
    
    /**
     * Start foreground notification for party hosting
     */
    public void startPartyHosting(@NonNull String partyName, int guestCount) {
        this.isPartyActive = true;
        this.currentPartyName = partyName;
        this.guestCount = guestCount;
        this.isHosting = true;
        
        Notification notification = buildPartyNotification(partyName, guestCount, true);
        startForegroundSafe(PARTY_NOTIFICATION_ID, notification);
    }
    
    /**
     * Start foreground notification for party guest joining
     */
    public void startPartyGuest(@NonNull String hostPartyName) {
        this.isPartyActive = true;
        this.currentPartyName = hostPartyName;
        this.guestCount = 0;
        this.isHosting = false;
        
        Notification notification = buildPartyNotification(hostPartyName, 0, false);
        startForegroundSafe(PARTY_NOTIFICATION_ID, notification);
    }
    
    /**
     * Update guest count on running notification
     */
    public void updateGuestCount(int newCount) {
        this.guestCount = newCount;
        if (isPartyActive && notificationManager != null) {
            Notification notification = buildPartyNotification(currentPartyName, newCount, isHosting);
            notificationManager.notify(PARTY_NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * Safely start foreground service, handling background restrictions on Android 12+
     * and specifying types for Android 14+.
     */
    private void startForegroundSafe(int id, Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaService.startForeground(id, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE |
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                mediaService.startForeground(id, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            isPartyActive = false;
        }
    }

    /**
     * Stop party foreground notification
     */
    public void stopPartyForeground() {
        isPartyActive = false;
        currentPartyName = "";
        guestCount = 0;
        mediaService.stopForeground(false);
    }
    
    /**
     * Build the party mode notification
     */
    private Notification buildPartyNotification(
            @NonNull String partyName,
            int guestCount,
            boolean isHost) {
        
        // Create intent to return to app
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.setAction("com.heartbeatz.party.SHOW_PARTY");
        } else {
            // Fallback if launch intent is not found
            intent = new Intent("com.heartbeatz.party.SHOW_PARTY");
            intent.setPackage(context.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create leave party action
        Intent leaveIntent = new Intent(context, MediaPlayerService.class);
        leaveIntent.setAction("com.heartbeatz.action.LEAVE_PARTY");
        
        PendingIntent leavePendingIntent = PendingIntent.getService(
                context,
                1,
                leaveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_PARTY_MODE)
                .setSmallIcon(com.giga.tech1000.icons_pack.R.drawable.album_24px)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        
        if (isHost) {
            builder.setContentTitle("Hosting: " + partyName)
                    .setContentText(guestCount + " guest" + (guestCount == 1 ? "" : "s"))
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Your party is active and streaming to " +
                                    guestCount + " guest" + (guestCount == 1 ? "" : "s")));
        } else {
            builder.setContentTitle("Party Guest: " + partyName)
                    .setContentText("Connected and receiving stream")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Receiving audio stream from party host"));
        }
        
        // Add leave action
        builder.addAction(
                0,
                "Leave Party",
                leavePendingIntent
        );
        
        return builder.build();
    }
    
    /**
     * Check if party mode is currently active
     */
    public boolean isPartyModeActive() {
        return isPartyActive;
    }
    
    /**
     * Get current party name
     */
    public String getCurrentPartyName() {
        return currentPartyName;
    }
    
    /**
     * Get current guest count
     */
    public int getGuestCount() {
        return guestCount;
    }
    
    /**
     * Check if currently hosting
     */
    public boolean isHosting() {
        return isHosting;
    }
}
