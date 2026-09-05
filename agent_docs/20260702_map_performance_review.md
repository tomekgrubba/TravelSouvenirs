# Map Pin Handling — Performance Review

**Date:** 2026-07-02
**Scope:** Scalability of map pin rendering on Android as item count grows. Files inspected: `MapViewModel.kt`, `GoogleMapsContent.android.kt`, `MarkerBitmapUtil.kt`, `MapOverlays.kt`.
**Deliverable:** Findings and prioritized fix proposal. No code changes made.

Severity legend: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low

---

## 1. How the pipeline currently works

**Data → pins (in `MapViewModel`):**
- `itemPins: StateFlow<List<ItemPin>>` = `repository.allItems.map(::spreadOverlapping)` — spreads exact-overlap items in a small circle. O(N) per DB emission.
- `itemGroups: StateFlow<List<ItemGroup>>` = `repository.allItems.map(::computeGroups)` — exact-rounded groups. O(N) per DB emission.
- `groupByZoom(items, zoom)` — companion static, O(N log N), called from the composable.

**In `GoogleMapsContent.android.kt`:**
- Category-filter applied on the composition thread via `remember(allItems, selectedCategories)` and `remember(allPins, selectedCategories)`.
- `visibleBounds = remember(cameraPositionState.position, cameraPositionState.projection) { … }`.
- `offScreen = remember(items, visibleBounds) { computeEdgeCounts(items, …) }`.
- `LaunchedEffect(items, zoom.toInt(), showIndividual) { itemGroups = withContext(Default) { groupByZoom(items, zoom) } }`.
- `rememberIndividualIcons(itemPins, sizePx)` and `rememberGroupIcons(itemGroups, sizePx)` build `BitmapDescriptor`s off the main thread and cache in a `mutableStateMapOf`.
- `if (showIndividual) itemPins.forEach { Marker(...) } else itemGroups.forEach { Marker(...) }`.

**Marker bitmap build (`buildPolaroidBitmap`):**
- Coil loads photo at target size, then `Bitmap.createScaledBitmap` runs again, then a polaroid frame + optional count badge is drawn to a fresh `ARGB_8888` canvas.
- Wrapped in `BitmapDescriptorFactory.fromBitmap(bmp)` which uploads a native GPU texture.

---

## 2. Findings

### 🔴 F1 — Off-screen edge-count scan runs on every camera frame
`GoogleMapsContent.android.kt:97-109`

```kotlin
val visibleBounds = remember(cameraPositionState.position, cameraPositionState.projection) { … }
val offScreen = remember(items, visibleBounds) { computeEdgeCounts(items, …) }
```

`cameraPositionState.position` is a `State` that updates every animation frame during pan/zoom. So `visibleBounds` and then `computeEdgeCounts` re-run **at ~60 Hz while the user is panning**.

`computeEdgeCounts` (`MapOverlays.kt:32-58`) does an unconditional `items.filter { … }.forEach { … }` — allocates an intermediate list, iterates all N items. At 500 items this is 30k iterations/sec + 60 list allocations/sec **while dragging**. This is the first thing that will feel janky.

### 🔴 F2 — Individual-icon cache never evicts
`MarkerBitmapUtil.kt:49-63`

`rememberIndividualIcons` guards with `containsKey` but never removes stale entries. Compare with `rememberGroupIcons` at `lines 71-73`, which correctly drops keys not in the new set. Every pin ever seen (even after deletion or filter-out) keeps its `BitmapDescriptor` alive, and each descriptor holds a GPU texture. Long sessions with lots of add/delete/filter leak native memory.

### 🔴 F3 — `MapViewModel.itemGroups` is dead code, computed on every DB emission
`MapViewModel.kt:52-55`

```kotlin
val itemGroups: StateFlow<List<ItemGroup>> = repository.allItems.map(::computeGroups)…
```

Nobody reads this StateFlow. `GoogleMapsContent` calls the static `MapViewModel.groupByZoom(...)` instead. So every item edit re-runs `computeGroups` on the whole collection for nothing.

### 🟠 F4 — Zoom-in renders every pin globally with no viewport culling
`GoogleMapsContent.android.kt:213-226`

```kotlin
if (showIndividual) { itemPins.forEach { pin -> key(pin.item.id) { Marker(...) } } }
```

At zoom 13 with 500 items scattered across the world, only ~10 are visible but 500 Compose Marker nodes + 500 native markers are created. Google Maps SDK culls draw calls, but the Compose overhead (MarkerState per pin, recomposition on any camera change) is unavoidable.

### 🟠 F5 — Bitmap is double-scaled, and build concurrency is unbounded
`MarkerBitmapUtil.kt:102, 121`

Coil is asked for `imgSize × imgSize` (line 102) and `Bitmap.createScaledBitmap(source, imgSize, imgSize, true)` runs again (line 121). Extra allocation + copy per pin.

Also, when N new pins arrive, `pins.forEach { … launch { buildPolaroidBitmap(...) } }` fires N concurrent coroutines with no throttle.

### 🟠 F6 — `groupByZoom` recomputes on every whole-number zoom step; no hysteresis
`GoogleMapsContent.android.kt:112` — `LaunchedEffect(items, zoom.toInt(), showIndividual)`. Pinch from zoom 5→13 fires ~8 O(N log N) sorts. Slow zoom near the 13.0 boundary flip-flops between rendering modes.

### 🟡 F7 — Icon `null` until built → default red pin flash
While `buildPolaroidBitmap` is in flight, `individualIcons[id]` returns null and `Marker(icon = null, …)` renders as the default red pin, then swaps once the bitmap is ready.

### 🟡 F8 — Category filter runs on the composition thread
`GoogleMapsContent.android.kt:84-89` — `remember(allItems, selectedCategories) { categoryFilter.filterItems(allItems) }`. Fine at 1k items, worth moving into `MapViewModel` on `Dispatchers.Default` for consistency with other pin computations.

### 🟡 F9 — Zoom read on every animation frame recomposes the whole composable
`val zoom = cameraPositionState.position.zoom` (line 94) — read directly, so every animation frame recomposes `GoogleMapsContent`.

### 🟢 F10 — No spatial index
All pin operations are O(N). Fine for a personal-collection app; would matter above ~5k items.

### 🟢 F11 — `Marker` state allocated per-pin per-composition
`rememberUpdatedMarkerState(position = …)` inside `key(pin.item.id)` recreates state when the pin moves. Since spread positions can shift when a same-coord group grows, this is defensible; noting the cost only.

---

## 3. Fix proposal

### P0 — high impact, small change

1. **Debounce `computeEdgeCounts` off the per-frame path.**
   Options, in order of preference:
   - Trigger only when `cameraPositionState.isMoving` transitions to `false` (camera idle).
   - Or discretize the bounds key: `remember(items, roundedZoom, roundedCenter) { … }`, e.g. `roundedCenter = LatLng(round(lat*100)/100, round(lng*100)/100)`.
   - Or throttle to 5 Hz via a `snapshotFlow` collected with `sample(200.ms)`.

2. **Evict stale individual icons.** Mirror `rememberGroupIcons`:
   ```kotlin
   val newIds = pins.map { it.item.id }.toSet()
   icons.keys.toList().forEach { if (it !in newIds) icons.remove(it) }
   ```
   Optionally cap the cache size (LRU) at e.g. 500 entries.

3. **Delete `MapViewModel.itemGroups` StateFlow** — dead code eating one O(N) pass per DB emission.

### P1 — meaningful gains at higher counts

4. **Viewport-cull individual markers when zoomed in.** Filter `itemPins` by `visibleBounds` (with 20 % padding to avoid pop-in on gentle pans), and only recompute the filter on camera-idle so panning stays smooth.

5. **Drop the double `Bitmap.createScaledBitmap`** in `buildPolaroidBitmap` — use Coil's output directly (it already respects the requested `size(imgSize, imgSize)`).

6. **Cap bitmap-build concurrency.** Wrap `buildPolaroidBitmap` in a shared `Semaphore(4)` (or route through a small `Channel` producer) so a large sync burst doesn't spawn 500 concurrent decodes.

7. **Preload a shared "placeholder" `BitmapDescriptor`.** Pass it as `icon` while `individualIcons[id]` is still null to eliminate the default-red-pin flash.

### P2 — polish and future-proofing

8. **Read discretized zoom via `derivedStateOf`.**
   ```kotlin
   val zoomInt by remember { derivedStateOf { cameraPositionState.position.zoom.toInt() } }
   ```
   `GoogleMapsContent` then recomposes only when the integer zoom changes, not every animation frame.

9. **Add hysteresis to the cluster/individual toggle** — e.g. individual when zoom > 13.3, cluster when zoom < 12.7. Also gate the transition on `isMoving == false` to avoid flip-flop.

10. **Move category filtering into `MapViewModel`** as a `combine(allItems, selectedCategories)` flow with `flowOn(Dispatchers.Default)`. Consistency with existing `itemPins`/`groupByZoom` flow model.

11. **Persist the icon cache across configuration changes** (screen rotation, theme switch). Hoist the map into a ViewModel-scoped or `rememberSaveable`-backed holder so dark-mode toggle doesn't force a full rebuild.

### P3 — if the app ever grows to 5k+ pins

12. **Add a simple grid-hash spatial index in `MapViewModel`** keyed by `(round(lat/cell), round(lng/cell))`. Viewport queries, grouping, and edge counts become O(k) with k = items per cell.

13. **Consider Google Maps Utils `ClusterManager`.** Loses the polaroid styling but gains battle-tested viewport culling and animated cluster expand. Fair tradeoff at scale; keep custom rendering while collections stay personal-sized.

---

## 4. Expected impact

| Fix | Baseline pain point | Expected result |
|---|---|---|
| P0-1 (edge-count debounce) | 60 Hz N-scan while panning | Zero cost during pan; recomputed only at rest. Biggest single win. |
| P0-2 (icon eviction) | Native memory grows unbounded | Bounded working set; no GPU-texture leak. |
| P0-3 (delete itemGroups flow) | Wasted O(N) per DB emission | One fewer flow, one fewer background compute. |
| P1-4 (viewport cull) | 500 Compose nodes at zoom-in | Only visible markers exist; smoother recomposition. |
| P1-5 (double-scale) | Extra bitmap allocation per pin | ~30 % less allocation churn during marker build. |
| P1-6 (concurrency cap) | 500 concurrent decodes on sync burst | Steady throughput; less GC pressure. |
| P2-8 (derivedStateOf zoom) | Per-frame recomposition of whole map | Recomposition rate matches user-visible state, not animation rate. |

A collection under ~500 items should feel smooth after just P0-1 and P0-2. Beyond that, the P1 set matters. The P3 items are only justified if the app changes scope.
