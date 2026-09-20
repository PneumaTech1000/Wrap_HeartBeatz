# Deprecations & freezes (§6)

## Active party path (keep)

- `PartySession` / `FirebasePartySession`
- `EnhancedFirebasePartyHostRepository`
- Firebase RTDB: parties, members, presence, sync
- Media3 local playback
- Invites: `partyId` + PIN deep links

## Frozen (do not extend; delete after §7)

| Component | Reason |
|-----------|--------|
| `party_mode.webrtc.*` | WebRTC audio; no TURN/SFU work |
| `party_mode.streaming.StreamServer` | Local HTTP audio server |
| `PartyManager` | WebRTC facade |
| UDP discovery | Already replaced by Firebase |

## Deprecated app classes

| Class | Prefer |
|-------|--------|
| `PartyHostManager` | `PartySession` |
| `FirebasePartyHostRepository` | `EnhancedFirebasePartyHostRepository` |
| `QrCodeUtil.formatPartyQr(ip,port,…)` | `formatPartyInvite(partyId,…)` |
| `UIThread.getInstance()` | `HeartBeatzApp.container(context)` |

## Explicitly cancelled

- **TURN / SFU** for party audio → **WONTFIX** (replaced by §7 cloud URL + DB sync)

## Logging

- `UIInfoLog` is **debug-only** (`BuildConfig.DEBUG`)

## Modules

- `multiSlidingUpPanel`: unused by main activity chrome after Material bottom sheet; keep dependency until fully unused in library code, then remove from Gradle.
- Do not add new features on frozen transports.
