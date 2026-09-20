# Frozen party transport (do not extend)

**Status:** FROZEN until §7 (cloud URL + DB sync) ships, then delete.

| Path | Role historically | Action |
|------|-------------------|--------|
| `webrtc/` | WebRTC audio + signaling for party | **Do not add features.** No TURN/SFU work. |
| `streaming/StreamServer` | Local HTTP audio server | **Do not add features.** |
| `PartyManager` | Facade over WebRTC | **Deprecated.** App uses Firebase `PartySession` instead. |

**Current product path:** Firebase discovery/membership + Media3 local/party sync fields.  
**Next product path (§7):** Upload to Supabase/R2 → shared media URL → DB position sync.

UDP LAN discovery is already removed from the app layer (Firebase replaced it).
