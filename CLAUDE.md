# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tool Usage Guidelines

- Avoid complex shell expansions or command substitutions when simple commands suffice.
- Always use `"${VARIABLE_NAME}"` format for shell variables.
- All Gradle commands require the Android Studio JDK: prefix with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Build & Install

```bash
# Build debug APK
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :composeApp:assembleDebug

# Build and install on all connected devices (primary workflow)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug

# Run all unit tests
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :composeApp:testDebugUnitTest

# Run a single test class
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :composeApp:testDebugUnitTest --tests "com.travelsouvenirs.main.ui.add.AddItemViewModelTest"

# Lint
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :composeApp:ktlintCheck
```

`local.properties` (gitignored) must contain `sdk.dir` and `MAPS_API_KEY`. The Maps API key is injected via `manifestPlaceholders` in `build.gradle.kts`.

## Architecture

**Kotlin Multiplatform** project with Android as the primary target and an iOS stub. All code lives in `composeApp/src/`:

| Source set | Role |
|---|---|
| `commonMain` | Domain models, interfaces, ViewModels, use cases, navigation, DI modules |
| `androidMain` | Platform implementations: Room DB, Google Maps, FusedLocation, UCrop, Firebase, ML Kit |
| `iosMain` | Stub implementations (no-op or basic UIKit wrappers) |

### Dependency Injection (Koin)

Five Koin modules wired in `Modules.kt` + `PlatformModule.kt`:

- `dataModule` — Room database, DAOs, `ItemRepository`, `CategoryRepository`
- `syncModule` — Firebase Firestore/Storage, `SyncCoordinator`, sync services
- `authModule` — `FirebaseAuthRepository` / Google Sign-In
- `useCaseModule` — `SaveItemUseCase`, `DeleteItemUseCase`, `FilterItemsUseCase`
- `viewModelModule` — all ViewModels; `AddItemViewModel` is a parametrized factory taking `editId: Long?`
- `platformModule` (per platform) — `LocationService`, `ImageStorage`, `NetworkMonitor`, `ImageLocationAnalyzer`, `AppSettings`

### Data Flow

`Item` is the core domain model (in `commonMain/domain/`). Room entities (`ItemEntity`, `CategoryEntity`) live in `commonMain/data/` and are mapped to/from `Item` at the repository boundary. The `ItemRepository` exposes `Flow<List<Item>>` for reactive UI updates.

### Navigation

`AppNavGraph.kt` (Navigation Compose) defines these routes: `Main` → `AddItem` / `ItemDetail` / `EditItem` / `Settings` / `SignIn`. `AddItemScreen` doubles as the edit screen when `itemId` is passed.

### Main Screen Structure

`MainScreen` hosts two tabs (Map / List) via `PrimaryTabRow`. The FAB and category filter button live here and are shared between both tab views. `AppViewModel` is provided via `CompositionLocalProvider` so nested screens can access global state (sync status, auth).

### expect/actual Pattern

Platform-specific Composables and utilities use `expect`/`actual`:

- `PhotoPicker.kt` — gallery picker and camera capture; Android reads EXIF GPS *before* UCrop strips it, then passes coordinates to the ViewModel
- `MapContent.kt` / `MapLocationPicker.kt` — Google Maps on Android, stub on iOS
- `DateUtil.kt`, `BackHandler.kt` — platform utilities

### Add / Edit Item Flow

`AddItemViewModel` drives both add and edit. Key responsibilities:
- `ImagePickerHandler` manages photo copy lifecycle and orphan cleanup on cancel
- EXIF GPS and date are extracted before UCrop and stored as pending state
- `AndroidImageLocationAnalyzer` (ML Kit Gemini Nano, `FULL` preference) runs on-device image analysis after photo selection; result triggers an `AlertDialog` offering to prefill name, location (via forward geocoding), and date
- `onAcceptAiSuggestion()` calls `locationService.searchByName()` to resolve coordinates from the AI-detected place name; `onDismissAiSuggestion()` discards all pending state

### Firebase Sync

`SyncCoordinator` orchestrates two-phase sync (metadata via Firestore, then images via Storage) using GitLive Firebase SDK. Sync triggers automatically on sign-in. A WiFi-only toggle is stored in `AppSettings` (backed by `multiplatform-settings`). `NetworkMonitor` is an `expect`/`actual` interface.

### Testing

Unit tests are in `androidUnitTest`. ViewModels are tested with fake DAOs (`FakeItemDao`, `FakeCategoryDao`) and fake service implementations defined inline in each test file. Use `UnconfinedTestDispatcher` and `Dispatchers.setMain` for coroutine testing. Turbine and Mockito-Kotlin are available.

When adding a new constructor parameter to a ViewModel, update the corresponding test's factory helper function.
