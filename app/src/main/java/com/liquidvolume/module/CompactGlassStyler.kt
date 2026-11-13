package com.liquidvolume.module

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar

/**
 * Liquid Glass on Samsung's real compact VolumeSeekBar.
 *
 * volume_row_view.xml:
 * - VolumeRowView (0x7e0a1050)
 * - native blur ImageView (0x7e0a1030)
 * - slider container (0x7e0a1052)
 * - VolumeSeekBar (0x7e0a1051)
 *
 * Crucial discovery from the supplied SystemUI.apk:
 * volume_seekbar_drawable_no_blur.xml is a LayerDrawable containing both an
 * opaque android.R.id.background and a separate native android.R.id.progress.
 * Styling only the slider container did not affect that opaque inner layer.
 *
 * We replace ONLY the inner background-layer drawable. We do not replace
 * progressDrawable itself, the SecRoundedCornerSeekBarDrawable fill, view
 * hierarchy, Samsung foreground, animations, or volume callbacks.
 */
internal object CompactGlassStyler {
    private const val ROW_ID = 0x7e0a1050
    private const val BLUR_ID = 0x7e0a1030
    private const val TRACK_ID = 0x7e0a1052
    private const val SLIDER_ID = 0x7e0a1051
    private const val STATE_TAG = 0x7f0a7e10
    private const val TAG = "LiquidVolume"
    private const val COMPACT_PANEL =
        "com.android.systemui.volume.view.standard.VolumePanelView"
    private const val ROW_CLASS = "com.android.systemui.volume.view.VolumeRowView"
    private const val SEEK_CLASS = "com.android.systemui.volume.view.VolumeSeekBar"

    private data class State(
        val originalTrackBackground: Drawable?,
        val progressLayers: LayerDrawable,
        val originalInnerBackground: Drawable,
        val transparentInnerBackground: Drawable,
        val shell: LiquidGlassShellDrawable,
        val rim: LiquidGlassRimDrawable
    )

    fun apply(compactPanel: View, blurView: View): Boolean {
        if (compactPanel.javaClass.name != COMPACT_PANEL || blurView.id != BLUR_ID) return false
        val row = ancestor(blurView, ROW_CLASS) as? ViewGroup ?: return false
        if (row.id != ROW_ID || !row.isAttachedToWindow) return false

        val track = row.findViewById<ViewGroup>(TRACK_ID) ?: return false
        val slider = row.findViewById<ProgressBar>(SLIDER_ID) ?: return false
        if (slider.javaClass.name != SEEK_CLASS || slider.parent !== track ||
            track.width <= 0 || track.height <= 0) return false

        val layers = slider.progressDrawable as? LayerDrawable ?: return false
        val backgroundLayer = layers.findDrawableByLayerId(android.R.id.background) ?: return false
        val fillLayer = layers.findDrawableByLayerId(android.R.id.progress) ?: return false
        if (layers.numberOfLayers < 2 || fillLayer === backgroundLayer) return false

        val existing = track.getTag(STATE_TAG) as? State
        if (existing != null && existing.progressLayers === layers) {
            // Vendor code can restore a drawable while displaying the panel.
            // Reapply only on the normal onShow callback, never on every frame.
            if (track.background !== existing.shell) track.background = existing.shell
            if (layers.findDrawableByLayerId(android.R.id.background) !==
                existing.transparentInnerBackground) {
                layers.setDrawableByLayerId(
                    android.R.id.background, existing.transparentInnerBackground
                )
            }
            if (existing.rim.bounds.width() != track.width ||
                existing.rim.bounds.height() != track.height) {
                existing.rim.setBounds(0, 0, track.width, track.height)
            }
            return true
        }

        if (existing != null) {
            // Samsung re-created the seekbar drawable. Undo only our old view
            // decoration and reattach to the current native layers.
            rollback(track, existing)
        }

        val shell = LiquidGlassShellDrawable()
        val rim = LiquidGlassRimDrawable().apply {
            setBounds(0, 0, track.width, track.height)
        }
        // Clone Samsung's original <shape> instead of inventing a replacement
        // with different intrinsic dimensions. Preserve native size, radius and
        // layer geometry, changing only the gray fill and outline.
        val originalShape = backgroundLayer as? GradientDrawable ?: return false
        val transparentBase =
            originalShape.constantState?.newDrawable()?.mutate() as? GradientDrawable
                ?: return false
        transparentBase.setColor(0x05FFFFFF)
        transparentBase.setStroke(0, 0x00FFFFFF)
        val state = State(
            originalTrackBackground = track.background,
            progressLayers = layers,
            originalInnerBackground = backgroundLayer,
            transparentInnerBackground = transparentBase,
            shell = shell,
            rim = rim
        )

        try {
            if (!layers.setDrawableByLayerId(android.R.id.background, transparentBase)) {
                Log.w(TAG, "GLASS_SKIPPED: native seekbar layer is immutable")
                return false
            }
            // Native progress / opacity fill stays untouched. The translucent
            // shell is now visible THROUGH the cleared internal background.
            track.background = shell
            // Draw a subtle outline ABOVE the native fill without replacing
            // Samsung's foreground (and without consuming any touch events).
            track.overlay.add(rim)
            track.setTag(STATE_TAG, state)
            track.invalidate()
            slider.invalidate()

            Log.i(TAG, "GLASS_VISIBLE_APPLIED: cleared native gray seekbar layer")
            return true
        } catch (error: Throwable) {
            rollback(track, state)
            Log.w(TAG, "GLASS_STYLE_ROLLED_BACK ${error.javaClass.simpleName}: ${error.message}")
            return false
        }
    }

    private fun rollback(track: ViewGroup, state: State) {
        try {
            if (state.progressLayers.findDrawableByLayerId(android.R.id.background) ===
                state.transparentInnerBackground) {
                state.progressLayers.setDrawableByLayerId(
                    android.R.id.background, state.originalInnerBackground
                )
            }
            track.overlay.remove(state.rim)
            track.background = state.originalTrackBackground
            track.setTag(STATE_TAG, null)
        } catch (error: Throwable) {
            Log.w(TAG, "GLASS_RESTORE_FAILED ${error.javaClass.simpleName}")
        }
    }

    private fun ancestor(view: View, expected: String): View? {
        var current: View? = view
        repeat(12) {
            if (current?.javaClass?.name == expected) return current
            current = current?.parent as? View
        }
        return null
    }
}
