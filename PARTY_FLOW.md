# Party mode — solid process

## 1. Auth
Both host and guest must be signed in (Firebase Auth). Display name comes from Auth profile (signup full name).

## 2. Host creates party
1. `createParty(name, pin)` → writes `/parties/{id}` root  
2. Writes host into `/parties/{id}/members/{uid}` with **displayName**  
3. State **HOSTING** → `PartyLiveBridge.startHost`  
4. Guest list shows **display names** (not raw UIDs)

## 3. Host plays a song (automatic party track)
1. `PlaybackStateRepository` current song changes  
2. Bridge uploads file in background (`PartyTrackUploader` → Supabase/R2/NoOp)  
3. On success writes `/parties/{id}/sync` with mediaUrl, title, artist, position, isPlaying  
4. Heartbeat every 2s updates position while playing  

## 4. Guest joins
1. Scan QR / discovery + PIN  
2. Writes `authenticatedUsers/{uid}=true` and `members/{uid}` (+ displayName)  
3. State **JOINED** → `PartyLiveBridge.startGuest`  
4. Observes `/parties/{id}/sync`  
5. Chat banner shows host track metadata  
6. Full player **dimmed/locked** (guest chrome)

## 5. Leave
- Host: `stopHosting` deletes party node, stops bridge  
- Guest: removes members + auth entry, stops bridge  

## 6. Chat
Unlocks when HOSTING or JOINED; track line follows `PartyPlaybackSync`.

## Still later
- Guest Media3 play from remote URL (needs PlaybackStateRepository remote play API)
- Chat message Firebase stream
- Approve guest track requests
