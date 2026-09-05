# App Review — Travel Souvenirs (Follow-up)

**Date:** 2026-07-02 (v2 — follow-up to `20260702_code_review_report.md`)
**Scope:** Re-review after fixes to `hardDeleteByFirebaseId`, `MetadataSyncService.uploadPending` delete branch, `CategorySyncService` exception propagation, `AddItemViewModel.saveItem` ordering, and `CategoryRepository.delete`/`setAll` sync-safety.
**Deliverable:** Fix-status audit, new issues introduced by the fixes, and the still-open backlog. No code changes made.

Severity legend: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low

---

## 0. What was fixed since v1

| # | v1 item | Status | Evidence |
|---|---|---|---|
| P0-1 | `hardDeleteByFirebaseId("")` mass-delete | ✅ Fixed | `ItemDao.kt:48` — query now `WHERE firebaseId = :fbId AND firebaseId != ''` |
| P0-2 | `AddItemViewModel.saveItem` deletes original before confirming save | ✅ Fixed | `AddItemViewModel.kt:306-327` — `cleanupOnSave` moved inside `if (saved)` |
| P0-3 | `CategorySyncService` overwrites remote on any exception | ✅ Fixed | `CategorySyncService.kt:40-43` — exceptions now rethrown |
| §2 High | `CategoryRepository.delete` doesn't mark items `PENDING_UPLOAD` | ✅ Fixed | `CategoryRepository.kt:22-38` — items are re-inserted as `PENDING_UPLOAD` |
| §2 Crit | `CategoryRepository.setAll` non-transactional divergence | ⚠️ Partially — now diffs and delegates to `delete()` per removed category, so items get marked pending. But atomicity still missing (see §1 New H1). |
| P0-Delete branch | `MetadataSyncService` PENDING_DELETE: `hardDeleteByFirebaseId(entity.firebaseId)` even when empty | ✅ Fixed | `MetadataSyncService.kt:54-62` — hard-delete only when `firebaseId.isNotEmpty()`, else `dao.deleteItem(entity)` |

Still-open items from v1 are consolidated in §2 below.

---

## 1. NEW issues introduced by the fixes

### 🔴 N-C1. `CategoryRepository.delete` resurrects `PENDING_DELETE` items
`CategoryRepository.kt:25` — `itemDao.getItemsByCategory(name)` has no `syncStatus` filter, so it returns rows already marked `PENDING_DELETE`. The subsequent loop calls `itemDao.insertItem(item.copy(syncStatus = PENDING_UPLOAD, ...))` (line 31-37), which overwrites the pending-delete status. Net effect: **deleting a category resurrects any items in it that the user had already deleted**, and the next sync uploads them back to Firebase. Data-resurrection bug.

Fix direction: filter the read (`WHERE category = :name AND syncStatus != 'PENDING_DELETE'`) or preserve `syncStatus` when it's already `PENDING_DELETE`.

### 🟠 N-H1. `CategoryRepository.delete` is not atomic
`CategoryRepository.kt:22-38` — three-phase (read items → delete category → N inserts) with each `insertItem` in its own transaction. If the process dies or a coroutine is cancelled mid-loop, some items end up `PENDING_UPLOAD@DEFAULT` and others remain unchanged (already flipped to `DEFAULT_CATEGORY` in-DB by the FK `SET DEFAULT` cascade, but still `SYNCED`, so the change never reaches Firebase). Cloud diverges silently again.

Fix direction: wrap in a Room `@Transaction` on a dedicated DAO method or a `withTransaction { ... }` block. Because `SET DEFAULT` cascades run inside the same statement, the safest form is a single `UPDATE items SET category=:default, syncStatus='PENDING_UPLOAD', updatedAtMillis=:ts WHERE category=:name AND syncStatus != 'PENDING_DELETE'` **before** the `DELETE FROM categories`.

### 🟠 N-H2. Cascade race between category delete and item update
Same file, lines 28–37. Between `dao.deleteCategory(name)` (which cascades `SET DEFAULT` in-DB) and the item re-insert, any observer of `allItems`/`getAllActiveItems` sees an intermediate emission where items are in `DEFAULT_CATEGORY` but still `SYNCED`. UI briefly renders items in the default bucket before the pending flag is set; sync won't pick them up if it fires in that window.

### 🟡 N-M1. `setAll` is O(N×M) DB churn
`CategoryRepository.kt:47` — calls `delete(it)` per removed name, each doing its own item scan + cascade + re-insert loop. For a bulk category reset (e.g. after sign-out+sign-in), this can multiply into hundreds of writes. Batch into one query or a single transaction.

### 🟡 N-M2. `setAll` "kept" categories don't refresh `updatedAtMillis`
Line 50 — `dao.insertCategory` uses `OnConflictStrategy.IGNORE`. Categories that were already present keep their old `updatedAtMillis`. Downstream `CategorySyncService` compares timestamps to resolve conflicts; a "re-added same list" won't push forward. Minor, but subtle.

---

## 2. Still-open backlog from v1 (unchanged)

### Sync / Firebase
- 🔴 Orphaned Storage blobs when sign-out cancels mid-upload (`SyncCoordinator.kt:70` — `cancelAllSyncs` cancels between `upload()` and `updateSyncMeta`).
- 🔴 Storage image never deleted on hard-delete path (`MetadataSyncService.kt:56` — only Firestore doc marked `deleted=true`; the blob at `photoStoragePath` persists forever). Same for the pull-side `dao.hardDeleteByFirebaseId(fbId)` on remote-`deleted` docs (line 33).
- 🔴 Remote-vs-local write race — no CAS between `getItemByFirebaseId` and `insertItem` REPLACE (`MetadataSyncService.kt:36-42`).
- 🟠 Two-phase sync fires in parallel from `SyncCoordinator.init { … syncData(); launch { syncImages() } }`.
- 🟠 Category cross-device conflict resolution is still last-write-wins with no merge (`CategorySyncService.kt:34-53`) — the rethrow fix prevents "clobber on network error", but doesn't add merging.
- 🟠 Auto-sync fires on every `isConnected==true` emission (`MainActivity.kt:60-68`); no `distinctUntilChanged` / edge detection.
- 🟠 Duplicate sync trigger on resume — `MainScreen.kt:122-127` + MainActivity network callback + `SyncCoordinator.init` auth listener = up to 3 sync passes per sign-in.
- 🟠 `AndroidNetworkMonitor.checkConnected()` runs on main during init; callback never unregistered.
- 🟠 Clock-skew hole: `MetadataSyncService.kt:27` uses `lastSyncMillis - 5_000L` guard vs device-local clock; phones with >5s drift silently miss remote edits.
- 🟡 `randomId()` non-cryptographic (`MetadataSyncService.kt:105-108`); use `firestore.collection(...).document().id`.
- 🟡 `ImageSyncService.downloadMissingImages` silently swallows all failures.
- 🟡 No retry / exponential backoff anywhere.
- 🟡 `downloadUrlToFile` writes directly to final path — mid-download crash leaves partial JPEG.
- 🟡 `SyncCoordinator` uses `Dispatchers.Default`; should be `Dispatchers.IO`.
- 🟡 `_errors` `MutableSharedFlow` buffer = 4 with `tryEmit` — bursts drop.
- 🟡 `localFileExists` declared but never used (`StorageExt.kt:6`).
- 🟢 Dead `SyncRepository.kt` stub.
- 🟢 `FirebaseItem` uses `0.0`/`0L` sentinels.
- 🟢 Client-clock `updatedAtMillis`; prefer `serverTimestamp()`.
- 🟢 `insertCategory` per remote item hot loop in `downloadRemoteMetadata` (line 41) — batch or dedupe.

### Data / Room / Repositories
- 🔴 Cross-user data leak risk — single global `items_db`; no `userId` column; account-switch without an explicit sign-out shares the file (`ItemDatabaseBuilder.android.kt:8-11`).
- 🔴 `Item.toEntity()` silently drops `firebaseId`, `syncStatus`, `photoStoragePath`, `photoStorageUrl` (`ItemEntity.kt:55-65`) — landmine for any future refactor.
- 🟠 Deleting `DEFAULT_CATEGORY` isn't blocked at DAO/repo layer.
- 🟠 `DeleteItemUseCase.kt:12-13` deletes local photo before DB update; broken thumbnails during the interim.
- 🟠 `ItemDatabase @Database(version = 6)` still conflicts with CLAUDE.md ("Room v4").
- 🟡 No `MigrationTestHelper` coverage for `1→2 … 5→6`.
- 🟡 No index on `items(syncStatus, dateAcquiredMillis)`.
- 🟡 Empty-string sentinels in queries.
- 🟡 Timezone edge case (`atStartOfDayIn(UTC)` vs EXIF wall-clock).
- 🟡 `FilterItemsUseCase` silently passes orphan-category items.
- 🟢 `SaveItemUseCase` blank-input silent fail.
- 🟢 `MAX_CUSTOM_CATEGORIES = 5` UI-only.
- 🟢 In-memory `filter { != DEFAULT }`; push to DAO.

### Image / Photo Flow
- 🔴 Camera `cameraOutputUri` lost on process death (`PhotoPicker.android.kt:128, 167`) — `remember`-only.
- 🟠 OOM on large photos — `ImageStorageHelper.kt:31` decodes full-res.
- 🟠 `AndroidImageStorage.copyToInternalStorage` is `suspend` but blocking; no `Dispatchers.IO`.
- 🟠 EXIF orientation misses FLIP/TRANSPOSE variants (`ImageStorageHelper.kt:47-57`).
- 🟠 UCrop temp files leak from every gallery pick (`clearTempFiles` only at start of camera flow).
- 🟠 Double re-encode of UCrop output.
- 🟠 `ACCESS_MEDIA_LOCATION` runtime permission not requested — GPS silently dropped on gallery photos.
- 🟠 ML Kit analyzer races across photo selections (`AddItemViewModel.kt:117-125` — no `Job` cancellation).
- 🟡 `onPhotoSelected` copy overrides user-initiated Remove.
- 🟡 `deleteImage` safety only checks `filesDir` prefix.
- 🟡 Analyzer discards passed lat/lng.
- 🟡 `(0.0, 0.0)` as unset.
- 🟡 `ImagePickerHandler` cleanup on `Dispatchers.Default`.
- 🟡 `onCleared` fire-and-forget cleanup.
- 🟢 Analyzer branches on `javaClass.simpleName` (R8-fragile).
- 🟢 Prompt hard-coded English.
- 🟢 `IMAGE_MAX_SIDE_PX = 1200` looks soft.

### Navigation, DI, Lifecycle
- 🟠 `FirebaseAuthRepository` leaked `CoroutineScope` + `.user!!` NPE risk.
- 🟠 `TravelSouvenirsApp.instance` static used from Room builder.
- 🟠 Location permission requested with no rationale / re-prompt / "don't ask again" path.
- 🟠 `AppSettings.clear()` on sign-out wipes device-scoped prefs (view mode, WiFi-only, notes) alongside per-user state.
- 🟠 `AppSettings.lastSyncMillis` / `categoriesUpdatedAt` not scoped per uid.
- 🟠 `SettingsViewModel.signOut` sequence non-atomic; races in-flight sync.
- 🟡 `AppViewModel` scope is composition-wide, not NavGraph-scoped as doc'd.
- 🟡 `Screen` sealed class mixes string routes with `@Serializable` typed routes.
- 🟡 `MainActivity.observeAndSync` duplicates the auth listener in `SyncCoordinator.init`.
- 🟡 `AndroidNetworkMonitor` context leak risk if any Activity ever passed.
- 🟡 `MainActivity` portrait-locked, blocks tablet landscape.
- 🟢 `screenOrientation` without `configChanges`.
- 🟢 Package name `com.travelsouvenirs.main.*` — `.main` segment unusual.

### ViewModels / UI
- 🟠 `ItemDetailViewModel.deleteItem` — `let`-gated nav callback silently no-ops if flow hasn't emitted.
- 🟠 `ListViewModel` search has no debounce.
- 🟠 `MapViewModel.initialCameraSet` is a plain `var` — lost on process death.
- 🟠 `MapViewModel.nativeMapView: Any?` in `commonMain` — iOS platform reference in shared code.
- 🟠 `AddItemViewModel.saveItem` still swallows errors: `state.photoPath ?: return` (line 308) is silent; `saveItem(...)` returning `false` (blank name) or throwing produces no UI feedback. (The v1 file-deletion bug is fixed, but there's still no error surface.)
- 🟡 `ListScreen.kt:84` category filter runs on composition thread.
- 🟡 Sort ties unstable across all `SortOption`s.
- 🟡 `AddItemViewModel.confirmLocation` + `closeLocationDialog` race on rapid taps.
- 🟡 `openLocationDialog` mutates state 3× — recomposition storm.
- 🟢 `PolaroidListCard` rotation formula bakes UI polish into a data-derived expression.

### Manifest / Build
- 🟠 `ACCESS_MEDIA_LOCATION` declared but no runtime request path.
- 🟠 `android:allowBackup="false"` — no OS-level backup fallback for users who never sign in.
- 🟠 No `<queries>` element for `ACTION_IMAGE_CAPTURE` on Android 11+.
- 🟡 No `taskAffinity` / `launchMode="singleTop"`.
- 🟡 No `networkSecurityConfig`.

---

## 3. Updated prioritized implementation plan

### P0 — data loss / silent corruption
1. **Fix `CategoryRepository.delete` resurrection bug** (§1 N-C1). Filter `PENDING_DELETE` items out of the update loop or preserve their status.
2. **Wrap category delete/reassign in a single transaction** (§1 N-H1) — ideally as a single `UPDATE items … WHERE category=:name AND syncStatus != 'PENDING_DELETE'` before the `DELETE FROM categories`.
3. Delete the Storage blob (both push-delete and pull-remote-deleted paths) in `MetadataSyncService`.
4. Close the sync-cancellation orphan window — wrap `upload + set + updateSyncMeta` in `withContext(NonCancellable)` per item, check `ensureActive` only between items.
5. `Item.toEntity()` — either delete it or preserve sync fields.
6. Scope `items_db` per-user (either separate DB file per uid, or a `userId` column + query filter), so account-switch or crashed sign-out can't leak data.
7. Debounce sign-out's `hasPendingUploads` check + `cancelAllSyncs` so the wipe only runs after any in-flight sync has actually stopped.

### P1 — races / policy correctness
8. Deduplicate sync triggers — keep only `SyncCoordinator.init` auth listener; remove `MainActivity.observeAndSync` and `MainScreen.ON_RESUME` trigger; add `distinctUntilChanged()` and a real debouncer.
9. Sequence `syncData()` before `syncImages()` in `SyncCoordinator.init`.
10. Add a real conflict-resolution transaction: `getItemByFirebaseId` + `insertItem` inside a single `withTransaction` block, gated by `updatedAtMillis` comparison.
11. Fix `DeleteItemUseCase` order — mark row `PENDING_DELETE` first, delete photo only after sync confirms remote delete.
12. Replace `randomId()` with `firestore.collection(...).document().id`.
13. Cancel prior ML-Kit analyzer job on new photo selection in `AddItemViewModel.onPhotoSelected`.
14. Persist camera `cameraOutputUri` via `SavedStateHandle` / `rememberSaveable`.
15. Split `AppSettings` into device-scoped and per-user-scoped storage; key user-scoped values by uid.
16. Batch `CategoryRepository.setAll` into a single transaction (§1 N-M1).

### P2 — robustness / performance
17. Decode with `inJustDecodeBounds` + `inSampleSize`; drop the UCrop-output re-encode double-pass.
18. Handle EXIF FLIP/TRANSPOSE orientations.
19. Move disk/network work to `Dispatchers.IO`; `downloadUrlToFile` should write to `.tmp` then rename.
20. Retry/exponential backoff for sync; surface per-item errors without dropping.
21. Debounce `ListViewModel` search (~200 ms).
22. Persist `MapViewModel.initialCameraSet`; move `nativeMapView` out of `commonMain`.
23. `MigrationTestHelper` tests for all Room migrations.
24. Add index on `items(syncStatus, dateAcquiredMillis)`.
25. Add `saveError`/`saveInProgress` state in `AddItemViewModel`; block Save while photo copy in flight; surface failures.

### P3 — housekeeping
26. Delete `SyncRepository.kt` stub.
27. Fix `SyncRepositoryTest.FakeAuthRepository.uid` stubbing so signed-in tests actually run.
28. Update CLAUDE.md (Room v6, corrected `AppViewModel` scope).
29. Replace `TravelSouvenirsApp.instance` static with Koin `androidContext()`.
30. Camera flow — clean up temp files also on gallery pick.
31. Guard `deleteCategory(DEFAULT_CATEGORY)` at repo layer.
32. `AndroidLocationService` — check permission before FusedLocation calls; cache Geocoder results (rate-limited to ~60/hr).
33. Manifest: add `<queries>` for `ACTION_IMAGE_CAPTURE`; reconsider `allowBackup`.

---

## 4. Net summary

- **Fixed:** 6 items from v1 (5 clean, 1 partial).
- **Newly introduced:** 1 🔴 (`delete` resurrects `PENDING_DELETE` items), 2 🟠 (non-atomicity + cascade-read race), 2 🟡 (churn + stale timestamps).
- **Overall Critical count:** was 8, now 7 (fixed 3, introduced 1 different one, 1 remaining critical is unchanged/partial).
- **Overall High count:** was ~19, now ~19 (fixed 2, introduced 2).
