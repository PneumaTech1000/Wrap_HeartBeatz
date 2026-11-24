package com.giga.tech1000.heartbeatz.architecture.freeshelf;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Allowed licenses for the legal free shelf. Commercial catalogs are rejected.
 */
public enum FreeShelfLicense {
    /** Public domain / CC0 — no attribution required (still polite to show source). */
    CC0("CC0", false),
    /** Creative Commons Attribution — attribution required. */
    CC_BY("CC-BY", true),
    /** CC-BY-SA — attribution + share-alike note. */
    CC_BY_SA("CC-BY-SA", true),
    /** Explicit artist free drop with their terms. */
    ARTIST_FREE("Artist free", true),
    /** Public-domain recording (not just composition). */
    PUBLIC_DOMAIN("Public domain", false),
    /** Unknown / disallowed for shelf. */
    UNKNOWN("Unknown", true);

    public final String label;
    public final boolean requiresAttribution;

    FreeShelfLicense(String label, boolean requiresAttribution) {
        this.label = label;
        this.requiresAttribution = requiresAttribution;
    }

    @NonNull
    public static FreeShelfLicense fromString(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return UNKNOWN;
        String s = raw.trim().toUpperCase()
                .replace(' ', '_')
                .replace("-", "_");
        switch (s) {
            case "CC0":
            case "CC_ZERO":
                return CC0;
            case "CC_BY":
            case "CCBY":
            case "CC_BY_3_0":
            case "CC_BY_4_0":
                return CC_BY;
            case "CC_BY_SA":
            case "CC_BY_SA_3_0":
            case "CC_BY_SA_4_0":
                return CC_BY_SA;
            case "ARTIST_FREE":
            case "FREE_DROP":
                return ARTIST_FREE;
            case "PUBLIC_DOMAIN":
            case "PD":
            case "PD_US":
                return PUBLIC_DOMAIN;
            default:
                return UNKNOWN;
        }
    }

    public boolean isAllowedOnShelf() {
        return this != UNKNOWN;
    }
}
