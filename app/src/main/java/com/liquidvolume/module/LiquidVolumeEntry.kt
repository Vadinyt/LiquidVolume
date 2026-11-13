package com.liquidvolume.module

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicInteger

/**
 * Styles Samsung's existing compact volume slider and native backdrop blur.
 * Does not add a new panel, alter audio handling, or touch expanded volume UI.
 */
class LiquidVolumeEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "Loaded: API=${getApiVersion()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName != SYSTEM_UI || Application.getProcessName() != SYSTEM_UI) return

        try {
            val compact = Class.forName(COMPACT_PANEL, false, param.defaultClassLoader)
            val blurType = compact.declaredFields.firstOrNull { it.name == "blurEffect" }?.type
                ?: return
            val blurMethod = blurType.declaredMethods.firstOrNull {
                it.name == "setRealTimeBlur" &&
                    it.parameterTypes.size == 4 &&
                    View::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[2] == Float::class.javaPrimitiveType &&
                    it.parameterTypes[3] == Int::class.javaPrimitiveType
            } ?: return

            val logged = AtomicInteger(0)
            hook(blurMethod).setId("liquidvolume.compact.realglass").intercept { chain ->
                val blurView = chain.args[0] as? View
                if (blurView == null || findAncestor(blurView, COMPACT_PANEL) == null) {
                    return@intercept chain.proceed()
                }

                // Preserve Samsung's blur radius and corner settings, adjust only tint.
                val args = chain.args.toTypedArray()
                if (args[1] is Int) args[1] = GLASS_TINT
                val result = chain.proceed(args)

                // Style after the stock onShow / layout, not an invisible placeholder.
                blurView.post {
                    try {
                        val row = findAncestor(blurView, VOLUME_ROW) as? ViewGroup
                        if (row != null) {
                            val visibleBlur = activateExistingBlur(blurView)
                            val (tracks, backgrounds) = styleActualSlider(row, blurView)
                            blurView.postDelayed({
                                try { styleActualSlider(row, blurView) } catch (_: Throwable) { }
                            }, 120L)
                            if (logged.incrementAndGet() <= 6) {
                                log(Log.INFO, TAG, "GLASS_RENDER row=${row.width}x${row.height} " +
                                    "blurVisible=$visibleBlur seekbars=$tracks surfaces=$backgrounds")
                            }
                        }
                    } catch (error: Throwable) {
                        log(Log.WARN, TAG, "GLASS_RENDER_FAILED ${error.javaClass.simpleName}: ${error.message}")
                    }
                }
                result
            }
            log(Log.INFO, TAG, "GLASS_HOOK_INSTALLED: native compact row styling")
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "GLASS_HOOK_FAILED ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun findAncestor(view: View, className: String): View? {
        var current: View? = view
        repeat(12) {
            if (current == null) return null
            if (current?.javaClass?.name == className) return current
            current = current?.parent as? View
        }
        return null
    }

    /** Only show Samsung's own blur ImageView if it is behind sibling controls. */
    private fun activateExistingBlur(view: View): Boolean {
        val parent = view.parent as? ViewGroup ?: return view.visibility == View.VISIBLE
        val background = view.background ?: return false
        val native = background.javaClass.name.contains("BackgroundBlurDrawable")
        if (native && parent.indexOfChild(view) == 0 && view.visibility == View.INVISIBLE) {
            view.visibility = View.VISIBLE
        }
        return native && view.visibility == View.VISIBLE
    }

    /**
     * The old releases styled the invisible blurView or the oversized panel.
     * Here we modify only the visible, narrow track inside VolumeRowView.
     */
    private fun styleActualSlider(row: ViewGroup, blurView: View): Pair<Int, Int> {
        val all = ArrayList<View>()
        fun visit(view: View, depth: Int) {
            if (depth > 9 || all.size >= 90) return
            all.add(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i), depth + 1)
            }
        }
        visit(row, 0)

        var seekbars = 0
        var surfaces = 0
        for (view in all) {
            if (view === blurView || view.visibility != View.VISIBLE) continue
            if (view is ProgressBar) {
                val drawable = view.progressDrawable
                if (drawable is LayerDrawable) {
                    val track = drawable.findDrawableByLayerId(android.R.id.background)
                        ?: if (drawable.numberOfLayers > 1) drawable.getDrawable(0) else null
                    if (track != null) {
                        track.mutate().setTint(GLASS_TINT)
                        seekbars++
                    }
                } else {
                    view.progressBackgroundTintList = ColorStateList.valueOf(GLASS_TINT)
                    seekbars++
                }
                continue
            }

            // The compact face is ~118x591px; its row is ~223x712px.
            // Never recolor the row itself, panel, window, or small icon views.
            if (view is TextView || view is ViewGroup &&
                (view.width > blurView.width * 1.35f || view.height < blurView.height * 0.70f)
            ) continue
            if (view.width < blurView.width * 0.65f ||
                view.width > blurView.width * 1.35f ||
                view.height < blurView.height * 0.70f ||
                view.height > blurView.height * 1.20f) continue

            val background = view.background ?: continue
            if (background is GradientDrawable) {
                val shaped = background.mutate() as GradientDrawable
                shaped.setColor(GLASS_TINT)
                shaped.setStroke(2, 0xB0FFFFFF.toInt())
                surfaces++
            } else if (background is ColorDrawable) {
                background.mutate().setTint(GLASS_TINT)
                surfaces++
            } else if (background is LayerDrawable) {
                val track = background.findDrawableByLayerId(android.R.id.background)
                if (track != null) {
                    track.mutate().setTint(GLASS_TINT)
                    surfaces++
                }
            }
        }
        return seekbars to surfaces
    }

    companion object {
        private const val TAG = "LiquidVolume"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val COMPACT_PANEL = "com.android.systemui.volume.view.standard.VolumePanelView"
        private const val VOLUME_ROW = "com.android.systemui.volume.view.VolumeRowView"
        private const val GLASS_TINT: Int = 0x70FFFFFF
    }
}
