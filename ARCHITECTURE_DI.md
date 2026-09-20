# Dependency composition (AppContainer)

## Rule

**Do not** call `UIThread.getInstance()` or invent new static service holders.

Obtain dependencies from the application composition root:

```java
AppContainer c = HeartBeatzApp.container(context);
c.partySession();
c.uiThreadOrNull();          // null until MainActivity attaches
c.requireUiThread();         // after MainActivity init
c.playbackRepositoryOrNull();
```

## Lifecycle

1. `HeartBeatzApp.onCreate` → creates `AppContainer` + `FirebasePartySession`
2. `MainActivity` → `new UIThread` → `uiThread.init()` → `container.attachUiThread(uiThread)`
3. `MainActivity.onDestroy` → `container.detachUiThread()`

## Party

- `PartySession` — UI/ViewModel API
- `FirebasePartySession` — default implementation
- `PartyPresenceStore` — `/presence` only
- `EnhancedFirebasePartyHostRepository` — membership/discovery (to be split further later)

## Remaining debt

- `SongRepository.getInstance()` in media_player module (phase 2)
- ViewHolders still reach UIThread via container (prefer PlaybackStateRepository LiveData later)
