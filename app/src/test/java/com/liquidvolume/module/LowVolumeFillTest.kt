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

    private fun nativeLow(canvas: Canvas, nativeBounds: Rect, level: Int) {
        val width = nativeBounds.width().toFloat()
        val thickness = nativeBounds.height().toFloat()
        val limit = (thickness * 10000 / width).toInt()
        val extent = thickness * level / limit
        val radius = extent - thickness / 2
        val nativeClip = android.graphics.Path().apply {
            addCircle(thickness / 2, width / 2, thickness / 2, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(nativeClip)
        canvas.drawRoundRect(RectF(extent - thickness, width / 2 + thickness / 2,
            extent, width / 2 - thickness / 2), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    @Test fun originalSamsungLowBranchWorksOnSquareCanvasAndFailsOnOldRectangle() {
        for (level in listOf(66, 133, 666, 1333, 2666, threshold)) {
            val broken = Bitmap.createBitmap(591, 191, Bitmap.Config.ARGB_8888)
            nativeLow(Canvas(broken), Rect(0, 0, 591, 191), level)
            assertEquals("rectangular canvas at $level", 0, painted(broken))
            val fixed = Bitmap.createBitmap(191, 591, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fixed)
            // Exact FrameLayout CENTER placement and View rotation from Samsung XML.
            canvas.translate(-200f, 0f)
            canvas.rotate(270f, 295.5f, 295.5f)
            nativeLow(canvas, Rect(0, 200, 591, 391), level)
            assertTrue("square canvas at $level", painted(fixed) > 0)
            assertEquals(0, fixed.getPixel(95, 0))
        }
    }

    @Test fun squareSeekBarKeepsApprovedTrackThicknessAndCenter() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val parent = android.widget.FrameLayout(context)
        val seek = android.widget.SeekBar(context).apply {
            background = null
            thumb = null
            minHeight = 191
            maxHeight = 191
            setPadding(0, 0, 0, 0)
            rotation = 270f
            progressDrawable = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE) }
        }
        parent.addView(seek, android.widget.FrameLayout.LayoutParams(591, 591, android.view.Gravity.CENTER))
        parent.measure(android.view.View.MeasureSpec.makeMeasureSpec(191, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(591, android.view.View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 191, 591)
        assertEquals(591, seek.width)
        assertEquals(591, seek.height)
        assertEquals(-200, seek.left)
        assertEquals(0, seek.top)
        assertEquals(Rect(0, 200, 591, 391), seek.progressDrawable.bounds)
    }

    @Test fun firstNativeMediaStepAndEveryLowLevelDrawVisiblePixels() {
        val renderer = PreviewFillRenderer()
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
        val renderer = PreviewFillRenderer()
        val image = bitmap()
        val canvas = Canvas(image)
        assertTrue(renderer.draw(canvas, bounds, 0, Color.WHITE, Color.WHITE, 0))
        assertFalse(renderer.draw(canvas, bounds, threshold + 1, Color.WHITE, Color.WHITE, 0))
        assertFalse(renderer.draw(canvas, bounds, 10000, Color.WHITE, Color.WHITE, 0))
        assertFalse(renderer.draw(canvas, Rect(), 100, Color.WHITE, Color.WHITE, 0))
        assertEquals(0, painted(image))
    }

    @Test fun endpointMatchesNativeCircleAndCanvasStateIsRestored() {
        val renderer = PreviewFillRenderer()
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
        val renderer = PreviewFillRenderer()
        for (level in listOf(threshold, 1333, 66, 0)) {
            val reused = bitmap()
            val fresh = bitmap()
            val moved = Rect(bounds).apply { offset(9, -40) }
            renderer.draw(Canvas(reused), moved, level, 0x66FFFFFF, Color.WHITE, 0)
            PreviewFillRenderer().draw(Canvas(fresh), moved, level, 0x66FFFFFF, Color.WHITE, 0)
            assertTrue(reused.sameAs(fresh))
        }
    }
}
