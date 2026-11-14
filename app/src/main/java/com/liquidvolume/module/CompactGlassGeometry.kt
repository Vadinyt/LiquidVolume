package com.liquidvolume.module

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import kotlin.math.roundToInt

/** One layout transaction per compact row, preserving the native input views. */
internal class CompactGlassGeometry(
    private val track: ViewGroup,
    private val blur: View,
    private val seek: ProgressBar,
    private val row: ViewGroup,
    private val panel: View,
    private val onLaidOut: () -> Unit
) {
    private data class Size(val view: View, val width: Int, val height: Int)
    private data class Tint(val view: ImageView, val tint: ColorStateList?)
    private val sizes = listOf(track, blur, seek).map { Size(it, it.layoutParams.width, it.layoutParams.height) }
    private data class Decoration(val view: View, val original: Drawable, val clear: Drawable)
    private val decorations = mutableListOf<Decoration>()
    private var iconSkin: MediaSpeakerSkin.Skin? = null
    private val tints = mutableListOf<Tint>()
    private val lightIcon = ColorStateList.valueOf(0xE8FFFFFF.toInt())
    private val quietDots = ColorStateList.valueOf(0xA8FFFFFF.toInt())
    private val fill = (((seek.progressDrawable as? LayerDrawable)
        ?.findDrawableByLayerId(android.R.id.progress) as? InsetDrawable)
        ?.drawable as? LayerDrawable)?.findDrawableByLayerId(0x7e0a1056) as? GradientDrawable
    private val nativeMinHeight = seek.minHeight
    private val nativeMaxHeight = seek.maxHeight
    private val nativePaddingTop = seek.paddingTop
    private val nativePaddingBottom = seek.paddingBottom
    private val originalCorner = fill?.cornerRadius
    private val originalCorners = fill?.cornerRadii?.clone()
    private val originalProgress = seek.progressDrawable
    private val originalProgressBounds = Rect(originalProgress.bounds)
    private var listener: View.OnLayoutChangeListener? = null
    private var restored = false

    fun apply() {
        val height = track.height
        val width = (height / 3.1f).roundToInt()
        // Preserve the reference proportions without growing taller. Never shrink
        // native geometry or style a landscape/wide variant through this path.
        require(width >= track.width && width <= height / 2)
        require(kotlin.math.abs(seek.rotation - 270f) < 1f)
        val ready = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                ol: Int, ot: Int, or: Int, ob: Int) {
                track.removeOnLayoutChangeListener(this)
                listener = null
                // Post after the whole sibling layout, so blur and track agree.
                track.post {
                    if (track.isAttachedToWindow && !restored) {
                        // The square SeekBar keeps the same measured size. Merely
                        // changing maxHeight does not trigger onSizeChanged, so
                        // its old narrow drawable bounds otherwise survive.
                        NativeTrackBounds.apply(seek, track.width)
                        onLaidOut()
                    }
                }
            }
        }
        listener = ready
        track.addOnLayoutChangeListener(ready)
        resize(track, width, height)
        resize(blur, width, height)
        // Samsung's rotated SeekBar uses a square View centered in the capsule.
        // Its low-level painter centers Y at bounds.width()/2. Keep that square
        // canvas and change only track thickness, or low fills land off-screen.
        // AbsSeekBar clamps track thickness to min(maxHeight, paddedHeight).
        // LayoutParams alone leave a narrow native track inside the wide shell.
        seek.minHeight = width
        seek.maxHeight = width
        seek.setPadding(seek.paddingLeft, 0, seek.paddingRight, 0)
        resize(seek, height, height)
        fill?.cornerRadius = width / 2f
        // These are dedicated native outline planes, NOT the progress drawable.
        // Keep their Views and animations, but remove all paint from cloned shapes.
        for (id in intArrayOf(0x7e0a1054, 0x7e0a1055)) {
            val view = row.findViewById<View>(id) ?: continue
            val original = view.background as? GradientDrawable ?: continue
            val clear = original.constantState?.newDrawable()?.mutate() as? GradientDrawable ?: continue
            clear.setColor(Color.TRANSPARENT)
            clear.setStroke(0, Color.TRANSPARENT)
            decorations.add(Decoration(view, original, clear))
            view.background = clear
            android.util.Log.i("LiquidVolume", "GLASS_OUTLINE_REMOVED id=${id.toString(16)} visibility=${view.visibility} size=${view.width}x${view.height}")
        }
        row.findViewById<View>(0x7e0a1005)?.let {
            tintImages(it)
            iconSkin = MediaSpeakerSkin.install(it)
        }
        (panel.findViewById<View>(0x7e0a103a) as? ImageView)?.let {
            tints.add(Tint(it, it.imageTintList))
            it.imageTintList = quietDots
        }
    }

    private fun resize(view: View, width: Int, height: Int) {
        val params = view.layoutParams
        params.width = width
        params.height = height
        view.layoutParams = params
    }

    private fun tintImages(view: View) {
        if (view is ImageView) {
            tints.add(Tint(view, view.imageTintList))
            view.imageTintList = lightIcon
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) tintImages(view.getChildAt(i))
    }

    fun restore() {
        restored = true
        listener?.let { track.removeOnLayoutChangeListener(it) }
        listener = null
        seek.minHeight = nativeMinHeight
        seek.maxHeight = nativeMaxHeight
        seek.setPadding(seek.paddingLeft, nativePaddingTop, seek.paddingRight, nativePaddingBottom)
        sizes.forEach { resize(it.view, it.width, it.height) }
        if (seek.progressDrawable === originalProgress) originalProgress.bounds = originalProgressBounds
        if (originalCorners != null) fill?.cornerRadii = originalCorners
        else originalCorner?.let { fill?.cornerRadius = it }
        tints.forEach {
            if (it.view.imageTintList === lightIcon || it.view.imageTintList === quietDots)
                it.view.imageTintList = it.tint
        }
        tints.clear()
        iconSkin?.restore()
        iconSkin = null
        decorations.forEach { if (it.view.background === it.clear) it.view.background = it.original }
        decorations.clear()
    }
}

/** Refresh the existing track once after layout, never during draw or touch. */
internal object NativeTrackBounds {
    fun apply(seek: ProgressBar, thickness: Int) {
        val drawable = seek.progressDrawable ?: return
        val width = seek.width - seek.paddingLeft - seek.paddingRight
        val height = seek.height - seek.paddingTop - seek.paddingBottom
        if (thickness <= 0 || width <= thickness || height < thickness) return
        val top = (height - thickness) / 2
        drawable.setBounds(0, top, width, top + thickness)
        seek.invalidate()
    }
}
