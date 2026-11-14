package com.liquidvolume.module

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import java.util.WeakHashMap

/**
 * Targets ONLY the existing compact Samsung volume row. Earlier builds placed
 * the glass behind an opaque Samsung VolumeSeekBar, making it invisible.
 *
 * This version places a translucent optical layer in track.overlay ABOVE the
 * native seekbar. It is not a new panel or an input-handling overlay: the real
 * Samsung slider, icons, gestures, progress and audio callbacks remain intact.
 */
internal object CompactGlassStyler {
    private const val TAG = "LiquidVolume"
    private const val BLUR_ID = 0x7e0a1030
    private const val TRACK_ID = 0x7e0a1052
    private const val SEEK_ID = 0x7e0a1051
    private const val PANEL_CLASS =
        "com.android.systemui.volume.view.standard.VolumePanelView"
    private const val ROW_CLASS = "com.android.systemui.volume.view.VolumeRowView"

    private val installed = WeakHashMap<ViewGroup, StyleState>()

    private class StyleState(
        val originalTrackBackground: Drawable?,
        val shell: LiquidGlassShellDrawable,
        val opticalOverlay: LiquidGlassOverlayDrawable,
        val layers: LayerDrawable?,
        val originalInnerBackground: Drawable?,
        val transparentInnerBackground: Drawable?
    )

    fun apply(panel: View, blurView: View): Boolean {
        if (panel.javaClass.name != PANEL_CLASS) return false

        val row = ancestor(blurView, ROW_CLASS) as? ViewGroup ?: return false
        val seekBar = (row.findViewById<View>(SEEK_ID) as? ProgressBar)
            ?: findVolumeSeekBar(row) ?: return false

        // The track is the narrow parent of the existing Samsung seekbar.
        // Use the XML ID when present, otherwise its real parent.
        val xmlTrack = row.findViewById<View>(TRACK_ID) as? ViewGroup
        val directParent = seekBar.parent as? ViewGroup ?: return false
        val track = if (xmlTrack != null && containsView(xmlTrack, seekBar)) {
            xmlTrack
        } else {
            directParent
        }

        if (!track.isAttachedToWindow || track.width <= 20 || track.height <= 20) {
            return false
        }
        // Prevent the oversized double capsule from v0.10.
        if (blurView.width > 0 && track.width > blurView.width * 1.6f) {
            Log.w(TAG, "GLASS_SKIP_OVERSIZED_TRACK ${track.width} vs blur ${blurView.width}")
            return false
        }

        val previous = installed[track]
        if (previous != null) {
            previous.opticalOverlay.setBounds(0, 0, track.width, track.height)
            if (track.background !== previous.shell) {
                track.background = previous.shell
            }
            return true
        }

        val nativeLayers = seekBar.progressDrawable as? LayerDrawable
        val originalInner = nativeLayers
            ?.findDrawableByLayerId(android.R.id.background)
        val clone = (originalInner as? GradientDrawable)?.constantState
            ?.newDrawable()?.mutate() as? GradientDrawable
        clone?.setColor(0x05FFFFFF)

        val glassBody = LiquidGlassShellDrawable()
        val opticalLayer = LiquidGlassOverlayDrawable().apply {
            setBounds(0, 0, track.width, track.height)
        }
        val state = StyleState(
            originalTrackBackground = track.background,
            shell = glassBody,
            opticalOverlay = opticalLayer,
            layers = nativeLayers,
            originalInnerBackground = originalInner,
            transparentInnerBackground = clone
        )

        try {
            // Optional: lighten Samsung's own gray fill when the native drawable
            // supports safe replacement of this ONE layer. Never replace the
            // ProgressBar's progressDrawable or the progress layer.
            val nativeCleared =
                nativeLayers != null && clone != null &&
                nativeLayers.setDrawableByLayerId(android.R.id.background, clone)

            track.background = glassBody

            // Crucial: this draws ON TOP of the opaque slider, unlike v0.19/0.20
            // which only changed backgrounds hidden by Samsung.
            // ViewOverlay does not receive touch events.
            track.overlay.add(opticalLayer)
            installed[track] = state
            // The volume panel is temporary. Remove the overlay and restore
            // the exact Samsung drawables when SystemUI detaches this row.
            track.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    rollback(track, state)
                    track.removeOnAttachStateChangeListener(this)
                }
            })
            track.invalidate()
            seekBar.invalidate()

            Log.i(TAG, "GLASS_FRONT_LAYER_APPLIED track=${track.width}x${track.height} nativeCleared=$nativeCleared")
            return true
        } catch (error: Throwable) {
            rollback(track, state)
            Log.e(TAG, "GLASS_FRONT_LAYER_FAILED ${error.javaClass.simpleName}: ${error.message}")
            return false
        }
    }

    private fun rollback(track: ViewGroup, state: StyleState) {
        try {
            track.overlay.remove(state.opticalOverlay)
            if (track.background === state.shell) {
                track.background = state.originalTrackBackground
            }
            if (state.layers != null && state.originalInnerBackground != null &&
                state.transparentInnerBackground != null &&
                state.layers.findDrawableByLayerId(android.R.id.background) ===
                    state.transparentInnerBackground) {
                state.layers.setDrawableByLayerId(
                    android.R.id.background, state.originalInnerBackground
                )
            }
            installed.remove(track)
        } catch (error: Throwable) {
            Log.w(TAG, "GLASS_ROLLBACK_FAILED ${error.javaClass.simpleName}")
        }
    }

    private fun findVolumeSeekBar(root: ViewGroup): ProgressBar? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ProgressBar &&
                child.javaClass.name.contains("VolumeSeekBar")) return child
            if (child is ViewGroup) {
                val found = findVolumeSeekBar(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun containsView(root: ViewGroup, child: View): Boolean {
        var parent: View? = child
        repeat(8) {
            if (parent === root) return true
            parent = parent?.parent as? View
        }
        return false
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
