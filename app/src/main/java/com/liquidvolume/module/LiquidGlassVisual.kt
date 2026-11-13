package com.liquidvolume.module

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Original Liquid Glass-inspired visual system for the compact vertical slider.
 *
 * Visuals are independent from SystemUI hooks so the design can be completed and
 * reviewed before touching Samsung's native volume lifecycle.
 *
 * The actual SystemUI implementation will use Samsung's background blur behind
 * this translucent skin. This painter does not claim to simulate real refraction.
 */
object LiquidGlassVisual {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, outer: RectF, level: Float, muted: Boolean, density: Float) {
        if (outer.width() <= 0f || outer.height() <= 0f) return
        val r = outer.width() * 0.5f
        val fraction = level.coerceIn(0f, 1f)
        val saved = canvas.save()

        // Optical body: light passes through the capsule. No opaque gray backing.
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            outer.left, outer.top, outer.right, outer.bottom,
            intArrayOf(0x94FFFFFF.toInt(), 0x55EDF5FF, 0x62D9EAFF, 0x86FFFFFF.toInt()),
            floatArrayOf(0f, 0.32f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(outer, r, r, paint)

        val clip = Path().apply { addRoundRect(outer, r, r, Path.Direction.CW) }
        canvas.clipPath(clip)

        // Curved internal reflection, deliberately asymmetrical like polished glass.
        paint.shader = RadialGradient(
            outer.left + outer.width() * 0.13f,
            outer.top + outer.height() * 0.26f,
            outer.height() * 0.66f,
            intArrayOf(0x46FFFFFF, 0x12DDEEFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(outer, r, r, paint)

        // White liquid fill: pronounced top meniscus with a soft translucent body.
        if (fraction > 0f) {
            val usableTop = outer.top + outer.width() * 0.74f
            val usableBottom = outer.bottom - outer.width() * 0.68f
            val fillTop = usableBottom - (usableBottom - usableTop) * fraction
            val fill = RectF(outer.left + density * 3f, fillTop,
                outer.right - density * 3f, outer.bottom - density * 3f)
            val fr = fill.width() * 0.5f
            paint.shader = LinearGradient(
                fill.left, fill.top, fill.right, fill.bottom,
                intArrayOf(0xDDFEFFFF.toInt(), 0xB9F1FBFF.toInt(), 0xBCE2F1FF.toInt()),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(fill, fr, fr, paint)

            // A delicate bright meniscus line is visible above the current level.
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density * 1.2f
            paint.color = 0xA4FFFFFF.toInt()
            canvas.drawRoundRect(fill, fr, fr, paint)
            paint.style = Paint.Style.FILL
        }

        // Slim inner light streak along the left-hand edge.
        paint.shader = LinearGradient(
            outer.left, outer.top, outer.left + outer.width() * 0.27f, outer.top,
            intArrayOf(0xBFFFFFFF.toInt(), 0x36FFFFFF, 0x00FFFFFF),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            RectF(outer.left + density * 1.5f, outer.top + density * 2f,
                outer.left + outer.width() * 0.23f, outer.bottom - density * 2f),
            r, r, paint
        )
        canvas.restoreToCount(saved)

        // Fine specular rim; avoid a heavy outline or second capsule.
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 1.15f
        paint.shader = LinearGradient(
            outer.left, outer.top, outer.right, outer.bottom,
            intArrayOf(0xF0FFFFFF.toInt(), 0x95C7EEFF.toInt(), 0x6CFFFFFF, 0xE3FFFFFF.toInt()),
            null, Shader.TileMode.CLAMP
        )
        val inset = density * 0.8f
        canvas.drawRoundRect(RectF(outer.left + inset, outer.top + inset,
            outer.right - inset, outer.bottom - inset), r, r, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL

        // The menu dots and volume icon remain in the Samsung layout in production.
        // They are rendered here only for a faithful standalone design preview.
        drawMenuDots(canvas, outer, density)
        drawSoundGlyph(canvas, outer, muted, density)
    }

    private fun drawMenuDots(canvas: Canvas, rect: RectF, density: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = 0xD81D2633.toInt()
        val cy = rect.top + rect.width() * 0.54f
        val radius = max(1.6f * density, rect.width() * 0.034f)
        for (dx in listOf(-0.24f, 0f, 0.24f)) {
            canvas.drawCircle(rect.centerX() + dx * rect.width(), cy, radius, paint)
        }
    }

    private fun drawSoundGlyph(canvas: Canvas, rect: RectF, muted: Boolean, density: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = 0xD81D2633.toInt()
        paint.strokeWidth = density * 2.4f
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        val cx = rect.centerX()
        val cy = rect.bottom - rect.width() * 0.58f
        val s = rect.width() * 0.18f
        val glyph = Path().apply {
            moveTo(cx - s * 1.16f, cy - s * 0.44f)
            lineTo(cx - s * 0.58f, cy - s * 0.44f)
            lineTo(cx + s * 0.25f, cy - s * 1.05f)
            lineTo(cx + s * 0.25f, cy + s * 1.05f)
            lineTo(cx - s * 0.58f, cy + s * 0.44f)
            lineTo(cx - s * 1.16f, cy + s * 0.44f)
            close()
        }
        canvas.drawPath(glyph, paint)
        if (muted) {
            canvas.drawLine(cx - s * 1.45f, cy - s * 1.42f,
                cx + s * 1.56f, cy + s * 1.42f, paint)
        } else {
            canvas.drawArc(RectF(cx - s * 0.45f, cy - s,
                cx + s * 1.15f, cy + s), -62f, 124f, false, paint)
            canvas.drawArc(RectF(cx - s * 0.44f, cy - s * 1.38f,
                cx + s * 1.68f, cy + s * 1.38f), -58f, 116f, false, paint)
        }
        paint.style = Paint.Style.FILL
    }
}

/**
 * Reusable translucent artwork for real native slider containers.
 * This drawable intentionally excludes dots and glyphs, so Samsung controls
 * remain on top and receive their normal touch events.
 */
class LiquidGlassShellDrawable : Drawable() {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val r = RectF(bounds)
        if (r.isEmpty) return
        val radius = r.width() * 0.5f
        brush.style = Paint.Style.FILL
        brush.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
            intArrayOf(0x75FFFFFF, 0x32E7F3FF, 0x65FFFFFF), null,
            Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, radius, radius, brush)
        brush.shader = null
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = max(1f, r.width() / 65f)
        brush.color = 0xBDFFFFFF.toInt()
        val inset = brush.strokeWidth * 0.5f
        canvas.drawRoundRect(RectF(r.left + inset, r.top + inset,
            r.right - inset, r.bottom - inset), radius, radius, brush)
        brush.style = Paint.Style.FILL
    }
    override fun setAlpha(alpha: Int) { invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { invalidateSelf() }
    @Deprecated("Deprecated in Android framework")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class LiquidGlassPreviewView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level = 0.38f
    private val density = resources.displayMetrics.density
    private val sliderRect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Liquid Glass volume design preview"
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        brush.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(0xFF0A1425.toInt(), 0xFF172543.toInt(), 0xFF302547.toInt()),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, brush)

        // Rich colors behind the glass to make real translucency visible.
        val circles = listOf(
            Triple(0.26f to 0.38f, 0x987B73FC.toInt(), 0.43f),
            Triple(0.68f to 0.67f, 0x8366D5FF.toInt(), 0.42f),
            Triple(0.43f to 0.88f, 0x7BFF8DAA, 0.39f)
        )
        for ((position, color, radius) in circles) {
            val x = w * position.first
            val y = h * position.second
            val rr = max(w, h) * radius
            brush.shader = RadialGradient(x, y, rr,
                intArrayOf(color, color and 0x00FFFFFF, 0x00000000),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, rr, brush)
        }
        brush.shader = null

        // Compact volume strip matches the narrow native Samsung footprint.
        val pillW = min(81f * density, w * 0.29f)
        val pillH = min(345f * density, h * 0.79f)
        sliderRect.set(w * 0.69f - pillW * 0.5f,
            (h - pillH) * 0.5f,
            w * 0.69f + pillW * 0.5f, (h + pillH) * 0.5f)
        LiquidGlassVisual.draw(canvas, sliderRect, level, level <= 0.01f, density)

        brush.reset()
        brush.isAntiAlias = true
        brush.color = 0xE8FFFFFF.toInt()
        brush.textSize = 21f * density
        brush.isFakeBoldText = true
        canvas.drawText("Liquid", 21f * density, 68f * density, brush)
        canvas.drawText("Glass", 21f * density, 92f * density, brush)
        brush.isFakeBoldText = false
        brush.textSize = 11f * density
        brush.color = 0xB7DBE9F9.toInt()
        canvas.drawText("VOLUME", 21f * density, 119f * density, brush)
        canvas.drawText("COMPACT", 21f * density, 136f * density, brush)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (!sliderRect.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN)
                    return false
                val top = sliderRect.top + sliderRect.width() * 0.74f
                val bottom = sliderRect.bottom - sliderRect.width() * 0.68f
                level = ((bottom - event.y) / max(1f, bottom - top)).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
