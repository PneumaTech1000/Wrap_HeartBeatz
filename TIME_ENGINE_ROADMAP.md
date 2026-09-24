# TimeEngine Roadmap — HeartBeatz Party Sync

**Goal:** All party devices share one **session timeline** and release audio at the same scheduled track position, with **buffer → lock → release**.

**Reality check:** True sample-locked “100% forever” across arbitrary phones + internet is not guaranteed (hardware clocks, audio path, jitter). This roadmap targets **professional-grade lock**: stable alignment, scheduled starts, minimal drift, no seek thrash—as close to “one beat” as mobile allows.

---

## A. Principles

1. **Track position is the music clock** (ms or samples)—not wall GMT/UTC.
2. **Host is the time authority** for a party session.
3. **Firebase = delivery + coarse server clock**, not the audio clock.
4. **Monotonic clocks** (`elapsedRealtime`) measure intervals on each device.
5. **Schedule ahead (e.g. 5 s):** target time + target position; late readers still usable if still inside the window.
6. **Buffer before release:** prepare media early; **wait**; release sound at the scheduled instant.
7. **Correct softly** (rate) for small drift; **seek rarely** for large errors only.
8. **One expand / one audible start** per track after metadata + buffer ready—not on every packet.

---

## B. TimeEngine core (session time)

9. **`TimeEngine` module** — single API used by party (and later lyrics/UI).
10. **Session epoch** — when party goes live, define `t = 0` on the shared timeline (not device midnight).
11. **Tick API** — `nowSessionMs()`, `idealTrackPositionMs()`, `driftMs()`, `phase` (`IDLE / ARMED / LOCKED`).
12. **Host monotonic anchors** — each beat: `{ positionMs, hostMonoMs, isPlaying, mediaId, targetPositionMs, targetHostMonoMs }` (5 s lookahead).
13. **Guest mapping** — map host mono timeline → local mono → track position (EMA smoothing).
14. **Firebase coarse assist** — `ServerValue.TIMESTAMP` + `.info/serverTimeOffset` to bound error; never sole music clock.
15. **Optional RTT probe** — lightweight ping (Firebase or later WebSocket) → one-way lag estimate for compensation.
16. **Heartbeat** — host publishes anchors every ~1.5–2 s; denser only if needed.
17. **Play/pause as timeline events** — pause freezes ideal position; resume issues a **new scheduled release**.

---

## C. Schedule + buffer-then-release (5 s plan, tightened)

18. **Anchor contract**
    - At host time \(T_0\): position \(P_0\)
    - Target: at \(T_0 + 5s\) → position \(P_0 + 5s\) (if playing; clamp to duration).
19. **Guest on packet**
    - If `now < targetTime` → **BUFFERING / ARMED** (do not expand as “playing” yet).
    - If `now ≥ targetTime` → compute ideal = `targetPosition + (now − targetTime)`.
20. **Pre-roll buffer**
    - Load URL, `prepare()`, decode enough; hold **paused** at target position (or 0 for new track).
21. **Armed wait**
    - Wait on monotonic clock until release instant (ms-level check loop or handler).
22. **Release**
    - Single `play()` at scheduled time (avoid seek-at-release if already parked on position).
23. **Track change protocol**
    - New mediaId → new schedule after buffer ready; cancel old ARM.
24. **UI gate**
    - Progress dialog while buffering/arming; full player only when **LOCKED** (metadata applied + first release done or safely armed).

---

## D. Drift control (anti-distortion)

25. **Dead zone** — |drift| < ~20–40 ms → do nothing.
26. **Rate nudge** — small drift → playback speed ~0.97–1.03 briefly, then 1.0.
27. **Soft seek** — medium drift, throttled (seconds apart).
28. **Hard seek** — only if |drift| > ~1–1.5 s or after long pause/background.
29. **No seek storms** — max N seeks/minute; prefer rate.
30. **After seek** — re-ARM short schedule if needed so devices re-lock together.

---

## E. Media / player integration

31. **Party playback path** — remote URL via Media3; metadata (title/artist/album/duration) from anchor + Media3 duration poll.
32. **Output latency hook (later)** — optional per-device offset so “speaker time” matches better.
33. **Keep screen on / audio focus** while party LOCKED or local playing.
34. **Background** — on resume, one hard resync from latest anchor + short schedule.

---

## F. Firebase payload (delivery only)

35. **`parties/{id}/sync`** fields aligned to TimeEngine anchors (position, targetPosition, lookahead, hostMono, serverTime, isPlaying, mediaUrl, metadata, duration, scheduleId).
36. **`scheduleId`** — monotonic id so guests ignore stale packets.
37. **Rules** — host write sync; guests read.

---

## G. Host pipeline

38. On song change → upload → publish mediaUrl + **first schedule**.
39. Heartbeat while playing → refresh 5 s-ahead targets.
40. Pause/seek/skip → new scheduleId + anchors.

---

## H. Guest pipeline

41. Join → observe sync → TimeEngine.feed(anchor).
42. State machine: `IDLE → LOADING → BUFFERING → ARMED → LOCKED → (DRIFT_CORRECT) → …`
43. Loading UI until BUFFERING complete; expand player at ARMED/LOCKED with metadata.
44. Apply ideal position via TimeEngine only (remove ad-hoc seek logic).

---

## I. Validation / quality

45. **Debug overlay / Logcat** — hostPos, idealPos, drift, phase, scheduleId (dev builds).
46. **Metrics** — p50/p95 drift over session.
47. **Acceptance** — same track start within tight window; steady drift without audible pumping; pause/resume relocks.

---

## J. Later upgrades (not required for v1)

48. WebSocket/LAN denser anchors.
49. PTP-like or NTP-style refinement beyond Firebase offset.
50. Per-device audio latency calibration wizard.
51. TimeEngine for lyrics/visuals on the same session clock.

---

## Build order (implementation sequence)

1. **TimeEngine API + session tick + phases**
2. **Anchor model + Firebase payload (`scheduleId`, hostMono, targets)**
3. **Host publisher (heartbeat + 5 s schedule)**
4. **Guest feed + BUFFER → ARM → RELEASE**
5. **Drift policy (rate first, seek last)**
6. **UI gate (loading → metadata → player)**
7. **Polish** (pause/resume, track change, keep-screen-on)
8. **Dev metrics / drift logs**

---

## One-line summary

**TimeEngine = shared track timeline + scheduled release + buffer-before-play; Firebase only carries anchors and a coarse clock; devices wait locked and release together—then hold lock with gentle correction, not constant seeking.**

---

## Related design notes

### Original 5 s schedule (kept)

Host records a future instant: e.g. at wall/server sense of `T0` position `P0`, target at `T0+5s` position `P0+5s`. Guest may read late (e.g. at `T0+3s`) and still arm to hit `P0+5s` at `T0+5s`.

### Failure modes to avoid

- Treating Firebase timestamp as the sole music clock
- Seeking on every heartbeat (causes distortion)
- Expanding full player before buffer + metadata ready
- Using device wall GMT as the cross-device contract

### Shared time base options

| Source | Role |
|--------|------|
| Host `elapsedRealtime` | Primary interval math in anchors |
| Firebase `ServerValue.TIMESTAMP` + `.info/serverTimeOffset` | Coarse assist / delivery |
| Optional RTT probe | Lag compensation |

---

*Recorded for HeartBeatz party sync. Implementation starts at build order step 1 when approved.*

---

## Implementation status (v1)

| Step | Status |
|------|--------|
| 1. TimeEngine API + phases | Done — `architecture/timeengine/TimeEngine.java` |
| 2. Anchor model + Firebase payload | Done — `TimeAnchor`, `scheduleId`, `hostMonoMs`, repo fields |
| 3. Host publisher | Done — `PartyLiveBridge` heartbeat 2s + force schedule on transport |
| 4. Guest BUFFER → ARM → RELEASE | Done — `TimeEnginePlayerBridge` |
| 5. Drift policy (rate first) | Done — `TimeEngine.decideCorrection` |
| 6. UI gate on ready | Done — `getGuestReadyForUi` + FragmentParty |
| 7–8. Polish / metrics | Partial — Logcat tags `TimeEngine`, `TimeEnginePlayer`, `PartyLiveBridge` |

