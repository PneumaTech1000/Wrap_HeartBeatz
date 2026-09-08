package com.giga.tech1000.heartbeatz.theme.interfaces;

public interface PaletteStateListener {
    void onUpdateVibrantColor(int vibrantColor);
    void onUpdateVibrantLightColor(int vibrantLightColor);
    void onUpdateVibrantDarkColor(int vibrantDarkColor);

    // NEW – Muted family
    void onUpdateMutedColor(int mutedColor);
    void onUpdateMutedLightColor(int mutedLightColor);
    void onUpdateMutedDarkColor(int mutedDarkColor);

    // DOMINANT COLOR
    void onUpdateDominantColor(int dominantColor);
}
