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
import android.graphics.Rect
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
    private val speaker = MediaSpeakerSkin.SpeakerDrawable()

    fun drawControls(canvas: Canvas, rect: RectF, muted: Boolean, density: Float) {
        paint.color = 0xA8FFFFFF.toInt()
        val y = rect.top + rect.width() * 0.27f
        val radius = max(1.4f * density, rect.width() * 0.025f)
        for (offset in floatArrayOf(-0.10f, 0f, 0.10f)) {
            canvas.drawCircle(rect.centerX() + offset * rect.width(), y, radius, paint)
        }
        val size = rect.width() * 0.38f
        val cy = rect.bottom - rect.width() * 0.32f
        speaker.muted = muted
        speaker.setBounds((rect.centerX() - size / 2).toInt(), (cy - size / 2).toInt(),
            (rect.centerX() + size / 2).toInt(), (cy + size / 2).toInt())
        speaker.draw(canvas)
    }
}

/**
 * Reusable translucent artwork for real native slider containers.
 * This drawable intentionally excludes dots and glyphs, so Samsung controls
 * remain on top and receive their normal touch events.
 */
/**
 * Neutral glass capsule, no draw-over on native Samsung icons or text.
 * Only the original narrow track's background uses this painter.
 */
class LiquidGlassShellDrawable : Drawable() {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clipping = Path()
    private var backgroundShader: Shader? = null
    private var depthShader: Shader? = null
    private var overallAlpha = 255
    private var filter: ColorFilter? = null

    // Cache shaders/clip once when Samsung lays out the native capsule.
    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
        clipping.reset()
        if (rect.isEmpty) {
            backgroundShader = null
            depthShader = null
            return
        }
        val radius = rect.width() * 0.5f
        clipping.addRoundRect(rect, radius, radius, Path.Direction.CW)
        backgroundShader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(0x25FFFFFF, 0x18000000, 0x12000000, 0x30FFFFFF),
            floatArrayOf(0f, 0.30f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
        depthShader = RadialGradient(
            rect.left + rect.width() * 0.14f,
            rect.top + rect.height() * 0.30f,
            max(rect.width(), rect.height() * 0.60f),
            intArrayOf(0x19FFFFFF, 0x08FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.44f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val saved = canvas.save()
        canvas.clipPath(clipping)
        brush.reset()
        brush.isAntiAlias = true
        brush.alpha = overallAlpha
        brush.colorFilter = filter
        val radius = rect.width() * 0.5f
        brush.shader = backgroundShader
        canvas.drawRoundRect(rect, radius, radius, brush)
        brush.shader = depthShader
        canvas.drawRoundRect(rect, radius, radius, brush)
        brush.shader = null
        canvas.restoreToCount(saved)
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

/**
 * Rim lights do not cover the actual native volume progress or audio icons.
 * The dynamic meniscus reads Samsung's progress but never writes to it.
 */
/**
 * Glass rim: a single capsule perimeter with brighter ends.
 * No horizontal meniscus line or vertical full-height arc.
 */
class LiquidGlassRimDrawable : Drawable() {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rimRect = RectF()
    private var rimShader: Shader? = null
    private var overallAlpha = 255
    private var filter: ColorFilter? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
        if (rect.isEmpty) {
            rimShader = null
            return
        }
        val stroke = max(1.0f, rect.width() * 0.010f)
        rimRect.set(rect.left + stroke * 0.5f, rect.top + stroke * 0.5f,
            rect.right - stroke * 0.5f, rect.bottom - stroke * 0.5f)
        rimShader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(
                0xB8FFFFFF.toInt(), 0x60000000, 0x50000000, 0xD0FFFFFF.toInt()
            ),
            floatArrayOf(0f, 0.17f, 0.80f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        val radius = rect.width() * 0.5f
        brush.reset()
        brush.isAntiAlias = true
        brush.alpha = overallAlpha
        brush.colorFilter = filter
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = max(1.0f, rect.width() * 0.010f)
        brush.shader = rimShader
        canvas.drawRoundRect(rimRect, radius, radius, brush)

        // One perimeter only. Specular light comes from the perimeter gradient,
        // never from an inset arc that reads as a second capsule.
        brush.shader = null
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

class LiquidGlassPreviewView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var showDarkBackdrop: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Keep the original large iOS design as the default preview. */
    var useNativeDimensions: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    var onLevelChanged: ((Int) -> Unit)? = null
    var previewStep: Int = 0
        private set
    private val level get() = previewStep / 150f
    fun setPreviewStep(step: Int) {
        previewStep = step.coerceIn(0, 150)
        contentDescription = "Aperçu du volume : ${kotlin.math.round(level * 100).toInt()} pour cent"
        onLevelChanged?.invoke(previewStep)
        invalidate()
    }
    private val lowFill = PreviewFillRenderer()
    private val horizontalBounds = Rect()
    private val density = resources.displayMetrics.density
    private val sliderRect = RectF()
    private val shell = LiquidGlassShellDrawable()
    private val rim = LiquidGlassRimDrawable()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var backdropShader: Shader? = null
    private val fillRect = RectF()
    private var fillShader: Shader? = null
    private var lastFillTop = Float.NaN
    private var lastFillBottom = Float.NaN

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backdropShader = if (w > 0 && h > 0) LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF0A1425.toInt(), 0xFF172543.toInt(), 0xFF302547.toInt()),
            null, Shader.TileMode.CLAMP
        ) else null
    }

    init {
        contentDescription = "Liquid Glass volume design preview"
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        brush.reset()
        brush.isAntiAlias = true
        if (showDarkBackdrop) {
            // Real-life comparison: this is close to the black app backgrounds
            // visible behind the actual Samsung SystemUI compact slider.
            brush.shader = null
            brush.color = 0xFF080B11.toInt()
            canvas.drawRect(0f, 0f, w, h, brush)
        } else {
            brush.shader = backdropShader
            canvas.drawRect(0f, 0f, w, h, brush)

            // Rich colors behind the glass show the color shifts in context.
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
        }

        // Deliberately enlarged, elegant design preview as in the original 0.17.
        // Samsung's real compact panel remains at its actual smaller native size.
        val pillW = min(
            (if (useNativeDimensions) 68f else 100f) * density,
            w * (if (useNativeDimensions) 0.25f else 0.34f)
        )
        val pillH = min(pillW * 3.1f, h * 0.79f)
        sliderRect.set(w * 0.69f - pillW * 0.5f,
            (h - pillH) * 0.5f,
            w * 0.69f + pillW * 0.5f, (h + pillH) * 0.5f)
        // Preview and SystemUI now use exactly the same shell and rim drawables.
        // Only the progress body is simulated here, Samsung owns it in SystemUI.
        shell.setBounds(
            sliderRect.left.toInt(), sliderRect.top.toInt(),
            sliderRect.right.toInt(), sliderRect.bottom.toInt()
        )
        shell.draw(canvas)

        if (level > 0f) {
            val saved = canvas.save()
            canvas.translate(sliderRect.left, sliderRect.bottom)
            canvas.rotate(-90f)
            horizontalBounds.set(0, 0, sliderRect.height().toInt(), sliderRect.width().toInt())
            val alphaLayer = canvas.saveLayerAlpha(0f, 0f, sliderRect.height(), sliderRect.width(), 158)
            if (!lowFill.draw(canvas, horizontalBounds, (level * 10000).toInt(),
                0x66FFFFFF, Color.WHITE, Color.TRANSPARENT)) {
                fillRect.set(0f, 0f, horizontalBounds.width() * level, horizontalBounds.height().toFloat())
                if (fillRect.right != lastFillTop || fillRect.bottom != lastFillBottom) {
                    fillShader = LinearGradient(0f, 0f, fillRect.right, 0f,
                        0x66FFFFFF, Color.WHITE, Shader.TileMode.CLAMP)
                    lastFillTop = fillRect.right
                    lastFillBottom = fillRect.bottom
                }
                fillPaint.shader = fillShader
                canvas.drawRoundRect(fillRect, fillRect.height() / 2f, fillRect.height() / 2f, fillPaint)
            }
            canvas.restoreToCount(alphaLayer)
            canvas.restoreToCount(saved)
        }
        rim.setBounds(
            sliderRect.left.toInt(), sliderRect.top.toInt(),
            sliderRect.right.toInt(), sliderRect.bottom.toInt()
        )
        rim.draw(canvas)
        LiquidGlassVisual.drawControls(canvas, sliderRect, previewStep == 0, density)

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
                parent?.requestDisallowInterceptTouchEvent(true)
                val fraction = ((sliderRect.bottom - event.y) / max(1f, sliderRect.height())).coerceIn(0f, 1f)
                setPreviewStep(kotlin.math.round(fraction * 150).toInt())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
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

