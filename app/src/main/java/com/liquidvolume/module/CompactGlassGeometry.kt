package com.liquidvolume.module

import android.content.res.ColorStateList
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
    private var listener: View.OnLayoutChangeListener? = null

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
                track.post { if (track.isAttachedToWindow) onLaidOut() }
            }
        }
        listener = ready
        track.addOnLayoutChangeListener(ready)
        resize(track, width, height)
        resize(blur, width, height)
        // The native horizontal SeekBar rotates by 270 degrees. Swap the physical
        // capsule axes instead of scaling the whole View (which distorts corners).
        // AbsSeekBar clamps track thickness to min(maxHeight, paddedHeight).
        // LayoutParams alone leave a narrow native track inside the wide shell.
        seek.minHeight = width
        seek.maxHeight = width
        seek.setPadding(seek.paddingLeft, 0, seek.paddingRight, 0)
        resize(seek, height, width)
        fill?.cornerRadius = width / 2f
        row.findViewById<View>(0x7e0a1005)?.let { tintImages(it) }
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
        listener?.let { track.removeOnLayoutChangeListener(it) }
        listener = null
        seek.minHeight = nativeMinHeight
        seek.maxHeight = nativeMaxHeight
        seek.setPadding(seek.paddingLeft, nativePaddingTop, seek.paddingRight, nativePaddingBottom)
        sizes.forEach { resize(it.view, it.width, it.height) }
        if (originalCorners != null) fill?.cornerRadii = originalCorners
        else originalCorner?.let { fill?.cornerRadius = it }
        tints.forEach {
            if (it.view.imageTintList === lightIcon || it.view.imageTintList === quietDots)
                it.view.imageTintList = it.tint
        }
        tints.clear()
    }
}
