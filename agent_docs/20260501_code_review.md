# Travel Souvenirs — Comprehensive Code Review

> Full codebase review across all source sets (commonMain, androidMain, iosMain) covering architecture, bugs, performance, platform parity, and code quality.

---

## 1. Bugs & Correctness Issues

### 1.1 Hardcoded "Date acquired" label
**File:** [AddItemScreen.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemScreen.kt#L359)

The date field label is a hardcoded English string `"Date acquired"` instead of using `stringResource()`. All other labels in the app are properly localized.

```kotlin
// Line 359 — hardcoded
label = { Text("Date acquired") },
// Should be:
label = { Text(stringResource(Res.string.label_date_acquired)) },
```

### 1.2 Hardcoded strings in SettingsScreen
**File:** [SettingsScreen.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/settings/SettingsScreen.kt)

Several strings are hardcoded in English instead of using string resources:
- Line 130: `"Signed in as $email"`
- Line 157: `"Sign out"`
- Line 160: `"Sign in to sync your items across devices."`
- Line 168: `"Sign in"`
- Line 279: `"Appearance"`
- Line 292: `"Theme"`
- Lines 317–318: Style descriptions (`"Cool deep-space purples..."`, `"Warm charcoal..."`)
- Lines 88–90: Delete category body text (dynamically constructed)

### 1.3 `onSaved` used as back button in AddItemScreen
**File:** [AddItemScreen.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemScreen.kt#L207)

The back arrow `IconButton(onClick = onSaved)` calls the same callback as a successful save. This means pressing back without saving triggers `onSaved` — which navigates away correctly but semantically conflates "saved" with "cancelled". If logic was ever added to `onSaved` (e.g., showing a toast), it would fire incorrectly.

### 1.4 Unused variable `repo` in OsmMapContent marker loop
**File:** [OsmMapContent.android.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/platform/OsmMapContent.android.kt#L305)

```kotlin
val repo = mapView.getRepository() ?: return@forEach  // 'repo' is never read
```
The `getRepository()` call is used as a guard but the result is bound to an unused variable. This should either be `mapView.getRepository() ?: return@forEach` without binding, or the variable name replaced with `_`.

### 1.5 Duplicate import in GoogleMapsContent
**File:** [GoogleMapsContent.android.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/platform/GoogleMapsContent.android.kt#L61)

```kotlin
import androidx.compose.runtime.collectAsState  // imported twice (line 29 and 61)
```

### 1.6 iOS OSM `onAddClick` not wired up
**File:** [OsmMapContent.ios.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/iosMain/kotlin/com/travelsouvenirs/main/platform/OsmMapContent.ios.kt#L52)

The iOS OSM map composable signature is `fun OsmMapContent(onPinClick: (Long) -> Unit)` — it doesn't accept `onAddClick`, so the empty state card is not clickable on iOS OSM. Compare with Android's `fun OsmMapContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit)`.

### 1.7 `NetworkMonitor` and `wifiOnlySync` are declared but never enforced
**Files:** [NetworkMonitor.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/network/NetworkMonitor.kt), [SyncRepository.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/SyncRepository.kt)

`SettingsViewModel` exposes `wifiOnlySync` and there is a `NetworkMonitor` interface, but `SyncRepository` never checks either of them before syncing. The setting toggle exists in the UI but has no effect.

---

## 2. Architecture & Design

### 2.1 Domain model leaks sync concerns
**File:** [Item.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/domain/Item.kt)

The "clean" domain model `Item` contains `firebaseId`, `syncStatus`, `updatedAtMillis`, `photoStoragePath`, and `photoStorageUrl`. These are sync/infrastructure concerns that don't belong in the domain layer. The UI never needs `syncStatus` or `photoStorageUrl`.

**Proposal:** Strip sync fields from `Item` and keep them only in `ItemEntity`. The mapping functions `toDomain()`/`toEntity()` already exist and can handle the translation.

### 2.2 ViewModel factories via CompositionLocal
**Files:** [AppLocals.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/di/AppLocals.kt), all screens

Every screen does:
```kotlin
val repository = LocalItemRepository.current
val viewModel: XxxViewModel = viewModel { XxxViewModel(repository, ...) }
```

This works but has a subtle issue: `viewModel {}` creates the VM with the factory on first call and ignores the factory on subsequent calls. If a `CompositionLocal` value ever changes, the existing ViewModel won't pick it up. Currently fine, but fragile.

### 2.3 MapViewModel stores native view references
**File:** [MapViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/map/MapViewModel.kt#L27)

`nativeMapView: Any?` in the ViewModel can hold platform-specific views (MKMapView, WKWebView). On Android, this was correctly avoided for osmdroid (using primitive zoom/lat/lng instead). But on iOS, `nativeMapView` directly holds a `MKMapView` or `WKWebView`, which is acceptable since iOS doesn't have the Activity lifecycle issue.

### 2.4 Category persistence uses CSV-in-a-string
**Files:** [SettingsViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/settings/SettingsViewModel.kt#L78), [AddItemViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemViewModel.kt#L93)

Categories are stored as a comma-joined string in `Settings`. This breaks if a category name contains a comma. Additionally, the same `KEY_CATEGORIES = "categories"` constant is duplicated across `SettingsViewModel`, `AddItemViewModel`, and `SyncRepository`.

**Proposal:** Either validate that commas aren't allowed in category names, or switch to JSON serialization. Extract the constant to a single location.

---

## 3. Performance

### 3.1 O(N²) clustering in `groupByZoom`
**File:** [MapViewModel.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/map/MapViewModel.kt#L66)

The clustering algorithm iterates over all remaining items for each seed, giving O(N²) complexity. For a personal souvenir app this is probably fine (< 1000 items), but it runs on every zoom event in `LaunchedEffect`.

**Proposal:** Consider a grid-based spatial hash (O(N)), or at minimum, move the `groupByZoom` call to `Dispatchers.Default` so it doesn't block the main thread.

### 3.2 Marker icons rebuilt on every zoom change
**File:** [OsmMapContent.android.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/platform/OsmMapContent.android.kt#L282)

`LaunchedEffect(itemPins, items, zoomLevel)` triggers on every zoom level change, which sequentially loads every pin's photo from disk via `buildCircularBitmap`. The `iconCache` helps for individual pins, but `clusterIconCache` is keyed by `(firstId, count)` — a zoom change produces different groups with different keys, causing cache misses and redundant IO.

### 3.3 Full Firestore collection download on every sync
**File:** [SyncRepository.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/sync/SyncRepository.kt#L129)

`downloadRemoteMetadata` does `firestore.collection("items").get()` — fetching the entire collection every time. As the item count grows, this becomes expensive (reads count toward Firestore billing too).

**Proposal:** Track a `lastSyncTimestamp` and use `.whereGreaterThan("updatedAtMillis", lastSync)` for incremental sync.

---

## 4. iOS Feature Parity Gaps

| Feature | Android | iOS |
|---|---|---|
| Photo markers (circular) | ✅ Custom `buildCircularBitmap` | ❌ Default red pins (MapKit) / default Leaflet markers (OSM) |
| Cluster badges | ✅ Count badge on grouped markers | ❌ No clustering on MapKit; no count on Leaflet |
| My Location button | ✅ FAB on map | ❌ Missing on MapKit; missing on iOS OSM |
| Edge indicators | ✅ `EdgeIndicator` shows off-screen item counts | ❌ Not implemented |
| `onAddClick` on empty state | ✅ Clickable overlay | ❌ OSM: missing; MapKit: ✅ |
| Approximate location circle | ✅ `buildApproxCircle` polygon | ❌ Not implemented |
| Filter menu styling | ✅ `RoundedCornerShape`, `surfaceContainerHigh` | ❌ Default dropdown styling |

---

## 5. Code Quality & Maintainability

### 5.1 Massive composable functions
[OsmMapContent.android.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/platform/OsmMapContent.android.kt) is **508 lines** in a single `@Composable` function. [GoogleMapsContent.android.kt](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/androidMain/kotlin/com/travelsouvenirs/main/platform/GoogleMapsContent.android.kt) is 343 lines.

**Proposal:** Extract reusable parts:
- `MapFilterFab` — the filter FAB + dropdown (duplicated across 4 map implementations)
- `MapLocationFab` — the "my location" FAB (duplicated across Android maps)
- `MapEmptyState` — the empty state card (duplicated across 5 locations)
- `EdgeIndicators` — the off-screen item count indicators

### 5.2 Duplicated date formatting
The date format `"${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { ... }} ${date.year}"` is copy-pasted in:
- [AddItemScreen.kt:355](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/add/AddItemScreen.kt#L355)
- [ItemDetailScreen.kt:226](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/detail/ItemDetailScreen.kt#L226)
- [ListScreen.kt:298](file:///Users/tomekgrubba/AndroidStudioProjects/TravelSouvenirs/composeApp/src/commonMain/kotlin/com/travelsouvenirs/main/ui/list/ListScreen.kt#L298)

**Proposal:** Extract to a `fun LocalDate.formatDisplay(): String` extension function.

### 5.3 Duplicated category filtering logic
The category filter expression `m.category in selectedCategories || m.category !in categoryFilter.allCategoriesSet` is repeated in:
- `OsmMapContent.android.kt` (lines 128, 134)
- `GoogleMapsContent.android.kt` (lines 87, 93)
- `OsmMapContent.ios.kt` (line 72)
- `ListScreen.kt` (line 81)

**Proposal:** Move to `CategoryFilterViewModel.filterItems(items: List<Item>)`.

### 5.4 No unit tests for SyncRepository
Tests exist for `MapViewModel`, `ListViewModel`, `AddItemViewModel`, `ItemDetailViewModel`, `SettingsViewModel`, entity mappers, and DAO — good coverage. But `SyncRepository`, which contains critical bidirectional sync logic, has no tests.

---

## 6. Error Handling

### 6.1 Silent exception swallowing
Many `catch (_: Exception) { }` blocks throughout the codebase silently swallow errors:
- `SyncRepository` — sync failures (lines 50, 67, 203)
- `OsmMapContent` — initial location fetch (line 219)
- `GoogleMapsContent` — initial location and "my location" (lines 154, 168, 192)
- `AddItemViewModel` — EXIF location prefill (line 160)

While non-fatal for a personal app, there's no way for the user to know why sync failed or why "my location" didn't work. A centralized error reporting mechanism (e.g., `SharedFlow<UserMessage>` displayed as a `Snackbar`) would improve UX significantly.

---

## 7. Proposed Priority Order

| Priority | Change | Impact | Effort |
|---|---|---|---|
| 🔴 High | Fix hardcoded strings (1.1, 1.2) | Localization broken for German users | Low |
| 🔴 High | Wire `onAddClick` on iOS OSM (1.6) | Empty state not interactive on iOS | Low |
| 🔴 High | Enforce `wifiOnlySync` (1.7) | Setting toggle does nothing | Medium |
| 🟡 Medium | Extract duplicated UI components (5.1) | Reduces ~400 lines of duplication | Medium |
| 🟡 Medium | Extract date formatting (5.2) | 3 files, easy win | Low |
| 🟡 Medium | Extract category filtering (5.3) | 4 files, reduces subtle bugs | Low |
| 🟡 Medium | Clean domain model (2.1) | Better separation of concerns | Medium |
| 🟡 Medium | Centralized error reporting (6.1) | Better UX on failures | Medium |
| 🟢 Low | Delta sync (3.3) | Reduces Firestore reads + billing | Medium |
| 🟢 Low | Background clustering (3.1) | Prevents frame drops at scale | Low |
| 🟢 Low | SyncRepository tests (5.4) | Confidence in sync correctness | Medium |
| 🟢 Low | iOS feature parity (4) | Full cross-platform experience | High |
| 🟢 Low | Fix category CSV (2.4) | Edge case, unlikely in practice | Low |
