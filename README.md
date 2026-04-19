# TravelSouvenirs

An Android app for collecting and cataloguing fridge magnets. Take a photo of each magnet, tag it with a location, and browse your whole collection on an interactive map.

---

## Features

- **Photo capture** — take a photo with the camera or pick one from the gallery
- **Location tagging** — auto-detect your current GPS position, or type a place name manually
- **Reverse geocoding** — GPS coordinates are automatically resolved to a human-readable place name
- **Collection map** — all magnets appear as pins on a Google Map; tap any pin to open its detail view
- **Detail screen** — full photo, place, date, notes, and a non-interactive mini-map of the location
- **Delete magnets** — long-tap the delete button on the detail screen with confirmation
- **Local storage** — all data is stored on-device using Room (SQLite); no account or internet required beyond map tiles

---

## Screenshots

| Map | Add Magnet | Detail |
|-----|-----------|--------|
| All magnets as map pins | Camera, form, GPS | Full photo + mini-map |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin (Multiplatform project, Android target) |
| UI | Jetpack Compose / Compose Multiplatform 1.10.3 |
| Maps | Google Maps Compose SDK (`maps-compose`) |
| Database | Room 2.7.1 + KSP |
| Location | Google Play Services — FusedLocationProviderClient |
| Geocoding | Android `Geocoder` (no extra API key needed) |
| Image loading | Coil 2.7.0 |
| Navigation | Navigation Compose 2.9.0 |
| Date/time | `kotlinx-datetime` |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

---

## Project Structure

```
composeApp/src/
├── androidMain/
│   ├── data/
│   │   ├── MagnetEntity.kt        # Room entity + toDomain / toEntity mappers
│   │   ├── MagnetDao.kt           # DAO (Flow queries + suspend mutations)
│   │   ├── MagnetDatabase.kt      # Room database singleton
│   │   └── MagnetRepository.kt    # Repository wrapping the DAO
│   ├── domain/
│   │   └── Magnet.kt              # Domain model (uses kotlinx LocalDate)
│   ├── image/
│   │   └── ImageStorageHelper.kt  # Copy photos to internal storage; FileProvider URI
│   ├── location/
│   │   └── LocationHelper.kt      # FusedLocationClient wrapper + Geocoder
│   ├── navigation/
│   │   └── AppNavGraph.kt         # NavHost with Map / AddMagnet / MagnetDetail routes
│   ├── theme/
│   │   └── AppTheme.kt            # Material3 theme
│   └── ui/
│       ├── map/
│       │   ├── MapScreen.kt        # Google Map with pins, FAB
│       │   └── MapViewModel.kt     # StateFlow<List<Magnet>>
│       ├── add/
│       │   ├── AddMagnetScreen.kt  # Photo + form + location UI
│       │   └── AddMagnetViewModel.kt
│       └── detail/
│           ├── MagnetDetailScreen.kt  # Full detail + mini-map + delete
│           └── MagnetDetailViewModel.kt
└── commonMain/                    # iOS scaffolding (stub App composable)
```

---

## Getting Started

### Prerequisites

- Android Studio (with bundled JDK)
- Android SDK (set in `local.properties`)
- A **Google Maps API key** with the *Maps SDK for Android* enabled

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/tomekgrubba/TravelSouvenirs.git
   cd TravelSouvenirs
   ```

2. Create `local.properties` in the project root (it is gitignored):
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
   ```

3. Get a Maps API key:
   - Go to [Google Cloud Console](https://console.cloud.google.com)
   - Create a project → Enable **Maps SDK for Android**
   - Create an API key under *APIs & Services → Credentials*

### Build & Run

**From the terminal:**
```bash
# Debug build
./gradlew :composeApp:assembleDebug

# Install directly on a connected device or emulator
./gradlew :composeApp:installDebug
```

**From Android Studio:**
Open the project and press **Run** (▶) with your device or emulator selected.

---

## Permissions

The app requests the following permissions at runtime:

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | Take photos of magnets |
| `ACCESS_FINE_LOCATION` | GPS tagging |
| `ACCESS_COARSE_LOCATION` | Fallback location |
| `INTERNET` | Load Google Map tiles |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | Pick photos from gallery (Android ≤ 12) |

---

## Data Storage

Photos are copied to the app's private internal storage (`filesDir/magnet_photos/`) and never leave the device. The Room database (`magnets_db`) stores metadata including file paths, coordinates, place name, date, and notes. Neither photos nor data are uploaded anywhere.

---

## Contributing

Pull requests are welcome. For larger changes, please open an issue first to discuss what you'd like to change.
