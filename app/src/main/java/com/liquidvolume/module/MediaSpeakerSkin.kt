package com.liquidvolume.module

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import java.util.WeakHashMap

/** Native icon button and accessibility remain intact; only media artwork changes. */
internal object MediaSpeakerSkin {
    private data class Status(val media: Boolean, val muted: Boolean)
    private val status = WeakHashMap<View, Status>()
    private val skins = WeakHashMap<View, Skin>()

    fun nativeUpdated(host: View, row: Any) {
        if (host.javaClass.name != "com.android.systemui.volume.view.icon.VPVolumeIcon") return
        val cls = row.javaClass
        val stream = cls.getMethod("getStreamType").invoke(row) as Int
        val muted = cls.getMethod("isMuted").invoke(row) as Boolean
        val level = cls.getMethod("getRealLevel").invoke(row) as Int
        status[host] = Status(stream == 3, muted || level <= 0)
        skins[host]?.update()
    }

    fun install(host: View): Skin {
        return skins[host] ?: Skin(host).also { skins[host] = it; it.update() }
    }

    class Skin(private val host: View) {
        private val originalAlpha = WeakHashMap<ImageView, Int>()
        private val speaker = SpeakerDrawable()
        private var added = false
        private val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        init { host.addOnLayoutChangeListener(layout) }

        fun update() {
            val current = status[host]
            if (current == null || !current.media) {
                showNative()
                return
            }
            fun hide(view: View) {
                if (view is ImageView) {
                    if (!originalAlpha.containsKey(view)) originalAlpha[view] = view.imageAlpha
                    view.imageAlpha = 0
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) hide(view.getChildAt(i))
            }
            hide(host)
            speaker.muted = current.muted
            speaker.setBounds(host.paddingLeft, host.paddingTop,
                host.width - host.paddingRight, host.height - host.paddingBottom)
            if (!added) { host.overlay.add(speaker); added = true }
            speaker.invalidateSelf()
        }

        private fun showNative() {
            if (added) { host.overlay.remove(speaker); added = false }
            originalAlpha.forEach { (view, alpha) -> if (view.imageAlpha == 0) view.imageAlpha = alpha }
            originalAlpha.clear()
        }

        fun restore() {
            skins.remove(host)
            status.remove(host)
            host.removeOnLayoutChangeListener(layout)
            showNative()
        }
    }

    class SpeakerDrawable : Drawable() {
        var muted = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val body = Path().apply {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 5f)
            quadTo(13f, 4.3f, 13f, 5.6f); lineTo(13f, 18.4f)
            quadTo(13f, 19.7f, 12f, 19f); lineTo(7f, 15f); lineTo(3f, 15f)
            quadTo(2f, 15f, 2f, 14f); lineTo(2f, 10f); quadTo(2f, 9f, 3f, 9f); close()
        }
        private val small = RectF(11f, 8f, 19f, 16f)
        private val large = RectF(7f, 4f, 23f, 20f)
        private var opacity = 232
        private var filter: ColorFilter? = null
        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val size = minOf(bounds.width(), bounds.height()).toFloat()
            val saved = canvas.save()
            canvas.translate(bounds.exactCenterX() - size / 2, bounds.exactCenterY() - size / 2)
            canvas.scale(size / 24f, size / 24f)
            paint.color = Color.WHITE; paint.alpha = opacity; paint.colorFilter = filter
            paint.style = Paint.Style.FILL
            canvas.drawPath(body, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
            paint.strokeCap = Paint.Cap.ROUND
            if (muted) canvas.drawLine(3f, 3f, 21f, 21f, paint)
            else {
                canvas.drawArc(small, -52f, 104f, false, paint)
                canvas.drawArc(large, -48f, 96f, false, paint)
            }
            canvas.restoreToCount(saved)
        }
        override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Android framework")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
