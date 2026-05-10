# Travel Souvenirs

A Kotlin Multiplatform app (Android primary, iOS stub) for cataloguing travel souvenirs. Photograph each item, tag it with a location, organise by category, and browse your whole collection on an interactive map or a searchable list. Items optionally sync across devices via Firebase.

---

## Features

- **Photo capture** — take a photo with the camera or pick one from the gallery; both pass through UCrop for cropping
- **AI location detection** — on supported devices (Android 8+, Pixel with Gemini Nano), on-device ML Kit analysis detects the location visible in the photo and offers to pre-fill the title, location, and date fields
- **Location tagging** — tap the map to pin a location, search by place name, or auto-detect via GPS
- **EXIF extraction** — GPS coordinates and capture date are read from the original image before cropping strips them
- **Reverse geocoding** — coordinates are automatically resolved to a human-readable place name
- **Categories** — assign each item to one of up to 5 custom categories (plus the built-in Default)
- **Map view** — all items appear as pins on a Google Map; nearby pins are clustered
- **List view** — grid or list layout with full-text search and sort by name, date, or location; filter by category
- **Detail screen** — full photo, place name, date, notes, and a non-interactive mini-map
- **Edit and delete** — long-tap delete with confirmation; dedicated edit flow
- **Firebase sync** — optional account sign-in via Google; syncs metadata (Firestore) and photos (Cloud Storage) across devices with a WiFi-only toggle
- **Settings** — manage categories, per-account notes, sync preferences, and full data deletion

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin Multiplatform |
| UI | Compose Multiplatform 1.10.3 |
| Maps | Google Maps Compose 6.5.3 |
| Database | Room 2.7.1 + KSP |
| DI | Koin 4.0.0 |
| Location | FusedLocationProviderClient (Play Services 21.3) |
| Geocoding | Android `Geocoder` (no extra API key) |
| Image loading | Coil 3.1.0 |
| Image cropping | UCrop 2.2.9 |
| AI analysis | ML Kit GenAI / Gemini Nano 1.0.0-beta2 |
| Navigation | Navigation Compose 2.9.0 |
| Firebase | GitLive Firebase SDK 2.1.0 (Auth · Firestore · Storage) |
| Auth | Google Sign-In via Credentials API |
| Settings | multiplatform-settings 1.2.0 |
| Date/time | kotlinx-datetime 0.6.2 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

---

## Getting Started

### Prerequisites

- Android Studio with its bundled JDK
- Android SDK (`sdk.dir` in `local.properties`)
- A **Google Maps API key** with *Maps SDK for Android* enabled
- A Firebase project with `google-services.json` placed in `composeApp/` (required for sync; the app works offline without it)

### Setup

1. Clone the repo:
   ```bash
   git clone https://github.com/tomekgrubba/TravelSouvenirs.git
   cd TravelSouvenirs
   ```

2. Create `local.properties` in the project root (gitignored):
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
   ```

3. *(Optional — for sync)* Add `composeApp/google-services.json` from the [Firebase Console](https://console.firebase.google.com).

### Build & Run

```bash
# Install on all connected devices
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
```

Or open the project in Android Studio and press **Run** (▶).

---

## Permissions

| Permission | Purpose |
|---|---|
| `CAMERA` | Take photos |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS tagging |
| `ACCESS_MEDIA_LOCATION` | Read GPS EXIF from gallery photos without redaction |
| `INTERNET` | Map tiles and Firebase sync |
| `ACCESS_NETWORK_STATE` | WiFi-only sync check |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | Gallery picker on Android ≤ 12 |
| `WRITE_EXTERNAL_STORAGE` | Temp files on Android ≤ 9 |

---

## Data Storage

Photos are copied into app-private internal storage (`filesDir/item_photos/`) and never leave the device unless the user signs in and enables sync. The Room database stores all metadata (name, notes, coordinates, place name, date, category). When sync is active, metadata is stored in Firestore under the signed-in user's UID, and photos are uploaded to Firebase Storage.
