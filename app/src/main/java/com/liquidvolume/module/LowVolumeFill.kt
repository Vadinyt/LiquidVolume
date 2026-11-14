package com.liquidvolume.module

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import java.util.WeakHashMap
import kotlin.math.max

/** Only the broken low-level branch of registered compact progress drawables. */
internal object LowVolumeFill {
    private val renderers = WeakHashMap<Drawable, LowVolumeFillRenderer>()
    fun register(drawable: Drawable) { renderers.getOrPut(drawable) { LowVolumeFillRenderer() } }
    fun remove(drawable: Drawable) { renderers.remove(drawable) }
    fun renderer(drawable: Drawable): LowVolumeFillRenderer? = renderers[drawable]
}

internal class LowVolumeFillRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val cachedBounds = Rect()
    private val fill = RectF()
    private var cachedLevel = -1
    private var cachedStart = 0
    private var cachedEnd = 0
    private var radius = 0f

    /** No view changes, invalidation, audio calls or drawable replacement here. */
    fun draw(canvas: Canvas, bounds: Rect, level: Int,
        startColor: Int, endColor: Int, buttonColor: Int): Boolean {
        val width = bounds.width()
        val thickness = bounds.height()
        if (width <= 0 || thickness <= 0 || width <= thickness || level < 0) return false
        val threshold = (thickness.toLong() * 10000 / width).toInt()
        if (threshold <= 0 || level > threshold) return false
        if (level == 0) return true

        if (cachedBounds != bounds || cachedLevel != level ||
            cachedStart != startColor || cachedEnd != endColor) {
            cachedBounds.set(bounds)
            cachedLevel = level
            cachedStart = startColor
            cachedEnd = endColor
            val diameter = thickness.toFloat()
            val extent = diameter * level / threshold
            // Same native horizontal progress coordinates, before View rotation.
            // Samsung's low branch passes (centerY+r, centerY-r) as top/bottom.
            // Use ordered bounds, including offsets, and a nonnegative radius.
            fill.set(bounds.left + extent - diameter, bounds.top.toFloat(),
                bounds.left + extent, bounds.bottom.toFloat())
            radius = max(0f, extent - diameter * 0.5f)
            clip.reset()
            clip.addCircle(bounds.left + diameter * 0.5f, bounds.exactCenterY(),
                diameter * 0.5f, Path.Direction.CW)
            paint.shader = LinearGradient(fill.left, 0f, fill.right, 0f,
                startColor, endColor, Shader.TileMode.CLAMP)
        }
        buttonPaint.color = buttonColor
        val saved = canvas.save()
        try {
            canvas.clipPath(clip)
            canvas.drawRoundRect(fill, radius, radius, paint)
            canvas.drawRoundRect(fill, radius, radius, buttonPaint)
        } finally {
            canvas.restoreToCount(saved)
        }
        return true
    }
}
