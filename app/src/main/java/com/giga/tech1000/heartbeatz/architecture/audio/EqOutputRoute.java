package com.giga.tech1000.heartbeatz.architecture.audio;

import android.media.AudioDeviceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Coarse output class for per-device EQ memory.
 * Finer keys (e.g. BT address) are optional suffixes in {@link EqOutputProfileStore}.
 */
public enum EqOutputRoute {
    SPEAKER("speaker", "Phone speaker"),
    WIRED_HEADSET("wired", "Wired headphones"),
    BLUETOOTH("bluetooth", "Bluetooth"),
    USB("usb", "USB audio"),
    CAR("car", "Car audio"),
    OTHER("other", "Other output");

    public final String key;
    public final String label;

    EqOutputRoute(String key, String label) {
        this.key = key;
        this.label = label;
    }

    @NonNull
    public static EqOutputRoute fromAudioDevice(@Nullable AudioDeviceInfo info) {
        if (info == null) return SPEAKER;
        int type = info.getType();
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return SPEAKER;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return WIRED_HEADSET;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return BLUETOOTH;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return USB;
            case AudioDeviceInfo.TYPE_BUS:
            case AudioDeviceInfo.TYPE_AUX_LINE:
                // Often used by car / docking — treat as car-like
                return CAR;
            default:
                if (info.isSink()) return OTHER;
                return SPEAKER;
        }
    }
}
