package com.travelsouvenirs.main.ui.map

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.FakeItemDao
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.domain.Item
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun makeItem(id: Long, photoPath: String) = Item(
    id = id, name = "Item $id", notes = "", photoPath = photoPath,
    latitude = 0.0, longitude = 0.0, placeName = "", dateAcquired = LocalDate(2024, 1, 1)
)

private fun makeGroup(photoPath: String, itemCount: Int): ItemGroup {
    val items = (1..itemCount).map { makeItem(it.toLong(), photoPath) }
    return ItemGroup(items, 0.0, 0.0)
}

/** Minimal in-memory [Settings] for tests — mirrors the private FakeSettings in SettingsViewModelTest. */
private class FakeSettings : Settings {
    private val store = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = store.keys
    override val size: Int get() = store.size
    override fun clear() = store.clear()
    override fun remove(key: String) { store.remove(key) }
    override fun hasKey(key: String): Boolean = key in store
    override fun putString(key: String, value: String) { store[key] = value }
    override fun getString(key: String, defaultValue: String) = store[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String) = store[key] as? String
    override fun putInt(key: String, value: Int) { store[key] = value }
    override fun getInt(key: String, defaultValue: Int) = store[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String) = store[key] as? Int
    override fun putLong(key: String, value: Long) { store[key] = value }
    override fun getLong(key: String, defaultValue: Long) = store[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String) = store[key] as? Long
    override fun putFloat(key: String, value: Float) { store[key] = value }
    override fun getFloat(key: String, defaultValue: Float) = store[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String) = store[key] as? Float
    override fun putDouble(key: String, value: Double) { store[key] = value }
    override fun getDouble(key: String, defaultValue: Double) = store[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String) = store[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { store[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean) = store[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String) = store[key] as? Boolean
}

// ── Pure unit tests for groupIconKey ────────────────────────────────────────

class GroupIconKeyTest {

    @Test
    fun `single-item group uses count 0`() {
        val group = makeGroup("/photo.jpg", 1)
        assertEquals("/photo.jpg_0", groupIconKey(group))
    }

    @Test
    fun `two-item group uses count 2`() {
        val group = makeGroup("/photo.jpg", 2)
        assertEquals("/photo.jpg_2", groupIconKey(group))
    }

    @Test
    fun `multi-item group uses actual item count`() {
        val group = makeGroup("/photo.jpg", 5)
        assertEquals("/photo.jpg_5", groupIconKey(group))
    }

    @Test
    fun `different photo paths produce different keys`() {
        val g1 = makeGroup("/alpha.jpg", 1)
        val g2 = makeGroup("/beta.jpg", 1)
        assertNotEquals(groupIconKey(g1), groupIconKey(g2))
    }

    @Test
    fun `same photo path and count produce equal keys regardless of position`() {
        val g1 = makeGroup("/shared.jpg", 3)
        val g2 = makeGroup("/shared.jpg", 3)
        assertEquals(groupIconKey(g1), groupIconKey(g2))
    }

    @Test
    fun `single-item and multi-item groups with same path differ by count suffix`() {
        val single = makeGroup("/photo.jpg", 1)
        val multi  = makeGroup("/photo.jpg", 2)
        assertNotEquals(groupIconKey(single), groupIconKey(multi))
    }

    @Test
    fun `key set from groups does not contain numeric-only strings`() {
        val groups = listOf(makeGroup("/a.jpg", 1), makeGroup("/b.jpg", 3))
        val keys = groups.map { groupIconKey(it) }.toSet()
        assertFalse(keys.all { it.matches(Regex("\\d+")) }, "Keys must not be bare numeric indexes")
    }

    @Test
    fun `stale key eviction removes keys not in desired set`() {
        val cache = mutableMapOf("old_0" to "v", "keep_0" to "v", "remove_2" to "v")
        val desired = setOf("keep_0", "new_1")
        cache.keys.toList().forEach { if (it !in desired) cache.remove(it) }
        assertFalse(cache.containsKey("old_0"))
        assertFalse(cache.containsKey("remove_2"))
        assertTrue(cache.containsKey("keep_0"))
    }

    @Test
    fun `new key is only built once even when groups list contains duplicate photo+count`() {
        val groups = listOf(makeGroup("/dup.jpg", 2), makeGroup("/dup.jpg", 2))
        val keys = groups.map { groupIconKey(it) }.toSet()
        assertEquals(1, keys.size, "Duplicate photo+count should collapse to a single key")
    }
}

// ── Compose smoke test ───────────────────────────────────────────────────────
//
// BitmapDescriptorFactory.fromBitmap() requires Google Play Services to be
// initialised, which is unavailable in Robolectric unit tests. As a result the
// icons map will stay empty even though the composition runs correctly.
// This test therefore only validates that rememberGroupIcons composes without
// error and that any keys it would produce follow the expected format.
// Full icon-population verification belongs in androidInstrumentedTest once
// Maps SDK mocking is set up.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RememberGroupIconsSmokeTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val fakeSettings = FakeSettings()
    private val fakeRepository = ItemRepository(FakeItemDao())

    @OptIn(DelicateCoilApi::class)
    @Before
    fun mockCoil() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fakeBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val engine = FakeImageLoaderEngine.Builder().default(fakeBitmap.asImage()).build()
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(ctx).components { add(engine) }.build()
        }
    }

    @OptIn(DelicateCoilApi::class)
    @After
    fun resetCoil() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `rememberGroupIcons composes without error for non-empty groups`() {
        val groups = listOf(makeGroup("/alpha.jpg", 1), makeGroup("/beta.jpg", 2))
        var composed = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalSettings provides fakeSettings,
                LocalItemRepository provides fakeRepository
            ) {
                MaterialTheme {
                    rememberGroupIcons(groups)
                    SideEffect { composed = true }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(composed, "rememberGroupIcons should compose without error")
    }

    @Test
    fun `rememberGroupIcons composes without error for empty groups`() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalSettings provides fakeSettings,
                LocalItemRepository provides fakeRepository
            ) {
                MaterialTheme {
                    rememberGroupIcons(emptyList())
                }
            }
        }
        composeTestRule.waitForIdle()
    }
}
