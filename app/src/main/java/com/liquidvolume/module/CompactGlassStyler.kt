package com.liquidvolume.module

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import java.util.WeakHashMap
import kotlin.math.abs

/** Changes only the verified compact-row composition, never Samsung's progress wrapper. */
internal object CompactGlassStyler {
    private const val TAG = "LiquidVolume"
    private const val BLUR_ID = 0x7e0a1030
    private const val TRACK_ID = 0x7e0a1052
    private const val SEEK_ID = 0x7e0a1051
    const val PANEL_CLASS = "com.android.systemui.volume.view.standard.VolumePanelView"
    const val ROW_CLASS = "com.android.systemui.volume.view.VolumeRowView"
    private val installed = WeakHashMap<ViewGroup, StyleState>()
    private val pending = WeakHashMap<View, Boolean>()
    private val backend by lazy { SamsungGlassBlur() }
    private var failures = 0
    private var reports = 0

    private class StyleState(
        val blur: View,
        val inner: Drawable,
        val originalTrackBackground: Drawable?,
        val originalBlurBackground: Drawable?,
        val originalBlurForeground: Drawable?,
        val originalVisibility: Int,
        val originalInnerAlpha: Int,
        var originalBlurInfo: Any?,
        var appliedBlurInfo: Any,
        val shell: LiquidGlassShellDrawable = LiquidGlassShellDrawable(),
        val rim: LiquidGlassRimDrawable = LiquidGlassRimDrawable(),
        val clearBackground: Drawable = ColorDrawable(Color.TRANSPARENT),
        // Samsung unconditionally calls getForeground().setAlpha(30). Never null it.
        val clearForeground: Drawable = ColorDrawable(Color.TRANSPARENT)
    ) {
        var detachListener: View.OnAttachStateChangeListener? = null
        var width = 0
        var height = 0
    }

    /** One queued update per row. No draw hooks, touch hooks or frame loop. */
    fun schedule(source: View) {
        val row = ancestor(source, ROW_CLASS) ?: return
        if (failures >= 3 || pending.put(row, true) != null) return
        row.post {
            pending.remove(row)
            if (failures >= 3 || !row.isAttachedToWindow) return@post
            try {
                val panel = ancestor(row, PANEL_CLASS) ?: return@post
                val blur = row.findViewById<View>(BLUR_ID) ?: return@post
                if (blur.width > 0 && blur.height > 0) {
                    apply(panel, blur)
                } else {
                    // Exactly one layout callback, removed on layout or detach.
                    val waiter = object : View.OnLayoutChangeListener, View.OnAttachStateChangeListener {
                        private fun remove(v: View) {
                            v.removeOnLayoutChangeListener(this)
                            v.removeOnAttachStateChangeListener(this)
                        }
                        override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                            ol: Int, ot: Int, or: Int, ob: Int) {
                            remove(v)
                            if (v.isAttachedToWindow && failures < 3) {
                                try { apply(panel, v) } catch (error: Throwable) {
                                    failures++
                                    Log.e(TAG, "GLASS_LAYOUT_FAILED #$failures", error)
                                }
                            }
                        }
                        override fun onViewAttachedToWindow(v: View) = Unit
                        override fun onViewDetachedFromWindow(v: View) = remove(v)
                    }
                    blur.addOnLayoutChangeListener(waiter)
                    blur.addOnAttachStateChangeListener(waiter)
                }
            } catch (error: Throwable) {
                failures++
                Log.e(TAG, "GLASS_COMPOSITION_FAILED #$failures", error)
            }
        }
    }

    private fun apply(panel: View, blur: View) {
        if (panel.javaClass.name != PANEL_CLASS) return
        val row = ancestor(blur, ROW_CLASS) as? ViewGroup ?: return
        val seek = row.findViewById<View>(SEEK_ID) as? ProgressBar ?: return
        if (seek.javaClass.name != "com.android.systemui.volume.view.VolumeSeekBar") return
        val track = row.findViewById<View>(TRACK_ID) as? ViewGroup ?: return
        val parent = track.parent as? ViewGroup ?: return
        // Verify the XML's sibling planes occupy ONE capsule. Fail closed for
        // expanded, landscape/dual layouts with a different composition.
        if (seek.parent !== track || blur.parent !== parent ||
            parent.indexOfChild(blur) >= parent.indexOfChild(track) ||
            track.width <= 20 || track.height < track.width * 2.5f ||
            abs(track.width - blur.width) > 2 || abs(track.height - blur.height) > 2 ||
            abs(track.left - blur.left) > 2 || abs(track.top - blur.top) > 2) {
            if (reports++ < 6) Log.w(TAG, "GLASS_UNSUPPORTED_GEOMETRY blur=${blur.width}x${blur.height} track=${track.width}x${track.height}")
            return
        }
        val resolver = row.context.contentResolver
        if (Settings.System.getInt(resolver, "accessibility_reduce_transparency", 0) != 0 ||
            Settings.Secure.getInt(resolver, "accessibility_reduce_transparency", 0) != 0) {
            installed[track]?.let { rollback(track, it) }
            return
        }
        val layers = seek.progressDrawable as? LayerDrawable ?: return
        val inner = layers.findDrawableByLayerId(android.R.id.background) ?: return
        val progress = layers.findDrawableByLayerId(android.R.id.progress) ?: return
        if (progress.javaClass.name != "com.android.systemui.volume.view.SecRoundedCornerSeekBarDrawable") return

        var state = installed[track]
        if (state != null && state.inner !== inner) {
            rollback(track, state)
            state = null
        }
        val isNew = state == null
        val currentBlur = backend.current(blur)
        val applied = state ?: StyleState(blur, inner, track.background, blur.background,
            blur.foreground, blur.visibility, inner.alpha, currentBlur, backend.create(blur))
        try {
            if (!isNew && currentBlur !== applied.appliedBlurInfo) {
                // Remember a subsequent Samsung blur update for exact restoration.
                applied.originalBlurInfo = currentBlur
            }
            if (applied.width != blur.width || applied.height != blur.height) {
                applied.appliedBlurInfo = backend.create(blur)
                applied.width = blur.width
                applied.height = blur.height
            }
            // Apply the live backend BEFORE removing any opaque native material.
            backend.set(blur, applied.appliedBlurInfo)
            check(backend.current(blur) != null) { "Samsung did not retain SemBlurInfo" }
            setBackgroundPreservingPadding(blur, applied.clearBackground)
            blur.foreground = applied.clearForeground
            blur.visibility = View.VISIBLE
            // Keep the existing child and every Samsung cast/callback valid.
            inner.alpha = 0
            setBackgroundPreservingPadding(track, applied.shell)
            applied.rim.setBounds(0, 0, track.width, track.height)
            if (isNew) {
                track.overlay.add(applied.rim)
                installed[track] = applied
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) = rollback(track, applied)
                }
                applied.detachListener = listener
                track.addOnAttachStateChangeListener(listener)
            }
            seek.invalidate()
            if (reports++ < 6) Log.i(TAG,
                "GLASS_COMPOSITION_APPLIED backend=SemBlurInfo mode=0 " +
                    "blur=${blur.width}x${blur.height} visibility=${blur.visibility} " +
                    "innerAlpha=${inner.alpha} progressPreserved=${layers.findDrawableByLayerId(android.R.id.progress) === progress}")
        } catch (error: Throwable) {
            rollback(track, applied)
            throw error
        }
    }

    private fun setBackgroundPreservingPadding(view: View, drawable: Drawable?) {
        if (view.background === drawable) return
        val l = view.paddingLeft
        val t = view.paddingTop
        val r = view.paddingRight
        val b = view.paddingBottom
        view.background = drawable
        view.setPadding(l, t, r, b)
    }

    private fun rollback(track: ViewGroup, state: StyleState) {
        // Independent restoration steps: one failed backend call must not leave
        // the other layers modified. Never propagate an error from a detach callback.
        fun restore(action: () -> Unit) {
            try { action() } catch (error: Throwable) {
                Log.w(TAG, "GLASS_RESTORE_FAILED", error)
            }
        }
        installed.remove(track)
        state.detachListener?.let { track.removeOnAttachStateChangeListener(it) }
        restore { track.overlay.remove(state.rim) }
        restore { if (track.background === state.shell) setBackgroundPreservingPadding(track, state.originalTrackBackground) }
        restore { if (state.inner.alpha == 0) state.inner.alpha = state.originalInnerAlpha }
        restore { if (state.blur.background === state.clearBackground) setBackgroundPreservingPadding(state.blur, state.originalBlurBackground) }
        restore { if (state.blur.foreground === state.clearForeground) state.blur.foreground = state.originalBlurForeground }
        restore {
            if (backend.current(state.blur) === state.appliedBlurInfo) {
                backend.set(state.blur, state.originalBlurInfo)
                if (state.blur.visibility == View.VISIBLE) state.blur.visibility = state.originalVisibility
            }
        }
    }

    private fun ancestor(view: View, name: String): View? {
        var current: View? = view
        repeat(12) {
            if (current?.javaClass?.name == name) return current
            current = current?.parent as? View
        }
        return null
    }
}
