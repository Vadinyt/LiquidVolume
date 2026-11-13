package com.liquidvolume.module

import android.app.Application
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicInteger

/**
 * The compact Samsung panel remains in charge of volume, touch, focus,
 * accessibility, timers, layout and animations. We change only its artwork.
 *
 * No pre-draw listeners, no replacement seekbar/progressDrawable, no overlay
 * window, and no persistent loop touching SystemUI.
 */
class LiquidVolumeEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "Loaded; framework API=${getApiVersion()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName != SYSTEM_UI || Application.getProcessName() != SYSTEM_UI) return

        try {
            val panelClass = Class.forName(COMPACT_PANEL, false, param.defaultClassLoader)
            val blurClass = panelClass.declaredFields.firstOrNull { it.name == "blurEffect" }?.type
                ?: run {
                    log(Log.WARN, TAG, "GLASS_SKIPPED: unsupported Samsung blur field")
                    return
                }
            val blurMethod = blurClass.declaredMethods.firstOrNull {
                it.name == "setRealTimeBlur" &&
                    it.parameterTypes.size == 4 &&
                    View::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[2] == Float::class.javaPrimitiveType &&
                    it.parameterTypes[3] == Int::class.javaPrimitiveType
            } ?: run {
                log(Log.WARN, TAG, "GLASS_SKIPPED: unsupported blur signature")
                return
            }

            val styleFailures = AtomicInteger(0)
            val skippedLayouts = AtomicInteger(0)
            val successfulStyles = AtomicInteger(0)
            hook(blurMethod).setId("liquidvolume.compact.liquidglass").intercept { chain ->
                val image = chain.args[0] as? View
                val compactPanel = image?.let { ancestor(it, COMPACT_PANEL) }
                if (image == null || image.id != BLUR_VIEW_ID ||
                    compactPanel == null || styleFailures.get() >= 3) {
                    return@intercept chain.proceed()
                }

                // Native blur behind a translucent white/pearl layer.
                // Keep Samsung radius, corner rounding and audio UI untouched.
                val args = chain.args.toTypedArray()
                if (args[1] is Int) args[1] = GLASS_BLUR_TINT
                val nativeResult = chain.proceed(args)

                // One bounded UI-thread update after native onShow. No OnPreDraw
                // callbacks, no postDelayed loops, no second capsule.
                image.post {
                    if (styleFailures.get() >= 3) return@post
                    try {
                        // A hidden native blur ImageView may be shown ONLY when
                        // it owns the genuine BackgroundBlurDrawable and sits
                        // behind the original Samsung slider in the compact row.
                        revealSamsungBlurIfValid(image)
                        if (CompactGlassStyler.apply(compactPanel, image)) {
                            if (successfulStyles.incrementAndGet() <= 3) {
                                log(Log.INFO, TAG, "GLASS_RENDER_SUCCESS on original compact slider")
                            }
                        } else if (skippedLayouts.incrementAndGet() <= 3) {
                            // An unattached or not-yet-measured view is normal
                            // during the volume animation, not a module failure.
                            log(Log.INFO, TAG, "GLASS_LAYOUT_PENDING: native row not ready")
                        }
                    } catch (error: Throwable) {
                        val failures = styleFailures.incrementAndGet()
                        log(Log.ERROR, TAG, "GLASS_RENDER_ERROR #$failures: ${error.javaClass.simpleName}")
                    }
                }
                nativeResult
            }
            log(Log.INFO, TAG, "GLASS_HOOK_INSTALLED: finished iOS-like compact artwork")
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "GLASS_HOOK_FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun revealSamsungBlurIfValid(view: View) {
        if (view.id != BLUR_VIEW_ID) return
        if (view.visibility != View.INVISIBLE) return
        if (view.background?.javaClass?.name != NATIVE_BLUR_DRAWABLE) return
        val parent = view.parent as? android.view.ViewGroup ?: return
        if (parent.indexOfChild(view) != 0) return
        if (parent.findViewById<View>(TRACK_ID) == null) return
        // Existing system blur instead of a fake screenshot-blur overlay.
        view.visibility = View.VISIBLE
    }

    private fun ancestor(view: View, expected: String): View? {
        var current: View? = view
        repeat(12) {
            if (current?.javaClass?.name == expected) return current
            current = current?.parent as? View
        }
        return null
    }

    companion object {
        private const val TAG = "LiquidVolume"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val COMPACT_PANEL = "com.android.systemui.volume.view.standard.VolumePanelView"
        private const val NATIVE_BLUR_DRAWABLE =
            "com.android.internal.graphics.drawable.BackgroundBlurDrawable"
        private const val BLUR_VIEW_ID = 0x7e0a1030
        private const val TRACK_ID = 0x7e0a1052
        private const val GLASS_BLUR_TINT: Int = 0x45FFFFFF
    }
}
