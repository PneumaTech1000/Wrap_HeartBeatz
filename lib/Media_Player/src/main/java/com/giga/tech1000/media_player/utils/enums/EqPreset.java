package com.giga.tech1000.media_player.utils.enums;

/**
 * Built-in EQ profiles. Each array is <b>exactly 10 band gains in dB</b>
 * for ISO centers: 32, 64, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz.
 * Range expected by UI/DSP: roughly −12 … +12 dB.
 */
public enum EqPreset {

    FLAT("Flat", new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}),

    NORMAL("Normal", new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}),

    BASS_BOOST("Bass boost", new int[]{7, 6, 4, 2, 0, -1, 0, 0, 0, 0}),

    HIP_HOP("Hip hop", new int[]{6, 5, 2, 0, -1, 1, 2, 3, 4, 4}),

    STRAIGHTNESS("Straightness", new int[]{2, 2, 1, 1, 0, -2, -3, -3, -3, -3}),

    DEEP("Deep", new int[]{7, 6, 4, 2, 1, 0, -1, -1, 0, 0}),

    R_AND_B("R&B", new int[]{5, 4, 2, 1, 0, 1, 2, 3, 3, 4}),

    LOUD("Loud", new int[]{5, 4, 2, 1, 0, 0, 1, 2, 4, 5}),

    ROCK("Rock", new int[]{4, 3, 1, -1, -1, 1, 2, 3, 4, 5}),

    HEAVY_METAL("Heavy metal", new int[]{4, 3, 0, 2, 3, 3, 1, 2, 4, 5}),

    ELECTRONIC("Electronic", new int[]{5, 4, 2, 0, -1, 0, 2, 3, 4, 5}),

    DANCE("Dance", new int[]{6, 4, 2, 0, 0, 1, 2, 3, 4, 3}),

    VOCAL_BOOST("Vocal boost", new int[]{-2, -1, 0, 2, 4, 5, 4, 2, 0, -1}),

    ACOUSTIC("Acoustic", new int[]{3, 2, 1, 1, 2, 2, 2, 2, 1, 1}),

    FOLK("Folk", new int[]{2, 1, 0, 0, 1, 1, -1, -2, -2, -2}),

    LOUNGE("Lounge", new int[]{-1, 0, 1, 2, 2, 2, 1, 0, -1, -1}),

    PIANO("Piano", new int[]{1, 0, 0, 2, 3, 3, 2, 1, 0, 0}),

    CLASSICAL("Classical", new int[]{4, 1, 0, 2, 0, 0, 2, 2, 3, 3}),

    JAZZ("Jazz", new int[]{3, 2, 1, 0, -1, 0, 2, 3, 3, 4}),

    TREBLE_BOOST("Treble boost", new int[]{0, 0, 0, 0, 1, 2, 4, 5, 6, 7}),

    POP("Pop", new int[]{2, 3, 3, 1, 0, 0, 1, 2, 3, 3}),

    LATIN("Latin", new int[]{4, 3, 1, 0, 1, 2, 3, 2, 3, 4}),

    HEADPHONES("Headphones", new int[]{4, 3, 1, 0, 1, 2, 3, 4, 5, 5}),

    CUSTOM("Custom", new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

    private final String label;
    private final int[] dbLevels;

    EqPreset(String label, int[] dbLevels) {
        this.label = label;
        if (dbLevels == null || dbLevels.length != 10) {
            this.dbLevels = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        } else {
            this.dbLevels = dbLevels;
        }
    }

    public String getLabel() {
        return label;
    }

    /** Exactly 10 band gains in dB. */
    public int[] getDbLevels() {
        return dbLevels.clone();
    }

    /** Gain for band index 0..9 in dB. */
    public float getBandDb(int index) {
        if (index < 0 || index >= dbLevels.length) return 0f;
        return dbLevels[index];
    }
}
