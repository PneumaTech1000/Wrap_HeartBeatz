# HeartBeatz — Issues & Intended Corrections

Living document. Tackle items one by one; mark status as work proceeds.

**Legend:** `OPEN` · `IN PROGRESS` · `DONE` · `WONTFIX`

---

## 0. UI question (resolved in design terms)

### Requirement

- **Full media player expanded** → bottom navigation **hidden**
- **Mini player collapsed** → bottom navigation **visible** (and stacked correctly with the mini bar)

### Does Material 3 bottom nav + standard fragments achieve the same result?

**Yes — for the product behavior (show/hide nav with player state).**  
**No — not as a drop-in replacement for the entire MultiSlidingUpPanel stack.**

| Concern | MultiSlidingUpPanel (current) | Material 3 bottom nav + standard fragments |
|--------|-------------------------------|--------------------------------------------|
| Hide nav when player is full-screen | Yes (panel floors / `isHidden`) | Yes: `BottomNavigationView.setVisibility(GONE)` or slide-off when player is expanded |
| Show nav when mini player visible | Yes | Yes: nav visible; mini player sits **above** nav (`padding` / `WindowInsets` / a bottom `CoordinatorLayout` barrier) |
| Coordinated slide gesture (player + nav as stacked sliding panels) | Built into the library | **Not free** — use a bottom sheet / custom motion / `MotionLayout` for the **player only** |
| Primary tabs (Home / Party) | Was coupled to panel host; now show/hide fragments | **Natural fit** for Material 3 bottom nav |
| Risk | High (layout math, elevation, destroy/recreate) | Lower if player is one sheet and tabs are normal fragments |

**Intended direction**

1. Keep **one** custom surface for the **player** (bottom sheet or sliding panel): expanded = full UI, collapsed = mini bar.
2. Drive **bottom nav visibility** from player state (`EXPANDED` → hide nav; `COLLAPSED` / hidden → show nav).
3. Use **standard fragments + Material 3 bottom nav** for Home / Party (and future tabs).
4. Do **not** require MultiSlidingUpPanel to own both **nav chrome** and **tab content** — that coupling caused most of the lag and z-order bugs.

So: the **result you want is achievable without MultiSlidingUpPanel owning navigation**. MultiSlidingUpPanel (or a Material bottom sheet) can remain **only for the media player**.

---

## 1. Architecture / coupling

**Status:** `DONE` — AppContainer, PartySession, PartyPresenceStore; UIThread.getInstance throws; holders use container. Residual: SongRepository.getInstance still used in a few places.

### Problems

- Multiple access paths to the same services (`UIThread.getInstance()`, repository singletons, ViewModels, panels).
- `PartyViewModel` bridges Firebase, playback, and UI lifecycle in one place.
- `EnhancedFirebasePartyHostRepository` is a large god-object (~1.4k lines): discovery, presence, members, host lifecycle, network.

### Intended corrections

- Introduce a small **session / use-case layer**:
  - `PlaybackSession` (or keep a single `PlaybackStateRepository` as the only API).
  - `PartySession`: `create`, `join`, `leave`, `observeParties`, `observeConnection`.
- UI and ViewModels depend on those APIs only — **no** `UIThread.getInstance()` from adapters/holders long-term.
- Split Firebase party code: `PartyDiscovery`, `PartyMembership`, `PartyPresence`, signaling/sync as needed.
- Prefer constructor injection (Hilt later optional); phase out ad-hoc singletons.
- **Progress:** `AppContainer`, `PartySession` / `FirebasePartySession`, `PartyPresenceStore`, static `UIThread.getInstance()` removed.

---

## 2. Firebase design & security

**Status:** `DONE` (rules in `firebase.rules` including presence + sync; **publish in Console** if not already)

### Problems

- `/presence` was missing from rules → permission denied after create party.
- Plain PIN and open write patterns need ongoing hardening.
- Abandoned parties / listeners can linger without strict TTL and cleanup.

### Intended corrections

- Deploy `firebase.rules` (parties + presence + users) via Console or `firebase deploy --only database`.
- Validate rules with Firebase emulator.
- Prefer PIN hashing or short-lived join tokens; enforce `ownerId` / member writes tightly.
- `onDisconnect` for presence and party membership cleanup.
- Party TTL or host heartbeat so dead parties disappear from discovery.

---

## 3. UI / navigation complexity

**Status:** `DONE` — Material BottomSheet player + fixed bottom nav; PlayerChromeController; show/hide tabs; drawer in Activity; UIInfoLog DEBUG-only

### Problems

- MultiSlidingUpPanel used for **player + navigation chrome**, which forced fragile height/`isHidden` coordination.
- NavController saveState did not keep Home alive → lag on tab switch (mitigated with show/hide).
- FragmentHome does heavy work on attach (ViewPager, library pages, overlays).
- Drawer originally under panel stack → covered by mini/nav (mitigated by Activity-level drawer).

### Intended corrections

- **Tabs:** Material 3 `BottomNavigationView` + standard fragments (or keep current show/hide; same idea).
- **Player only:** one sliding surface (MultiSlidingUpPanel *or* Material bottom sheet).
  - Expanded → hide bottom nav (and optionally system bars).
  - Collapsed mini → show bottom nav; content padding = mini height + nav height.
- **Drawer:** stay at Activity level (already done); fragments use `DrawerController`.
- Library: Activity-scoped ViewModels; avoid full rebuild of all tabs when possible; Paging 3 later for large libraries.
- Debug `UIInfo` logs behind a build flag for release.

---

## 4. Quality, lifecycle, and polish

**Status:** `DONE` — party auth gate only for party; library free; party errors Toast/inline; ViewModel attach after UIThread.init

### Problems

- Lifecycle races (`PartyViewModel` before `UIThread.init`, padding before `MediaNavigationManager`).
- Sparse automated tests around party join/leave and playback.
- Auth required for party but UX may not always force/explain sign-in.
- Weak offline / connection-failure messaging.

### Intended corrections

- Initialize dependent ViewModels **after** core services are ready (pattern started for Party + playback attach).
- Null-safe UI callbacks when views not ready; re-apply state when ready (pattern started for bar padding).
- Instrumentation or unit tests for: create party, join with PIN, leave, rules denial.
- Party tab: explicit sign-in gate (**done**: create/join/discover require Firebase Auth; local library does not).
- User-visible errors: network, permission denied, party errors via Toast/inline (**party error observe done**).
- MultiSliding ANCHORED: not required for Material player sheet (slideOffset via onSlide).

---

## 5. Product features to add (professional baseline)

**Status:** `DONE` (except remote media → §7) — partyId invites, deep links, share/QR, connection labels, PartyAnalytics

| Item | Intended correction |
|------|---------------------|
| Invites | **Done:** deep link + share + QR (`partyId` + PIN); legacy HB_PARTY still parsed |
| Remote listening | Deferred to **§7** (cloud URL + DB sync) |
| Connection UI | **Done:** `PartyConnectionStatus` labels + party error banner; upload chip with §7 |
| Host tools | Guest list + kick/handover existing; lock via PIN at create |
| Analytics | **Done:** `PartyAnalytics` (no PII) |
| Release hygiene | Docs: `PRODUCT_BASELINE.md`, `ARCHITECTURE_DI.md`; R8/signing at store release |

---

## 6. Candidates to remove or freeze

**Status:** `DONE` — WebRTC/StreamServer/PartyManager frozen+@Deprecated; TURN WONTFIX; multiSliding unused for chrome (module remains until zero refs). See `DEPRECATIONS_AND_FREEZES.md`

| Candidate | Action |
|-----------|--------|
| UDP-era discovery | **Done:** app uses Firebase only; comments updated |
| IP:port primary join UX | **Done (§5):** partyId invites; legacy QR parse only |
| WebRTC audio / StreamServer | **Frozen + @Deprecated**; delete after §7 |
| TURN / SFU | **WONTFIX** — §7 cloud URL + DB sync |
| Further growth of single party repository class | Split instead of extend |
| Production spam logging | **Done:** UIInfoLog DEBUG-only |
| Global singletons from holders | **Partial:** via AppContainer; finish remaining SongRepository later |
| Unused modules (e.g. multiSliding when fully unused) | Remove from Gradle when zero references |

---

## 7. Party media migration — cloud URL + DB sync (last)

**Status:** `OPEN`  
**Priority:** **Do last** — after §1–6 foundations are stable.

### Decision (replaces old “TURN/SFU” and “dual transport” items)

**Do not** stream host audio over WebRTC with TURN/SFU.

**Do** this instead:

1. Host creates party (Firebase membership/presence as today).
2. Host uploads the intended track to **object storage**.
3. All devices play the **same media URL** via Media3.
4. **Database sync** (position, play/pause, track id, timestamps) keeps devices aligned over time.

| Environment | Object storage |
|-------------|----------------|
| Testing | **Supabase Storage** |
| Production | **Cloudflare R2** (S3-compatible) |

Design storage behind a single abstraction so swapping Supabase → R2 is config/implementation, not an app rewrite.

### Problems this solves

- Cross-location parties without NAT traversal or TURN ops.
- Host phone is not a live media server for every guest.
- Scales beyond 2–3 listeners more naturally (CDN/storage vs mesh).

### Problems this introduces (must design for)

- Upload latency before guests can play (progress UX on host).
- Storage cost, TTL, delete-on-party-end, optional content-hash dedupe.
- Sync quality (drift correction), not “URL alone.”
- Rights/ToS for redistributing local files via your bucket.
- Prefer **signed, time-limited URLs** over a fully public bucket.

### Intended corrections

1. **`PartyMediaStore` interface** (put/delete/signed URL) — Supabase impl for test, R2 impl for prod.
2. **Object keys** e.g. `parties/{partyId}/tracks/{contentHash}.{ext}`.
3. **Sync document** under party (Firebase or later Supabase Realtime), host authoritative:
   - `mediaUrl` / storage key, `trackId`, `positionMs`, `isPlaying`, `updatedAt` (server time).
4. **Guests:** Media3 plays URL; apply play/pause/seek; correct drift when threshold exceeded.
5. **Host UX:** “Preparing track…” upload state; optional prefetch next track.
6. **Lifecycle:** delete party prefix when party ends; storage lifecycle rules in prod.
7. **Remove** party WebRTC audio path / unused stream server once this path is stable.
8. **Invites** stay `partyId` + PIN (deep link); LAN IP/port not required for remote join.

### Explicitly out of scope for this item

- TURN servers  
- SFU (LiveKit/Agora) for party audio  
- Continuing dual WebRTC-audio + URL pipelines long term  

---

## Suggested order of work

1. **Finish architecture coupling** (§1) — AppContainer, no static sprawl, repo splits  
2. **Firebase rules deployed** + party auth gate (§2, §4) — unblocks real devices  
3. **UI split: player sheet vs Material tabs** (§3, §0) — stabilizes chrome  
4. **Quality / lifecycle / tests / sign-in gate** (§4)  
5. **Product baseline** invites, host tools, analytics (§5) — invites as partyId+PIN  
6. **Remove dead transport + logging hygiene** (§6) — as paths become unused  
7. **Last: Party media migration — Supabase → R2, URL + DB sync** (§7)  

---

## Status log

| Date | Note |
|------|------|
| 2026-09-20 | Document created from architecture review; UI show/hide nav clarified vs Material 3 |
| 2026-09-20 | §1: AppContainer, PartySession, PartyPresenceStore; UIThread.getInstance removed |
| 2026-09-20 | Removed old §2 dual-transport and §3 TURN/SFU; replaced with **§7 cloud URL + DB sync** (last). Renumbered §2–6. |
| 2026-09-20 | §3 UI: PlayerChromeController, UIInfoLog DEBUG-only, Material bottom nav polish |
| | Prior fixes already in tree: shared playback path, drawer in Activity, tab show/hide, presence rules in `firebase.rules`, various NPEs |
