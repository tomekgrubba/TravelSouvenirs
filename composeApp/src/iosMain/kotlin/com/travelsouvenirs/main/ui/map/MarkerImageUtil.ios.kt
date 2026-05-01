package com.travelsouvenirs.main.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGContextAddEllipseInRect
import platform.CoreGraphics.CGContextClip
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectInset
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes

/** Builds and caches circular photo map markers for individual items, keyed by item id. */
@Composable
fun rememberIndividualIosIcons(pins: List<ItemPin>, sizePx: Int = 120): Map<Long, UIImage> {
    val borderColor = MaterialTheme.colorScheme.onPrimaryContainer
    val icons = remember { mutableStateMapOf<Long, UIImage>() }
    LaunchedEffect(pins, borderColor) {
        pins.forEach { pin ->
            if (!icons.containsKey(pin.item.id)) {
                launch {
                    val img = withContext(Dispatchers.Default) {
                        buildCircularUIImage(pin.item.photoPath, 0, sizePx, borderColor = borderColor)
                    }
                    if (img != null) icons[pin.item.id] = img
                }
            }
        }
    }
    return icons
}

/** Builds and caches circular photo map markers for clusters; shows a count badge when group size > 1. */
@Composable
fun rememberGroupIosIcons(groups: List<ItemGroup>, sizePx: Int = 120): Map<Int, UIImage> {
    val badgeColor  = MaterialTheme.colorScheme.primaryContainer
    val borderColor = MaterialTheme.colorScheme.onPrimaryContainer
    val icons = remember { mutableStateMapOf<Int, UIImage>() }
    LaunchedEffect(groups, badgeColor, borderColor) {
        icons.clear()
        groups.forEachIndexed { idx, group ->
            launch {
                val count = if (group.items.size > 1) group.items.size else 0
                val img = withContext(Dispatchers.Default) {
                    buildCircularUIImage(group.items.first().photoPath, count, sizePx, badgeColor, borderColor)
                }
                if (img != null) icons[idx] = img
            }
        }
    }
    return icons
}

/** Produces a circular photo UIImage suitable for MKAnnotationView. Returns null on failure. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun buildCircularUIImage(
    photoPath: String,
    count: Int,
    sizePx: Int,
    badgeColor: Color? = null,
    borderColor: Color = Color.White
): UIImage? = withContext(Dispatchers.Default) {
    if (photoPath.isBlank()) return@withContext null
    try {
        val source = UIImage.imageWithContentsOfFile(photoPath) ?: return@withContext null
        val sz = sizePx.toDouble()
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(sz, sz), false, 0.0)
        val ctx = UIGraphicsGetCurrentContext()
            ?: run { UIGraphicsEndImageContext(); return@withContext null }
        val rect = CGRectMake(0.0, 0.0, sz, sz)

        // Draw photo clipped to circle
        CGContextSaveGState(ctx)
        CGContextAddEllipseInRect(ctx, rect)
        CGContextClip(ctx)
        source.drawInRect(rect)
        CGContextRestoreGState(ctx)

        // White border ring — same proportions as Android: 5.5% of size, min 3px
        val borderWidth = (sz * 0.055).coerceAtLeast(3.0)
        CGContextSetStrokeColorWithColor(ctx, uiColor(borderColor).CGColor)
        CGContextSetLineWidth(ctx, borderWidth)
        CGContextAddEllipseInRect(ctx, CGRectInset(rect, borderWidth / 2.0, borderWidth / 2.0))
        CGContextStrokePath(ctx)

        // Count badge — same geometry as Android: br=27%, bx=sz-br+2, by=br-2
        if (count > 1) {
            val br = sz * 0.27
            val bx = sz - br + 2.0
            val by = br - 2.0
            val badgeRect = CGRectMake(bx - br, by - br, br * 2.0, br * 2.0)

            CGContextSetFillColorWithColor(ctx, uiColor(badgeColor ?: Color(0xFF1976D2)).CGColor)
            CGContextAddEllipseInRect(ctx, badgeRect)
            CGContextFillPath(ctx)

            CGContextSetStrokeColorWithColor(ctx, UIColor.whiteColor.CGColor)
            CGContextSetLineWidth(ctx, 3.0)
            CGContextAddEllipseInRect(ctx, CGRectInset(badgeRect, 1.5, 1.5))
            CGContextStrokePath(ctx)

            val font = UIFont.boldSystemFontOfSize(br * 1.15)
            @Suppress("UNCHECKED_CAST")
            val attrs = mapOf(
                NSFontAttributeName to font,
                NSForegroundColorAttributeName to UIColor.whiteColor
            ) as Map<Any?, *>
            val nsLabel = count.toString() as NSString
            nsLabel.sizeWithAttributes(attrs).useContents {
                nsLabel.drawAtPoint(
                    CGPointMake(bx - width / 2.0, by - height / 2.0),
                    withAttributes = attrs
                )
            }
        }

        val result = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        result
    } catch (_: Exception) {
        null
    }
}

/** Produces a circular-clipped JPEG base64 data URL for Leaflet divIcon use. Returns null on failure. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun buildCircularDataUrl(photoPath: String, sizePx: Int = 80): String? =
    withContext(Dispatchers.Default) {
        if (photoPath.isBlank()) return@withContext null
        try {
            val source = UIImage.imageWithContentsOfFile(photoPath) ?: return@withContext null
            val sz = sizePx.toDouble()
            UIGraphicsBeginImageContextWithOptions(CGSizeMake(sz, sz), false, 1.0)
            val ctx = UIGraphicsGetCurrentContext()
                ?: run { UIGraphicsEndImageContext(); return@withContext null }
            val rect = CGRectMake(0.0, 0.0, sz, sz)
            CGContextSaveGState(ctx)
            CGContextAddEllipseInRect(ctx, rect)
            CGContextClip(ctx)
            source.drawInRect(rect)
            CGContextRestoreGState(ctx)
            val clipped = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            clipped ?: return@withContext null
            val jpegData = UIImageJPEGRepresentation(clipped, 0.75) ?: return@withContext null
            val b64 = jpegData.base64EncodedStringWithOptions(0u)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }

private fun uiColor(color: Color): UIColor = UIColor(
    red   = color.red.toDouble(),
    green = color.green.toDouble(),
    blue  = color.blue.toDouble(),
    alpha = color.alpha.toDouble()
)
