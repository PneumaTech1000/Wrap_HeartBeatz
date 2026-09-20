# Firebase Realtime Database — full rules

**Source file to paste in Console:** `firebase.rules` (JSON body only)

## How to apply

1. Open [Firebase Console](https://console.firebase.google.com) → your project  
2. **Build** → **Realtime Database** → **Rules**  
3. Replace everything with the contents of `firebase.rules`  
4. **Publish**

Or CLI (if `firebase.json` points at this file):

```bash
firebase deploy --only database
```

## What these rules enforce

| Path | Read | Write |
|------|------|--------|
| Default (`/`) | Denied | Denied |
| `/parties` | Signed-in users | — |
| `/parties/{partyId}` | Signed-in | Create if new, or only **owner** updates root party doc |
| `/parties/{partyId}/members/{memberId}` | Signed-in | Owner **or** that member |
| `/parties/{partyId}/authenticatedUsers/{uid}` | Signed-in | Owner **or** that uid |
| `/parties/{partyId}/sync` | Signed-in | **Owner only** (playback URL + position for cloud sync) |
| `/parties/{partyId}/webrtc_signaling/{id}` | Signed-in | Signed-in; `senderId` must be `auth.uid` |
| `/presence/{userId}` | Signed-in | **Only that user** (`auth.uid == userId`) |
| `/users/{userId}` | Own profile only | Own profile only |

## Notes

- **Auth required** for all party and presence access (`auth != null`).
- **Party create:** first write allowed when node does not exist; `ownerId` must equal `auth.uid`.
- **`sync`:** reserved for host-authoritative media URL + position (cloud URL migration). Guests read only.
- **PIN** is still stored in the clear if the app writes it — hashing is an app-level follow-up, not expressed fully in rules alone.
- After Publish, create a party again; presence errors should stop if the user is signed in.

## Optional hardening later

- Rate limits (App Check)
- Hash PIN; never store plain PIN in RTDB
- Restrict `parties` list reads to non-sensitive fields via a public index node
