package com.giga.tech1000.heartbeatz.architecture.audio;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks the preferred audio output sink and notifies listeners on change.
 * Debounced to avoid thrashing when BT connects (multiple device events).
 */
public final class EqOutputRouteMonitor {

    public interface Listener {
        void onOutputRouteChanged(@NonNull EqOutputRoute route, @Nullable String deviceSuffix, @Nullable String displayName);
    }

    private final AudioManager audioManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Runnable debounce = this::emitCurrent;

    @Nullable private AudioDeviceCallback callback;
    @NonNull private EqOutputRoute currentRoute = EqOutputRoute.SPEAKER;
    @Nullable private String currentSuffix;
    @Nullable private String currentDisplay;

    public EqOutputRouteMonitor(@NonNull Context context) {
        this.audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    @NonNull
    public EqOutputRoute getCurrentRoute() {
        return currentRoute;
    }

    @Nullable
    public String getCurrentDeviceSuffix() {
        return currentSuffix;
    }

    @Nullable
    public String getCurrentDisplayName() {
        return currentDisplay;
    }

    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        listener.onOutputRouteChanged(currentRoute, currentSuffix, currentDisplay);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    public void start() {
        if (audioManager == null) return;
        emitCurrent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (callback != null) return;
            callback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    scheduleEmit();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    scheduleEmit();
                }
            };
            audioManager.registerAudioDeviceCallback(callback, main);
        }
    }

    public void stop() {
        if (audioManager != null && callback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager.unregisterAudioDeviceCallback(callback);
            } catch (Exception ignored) { }
        }
        callback = null;
        main.removeCallbacks(debounce);
    }

    private void scheduleEmit() {
        main.removeCallbacks(debounce);
        main.postDelayed(debounce, 350L);
    }

    private void emitCurrent() {
        AudioDeviceInfo sink = pickPreferredSink();
        EqOutputRoute route = EqOutputRoute.fromAudioDevice(sink);
        String suffix = null;
        String display = route.label;
        if (sink != null) {
            CharSequence pn = sink.getProductName();
            if (pn != null && pn.length() > 0) {
                display = pn.toString();
            }
            if (route == EqOutputRoute.BLUETOOTH || route == EqOutputRoute.USB) {
                // Prefer stable address when available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        String addr = sink.getAddress();
                        if (addr != null && !addr.isEmpty() && !"00:00:00:00:00:00".equals(addr)) {
                            suffix = addr;
                        }
                    } catch (Exception ignored) { }
                }
                if (suffix == null && pn != null) {
                    suffix = Integer.toHexString(pn.toString().hashCode());
                }
            }
        }

        boolean changed = route != currentRoute
                || (suffix == null ? currentSuffix != null : !suffix.equals(currentSuffix));
        currentRoute = route;
        currentSuffix = suffix;
        currentDisplay = display;
        if (changed) {
            for (Listener l : listeners) {
                try {
                    l.onOutputRouteChanged(route, suffix, display);
                } catch (Exception ignored) { }
            }
        }
    }

    @Nullable
    private AudioDeviceInfo pickPreferredSink() {
        if (audioManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null || devices.length == 0) return null;

        // Priority: BT A2DP → wired → USB → car-like → builtin speaker
        AudioDeviceInfo best = null;
        int bestScore = -1;
        for (AudioDeviceInfo d : devices) {
            if (d == null || !d.isSink()) continue;
            int score = score(d);
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    private static int score(@NonNull AudioDeviceInfo d) {
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return 100;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return 80;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return 70;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return 60;
            case AudioDeviceInfo.TYPE_BUS:
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return 50;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return 10;
            default:
                return 5;
        }
    }
}
