# Party media — cloud URL + DB sync (§7)

## Goal

Host uploads the current track to **object storage**. All devices play the **same URL** with Media3.  
**Firebase** `parties/{partyId}/sync` carries position / play-pause / track metadata so guests stay aligned.  
No WebRTC audio, TURN, or SFU for party listening.

## Backends

| Env | Class | Config |
|-----|--------|--------|
| Testing | `SupabasePartyMediaStore` | `PartyMediaConfig` Supabase fields |
| Production | `R2PartyMediaStore` | `PartyMediaConfig` R2 fields |
| Offline UI | `NoOpPartyMediaStore` | `backend = NOOP` |

Switch with:

```java
PartyMediaConfig cfg = PartyMediaConfig.debugDefaults();
cfg.backend = PartyMediaConfig.Backend.SUPABASE; // or CLOUDFLARE_R2 / NOOP
```

`AppContainer` builds the store via `PartyMediaStoreProvider.create(cfg)`.

---

## Where to put your secrets (edit these)

**File:** `app/src/main/java/.../architecture/media/PartyMediaConfig.java`

### Supabase (testing)

1. Open [Supabase Dashboard](https://supabase.com/dashboard) → your project.
2. **Settings → API**
   - `supabaseUrl` ← **Project URL**
   - `supabaseApiKey` ← **anon** key for client tests (or a user JWT after Auth).  
     Avoid shipping **service_role** in a public app binary.
3. **Storage** → create bucket `party-tracks` (name must match `supabaseBucket`).
   - For early testing you can enable **public** read on the bucket, or use signed URLs later.
4. Storage policies: allow authenticated (or anon) **insert/update** on `party-tracks` for host upload paths.

```text
supabaseUrl     = "https://xxxxxxxx.supabase.co"
supabaseBucket  = "party-tracks"
supabaseApiKey  = "eyJhbGciOi..."   // anon or user JWT
```

### Cloudflare R2 (production)

1. R2 → create bucket `heartbeatz-party-tracks` (or your name → `r2Bucket`).
2. Manage R2 API Tokens → Access Key ID + Secret → `r2AccessKeyId` / `r2SecretAccessKey`.
3. Endpoint: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` → `r2Endpoint`.
4. Optional CDN/custom domain → `r2PublicBaseUrl` for playback.
5. Set `backend = CLOUDFLARE_R2`.
6. **Before real traffic:** implement AWS **SigV4** or **presigned PUT/GET** from a small backend  
   (`R2PartyMediaStore.authorizedPut` is intentionally incomplete without signing).

Object keys are shared (`PartyMediaKeys`):

```text
parties/{partyId}/tracks/{hashOrTrackId}.mp3
```

So migration does not require rewriting path logic.

---

## Firebase sync shape

Path: `parties/{partyId}/sync`

| Field | Meaning |
|-------|---------|
| `mediaUrl` | Playable URL |
| `objectKey` | Storage key |
| `trackId` | App track id |
| `title` / `artist` | Display |
| `positionMs` | Host position |
| `isPlaying` | Host transport |
| `updatedAt` | Server timestamp |
| `updatedAtClientMs` | Host clock helper |

**Host:** after successful upload → `PartyPlaybackSyncRepository.publishHostSync(...)`.  
While playing, throttle `publishPosition` (e.g. every 2–5s).  
**Guest:** `observeParty(partyId)` → Media3 `setMediaItem(mediaUrl)` + seek/play when drift &gt; threshold (e.g. 1.5s).

Rules: only party **owner** writes `sync`; members can read (see `firebase.rules`).

---

## Host UX (wire in Party UI)

1. User picks a local song while hosting.
2. Observe `partyTrackUploader().getStatus()` → show “Preparing track…” + progress.
3. On `SUCCESS`, publish sync + start local Media3 from same URL (or local file + publish URL for guests).
4. On party end: `delete` known keys / `clearSync` / optional lifecycle rules on the bucket.

---

## Checklist

- [ ] Fill Supabase URL + key + bucket in `PartyMediaConfig`
- [ ] Create Storage bucket + policies
- [ ] Host upload path wired from party “play for party”
- [ ] Guest Media3 observes `sync`
- [ ] Drift correction threshold tuned
- [ ] Switch `backend` to R2 + SigV4/presign for production
- [ ] Remove frozen WebRTC audio path when stable
