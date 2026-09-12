package com.giga.tech1000.media_player.utils.enums;

public enum EqPreset {

    // =====================
    // Neutral / Reference
    // =====================

    FLAT("Flat", new int[]{
            1, 0, 0, 0, -2, 3, 5, 3, 5, 3
    }),

    NORMAL("Normal", new int[]{
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    }),

    BASS_BOOST("Bass boost", new int[]{
            3, 3, 3, 3, 3, -9, -9, -9, -9, -9
    }),

    // =====================
    // Bass-forward profiles
    // =====================

    HIP_HOP("Hip hop", new int[]{
            2, 0, 0, 0, 0, 2, 3, 3, 5, 5
    }),

    STRAIGHTNESS("Straightness", new int[]{
            1, 1, 1, 1, 1, -7, -7, -7, -7, -7
    }),

    DEEP("Deep", new int[]{

    }),

    R_AND_B("R&B", new int[]{
            4, 3, 2, 1, 0, 1, 2, 3, 3, 4
    }),

    LOUD("Loud", new int[]{
            5, 4, 2, 1, 0, 0, 1, 2, 4, 5
    }),

    // =====================
    // Rock / Energy
    // =====================

    ROCK("Rock", new int[]{
            1, 1, 0, -1, 1, 1, -4, 2, 5, 5
    }),

    HEAVY_METAL("Heavy metal", new int[]{
            0, 0, 0, 3, 3, 3, 0, 2, 3, 5
    }),

    ELECTRONIC("Electronic", new int[]{
            4, 3, 2, 0, -1, 0, 2, 3, 4, 5
    }),

    DANCE("Dance", new int[]{
            4, 1, 1, 1, 1, 0, 0, 0, 1, 1
    }),

    // =====================
    // Vocal / Acoustic
    // =====================

    VOCAL_BOOST("Vocal boost", new int[]{
            -2, -1, 0, 2, 4, 5, 4, 2, 0, -1
    }),

    ACOUSTIC("Acoustic", new int[]{
            2, 1, 0, 2, 3, 3, 2, 1, 0, 0
    }),

    FOLK("Folk", new int[]{
            0, 0, 0, 0, 0, 0, -6, -6, -6, -6
    }),

    LOUNGE("Lounge", new int[]{
            -2, -1, 0, 1, 2, 2, 1, 0, -1, -2
    }),

    PIANO("Piano", new int[]{
            1, 0, 0, 2, 3, 3, 2, 1, 0, 0
    }),

    CLASSICAL("Classical", new int[]{
            5, 0, 0, 2, 0, 0, 2, 2, 3, 3
    }),

    JAZZ("Jazz", new int[]{
            2, 0, 0, -1, -1, 0, 3, 3, 3, 5, 5
    }),

    // =====================
    // Treble / Clarity
    // =====================

    TREBLE_BOOST("Treble boost", new int[]{
            -2, -1, 0, 1, 2, 3, 4, 5, 6, 6
    }),

    POP("Pop", new int[]{
            2, 0, -1, 2, -1, 0, 0, 2, 5, 5
    }),

    LATIN("Latin", new int[]{
            3, 2, 1, 0, 1, 2, 3, 2, 3, 4
    }),

    HEADPHONES("Headphones", new int[]{
            3, 2, 1, 0, 1, 2, 3, 4, 5, 5
    });

    // =====================
    // Internal
    // =====================

    private final String label;
    private final int[] dbLevels;

    EqPreset(String label, int[] dbLevels) {
        this.label = label;
        this.dbLevels = dbLevels;
    }

    public String getLabel() {
        return label;
    }

    public int[] getDbLevels() {
        return dbLevels;
    }
}
