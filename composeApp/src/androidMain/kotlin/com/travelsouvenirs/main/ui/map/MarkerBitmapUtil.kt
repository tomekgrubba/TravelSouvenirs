package com.travelsouvenirs.main.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Builds and caches circular photo map markers for individual items, keyed by item id. */
@Composable
fun rememberIndividualIcons(pins: List<ItemPin>, sizePx: Int = 120): Map<Long, BitmapDescriptor> {
    val context = LocalContext.current
    val icons = remember { mutableStateMapOf<Long, BitmapDescriptor>() }
    LaunchedEffect(pins) {
        pins.forEach { pin ->
            if (!icons.containsKey(pin.item.id)) {
                launch {
                    val bmp = buildCircularBitmap(context, pin.item.photoPath, 0, sizePx)
                    if (bmp != null) icons[pin.item.id] = BitmapDescriptorFactory.fromBitmap(bmp)
                }
            }
        }
    }
    return icons
}

/** Builds and caches circular photo map markers for clusters; shows a count badge when group size > 1. */
@Composable
fun rememberGroupIcons(groups: List<ItemGroup>, sizePx: Int = 120): Map<Int, BitmapDescriptor> {
    val context = LocalContext.current
    val icons = remember { mutableStateMapOf<Int, BitmapDescriptor>() }
    LaunchedEffect(groups) {
        icons.clear()
        groups.forEachIndexed { idx, group ->
            launch {
                val count = if (group.items.size > 1) group.items.size else 0
                val bmp = buildCircularBitmap(context, group.items.first().photoPath, count, sizePx)
                if (bmp != null) icons[idx] = BitmapDescriptorFactory.fromBitmap(bmp)
            }
        }
    }
    return icons
}

/** Produces a circular photo Bitmap suitable for any map SDK. Returns null on failure. */
internal suspend fun buildCircularBitmap(
    context: Context,
    photoPath: String,
    count: Int,
    sizePx: Int
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
            color = Color.WHITE
            strokeWidth = borderWidth
        })

        if (count > 1) {
            val br = sizePx * 0.27f
            val bx = sizePx - br + 2f
            val by = br - 2f
            canvas.drawCircle(bx, by, br, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(255, 25, 118, 210)
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
