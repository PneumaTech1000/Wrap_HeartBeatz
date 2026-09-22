package com.giga.tech1000.heartbeatz.architecture.media;

import androidx.annotation.NonNull;

/**
 * Object-storage settings for party tracks.
 * <p>
 * <b>Where to edit credentials (do not commit secrets):</b>
 * <ul>
 *   <li>Preferred: {@code local.properties} → read via BuildConfig fields (see CLOUD_MEDIA.md)</li>
 *   <li>Or temporarily hardcode placeholders below for local debug only</li>
 * </ul>
 * Switching Supabase → Cloudflare R2: change {@link #backend} and fill R2 fields;
 * app code uses {@link PartyMediaStore} only.
 */
public final class PartyMediaConfig {

    public enum Backend {
        /** Testing */
        SUPABASE,
        /** Production (S3-compatible) */
        CLOUDFLARE_R2,
        /** No network upload — returns fake URLs for UI plumbing tests */
        NOOP
    }

    /** Active backend — flip to {@link Backend#CLOUDFLARE_R2} for production builds. */
    @NonNull
    public Backend backend = Backend.SUPABASE;

    // ─── Supabase (testing) — EDIT THESE ───────────────────────────────────
    /** Project URL, e.g. https://xxxxxxxx.supabase.co */
    @NonNull
    public String supabaseUrl = "https://ruvstbznpsrylfxqhbfe.supabase.co";

    /** Storage bucket name, e.g. party-tracks */
    @NonNull
    public String supabaseBucket = "HeartBeatz_Party_tracks";

    /**
     * Service role or upload-capable key.
     * Prefer a short-lived user JWT from Auth; service role only on trusted host path.
     * REPLACE: paste anon or service key for testing (never ship service role in release).
     */
    @NonNull
    public String supabaseApiKey = "sb_publishable_9TO559VPA8U4OlH3MyD39g_YIf8mRgE";

    // ─── Cloudflare R2 (production) — EDIT WHEN MIGRATING ──────────────────
    /** Account endpoint, e.g. https://&lt;ACCOUNT_ID&gt;.r2.cloudflarestorage.com */
    @NonNull
    public String r2Endpoint = "https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com";

    @NonNull
    public String r2Bucket = "heartbeatz-party-tracks";

    @NonNull
    public String r2AccessKeyId = "YOUR_R2_ACCESS_KEY_ID";

    @NonNull
    public String r2SecretAccessKey = "YOUR_R2_SECRET_ACCESS_KEY";

    /**
     * Public or custom domain for playback URLs if objects are public via CDN.
     * If empty, store may return presigned URLs instead.
     */
    @NonNull
    public String r2PublicBaseUrl = "";

    /** Signed URL lifetime for guests (seconds). */
    public long signedUrlTtlSeconds = 3600;

    public static PartyMediaConfig debugDefaults() {
        return new PartyMediaConfig();
    }

    public boolean isConfigured() {
        switch (backend) {
            case SUPABASE:
                return !supabaseUrl.contains("YOUR_")
                        && !supabaseApiKey.contains("YOUR_")
                        && !supabaseBucket.isEmpty();
            case CLOUDFLARE_R2:
                return !r2Endpoint.contains("YOUR_")
                        && !r2AccessKeyId.contains("YOUR_")
                        && !r2SecretAccessKey.contains("YOUR_");
            case NOOP:
            default:
                return true;
        }
    }
}
