package com.liquidvolume.module

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeTrackBoundsTest {
    // Match the APK's LayerDrawable > SecRounded(InsetDrawable) > LayerDrawable
    // > GradientDrawable hierarchy and its native mid/high level bounds updates.
    private class NativeProgressGeometry(child: LayerDrawable) : InsetDrawable(child, 0) {
        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            onLevelChange(level)
        }
        override fun onLevelChange(level: Int): Boolean {
            val child = drawable ?: return false
            val b = Rect(child.bounds)
            if (bounds.width() > 0 && level >= b.height() * 10000 / bounds.width()) {
                child.setBounds(bounds.left, b.top,
                    bounds.left + bounds.width() * level / 10000, b.bottom)
            }
            return super.onLevelChange(level)
        }
    }

    @Test fun wideningAnAlreadyLaidOutSquareRetainsNarrowBoundsUntilExplicitRefresh() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val fill = GradientDrawable().apply { setSize(118, 118); setColor(Color.WHITE); cornerRadius = 59f }
        val inner = LayerDrawable(arrayOf(fill)).apply { setId(0, 0x7e0a1056) }
        val nativeProgress = NativeProgressGeometry(inner)
        val background = GradientDrawable().apply { setSize(-1, 118); setColor(Color.TRANSPARENT) }
        val layers = LayerDrawable(arrayOf(background, nativeProgress)).apply {
            setId(0, android.R.id.background); setId(1, android.R.id.progress)
        }
        val seek = SeekBar(context).apply {
            this.background = null
            thumb = null
            minHeight = 118
            maxHeight = 118
            setPadding(0, 0, 0, 0)
            rotation = 270f
            progressDrawable = layers
            max = 10000
            progress = 10000
        }
        parent.addView(seek, FrameLayout.LayoutParams(591, 591, Gravity.CENTER))
        fun layout(width: Int) {
            parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(591, View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, width, 591)
        }
        layout(118)
        assertEquals(118, layers.bounds.height())
        // 0.28 changes thickness after the native View was already laid out.
        seek.minHeight = 191
        seek.maxHeight = 191
        fill.cornerRadius = 95.5f
        seek.layoutParams = FrameLayout.LayoutParams(591, 591, Gravity.CENTER)
        layout(191)
        assertEquals(591, seek.height)
        assertEquals("onSizeChanged did not run for the unchanged square", 118, layers.bounds.height())

        NativeTrackBounds.apply(seek, 191)
        assertSame(layers, seek.progressDrawable)
        assertSame(nativeProgress, layers.findDrawableByLayerId(android.R.id.progress))
        assertEquals(Rect(0, 200, 591, 391), layers.bounds)
        for (level in listOf(3500, 5000, 10000, 5000, 3500)) {
            seek.progress = level
            assertEquals(191, nativeProgress.bounds.height())
            assertEquals(191, fill.bounds.height())
            val image = Bitmap.createBitmap(191, 591, Bitmap.Config.ARGB_8888)
            parent.draw(Canvas(image))
            val y = 591 - (591 * level / 10000 / 2)
            assertTrue("left edge at $level", Color.alpha(image.getPixel(3, y)) > 0)
            assertTrue("right edge at $level", Color.alpha(image.getPixel(187, y)) > 0)
        }
        // Calling again is harmless and does not change the native audio level.
        val level = seek.progress
        NativeTrackBounds.apply(seek, 191)
        assertEquals(level, seek.progress)
    }
}
