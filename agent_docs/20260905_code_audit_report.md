# Comprehensive Code Audit Report — Travel Souvenirs

**Date:** 2026-09-05  
**Project:** Travel Souvenirs (Kotlin Multiplatform / Compose Multiplatform for Android & iOS)  
**Scope:** Full-app audit covering architecture, sync engine, data layer, UI/UX, platform implementations, test suite status, and uncommitted working-tree changes.

---

## Executive Summary & Health Check

| Area | Status | Key Highlights |
|---|---|---|
| **Core Architecture** | 🟢 **Strong** | Clean Compose Multiplatform setup with high code reuse between Android and iOS. Well-structured Koin DI and reactive Room database with bundled SQLite. |
| **Unit Test Suite** | 🔴 **Broken (48/145 failing)** | Tests fail when run with Android Studio's bundled JBR 25 due to Robolectric ASM class version 69, Mockito mocking Kotlin final classes, and an uncommitted coroutine race condition. |
| **Sync Engine** | 🔴 **Critical Vulnerabilities** | Sync can permanently wedge on a single bad item, silently overwrites concurrent user edits (TOCTOU), and hard-deletes local items if a remote delete occurs regardless of newer local edits. |
| **Add/Edit & Location Flows** | 🟠 **High Risk** | Android 13+ Geocoder hangs indefinitely on network errors; AI landmark suggestions can place items at (0, 0); double-tap on Save creates duplicates; hardcoded year 2026. |
| **Working Tree** | 🟡 **Uncommitted Work** | Uncommitted changes from July 2026 exist across 6 files (photo replacement sync tracking, AI edit check, iOS active sync hook, and failing tests). |

---

## 1. What Was Solved Well (Strengths)

1. **Clean Compose Multiplatform Architecture**:
   - High degree of UI code sharing across Android and iOS in `commonMain` (`MainScreen`, `AddItemScreen`, `ListScreen`, `ItemDetailScreen`, `SettingsScreen`).
   - Cleanly separated platform-specific integrations (`PhotoPicker`, `MapContent`, `DateUtil`, `BackHandler`) using idiomatic `expect`/`actual` declarations.
   - Good responsiveness: adaptivity for tablets (`maxWidth >= 600.dp`) adjusting FAB sizes, typography, and grid columns.

2. **Smart EXIF Metadata Extraction**:
   - In [PhotoPicker.android.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/platform/PhotoPicker.android.kt) and [PhotoPicker.ios.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/iosMain/kotlin/com/travelsouvenirs/main/platform/PhotoPicker.ios.kt), the app extracts GPS coordinates and capture dates from original image streams/URLs **before** passing images to cropping libraries (UCrop / Swift cropper), neatly circumventing the standard problem where image croppers strip EXIF tags.

3. **High-Performance Map Experience & Clustering**:
   - Custom clustering in [MapViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/map/MapViewModel.kt#L70-L100) (`groupByZoom`) sorts by latitude to achieve \(O(N \log N)\) average performance with early threshold breaking.
   - `spreadOverlapping()` mathematically fans out co-located souvenir pins in a small circular spiral so multiple items at identical coordinates are individually selectable.
   - Screen-edge counters (`computeEdgeCounts` / `scrollOffScreen`) let users know how many souvenirs exist beyond their current viewport.

4. **Multiplatform Persistence with Room & Bundled SQLite**:
   - Uses modern `androidx.room` with `androidx.sqlite:sqlite-bundled`, allowing identical Room DAOs and database queries across both Android and iOS.
   - Migrations 1 through 7 are defined and versioned with exported JSON schemas in `composeApp/schemas/`.

5. **Image Memory & Temporary File Management**:
   - Strict downscaling (`IMAGE_MAX_SIDE_PX = 1920`, JPEG quality 85) prevents out-of-memory errors when processing high-resolution camera photos.
   - [ImagePickerHandler.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/ImagePickerHandler.kt) tracks copied files per session and automatically purges abandoned files if the user cancels out of the Add Item screen.
   - iOS avoids container UUID invalidation on app reinstalls by storing relative filenames rather than absolute paths in the database.

6. **Internationalization**:
   - Clean string resource extraction via Compose Resources (`Res.string.*`) with full English and German (`values-de`) localization.

---

## 2. Issues and Bugs

### 🔴 Critical

#### 1. Unit Test Suite Broken on JDK 25 (48 / 145 Tests Failing)
Running `./gradlew :composeApp:testDebugUnitTest` with Android Studio's default JDK (JBR 25) fails with 48 failures across 4 test suites:
- **Robolectric crash** ([ImageStorageHelperTest.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidUnitTest/kotlin/com/travelsouvenirs/main/image/ImageStorageHelperTest.kt) & [RememberGroupIconsTest.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidUnitTest/kotlin/com/travelsouvenirs/main/ui/map/RememberGroupIconsTest.kt)): Robolectric 4.14.1 crashes with `IllegalArgumentException: Unsupported class file major version 69` because ASM 9.x does not support Java 25 bytecode.
- **Mockito final class mocking failure** ([SettingsViewModelTest.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidUnitTest/kotlin/com/travelsouvenirs/main/ui/settings/SettingsViewModelTest.kt) & [SyncRepositoryTest.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidUnitTest/kotlin/com/travelsouvenirs/main/sync/SyncRepositoryTest.kt)): Mockito inline-mocking fails on JDK 25 when mocking final Kotlin classes (`SyncCoordinator`, `FirebaseFirestore`, `FirebaseUser`).
- **Uncommitted Coroutine Threading Bug** in [AddItemViewModelTest.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidUnitTest/kotlin/com/travelsouvenirs/main/ui/add/AddItemViewModelTest.kt#L152): The test `onPhotoSelected triggers AI analysis when creating new item` calls `vm.onPhotoSelected()`. `ImagePickerHandler` launches its copy work on `Dispatchers.Default` (unconfined background thread). The test completes and runs `tearDown()` (`Dispatchers.resetMain()`), after which the background coroutine finishes and attempts to launch on `viewModelScope` (`Dispatchers.Main`), throwing an unhandled `IllegalStateException`. This poisons the test runner, causing subsequent search debounce tests to fail with `UncaughtExceptionsBeforeTest`.

#### 2. Android 13+ (API 33+) Geocoder Infinite Suspension
- **Location**: [AndroidLocationService.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/location/AndroidLocationService.kt#L66-L72) & [AndroidLocationService.kt:93](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/location/AndroidLocationService.kt#L93)
- **Problem**: In Android 13 (Tiramisu), the asynchronous `Geocoder.getFromLocation` takes a `Geocoder.GeocodeListener` SAM. In Kotlin, passing a lambda `{ addresses -> ... }` only implements `onGeocode`. If geocoding fails (network offline or backend service unavailable), Android invokes `listener.onError(String)`. Because `onError` has a default no-op implementation, `cont.resume()` is **never called**.
- **Impact**: The coroutine suspends forever. In `confirmLocation()`, `isLocating` remains `true` permanently, the Confirm button spinner spins indefinitely, and the "My Location" button stays disabled.

#### 3. Single-Item Failure Permanently Wedges All Sync Operations
- **Location**: [MetadataSyncService.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/MetadataSyncService.kt#L61-L111) (`uploadPending`)
- **Problem**: The pending items are processed in a raw `for (entity in pending)` loop without per-item `try/catch`. If one item fails (e.g., local photo missing from disk causing `cloudImageStorage.upload` to throw, or a `PENDING_DELETE` item doc does not exist in Firestore causing `.update()` to throw NOT_FOUND), the exception terminates the entire method.
- **Impact**: The failing item remains in `PENDING_UPLOAD` or `PENDING_DELETE` state. Every subsequent sync attempts the same bad item, fails immediately, and leaves all items queued behind it permanently unsynced. The user sees "Sync failed. Check your connection." forever.

#### 4. TOCTOU Silent Loss of Concurrent User Edits
- **Location**: [ImageSyncService.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/ImageSyncService.kt#L18) and [MetadataSyncService.kt:100](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/MetadataSyncService.kt#L100)
- **Problem**:
  1. In `ImageSyncService.downloadMissingImages`, the service holds an in-memory `ItemEntity` snapshot, downloads the image file over network, and then executes `dao.insertItem(entity.copy(photoPath = storedPath))` (`OnConflictStrategy.REPLACE`). If the user modified the item's title or notes while the image was downloading, the user's edits are overwritten with the stale snapshot.
  2. In `MetadataSyncService.uploadPending`, after the Firestore upload completes, `dao.updateSyncMeta(id, status = SYNCED)` is called unconditionally. Any local edit made while the network request was in flight gets its status stamped back to `SYNCED`, so the edit is never uploaded.
- **Impact**: Silent data loss during normal user interactions while background sync is active.

#### 5. Remote Delete Overwrites Newer Local Edits
- **Location**: [MetadataSyncService.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/MetadataSyncService.kt#L35-L38) (`downloadRemoteMetadata`)
- **Problem**: When `remote.deleted` is true, it calls `dao.hardDeleteByFirebaseId(fbId)` unconditionally without checking timestamps. While non-delete updates check `remote.updatedAtMillis > local.updatedAtMillis`, deletes bypass this check.
- **Impact**: If Device B deletes an item, but Device A edited that same item offline with a newer timestamp, Device A's newer edit is permanently deleted when Device A reconnects.

#### 6. Destructive Sign-Out Without Confirmation
- **Location**: [SettingsViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/settings/SettingsViewModel.kt#L121-L135) (`signOut`)
- **Problem**: If `repository.hasPendingUploads()` is false, tapping "Sign out" immediately wipes all items (`repository.deleteAll()`), deletes all categories (`categoryRepository.setAll(emptyList())`), wipes local photos (`imageStorage.deleteAllImages()`), and clears preferences (`appSettings.clear()`) with **zero confirmation dialog**.
- **Impact**: A user tapping sign-out loses their entire offline collection on that device instantly without warning.

---

### 🟠 High Severity

#### 7. AI Suggestion Can Save Items at Null Island (0.0, 0.0)
- **Location**: [AddItemViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemViewModel.kt#L140-L147) & [AddItemScreen.kt:248](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemScreen.kt#L248)
- **Problem**: When accepting an AI suggestion, if forward geocoding fails to resolve coordinates, line 145 falls back to `_uiState.update { it.copy(placeName = place) }` while leaving `latitude` and `longitude` at `0.0`. `isFormValid` only checks `photoPath != null && name.isNotBlank() && placeName.isNotBlank()`.
- **Impact**: The souvenir is saved with coordinates `(0.0, 0.0)`. The map camera zooms to Null Island in the middle of the Atlantic Ocean.

#### 8. Double-Tap Duplicate Item Creation
- **Location**: [AddItemViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemViewModel.kt#L305-L327) (`saveItem`)
- **Problem**: `saveItem()` launches asynchronous coroutine work and does not disable itself with an `isSaving` guard. Two quick taps on "Save" trigger two coroutines, resulting in duplicate database inserts with `id = 0`.

#### 9. Unbounded Firebase Cloud Storage Leak on Deletions
- **Location**: [MetadataSyncService.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/MetadataSyncService.kt#L63-L71)
- **Problem**: When processing `PENDING_DELETE`, the Firestore document is updated to `deleted: true`, but the image file at `users/{userId}/photos/{firebaseId}.jpg` in Firebase Storage is **never deleted**.
- **Impact**: Cloud storage usage and hosting costs grow indefinitely, and deleted photos remain stored in Firebase Storage against user privacy expectations.

#### 10. Latent SQLite Constraint Crash on Category Deletion
- **Location**: [ItemDatabase.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/data/ItemDatabase.kt#L110-L116) and [ItemEntity.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/data/ItemEntity.kt#L16)
- **Problem**: The database defines `FOREIGN KEY(category) REFERENCES categories(name) ON UPDATE NO ACTION ON DELETE SET DEFAULT`. However, the `category` column is declared `TEXT NOT NULL` **without an SQLite DEFAULT value**. If SQLite's foreign key cascade ever fires, SQLite attempts to set `category` to NULL and throws a runtime `NOT NULL constraint failed: items.category` error.

#### 11. Koin Activity Reference Leak in GoogleSignInHelper
- **Location**: [MainActivity.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/MainActivity.kt#L27)
- **Problem**: `loadKoinModules(module { single<GoogleSignInHelper> { AndroidGoogleSignInHelper(this@MainActivity) } })` captures the Activity instance in Koin's singleton root. On device rotation or activity recreation, `SignInViewModel` retains a reference to the destroyed activity, causing `getIdToken()` to fail with `"Activity has been destroyed"`.

#### 12. Hardcoded Year 2026 in Year Picker
- **Location**: [AddItemScreen.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemScreen.kt#L975)
- **Problem**: `val currentYear = 2026; val years = remember { (currentYear downTo 1970).toList() }`.
- **Impact**: In 2027, users will not be able to select the current year in the date picker.

#### 13. UI Lockup During Sync
- **Location**: [MainScreen.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/main/MainScreen.kt#L147-L216)
- **Problem**: While `isSyncing` is true, `PlatformBackHandler(enabled = isSyncing)` blocks the system back gesture, the Map tab is disabled (`enabled = !isSyncing`), the List tab is disabled (`enabled = !isSyncing`), and the Settings button is disabled. Combined with sync running on every `ON_RESUME`, a slow cellular connection makes the app feel completely unresponsive.

---

### 🟡 Medium Severity

1. **Local Disk Orphan on Remote Photo Update**:
   - In the uncommitted diff of [MetadataSyncService.kt:47](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/MetadataSyncService.kt#L47), when `remoteUrlChanged` is detected, `localPhotoPath` is set to `""` so `ImageSyncService` downloads the replacement photo. However, the existing local photo file on disk is never unlinked or deleted, leaking internal storage.
2. **Sync Trigger Chaos (Lack of Debouncing & Centralization)**:
   - Sync is triggered independently by:
     - `MainScreen` on `ON_RESUME`
     - `AppNavGraph` on `networkMonitor.isConnected`
     - `SyncCoordinator.init` on `currentUser`
     - `iOSApp.swift` on `UIApplication.didBecomeActiveNotification`
   - These triggers fire in parallel with no debouncing. Furthermore, `SyncCoordinator.init` only triggers if `prevUid == null`, so switching directly from User A to User B without an intermediate null user never triggers a sync.
3. **Cancellation Swallowing in Image Sync**:
   - In [ImageSyncService.kt:19](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/ImageSyncService.kt#L19), `catch (_: Exception)` catches all exceptions, including `CancellationException`. Cancelling sync operations (such as during sign-out or cellular disconnect) does not abort in-flight image downloads.
4. **Onboarding Screen Flicker on Cold Start**:
   - In [MainScreen.kt:144](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/main/MainScreen.kt#L144), `val allItems by itemRepository.allItems.collectAsState(initial = emptyList())` initializes to `emptyList()`. On cold launch, `showOnboarding` temporarily evaluates to `true`, flashing the onboarding card before Room emits the stored items.
5. **Sort Option Not Persisted in List Screen**:
   - [ListViewModel.kt:35](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/list/ListViewModel.kt#L35) persists `viewMode` (`LIST` / `GRID`) to `appSettings`, but `sortOption` is not persisted, resetting to `NAME` every session.
6. **Path Portability Mismatch (Android vs iOS)**:
   - iOS stores relative filenames (`item_abc.jpg`) and dynamically prepends the document path via `resolvedLocalPath()`. Android stores full absolute paths (`/data/user/0/...`) in Room, making Android database backups and multi-user restores brittle.
7. **Redundant Operations in Category Management**:
   - In [SettingsViewModel.kt:105-110](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/settings/SettingsViewModel.kt#L105-L110), `deleteCategory` calls `categoryRepository.delete(name)` (which already reassigns items to `Default` inside an atomic transaction), and then redundantly calls `repository.reassignCategory(...)` a second time.
8. **iOS Location Permission Hang Potential**:
   - In [IosLocationService.kt:77-84](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/iosMain/kotlin/com/travelsouvenirs/main/location/IosLocationService.kt#L77-L84), `getCurrentLocation()` calls `requestWhenInUseAuthorization()` without a timeout. If the permission dialog is dismissed or remains undetermined, the coroutine can wait indefinitely.

---

### 🟢 Low Severity / Code Smells

1. **Hardcoded English Strings in ViewModels**:
   - `SignInViewModel.kt` lines 37, 72, 82 contain hardcoded English strings (`"Email and password are required."`, `"Please enter your email address..."`) instead of referencing Compose multiplatform string resources.
2. **Dead Files & Placeholders**:
   - [SyncRepository.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/SyncRepository.kt) is an empty 5-line stub file with a comment saying it should be removed.
   - `CategoryFilterViewModel.refreshCategories()` is an empty no-op method.
3. **AGP 9.0+ KMP Compatibility Deprecation**:
   - Build logs warn that `org.jetbrains.kotlin.multiplatform` will drop compatibility with `com.android.application` in upcoming releases, recommending moving the Android application target to an `:androidApp` subproject that depends on `:composeApp`.
4. **Platform Leak in Common ViewModel**:
   - [MapViewModel.kt:36](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/map/MapViewModel.kt#L36) declares `var nativeMapView: Any?` to cache the iOS `MKMapView`, leaking iOS view-hierarchy lifecycle management into common code.

---

## 3. What Should Be Adapted (Recommended Architectural Roadmap)

```mermaid
flowchart TD
    subgraph Phase 1: Test Suite & Stability
        T1["Configure Gradle JVM Toolchain (JDK 17/21)"] --> T2["Refactor SettingsViewModelTest & SyncRepositoryTest with Fakes/Interfaces"]
        T2 --> T3["Fix AddItemViewModelTest Coroutine Scope"]
    end

    subgraph Phase 2: Sync Engine Reliability
        S1["Wrap uploadPending items in per-item try/catch"] --> S2["Implement Targeted Atomic DB Updates (prevent TOCTOU)"]
        S2 --> S3["Tolerate NOT_FOUND on remote deletes"]
        S3 --> S4["Timestamp check for remote deletes"]
        S4 --> S5["Clean up Cloud Storage images on delete"]
    end

    subgraph Phase 3: UX & Critical Bugfixes
        U1["Implement onError & withTimeout in AndroidLocationService"]
        U2["Validate (lat, lng != 0.0) in AddItemScreen & ViewModel"]
        U3["Add isSaving guard in AddItemViewModel"]
        U4["Dynamic Clock.System.now().year in YearPicker"]
        U5["Add Sign-Out confirmation dialog"]
        U6["Remove UI-blocking during metadata sync"]
    end

    Phase 1 --> Phase 2
    Phase 2 --> Phase 3
```

### Recommendation 1: Stabilize the Test Suite
1. **Enforce JVM Toolchain**: In `composeApp/build.gradle.kts`, configure a Java toolchain (Java 17 or 21) for test compilation and execution so Gradle forks test runners with a JDK supported by Robolectric and Mockito:
   ```kotlin
   kotlin {
       jvmToolchain(17)
   }
   ```
2. **Replace Mockito with Fakes for Final Classes**:
   - Extract an interface `ISyncCoordinator` (or create a `FakeSyncCoordinator`) so `SettingsViewModelTest` does not require Mockito byte-buddy class alteration on final classes.
   - In `SyncRepositoryTest`, avoid mocking `FirebaseFirestore` directly; use the existing fake DAOs and services.
3. **Fix `AddItemViewModelTest`**: Pass an explicit test dispatcher or scope into `ImagePickerHandler` so image operations run deterministically under `runTest`.

### Recommendation 2: Harden the Sync Engine
1. **Per-Item Error Handling in `uploadPending`**:
   ```kotlin
   for (entity in pending) {
       try {
           // upload or delete single entity
       } catch (e: Exception) {
           if (e is CancellationException) throw e
           println("Failed to sync item ${entity.id}: $e")
           continue
       }
   }
   ```
2. **Atomic Targeted Updates Instead of REPLACE**:
   - Replace `dao.insertItem(entity.copy(photoPath = ...))` in `ImageSyncService` with a targeted DAO query:
     `UPDATE items SET photoPath = :path WHERE id = :id`
   - Compare timestamp on `updateSyncMeta`:
     `UPDATE items SET syncStatus = :status, ... WHERE id = :id AND updatedAtMillis = :expectedTs`
3. **Cloud Storage Cleanup**:
   - When processing `PENDING_DELETE`, call `cloudImageStorage.delete(entity.photoStoragePath)` to remove orphaned photos.

### Recommendation 3: Resolve User-Facing Defects
1. **Geocoder Listener**:
   ```kotlin
   geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
       override fun onGeocode(addresses: List<Address>) { cont.resume(addresses) }
       override fun onError(errorMessage: String?) { cont.resume(emptyList()) }
   })
   ```
   Wrap with `withTimeoutOrNull(5_000)` to guarantee resumption.
2. **Coordinate Validation**: Ensure `isFormValid` checks `latitude != 0.0 && longitude != 0.0` or prevents setting `placeName` without valid coordinates.
3. **Date Picker Year**: Replace `val currentYear = 2026` with `todayLocalDate().year`.
4. **Sign-Out UX**: Show a standard confirmation dialog: *"Are you sure you want to sign out? Your souvenirs are safely stored in your account and will be removed from this device."*
5. **Non-blocking Sync**: Keep tabs and navigation interactive during sync, relying on the top-bar progress indicator.

---

## 4. Prioritized Action Plan

| Priority | Item | Impact | Effort |
|---|---|---|---|
| **P0** | Fix test suite environment (JDK 17 toolchain + Mockito/coroutine fixes) | Restores continuous verification and prevents regressions | Medium |
| **P0** | Wrap `uploadPending` in per-item try/catch | Eliminates permanent sync wedge on single item error | Low |
| **P0** | Implement `onError` and timeout in `AndroidLocationService` | Fixes permanent spinner / hang on Android 13+ | Low |
| **P1** | Atomic targeted Room updates (`UPDATE ... SET photoPath`) | Prevents silent TOCTOU edit loss during sync | Medium |
| **P1** | Add `isSaving` flag to `AddItemViewModel` | Prevents duplicate souvenirs on double-tap | Very Low |
| **P1** | Dynamic year in `YearPicker` | Prevents date picker failure in 2027 | Very Low |
| **P1** | Require valid coordinates for AI place suggestions | Prevents Null Island (0,0) souvenirs | Low |
| **P1** | Confirmation dialog on Sign Out | Prevents accidental local collection wipe | Low |
| **P2** | Delete cloud photos on item deletion | Protects user privacy and bounds cloud storage cost | Low |
| **P2** | Unblock tabs and back navigation during sync | Dramatically improves perceived responsiveness | Low |
| **P3** | Centralize sync triggers with debouncer in `SyncCoordinator` | Eliminates redundant network requests | Medium |
