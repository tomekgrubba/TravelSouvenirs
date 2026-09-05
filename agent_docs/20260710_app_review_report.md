# App Review — Travel Souvenirs

**Date:** 2026-07-10
**Scope:** Full-app review — potential bugs, confusing options/user flows, and architectural issues across sync, data, ViewModels, screens, and both platform layers.
**Deliverable:** Findings only. No code changes made.

Severity legend: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low

---

## 1. Sync / Firebase

### 🔴 Critical
- **One bad item permanently wedges sync for everything behind it.** `MetadataSyncService.uploadPending` (`MetadataSyncService.kt:61`) processes pending items in a plain `for` loop with no per-item error handling. If any single item fails — the local photo file is missing so `cloudImageStorage.upload` throws, or a `PENDING_DELETE` item's Firestore doc no longer exists so `.update()` throws NOT_FOUND — the exception aborts the whole loop. The failing item stays pending, so **every future sync fails at the same item**, and all items queued after it never upload. The user just sees "Sync failed. Check your connection." forever. Fix: wrap each item in its own try/catch (skip and continue); for deletes use `set(..., merge)` or tolerate NOT_FOUND.
- **Sync can silently overwrite concurrent user edits (TOCTOU).** Two variants:
  - `ImageSyncService.downloadMissingImages` (`ImageSyncService.kt:18`) holds an entity snapshot across a slow network download, then does `dao.insertItem(entity.copy(photoPath = ...))` — a full-row REPLACE. A user edit made during the download is reverted to the stale snapshot. Should be a targeted `UPDATE items SET photoPath = ...`.
  - `uploadPending` calls `dao.updateSyncMeta(..., status = SYNCED)` unconditionally after the Firestore write (`ItemDao.kt:37-38`). An edit made between `getPendingItems()` and that call gets its `PENDING_UPLOAD` stamped back to `SYNCED` — the edit never syncs. Fix with compare-and-set (`WHERE updatedAtMillis = :snapshotTs`).
- **A remote delete beats a newer local edit.** `downloadRemoteMetadata` (`MetadataSyncService.kt:35`): `remote.deleted` triggers `hardDeleteByFirebaseId` with **no timestamp comparison**, while non-delete updates properly check `remote.updatedAtMillis > local.updatedAtMillis`. Device B deletes an item; device A edits it offline with a newer timestamp; when A syncs, its newer edit is silently hard-deleted.

### 🟠 High
- **Deleted items leak cloud storage forever.** The `PENDING_DELETE` branch updates the Firestore doc to `deleted: true` but never deletes `users/{uid}/photos/{fbId}.jpg` from Firebase Storage, and tombstone docs are never purged. Cost grows unbounded, and users reasonably expect a deleted photo to be gone from the cloud (privacy expectation).
- **Sync cancellation doesn't actually cancel image downloads.** `ImageSyncService.downloadMissingImages` (`ImageSyncService.kt:19`) catches *all* exceptions per item — including `CancellationException` — and continues the loop. `cancelAllSyncs()` (sign-out, WiFi-only toggle mid-download) won't stop it. Rethrow `CancellationException` (SyncCoordinator already does this correctly).
- **Account switch without sign-out never syncs.** `SyncCoordinator.init` (`SyncCoordinator.kt:49-60`) only triggers on `prevUid == null` → non-null. A direct uid A → uid B transition skips both the cancel and the sync.

### 🟡 Medium
- **Conflict resolution trusts client clocks.** `updatedAtMillis` is device wall-clock; one device with a skewed clock silently wins or loses every conflict. The 5-second read lookback helps queries but not write ordering. Firestore server timestamps would be robust.
- **Local file orphan on remote photo change** (in the current working-tree diff): when `remoteUrlChanged` clears `photoPath` (`MetadataSyncService.kt:44-47`), the old local file is never deleted — unreachable disk leak. (The diff's core logic — clearing storage path on photo replacement so it re-uploads — looks correct; re-upload to the same storage path regenerates the download token, so URL-change detection should work.)
- **Three overlapping sync triggers** (sign-in collector in `SyncCoordinator.init`, `ON_RESUME` in `MainScreen.kt:128`, connectivity collector in `AppNavGraph.kt:40`) coordinated only by non-atomic boolean StateFlows. Centralize in `SyncCoordinator` with a debounce.
- **`appDidBecomeActive()`** (uncommitted, `MainViewController.kt`) creates a fresh unsupervised `CoroutineScope` per foreground event; reuse one scope.

---

## 2. Location / Geocoding

### 🔴 Critical
- **Geocoder can hang forever on Android 13+.** `AndroidLocationService.reverseGeocode` and `searchByName` (`AndroidLocationService.kt:66`, `:93`): the Tiramisu path passes a lambda as `Geocoder.GeocodeListener`. The lambda implements only `onGeocode`; `onError` is a default no-op. When geocoding fails (typically no network), neither callback resumes the continuation — the coroutine **suspends forever**. In `confirmLocation` that leaves `isLocating = true` permanently: Confirm spins forever, the my-location button stays disabled. The surrounding `try/catch` doesn't help because nothing is thrown. Fix: implement `onError` explicitly; add `withTimeout` as belt-and-braces.

### 🟠 High
- **AI suggestion can save items at coordinates (0, 0).** `onAcceptAiSuggestion` (`AddItemViewModel.kt:144`): if the forward geocode of the AI place name returns nothing, it sets `placeName = place` while lat/lng stay `0.0`. `isFormValid` (`AddItemScreen.kt:248`) only checks `placeName.isNotBlank()`, so the item saves with a pin at Null Island and the post-save camera zooms there. Require real coordinates for validity, or don't set `placeName` without them.

### 🟡 Medium
- `IosLocationService.getCurrentLocation` has no timeout; if authorization stays notDetermined the coroutine can wait indefinitely.

---

## 3. Add / Edit Item flow

### 🟠 High
- **Double-tap on Save creates duplicate items.** `saveItem()` (`AddItemViewModel.kt:305`) launches async work and the button stays enabled until `isSaved` triggers navigation. Two quick taps in add mode → two inserts with `id = 0`. Guard with an `isSaving` flag.
- **Hardcoded "current year" 2026 in the date picker.** `YearPicker` (`AddItemScreen.kt:975`) uses `val currentYear = 2026`; fallbacks are the literal 2026/5/12. From January 2027 users can't pick the current year. Use `Clock.System.now()`.

### 🟡 Medium
- **Save disabled with no explanation.** The form requires photo + name + location, but a grey button is all the feedback — nothing says which field is missing. Also a validation mismatch: `SaveItemUseCase` checks only name + photo; the location rule exists only in the UI.
- **Dead-end in edit mode**: the ✕ button removes the photo, after which Save is permanently disabled (photo mandatory) with no explanation.
- **Dead EXIF-date plumbing.** `prefillDateFromExif`/`pendingExifDate` exist but comments say the feature is intentionally disabled — confusing leftover state.
- Gallery-flow temp files in `cacheDir/temp_photos` are only cleared when launching the *camera* (`PhotoPicker.android.kt:287`); they accumulate otherwise (cache dir, so OS-reclaimable — minor).

---

## 4. Settings / Auth

### 🔴 Critical
- **Sign-out silently destroys all local data.** `SettingsViewModel.signOut` (`SettingsViewModel.kt:121-135`) wipes items, photos, categories, *and all settings* — with **no confirmation at all** when everything is synced. The warning dialog appears only for pending uploads, and its text ("photos that haven't been saved to the cloud") is wrong when the pending items are pending *deletes*. At minimum always confirm, and say "this removes the collection from this device (it stays in your account)". Wiping `wifiOnlySync`/`viewMode` preferences is also surprising.

### 🟠 High
- **Koin `single` captures the Activity.** `MainActivity.onCreate` (`MainActivity.kt:27`) registers `AndroidGoogleSignInHelper(this@MainActivity)` as an app-scoped `single`. On rotation the module reloads, but `SignInViewModel` (outlives the Activity) may hold a helper bound to the destroyed Activity — leak, and Google sign-in can break right after a configuration change.
- **Latent FK crash: `ON DELETE SET DEFAULT` with no column default.** Migration 6→7 (`ItemDatabase.kt:110`) recreated `items` with `category TEXT NOT NULL` and no `DEFAULT` clause (v5→6 had one), but the FK still says `ON DELETE SET DEFAULT`. If the FK ever fires, SQLite sets `category` to NULL → NOT NULL constraint abort. Dormant today (code always reassigns before deleting), but any future direct category delete crashes.

### 🟡 Medium
- **`renameCategory` lies about success.** `SettingsViewModel.renameCategory` fires `categoryRepository.rename` in a coroutine and returns `true` immediately, ignoring the repository's own boolean (which can fail on the case-sensitive duplicate check). The dialog closes as if it worked.
- `SettingsViewModel.deleteCategory` reassigns items twice — the repository transaction already does it (`SettingsViewModel.kt:105-110`).
- **Location permission requested at cold app launch** (`MainActivity.kt:34`) with no context — violates permission UX guidance and is redundant with the in-context request in the location dialog.

---

## 5. Main screen / Filtering / List

### 🟠 High
- **The app locks up during metadata sync.** During `isSyncing`, back navigation is blocked (`MainScreen.kt:147`), both tabs are disabled, and the Settings button is disabled — with no timeout on the sync. Combined with sync firing on every resume and connectivity change, a slow network makes the app feel frozen. Prefer non-blocking sync with the existing progress indicator.

### 🟡 Medium
- **Onboarding flicker.** `showOnboarding = currentUser == null && allItems.isEmpty()` (`MainScreen.kt:144`) — `allItems` starts as `emptyList()`, so a signed-out user *with* items sees the onboarding card flash on every cold start until Room emits.
- **Single-category auto-select.** `CategoryFilterViewModel` (`CategoryFilterViewModel.kt:63-77`) force-selects the category when only one is in use, so the filter chip shows a specific category the user never chose. Functionally same as "All", visually confusing.
- **Rename-category affordance is invisible** — rename works by tapping the chip itself; no icon or hint (delete has one).
- **Sort option isn't persisted but view mode is** (`ListViewModel`) — resets to NAME every session.

### 🟢 Low
- Hardcoded English strings mixed with resources: AI dialog, sign-out warning, onboarding card, date-picker tabs/"Clear"/"Select Date", "Remove photo".

---

## 6. Architecture

- **The sync engine has no concurrency story with the UI.** Root cause of the TOCTOU bugs in §1: sync services read snapshots and write whole rows back with REPLACE while the user edits the same rows through the repository. No transactions, versioning, or compare-and-set at the DAO boundary. Fix as a pattern (targeted UPDATEs guarded by `syncStatus`/`updatedAtMillis`), not as individual patches.
- **UI bypasses ViewModels for data**: `MainScreen` and `SettingsScreen` inject `ItemRepository`/`AuthRepository` directly via `koinInject()` to compute onboarding state and affected-item counts, while everything else goes through ViewModels. Pick one pattern.
- **iOS details leak into common code**: `MapViewModel.nativeMapView: Any?` (`MapViewModel.kt:36`) holds an `MKMapView` in a common ViewModel. An `expect/actual` holder or platform-side cache would keep the boundary clean.
- **Android stores absolute photo paths in the DB** while iOS stores filenames (for container-UUID rotation). `resolvedLocalPath` exists precisely for this — Android could adopt relative paths too and become resilient to backup/restore.
- **Dead weight**: `SyncRepository.kt` is an empty placeholder (with a test still named after it), `CategoryRow` in `SettingsScreen.kt` is unused, `CategoryFilterViewModel.refreshCategories()` is a no-op.
- `randomId()` (`MetadataSyncService.kt:116`) uses non-cryptographic `kotlin.random.Random`; prefer Firestore auto-IDs via `.document()`.

---

## Prioritized fix order

1. 🔴 Sync wedge (`uploadPending` loop abort) — blocks all sync behind one bad item.
2. 🔴 TOCTOU edit loss + remote-delete-wins — silent data loss.
3. 🔴 Sign-out confirmation + accurate warning text — destroys user data without consent.
4. 🔴 Geocoder hang on API 33+ — easy fix, visible symptom (stuck spinner).
5. 🟠 Double-tap Save duplicates — easy `isSaving` guard.
6. 🟠 Hardcoded year 2026 — trivial, has a hard deadline (2027-01-01).
7. 🟠 Cloud image cleanup on delete — cost + privacy.
8. 🟠 Koin Activity leak / FK landmine / app lock during sync.
9. 🟡 The rest as opportunistic cleanups.
