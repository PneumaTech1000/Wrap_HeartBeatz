package com.giga.tech1000.heartbeatz.architecture.media;

import androidx.annotation.NonNull;

/**
 * Factory: one place to switch Supabase ↔ R2 ↔ NoOp.
 */
public final class PartyMediaStoreProvider {

    private PartyMediaStoreProvider() {}

    @NonNull
    public static PartyMediaStore create(@NonNull PartyMediaConfig config) {
        switch (config.backend) {
            case SUPABASE:
                return new SupabasePartyMediaStore(config);
            case CLOUDFLARE_R2:
                return new R2PartyMediaStore(config);
            case NOOP:
            default:
                return new NoOpPartyMediaStore();
        }
    }
}
