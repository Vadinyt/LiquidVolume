package com.liquidvolume.module

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar

/**
 * Applies the finished Liquid Glass artwork to the EXISTING Samsung compact row.
 *
 * Verified against the user's SystemUI.apk volume_row_view.xml:
 *   VolumeRowView     0x7e0a1050
 *   native blur image 0x7e0a1030 (normally INVISIBLE until blur is enabled)
 *   slider FrameLayout 0x7e0a1052
 *   VolumeSeekBar     0x7e0a1051 (rotated 270 degrees)
 *
 * Never changes the volume slider's progressDrawable, touch target, geometry,
 * visibility of its icons, audio callbacks, or any view outside the compact row.
 */
internal object CompactGlassStyler {
    private const val ROW_ID = 0x7e0a1050
    private const val BLUR_ID = 0x7e0a1030
    private const val TRACK_ID = 0x7e0a1052
    private const val SLIDER_ID = 0x7e0a1051
    private const val STATE_TAG = 0x7f0a7e10
    private const val TAG = "LiquidVolume"

    private data class State(
        val background: Drawable?,
        val shell: LiquidGlassShellDrawable,
        val rim: LiquidGlassRimDrawable,
        val compositedBackground: Drawable
    )

    fun apply(compactPanel: View, blurView: View): Boolean {
        if (compactPanel.javaClass.name != COMPACT_PANEL) return false
        if (blurView.id != BLUR_ID) return false
        val row = findAncestor(blurView, ROW_CLASS) as? ViewGroup ?: return false
        if (row.id != ROW_ID || !row.isAttachedToWindow) return false

        val track = row.findViewById<ViewGroup>(TRACK_ID) ?: return false
        val slider = row.findViewById<ProgressBar>(SLIDER_ID) ?: return false
        if (slider.parent !== track || track.width <= 0 || track.height <= 0) return false

        // The original blur remains a Samsung BackgroundBlurDrawable, rather than
        // a simulated screenshot overlay. Do not force it visible: Samsung owns its
        // visibility and may disable it for accessibility or power saving.
        if (blurView.background?.javaClass?.name?.contains("BackgroundBlurDrawable") != true) {
            Log.i(TAG, "GLASS_BLUR_NOTE: native backdrop blur not currently available")
        }

        val existing = track.getTag(STATE_TAG) as? State
        if (existing != null) {
            // OneUI may restore the stock background on show; reapply only then.
            if (track.background !== existing.compositedBackground) {
                track.background = existing.compositedBackground
            }
            return true
        }

        val glassShell = LiquidGlassShellDrawable()
        val glassRim = LiquidGlassRimDrawable()
        val state = State(
            background = track.background,
            shell = glassShell,
            rim = glassRim,
            // Glass drawing sits below Samsung's original seekbar and foreground.
            // Keeping the vendor foreground instance untouched preserves ripple
            // and press-state handling more reliably than wrapping it.
            compositedBackground = LayerDrawable(arrayOf(glassShell, glassRim))
        )
        try {
            // The glass body and border are a single background layer.
            // No changes to Samsung's native foreground, icons or slider.
            track.background = state.compositedBackground
            // Samsung's original volume_seekbar_drawable_no_blur has a genuine
            // white gradient and custom SecRoundedCornerSeekBarDrawable. Keep it
            // intact instead of tinting it flat, which also reduces crash risk.
            // Avoid clipToOutline changes: they can clip the rotating seekbar and
            // create renderer issues on Samsung's vendor SystemUI builds.
            track.setTag(STATE_TAG, state)
            track.invalidate()
            slider.invalidate()
            Log.i(TAG, "GLASS_NATIVE_APPLIED to original VolumeSeekBar without replacing its drawable")
            return true
        } catch (error: Throwable) {
            // Atomic-ish rollback if Samsung rejects any nonessential visual API.
            restore(track, state)
            Log.w(TAG, "GLASS_STYLE_ROLLED_BACK ${error.javaClass.simpleName}: ${error.message}")
            return false
        }
    }

    private fun restore(track: ViewGroup, state: State) {
        try {
            track.background = state.background
            track.setTag(STATE_TAG, null)
        } catch (ignored: Throwable) {
            Log.w(TAG, "GLASS_RESTORE_FAILED ${ignored.javaClass.simpleName}")
        }
    }

    private fun findAncestor(view: View, className: String): View? {
        var current: View? = view
        repeat(12) {
            if (current?.javaClass?.name == className) return current
            current = current?.parent as? View
        }
        return null
    }

    private const val COMPACT_PANEL = "com.android.systemui.volume.view.standard.VolumePanelView"
    private const val ROW_CLASS = "com.android.systemui.volume.view.VolumeRowView"
}
