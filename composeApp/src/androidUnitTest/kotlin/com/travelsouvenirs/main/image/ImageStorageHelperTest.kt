package com.travelsouvenirs.main.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImageStorageHelperTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = File(context.cacheDir, "test_helper").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── rotateForOrientation ─────────────────────────────────────────────────

    @Test
    fun `ROTATE_90 swaps width and height`() {
        val src = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.rotateForOrientation(src, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(400, result.width)
        assertEquals(600, result.height)
    }

    @Test
    fun `ROTATE_270 swaps width and height`() {
        val src = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.rotateForOrientation(src, ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(400, result.width)
        assertEquals(600, result.height)
    }

    @Test
    fun `ROTATE_180 preserves dimensions`() {
        val src = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.rotateForOrientation(src, ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(600, result.width)
        assertEquals(400, result.height)
    }

    @Test
    fun `ORIENTATION_NORMAL returns same instance`() {
        val src = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.rotateForOrientation(src, ExifInterface.ORIENTATION_NORMAL)
        assertTrue(result === src)
    }

    @Test
    fun `unknown orientation returns same instance`() {
        val src = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.rotateForOrientation(src, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue(result === src)
    }

    @Test
    fun `ROTATE_90 on a square bitmap keeps equal dimensions`() {
        val src = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.rotateForOrientation(src, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(300, result.width)
        assertEquals(300, result.height)
    }

    // ── scaledDown ───────────────────────────────────────────────────────────

    @Test
    fun `large landscape bitmap is scaled so longest side equals IMAGE_MAX_SIDE_PX`() {
        val src = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.scaledDown(src)
        assertEquals(IMAGE_MAX_SIDE_PX, result.width)
        assertEquals(800, result.height)
    }

    @Test
    fun `large portrait bitmap is scaled so longest side equals IMAGE_MAX_SIDE_PX`() {
        val src = Bitmap.createBitmap(1600, 2400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.scaledDown(src)
        assertEquals(800, result.width)
        assertEquals(IMAGE_MAX_SIDE_PX, result.height)
    }

    @Test
    fun `bitmap at exactly IMAGE_MAX_SIDE_PX on longest side is returned unchanged`() {
        val src = Bitmap.createBitmap(IMAGE_MAX_SIDE_PX, 800, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.scaledDown(src)
        assertTrue(result === src)
    }

    @Test
    fun `bitmap smaller than IMAGE_MAX_SIDE_PX is returned unchanged`() {
        val src = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.scaledDown(src)
        assertTrue(result === src)
    }

    @Test
    fun `aspect ratio is preserved when scaling down`() {
        val src = Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.scaledDown(src)
        assertEquals(3.0, result.width.toDouble() / result.height.toDouble(), 0.01)
    }

    @Test
    fun `large square bitmap is scaled to IMAGE_MAX_SIDE_PX on both sides`() {
        val src = Bitmap.createBitmap(2400, 2400, Bitmap.Config.ARGB_8888)
        val result = ImageStorageHelper.scaledDown(src)
        assertEquals(IMAGE_MAX_SIDE_PX, result.width)
        assertEquals(IMAGE_MAX_SIDE_PX, result.height)
    }

    // ── copyToInternalStorage ────────────────────────────────────────────────

    @Test
    fun `copyToInternalStorage returns null for a non-existent file URI`() {
        val result = ImageStorageHelper.copyToInternalStorage(
            context,
            Uri.parse("file:///nonexistent/path/photo.jpg")
        )
        assertNull(result)
    }

    // ── clearTempFiles ───────────────────────────────────────────────────────

    @Test
    fun `clearTempFiles removes all files from the temp directory`() {
        val dir = File(context.cacheDir, "temp_photos").also { it.mkdirs() }
        File(dir, "a.jpg").createNewFile()
        File(dir, "b.jpg").createNewFile()
        assertTrue(dir.listFiles()!!.isNotEmpty())

        ImageStorageHelper.clearTempFiles(context)

        assertTrue(!dir.exists() || dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `clearTempFiles is a no-op when temp directory does not exist`() {
        File(context.cacheDir, "temp_photos").deleteRecursively()
        ImageStorageHelper.clearTempFiles(context)
    }
}
