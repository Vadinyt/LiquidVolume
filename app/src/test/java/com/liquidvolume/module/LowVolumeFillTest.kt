package com.liquidvolume.module

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LowVolumeFillTest {
    private val bounds = Rect(17, 200, 608, 391)
    private val threshold = bounds.height() * 10000 / bounds.width()
    private fun bitmap() = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
    private fun painted(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 0 }
    }

    @Test fun reproducesSamsungInvertedRectangleFailure() {
        // Exact native RectF argument order in the supplied APK: bottom before top.
        val image = bitmap()
        Canvas(image).drawRoundRect(RectF(0f, 391f, 100f, 200f), 5f, 5f,
            Paint().apply { color = Color.WHITE })
        assertEquals(0, painted(image))
    }

    @Test fun firstNativeMediaStepAndEveryLowLevelDrawVisiblePixels() {
        val renderer = LowVolumeFillRenderer()
        var previous = 0
        // 66/10000 corresponds to media step 1/150; fifth key press is ~3333.
        for (level in listOf(66, 133, 666, 1333, 2000, 2666, threshold)) {
            val image = bitmap()
            val canvas = Canvas(image)
            val layer = canvas.saveLayerAlpha(0f, 0f, 640f, 640f, 158)
            assertTrue(renderer.draw(canvas, bounds, level, 0x66FFFFFF, Color.WHITE, 0))
            canvas.restoreToCount(layer)
            val count = painted(image)
            assertTrue("level=$level has $count pixels, previous=$previous", count > previous)
            previous = count
            // No leak outside the verified drawable's capsule.
            assertEquals(0, image.getPixel(bounds.left - 1, bounds.centerY()))
            assertEquals(0, image.getPixel(bounds.left + bounds.height() + 1, bounds.centerY()))
        }
    }

    @Test fun muteIsEmptyAndUpperRangeFallsThroughUntouched() {
        val renderer = LowVolumeFillRenderer()
        val image = bitmap()
        val canvas = Canvas(image)
        assertTrue(renderer.draw(canvas, bounds, 0, Color.WHITE, Color.WHITE, 0))
        assertFalse(renderer.draw(canvas, bounds, threshold + 1, Color.WHITE, Color.WHITE, 0))
        assertFalse(renderer.draw(canvas, bounds, 10000, Color.WHITE, Color.WHITE, 0))
        assertFalse(renderer.draw(canvas, Rect(), 100, Color.WHITE, Color.WHITE, 0))
        assertEquals(0, painted(image))
    }

    @Test fun endpointMatchesNativeCircleAndCanvasStateIsRestored() {
        val renderer = LowVolumeFillRenderer()
        val image = bitmap()
        val canvas = Canvas(image)
        val clipBefore = canvas.clipBounds
        val savesBefore = canvas.saveCount
        renderer.draw(canvas, bounds, threshold, Color.WHITE, Color.WHITE, 0)
        assertEquals(clipBefore, canvas.clipBounds)
        assertEquals(savesBefore, canvas.saveCount)
        val expected = bitmap()
        Canvas(expected).drawCircle(bounds.left + bounds.height() / 2f, bounds.exactCenterY(),
            bounds.height() / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        // Clip-edge antialiasing may differ slightly; geometry must be the same circle.
        assertEquals(painted(expected).toDouble(), painted(image).toDouble(), 400.0)
        assertEquals(Color.WHITE, image.getPixel(bounds.left + bounds.height() / 2, bounds.centerY()))
    }

    @Test fun descendingLevelsDoNotRetainStaleFillOrBounds() {
        val renderer = LowVolumeFillRenderer()
        for (level in listOf(threshold, 1333, 66, 0)) {
            val reused = bitmap()
            val fresh = bitmap()
            val moved = Rect(bounds).apply { offset(9, -40) }
            renderer.draw(Canvas(reused), moved, level, 0x66FFFFFF, Color.WHITE, 0)
            LowVolumeFillRenderer().draw(Canvas(fresh), moved, level, 0x66FFFFFF, Color.WHITE, 0)
            assertTrue(reused.sameAs(fresh))
        }
    }
}
