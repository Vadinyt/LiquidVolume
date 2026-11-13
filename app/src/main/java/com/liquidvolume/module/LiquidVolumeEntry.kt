package com.liquidvolume.module

import android.app.Application
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ProgressBar
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicInteger

/**
 * LiquidVolume for the compact volume_panel_view/volume_row_view layout in
 * the supplied One UI 9 SystemUI.apk. Target precise ORIGINAL view IDs.
 * Never draw another panel or intercept volume events.
 */
class LiquidVolumeEntry : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "Loaded; LibXposed API=${getApiVersion()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName != SYSTEM_UI || Application.getProcessName() != SYSTEM_UI) return

        try {
            val compact = Class.forName(COMPACT_PANEL, false, param.defaultClassLoader)
            val blur = compact.declaredFields.firstOrNull { it.name == "blurEffect" }?.type ?: return
            val method = blur.declaredMethods.firstOrNull {
                it.name == "setRealTimeBlur" &&
                    it.parameterTypes.size == 4 &&
                    View::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[2] == Float::class.javaPrimitiveType &&
                    it.parameterTypes[3] == Int::class.javaPrimitiveType
            } ?: return
            val counter = AtomicInteger(0)
            hook(method).setId("liquidvolume.compact.trackglass").intercept { chain ->
                val blurView = chain.args[0] as? View
                if (blurView == null ||
                    blurView.id != BLUR_VIEW_ID ||
                    ancestor(blurView, COMPACT_PANEL) == null) {
                    return@intercept chain.proceed()
                }

                // Only the compact panel. Samsung's blur method remains responsible
                // for its real-time blur, radius, and rounded corners.
                val args = chain.args.toTypedArray()
                if (args[1] is Int) args[1] = GLASS_TINT
                val result = chain.proceed(args)
                blurView.post {
                    try {
                        val row = ancestor(blurView, ROW_CLASS) as? ViewGroup
                        if (row != null && row.id == ROW_ID) {
                            val success = installGlassOnOriginalViews(row, blurView)
                            if (counter.incrementAndGet() <= 5) {
                                log(Log.INFO, TAG, "GLASS_TRACK_RESULT success=$success track=${row.findViewById<View>(TRACK_ID)?.javaClass?.simpleName}")
                            }
                        }
                    } catch (e: Throwable) {
                        log(Log.WARN, TAG, "GLASS_TRACK_ERROR ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
                result
            }
            log(Log.INFO, TAG, "GLASS_HOOK_INSTALLED for original compact slider")
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "GLASS_HOOK_FAILED ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun installGlassOnOriginalViews(row: ViewGroup, blurView: View): Boolean {
        val track = row.findViewById<View>(TRACK_ID) as? ViewGroup ?: return false
        val slider = row.findViewById<ProgressBar>(SEEK_ID) ?: return false
        if (slider.parent !== track || track.width <= 0) return false

        // The original XML sets the blur ImageView INVISIBLE by default.
        // It is the first child of the same FrameLayout that owns the track.
        val parent = blurView.parent as? ViewGroup
        if (parent != null && parent.indexOfChild(blurView) == 0 &&
            blurView.background != null &&
            blurView.background.javaClass.name.contains("BlurDrawable")) {
            blurView.visibility = View.VISIBLE
        }

        // The ORIGINAL narrow slider FrameLayout receives the white glass.
        // Unlike v0.10, the outer VolumePanelView and full-width row stay untouched.
        val glass = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0x78FFFFFF, 0x45FFFFFF, 0x64FFFFFF)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setStroke(2, 0xAAFFFFFF.toInt())
        }
        val emptyTrack = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(0x08FFFFFF)
        }
        val fill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xE8FFFFFF.toInt(), 0xBCFFFFFF.toInt())
        ).apply {
            cornerRadius = 999f
        }
        val clippedFill = ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        val seekLayers = LayerDrawable(arrayOf<Drawable>(emptyTrack, clippedFill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

        // Apply immediately, then enforce only when Samsung replaces a drawable.
        track.background = glass
        slider.progressDrawable = seekLayers
        track.clipToOutline = true
        track.invalidate()
        slider.invalidate()
        if (track.getTag(INSTALLED_TAG) == null) {
            track.setTag(INSTALLED_TAG, true)
            val observer = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!track.isAttachedToWindow) return true
                    if (track.background !== glass) track.background = glass
                    if (slider.progressDrawable !== seekLayers) slider.progressDrawable = seekLayers
                    return true
                }
            }
            track.viewTreeObserver.addOnPreDrawListener(observer)
            track.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    if (track.viewTreeObserver.isAlive) {
                        track.viewTreeObserver.removeOnPreDrawListener(observer)
                    }
                    track.setTag(INSTALLED_TAG, null)
                    track.removeOnAttachStateChangeListener(this)
                }
            })
        }
        return true
    }

    private fun ancestor(view: View, expectedClass: String): View? {
        var current: View? = view
        repeat(12) {
            if (current?.javaClass?.name == expectedClass) return current
            current = current?.parent as? View
        }
        return null
    }

    companion object {
        private const val TAG = "LiquidVolume"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val COMPACT_PANEL = "com.android.systemui.volume.view.standard.VolumePanelView"
        private const val ROW_CLASS = "com.android.systemui.volume.view.VolumeRowView"
        private const val ROW_ID = 0x7e0a1050
        private const val BLUR_VIEW_ID = 0x7e0a1030
        private const val TRACK_ID = 0x7e0a1052
        private const val SEEK_ID = 0x7e0a1051
        private const val INSTALLED_TAG = 0x7e0a0001
        private const val GLASS_TINT: Int = 0x55FFFFFF
    }
}
