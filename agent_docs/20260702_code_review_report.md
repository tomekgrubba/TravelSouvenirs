# App Review — Travel Souvenirs

**Date:** 2026-07-02
**Scope:** Full-app architectural review — pitfalls, potential bugs, and issues across sync, data, image, navigation, DI, ViewModels, and manifest.
**Deliverable:** Findings and prioritized implementation plan. No code changes made.

Severity legend: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low

---

## 1. Sync / Firebase

### 🔴 Critical
- **Orphaned Storage blobs when sign-out cancels mid-upload.** `SyncCoordinator.cancelAllSyncs()` (`SyncCoordinator.kt:70`) cancels `syncJob` on any `user==null` emission. If `MetadataSyncService.uploadPending` has finished `cloudImageStorage.upload(...)` but not the paired `set(...) + updateSyncMeta(...)`, the blob is stranded and the item gets a fresh `fbId` on next sign-in — permanent leak. No orphan-GC path.
- **`hardDeleteByFirebaseId("")` can nuke every unsynced row.** `MetadataSyncService.kt:59` runs unconditionally in the `PENDING_DELETE` branch. For a locally-created-then-deleted item, `firebaseId == ""`, and `ItemDao.kt:48-49` matches all rows with `firebaseId = ''` — mass data loss.
- **Delete flow leaks the Storage image forever.** `MetadataSyncService.kt:54-60` marks Firestore doc `deleted = true` but never calls `storage.reference(photoStoragePath).delete()`. Every deletion leaks the image; no TTL/GC on tombstones.
- **Remote-vs-local write race.** `MetadataSyncService.kt:37` compares `updatedAtMillis`, but between `getItemByFirebaseId` and Room's `REPLACE`-keyed `insertItem`, a user edit can slip in. The remote insert clobbers `syncStatus=PENDING_UPLOAD`, notes, category. No transaction/CAS.

### 🟠 High
- **Two-phase sync runs concurrently.** `SyncCoordinator.init { … syncData(); launch { syncImages() } }` (line 47–48) — `syncImages()` starts before `syncData()` finishes; both write to `items` in parallel.
- **`CategorySyncService.sync` overwrites remote on ANY exception.** `CategorySyncService.kt:40-50` swallows all errors and treats them as "doc missing", pushing local list. Transient auth/network wipes another device's categories.
- **Category cross-device conflict resolution is last-write-wins with no merge.** Same file, lines 34–49.
- **Auto-sync fires on every `isConnected==true` emission.** `MainActivity.kt:60-68` — no `distinctUntilChanged`, no false→true edge detection; sync re-fires on every capability flap.
- **Duplicate sync on resume.** `MainScreen.kt:122-127` also triggers `syncData()`/`syncImages()` on ON_RESUME. Combined with the MainActivity trigger and the auth listener in `SyncCoordinator.init`, one sign-in-then-resume can start 3 sync passes.
- **`AndroidNetworkMonitor.checkConnected()` runs on main during init** (`NetworkMonitor.android.kt:16`) and never unregisters its callback.
- **Clock-skew hole in incremental fetch.** `MetadataSyncService.kt:27` uses `lastSyncMillis - 5_000L` guard against device-local `now`. Phones with >5s clock drift silently miss remote edits.

### 🟡 Medium
- **`randomId()` via `kotlin.random.Random`** (`MetadataSyncService.kt:103-106`) is non-cryptographic and can collide; use `firestore.collection(...).document().id`.
- **`ImageSyncService.downloadMissingImages` swallows all failures** (`ImageSyncService.kt:19`); no retry/backoff; image stays "missing" forever silently.
- **No retry/backoff anywhere** — a single flap surfaces "Sync failed" and stops.
- **`downloadUrlToFile` writes directly to final path** (`StorageExt.android.kt:15-23`); crash mid-download leaves a partial JPEG at the final path.
- **`SyncCoordinator` uses `Dispatchers.Default`** (line 34); should be `Dispatchers.IO` for the disk/network work below.
- **`_errors` `MutableSharedFlow` buffer = 4 with `tryEmit`** — bursts silently drop.
- **`localFileExists` declared but never called** (`StorageExt.kt:6`) — likely a missing guard.

### 🟢 Low
- Dead `SyncRepository.kt` stub.
- `SyncRepositoryTest.FakeAuthRepository` mocks `FirebaseUser` without stubbing `uid` → the "signed-in" tests actually run the null-uid early-return branch and pass vacuously.
- `FirebaseItem` uses `0.0` sentinels for lat/lng → indistinguishable from Null Island.
- `insertCategory` per remote-item hot loop; batch it.
- Consider Firestore `serverTimestamp()` for `updatedAtMillis` instead of client clock.

---

## 2. Data / Room / Repositories

### 🔴 Critical
- **Cross-user data leak risk.** `ItemDatabaseBuilder.android.kt:8-11` opens a single global `items_db`. `SettingsViewModel.signOut` wipes it, but only on explicit sign-out — process death mid sign-in and account-switch (sign in as B without hitting sign-out) share the same file. No `userId` column either.
- **`Item.toEntity()` silently drops `firebaseId`, `syncStatus`, `photoStoragePath`, `photoStorageUrl`** (`ItemEntity.kt:55-65`). Currently unused in the write path (repo constructs entities manually), but any future refactor that calls it will duplicate items on next sync. Landmine — delete it or make it preserve.
- **`CategoryRepository.setAll` is non-transactional and doesn't flip child items to `PENDING_UPLOAD`.** `CategoryRepository.kt:20-24`. FK `SET DEFAULT` cascades reassignments in-DB, but the affected items never get their `syncStatus` bumped → cloud state diverges silently.

### 🟠 High
- **`CategoryRepository.delete`** — same divergence as above; deleted category cascades `SET DEFAULT` on items without marking them pending.
- **Deleting `DEFAULT_CATEGORY` isn't blocked** at DAO/repo layer.
- **`DeleteItemUseCase.kt:12-13` deletes local photo before DB update.** For a synced item, `deleteItem` marks it `PENDING_DELETE` — the photo is gone before sync can inspect it, and the UI list will render broken thumbnails for the interim.
- **`ItemDatabase @Database(version = 6)` conflicts with CLAUDE.md ("Room v4")** — docs stale, but confirm no v6→v7 migration is missing.

### 🟡 Medium
- No `MigrationTestHelper` coverage for `1→2 … 5→6`. `MIGRATION_5_6` uses `INSERT INTO items_new SELECT * FROM items` — column-order-dependent and fragile.
- `getAllActiveItems` has no index on `(syncStatus, dateAcquiredMillis)` — table scan per emission.
- Empty-string sentinels (`firebaseId=""`, `photoPath=""`) — prefer nullable columns.
- Timezone: `atStartOfDayIn(TimeZone.UTC)` both ways. Confirm EXIF date extraction is also UTC-consistent (otherwise off-by-one days at day boundaries).
- `FilterItemsUseCase` silently passes items whose category isn't in the available set (orphan safety net) — user-invisible.
- No `distinctUntilChanged` on `allItems`/`getItemByIdFlow` — probably fine but worth adding.

### 🟢 Low
- `SaveItemUseCase` fails silently on blank input.
- `MAX_CUSTOM_CATEGORIES = 5` enforced only in UI.
- Push `WHERE name != :default` to DAO to avoid in-memory filter.

---

## 3. Image / Photo Flow

### 🔴 Critical
- **`AddItemViewModel.saveItem` deletes the original photo BEFORE confirming save.** `AddItemViewModel.kt:306-326` calls `imagePicker.cleanupOnSave(path)` before `saveItem(...)`. If save returns `false` (validation, DB throws), original is gone with no rollback and no error surfaced (`isSaved` stays false, but original is deleted).
- **`saveItem` no-ops silently if `photoPath == null`** (`AddItemViewModel.kt:308`). Save tap with a still-in-flight copy shows no error.
- **Camera `cameraOutputUri` lost on process death** (`PhotoPicker.android.kt:128, 167`). `remember`-only. Fresh photo dropped if OS kills app in camera. Needs `SavedStateHandle`/`rememberSaveable`.

### 🟠 High
- **OOM on large photos.** `ImageStorageHelper.kt:31` uses `BitmapFactory.decodeStream` without `inSampleSize` — full-res decode of 48-108 MP photos will OOM on low-mid devices.
- **`AndroidImageStorage.copyToInternalStorage` is `suspend` but blocking**, no `withContext(Dispatchers.IO)`.
- **EXIF orientation misses FLIP/TRANSPOSE variants** (`ImageStorageHelper.kt:47-57`) — some OEMs produce them; images render mirrored.
- **UCrop temp files leak from every gallery pick** — `clearTempFiles` is only called at start of *camera* flow.
- **Double re-encode** — UCrop already outputs JPEG; then `copyToInternalStorage` decodes/rotates/resizes/re-encodes at 70% quality.
- **`ACCESS_MEDIA_LOCATION` runtime permission not verified in code.** `MediaStore.setRequireOriginal(uri)` silently drops GPS without it (`PhotoPicker.android.kt:43`). Manifest declares it, but no runtime request.
- **ML Kit analyzer races across photo selections.** `AddItemViewModel.kt:117-125` launches without cancelling the previous — the older Gemini call can set `aiSuggestedPlace` for the wrong image.

### 🟡 Medium
- **`onPhotoSelected` copy overrides a user-initiated Remove** (`ImagePickerHandler.kt:32-39` + `AddItemViewModel.kt:281-283`).
- **`deleteImage` safety only checks `filesDir` prefix**, not `item_photos` subdir.
- **Analyzer discards passed `lat/lng` params** (`AndroidImageLocationAnalyzer.kt:24, 34`) — would help place identification.
- **`(0.0, 0.0)` as "unset"** in `AddItemViewModel.kt:157, 200` — real (rare) coordinate becomes indistinguishable from empty.
- **`ImagePickerHandler` cleanup on `Dispatchers.Default`** — file IO; should be `Dispatchers.IO`.
- **`onCleared` fire-and-forget cleanup** can be killed mid-delete → orphans.

### 🟢 Low
- Analyzer branch on `javaClass.simpleName == "DownloadCompleted"` — fragile under R8/ProGuard.
- Analyzer prompt hard-coded English.
- `IMAGE_MAX_SIDE_PX = 1200` looks soft; consider 1600–2048.

---

## 4. Navigation, DI, Lifecycle

### 🟠 High
- **`FirebaseAuthRepository` creates a leaked `CoroutineScope`** (`FirebaseAuthRepository.kt:17`) that's never cancelled and holds an eager `stateIn`. Fine for a genuine singleton, but combined with `!!` on `.user` (lines 23, 26, 30) any race throws NPE that becomes a coroutine error.
- **`TravelSouvenirsApp.instance` static** used from `ItemDatabaseBuilder.android.kt:9` — anti-pattern. `androidContext()` from Koin is safer and testable.
- **Location permission requested with no rationale, no re-prompt, no "don't ask again" path** (`MainActivity.kt:47-53`).
- **`AppSettings.clear()` on sign-out wipes device-scoped prefs too** (view mode, WiFi-only, notes). Should be per-user scoped or preserved.
- **`AppSettings.lastSyncMillis` / `categoriesUpdatedAt` are not per-user** — cross-account contamination if sign-out ever fails partway.
- **`SettingsViewModel.signOut` sequence is non-atomic** and races with in-flight sync. If uploads finished but haven't been marked SYNCED yet, `hasPendingUploads()` returns false and data gets wiped.

### 🟡 Medium
- **`AppViewModel` scope is composition-wide, not NavGraph-scoped** (`AppNavGraph.kt:32-34`). Works, but the doc comment says "NavGraph-scoped".
- **`Screen` sealed class mixes string routes and `@Serializable` typed routes** — inconsistent style; typed for all four would be cleaner.
- **`observeAndSync` in MainActivity duplicates the auth listener in SyncCoordinator.init**, giving 2-3 sync triggers per sign-in.
- **`AndroidNetworkMonitor` registers callback with Application context** — verify (via `androidContext()` in Koin). If any Activity ever passed, leaks.
- **`MainActivity` locked to `portrait`** — fine for phone UX, but tablet users lose landscape (a common request).

### 🟢 Low
- `screenOrientation` without `configChanges` — every rotation would destroy activity if not portrait-locked. Currently portrait-locked, so moot but brittle.
- Package name `com.travelsouvenirs.main.*` — the `.main` segment is unusual.

---

## 5. ViewModels / UI

### 🟠 High
- **`ItemDetailViewModel.deleteItem` invokes `onDeleted()` even if `item.value` is null-then-recovered** (`ItemDetailViewModel.kt:27-34`) — nav callback is `let`-gated; silent no-op if the flow hasn't emitted yet.
- **`ListViewModel` search has no debounce** — every keystroke recomputes `sortedItems` (with per-item `.lowercase()` allocation).
- **`MapViewModel.initialCameraSet` is a plain `var`** (`MapViewModel.kt:32`) — lost on process death; camera re-zooms.
- **`MapViewModel.nativeMapView: Any?`** held in a common ViewModel (`MapViewModel.kt:36`) — an iOS platform reference in `commonMain` is a design smell; use `expect`/`actual` or a platform holder.
- **`AddItemViewModel.saveItem` swallows errors** — no state for save failure; user sees nothing.

### 🟡 Medium
- **Category filter's `filterItems` runs on the composition thread** (`ListScreen.kt:84`) inside `remember` — fine for small lists, but combines with the debounce-less search to make typing on a large collection janky.
- **Sort ties are unstable** — `sortedByDescending { it.dateAcquired }` and location/name have no secondary key.
- **`AddItemViewModel.confirmLocation` and `closeLocationDialog`** race on rapid taps (`isLocating` flag flips in finally).
- **`AddItemViewModel.openLocationDialog` mutates state 3 separate times** (line 194, 201, 207, 209, 212, 219) — recomposition storm.

### 🟢 Low
- `PolaroidListCard` rotation `(item.id % 9L - 4L) * 0.45f` bakes UI polish into a data-derived formula — non-obvious.

---

## 6. Manifest / Build

### 🟠 High
- **`ACCESS_MEDIA_LOCATION`** declared but no runtime request path (per §3).
- **`android:allowBackup="false"`** — user data isn't backed up by Android Auto-Backup. Given this is a memories app, users will lose data on device swap if they never signed in. Consider `allowBackup="true"` + a `full_backup_content` rule (or rely on Firebase sync + educate the user).
- **No `<queries>` for camera intent chooser on Android 11+** — camera capture uses `MediaStore.ACTION_IMAGE_CAPTURE`. Package visibility rules mean `resolveActivity` may return null on API 30+.

### 🟡 Medium
- No `taskAffinity`, `launchMode="singleTop"` — multiple share-target activations could stack.
- Missing `android:networkSecurityConfig` — fine while all traffic is Firebase (HTTPS), but worth being explicit.

---

## 7. Suggested Implementation Plan (Prioritized)

### P0 — data loss / silent corruption
1. Fix `hardDeleteByFirebaseId("")` mass-delete (`MetadataSyncService`) — guard `if (fbId.isEmpty()) dao.deleteById(entity.id) else …`.
2. Fix `AddItemViewModel.saveItem` — save DB first, delete original only on `saved == true`, wrapped in try/catch that surfaces error state.
3. Fix `CategorySyncService` exception path — never overwrite remote on non-404 errors; distinguish "doc missing" from "call failed".
4. Fix sync cancellation orphan window — either (a) always mark blob for cleanup before returning, or (b) run upload + Firestore write + `updateSyncMeta` inside a single non-cancellable block using `NonCancellable`, then check `ensureActive` only between items.
5. Delete Storage image in the `PENDING_DELETE` branch before deleting the Firestore doc.
6. `Item.toEntity()` — either delete or preserve sync fields.
7. Debounce sign-out's `hasPendingUploads` check + `cancelAllSyncs` — wait for any active sync to reach a stable state before wiping local data.

### P1 — races / policy correctness
8. Deduplicate sync triggers: keep exactly one (`SyncCoordinator.init` auth listener), remove `MainActivity.observeAndSync` and `MainScreen` `ON_RESUME` trigger. Add `distinctUntilChanged()` and a real debouncer.
9. Sequence `syncData()` before `syncImages()` in the auth-listener init.
10. Wrap `CategoryRepository.setAll`/`delete` in `@Transaction` and mark affected items `PENDING_UPLOAD`.
11. Fix `DeleteItemUseCase` order — DB mark first, image delete after successful cloud delete (or on hard-delete path only).
12. Replace `randomId()` with `firestore.collection(...).document().id`.
13. Fix ML-Kit analyzer race in `AddItemViewModel.onPhotoSelected` — track and cancel prior `Job`.
14. Camera flow — save `cameraOutputUri` via `SavedStateHandle`/`rememberSaveable`.
15. `AppSettings` — split device-scoped prefs from per-user prefs; scope `lastSyncMillis`/`categoriesUpdatedAt` by uid.

### P2 — robustness / performance
16. `ImageStorageHelper` — decode with `inJustDecodeBounds` + `inSampleSize`; drop the UCrop-output re-encode double-pass.
17. Add EXIF orientation FLIP/TRANSPOSE branches.
18. Wire `withContext(Dispatchers.IO)` in `AndroidImageStorage` and around `readLocalFileBytes` / `downloadUrlToFile`; write to `.tmp` then rename.
19. Add retry/exponential backoff to sync operations; surface per-item errors, don't drop.
20. Debounce `ListViewModel` search (say 200ms) + move sort to `Dispatchers.Default` (already there via `flowOn`).
21. Persist `MapViewModel.initialCameraSet` via `SavedStateHandle`; move `nativeMapView` out of common into an iOS-only holder.
22. Add Room `MigrationTestHelper` tests for 1→6.
23. Add index on `items(syncStatus, dateAcquiredMillis)`.

### P3 — housekeeping
24. Delete dead `SyncRepository.kt` stub.
25. Fix `SyncRepositoryTest` `FakeAuthRepository.uid` stubbing so signed-in tests actually run.
26. Update CLAUDE.md ("Room v4" → v6) and doc `AppViewModel` scope accurately.
27. Replace `TravelSouvenirsApp.instance` static with `androidContext()`.
28. Camera flow — clean up temp files also on gallery pick, or use a single scoped cache subdir cleared on ViewModel init.
29. Guard `deleteCategory(DEFAULT_CATEGORY)` at repo layer.
30. `AndroidLocationService` — check permission before calling FusedLocation; add search-result caching (Geocoder rate-limited to ~60/hr).
