package com.giga.tech1000.heartbeatz.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class QrCodeUtil {

    public static Bitmap generateQrCode(String content, int size) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            return barcodeEncoder.encodeBitmap(content, BarcodeFormat.QR_CODE, size, size);
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates a Wi-Fi connection QR code.
     * Format: WI-FI:S:SSID;T:WPA;P:PASSWORD;;
     */
    public static String formatWifiQr(String ssid, String password, String type) {
        return "WIFI:S:" + ssid + ";T:" + type + ";P:" + password + ";;";
    }
    
    public static String formatPartyQr(String ip, int port, String name, String pin) {
        return "HB_PARTY:" + ip + ":" + port + ":" + name + ":" + (pin != null ? pin : "");
    }
}
