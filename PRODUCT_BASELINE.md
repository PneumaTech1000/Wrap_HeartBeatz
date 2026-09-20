# Product baseline (§5)

## Invites

- **Primary:** `heartbeatz://party/{partyId}?name=&pin=`
- **Share text:** `QrCodeUtil.formatPartyShareText(...)`
- **QR:** same deep-link string (not LAN IP:port)
- **Legacy:** `HB_PARTY:ip:port:name:pin` still parsed
- **Manifest:** `VIEW` filters for `heartbeatz://party` and `https://heartbeatz.app/party`

## Auth

- Library playback: **no** account required
- Party create / join / discover: **Firebase Auth** required

## Connection UI

- Labels via `PartyConnectionStatus` (Hosting — ready, Connecting…, etc.)
- Party errors: Toast + inline banner
- Cloud upload states (Preparing track…) come with §7

## Host tools

- Guest list + kick / handover (existing host dialogs)
- Lock party: use non-empty PIN at create (`passwordProtected`)

## Analytics

- `PartyAnalytics` — event names only, no PII / no PIN logging

## Deferred to §7

- Remote audio via Supabase/R2 URL + DB sync
- Upload progress chip

## Release hygiene

- Architecture: `ARCHITECTURE_DI.md`, `PROJECT_ISSUES_AND_CORRECTIONS.md`
- R8/signing: configure in release build when shipping store builds
