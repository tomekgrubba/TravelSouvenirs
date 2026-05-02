package com.travelsouvenirs.main.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.travelsouvenirs.main.platform.rememberAppStyle
import com.travelsouvenirs.main.theme.AppStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Returns the cache key for [group]: `"<photoPath>_<count>"` where count is 0 for single-item groups. */
internal fun groupIconKey(group: ItemGroup): String {
    val count = if (group.items.size > 1) group.items.size else 0
    return "${group.items.first().photoPath}_$count"
}

/** Builds and caches photo map markers for individual items, keyed by item id. */
@Composable
fun rememberIndividualIcons(pins: List<ItemPin>, sizePx: Int = 120): Map<Long, BitmapDescriptor> {
    val context = LocalContext.current
    val borderColor = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    val isPolaroid = rememberAppStyle() == AppStyle.POLAROID
    val icons = remember(isPolaroid) { mutableStateMapOf<Long, BitmapDescriptor>() }
    LaunchedEffect(pins, borderColor, isPolaroid) {
        pins.forEach { pin ->
            if (!icons.containsKey(pin.item.id)) {
                launch {
                    val bmp = if (isPolaroid)
                        buildPolaroidBitmap(context, pin.item.photoPath, 0, sizePx)
                    else
                        buildCircularBitmap(context, pin.item.photoPath, 0, sizePx, borderColor = borderColor)
                    if (bmp != null) icons[pin.item.id] = BitmapDescriptorFactory.fromBitmap(bmp)
                }
            }
        }
    }
    return icons
}

/** Builds and caches photo map markers for clusters; shows a count badge when group size > 1. */
@Composable
fun rememberGroupIcons(groups: List<ItemGroup>, sizePx: Int = 120): Map<String, BitmapDescriptor> {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val borderColor = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    val isPolaroid = rememberAppStyle() == AppStyle.POLAROID
    val icons = remember(isPolaroid) { mutableStateMapOf<String, BitmapDescriptor>() }
    LaunchedEffect(groups, primaryColor, borderColor, isPolaroid) {
        val newKeys = groups.map { groupIconKey(it) }.toSet()
        icons.keys.toList().forEach { if (it !in newKeys) icons.remove(it) }
        groups.forEach { group ->
            val key = groupIconKey(group)
            val count = if (group.items.size > 1) group.items.size else 0
            if (!icons.containsKey(key)) {
                launch {
                    val bmp = if (isPolaroid)
                        buildPolaroidBitmap(context, group.items.first().photoPath, count, sizePx, primaryColor)
                    else
                        buildCircularBitmap(context, group.items.first().photoPath, count, sizePx, primaryColor, borderColor)
                    if (bmp != null) icons[key] = BitmapDescriptorFactory.fromBitmap(bmp)
                }
            }
        }
    }
    return icons
}

/** Produces a polaroid-shaped photo Bitmap: white frame with thick bottom strip. Returns null on failure. */
internal suspend fun buildPolaroidBitmap(
    context: Context,
    photoPath: String,
    count: Int,
    sizePx: Int,
    badgeColor: Int? = null,
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context).data(photoPath).size(sizePx, sizePx).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return@withContext null
        val decoded = try { result.image.toBitmap() } catch (_: Exception) { return@withContext null }
        val source = if (decoded.config == Bitmap.Config.HARDWARE)
            decoded.copy(Bitmap.Config.ARGB_8888, false) else decoded

        val border = (sizePx * 0.10f).coerceAtLeast(5f).toInt()
        val bottomStrip = (sizePx * 0.30f).toInt()
        val imgSize = sizePx - 2 * border
        val totalH = border + imgSize + bottomStrip

        // Badge geometry: straddles the top-right corner, overflowing by br*0.5 on right and top.
        // Expand canvas and shift polaroid content inward so the badge is fully visible.
        val br = sizePx * 0.24f
        val overflow = if (count > 1) (br * 0.5f + 1f).toInt() else 0
        val yOff = overflow

        val out = Bitmap.createBitmap(sizePx + overflow, totalH + yOff, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawRoundRect(RectF(0f, yOff.toFloat(), sizePx.toFloat(), (totalH + yOff).toFloat()), 6f, 6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        canvas.drawBitmap(Bitmap.createScaledBitmap(source, imgSize, imgSize, true),
            border.toFloat(), (border + yOff).toFloat(), null)

        canvas.drawRoundRect(RectF(1f, yOff + 1f, sizePx - 1f, totalH + yOff - 1f), 6f, 6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.argb(70, 0, 0, 0)
                strokeWidth = 2f
            })

        if (count > 1) {
            val bx = sizePx - br * 0.5f
            val by = yOff + br * 0.5f
            canvas.drawCircle(bx, by, br, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeColor ?: Color.argb(255, 25, 118, 210)
            })
            canvas.drawCircle(bx, by, br - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 3f
            })
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = br * 1.1f
                textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(count.toString(), bx, by - (tp.descent() + tp.ascent()) / 2, tp)
        }
        out
    } catch (_: Exception) { null }
}

/** Produces a circular photo Bitmap suitable for any map SDK. Returns null on failure. */
internal suspend fun buildCircularBitmap(
    context: Context,
    photoPath: String,
    count: Int,
    sizePx: Int,
    badgeColor: Int? = null,
    borderColor: Int = Color.WHITE
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context)
            .data(photoPath)
            .size(sizePx, sizePx)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return@withContext null
        val decoded = try { result.image.toBitmap() } catch (_: Exception) { return@withContext null }
        val source = if (decoded.config == Bitmap.Config.HARDWARE) {
            decoded.copy(Bitmap.Config.ARGB_8888, false)
        } else decoded

        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val r = sizePx / 2f

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(
                Bitmap.createScaledBitmap(source, sizePx, sizePx, true),
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(r, r, r, photoPaint)

        val borderWidth = (sizePx * 0.055f).coerceAtLeast(3f)
        canvas.drawCircle(r, r, r - borderWidth / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = borderWidth
        })

        if (count > 1) {
            val br = sizePx * 0.27f
            val bx = sizePx - br + 2f
            val by = br - 2f
            canvas.drawCircle(bx, by, br, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeColor ?: Color.argb(255, 25, 118, 210)
            })
            canvas.drawCircle(bx, by, br - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeWidth = 3f
            })
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = br * 1.15f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(count.toString(), bx, by - (tp.descent() + tp.ascent()) / 2, tp)
        }

        out
    } catch (_: Exception) {
        null
    }
}
