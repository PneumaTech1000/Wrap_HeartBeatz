# Firebase Realtime Database rules

Source of truth: `firebase.rules`

## Deploy (required after rule changes)

Firebase Console → Realtime Database → Rules → paste contents of `firebase.rules` → Publish

Or CLI:

```bash
firebase deploy --only database
```

(ensure `firebase.json` points `database.rules` at `firebase.rules`)

## Presence

App writes to `/presence/{uid}` when hosting/joining a party. Rules allow only `auth.uid == $userId`.
