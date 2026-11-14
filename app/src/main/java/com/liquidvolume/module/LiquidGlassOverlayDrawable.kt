package com.liquidvolume.module

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * Front-facing frosted-glass optical layer.
 *
 * This deliberately draws in a ViewOverlay ABOVE Samsung's opaque VolumeSeekBar
 * while leaving the underlying seekbar, volume callbacks and icon views intact.
 * No touch handling, new windows, ViewTreeObserver hooks or frame callbacks.
 */
internal class LiquidGlassOverlayDrawable : Drawable() {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val strokeRect = RectF()
    private val topHighlight = RectF()
    private val clip = Path()
    private var pearl: Shader? = null
    private var depth: Shader? = null
    private var edge: Shader? = null
    private var overallAlpha = 255
    private var filter: ColorFilter? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
        clip.reset()
        if (rect.isEmpty) return
        val r = rect.width() / 2f
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        val stroke = max(1f, rect.width() * 0.015f)
        strokeRect.set(rect.left + stroke, rect.top + stroke,
            rect.right - stroke, rect.bottom - stroke)
        topHighlight.set(rect.left + r * 0.11f, rect.top + r * 0.11f,
            rect.right - r * 0.11f, rect.top + 2f * r - r * 0.11f)

        // Alpha is sufficiently visible EVEN when Samsung draws its own opaque
        // gray ProgressBar behind this view. Never turn it into a solid panel.
        pearl = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                0x68F9FDFF, 0x3AE2F4FF, 0x32BBDDF5, 0x58FFFFFF
            ),
            floatArrayOf(0f, 0.32f, 0.71f, 1f),
            Shader.TileMode.CLAMP
        )
        depth = RadialGradient(
            rect.left + rect.width() * 0.17f,
            rect.top + rect.height() * 0.19f,
            max(rect.width(), rect.height() * 0.48f),
            intArrayOf(0x2FFFFFFF, 0x0CCEE9FF, 0x00FFFFFF),
            floatArrayOf(0f, 0.51f, 1f),
            Shader.TileMode.CLAMP
        )
        edge = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                0xE8FFFFFF.toInt(), 0xA9ECF9FF.toInt(),
                0x66BCE3FF, 0xC6FFFFFF.toInt()
            ),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val r = rect.width() / 2f
        val saved = canvas.save()
        canvas.clipPath(clip)

        brush.reset()
        brush.isAntiAlias = true
        brush.alpha = overallAlpha
        brush.colorFilter = filter
        brush.style = Paint.Style.FILL
        brush.shader = pearl
        canvas.drawRoundRect(rect, r, r, brush)
        brush.shader = depth
        canvas.drawRoundRect(rect, r, r, brush)
        canvas.restoreToCount(saved)

        brush.reset()
        brush.isAntiAlias = true
        brush.alpha = overallAlpha
        brush.colorFilter = filter
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = max(1.2f, rect.width() * 0.014f)
        brush.shader = edge
        canvas.drawRoundRect(strokeRect, r, r, brush)

        // Short top reflection. No horizontal white line, and no long
        // vertical arc like the earlier broken previews.
        brush.shader = null
        brush.color = 0x98FFFFFF.toInt()
        brush.strokeWidth = max(0.8f, rect.width() * 0.009f)
        canvas.drawArc(topHighlight, 208f, 119f, false, brush)
        brush.style = Paint.Style.FILL
    }

    override fun setAlpha(alpha: Int) {
        overallAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        filter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android framework")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
