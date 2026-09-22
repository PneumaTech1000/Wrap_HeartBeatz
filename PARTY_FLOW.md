# Party mode flow (corrected)

## Roles
| Role | Firebase | UI state |
|------|----------|----------|
| **Host** | Creates `/parties/{id}`, `isHosting=true` | `HOSTING` |
| **Guest** | Writes `members/{uid}` + `authenticatedUsers/{uid}` | `CONNECTING` → `JOINED` |

## Host path
1. User signs in → createParty(name, pin)
2. State `CREATING` → Firebase `setValue` party root
3. Success → `hostedParty` LiveData → state `HOSTING` → connected UI + QR
4. Leave → `stopHosting()` deletes party node → `IDLE`

## Guest path
1. Discovery or QR → PartyHost (id + pin from payload)
2. `joinParty(host, pin)` → state `CONNECTING`
3. PIN validated **locally** against host.pin
4. Guest writes `authenticatedUsers/{uid}=true` and `members/{uid}`
5. Success → `isGuestAuthenticated` → state `JOINED`
6. Leave → remove members + authenticatedUsers → `IDLE`

## Misconceptions fixed
- Guest must **not** post to `hostedParty` (that forced HOSTING UI)
- `connectedHost == null` must **not** force IDLE (raced over HOSTING)
- Host leave uses `stopHosting`, not `leaveParty` (leaveParty blocked hosts)
- `isGuest` = in a party we do not host (includes connecting)
- Auth uid from FirebaseAuth, not placeholder `"current_user"`

## Rules
Publish `firebase.rules`: parent write is create/delete only so guests can write members.
