package com.liquidvolume.module

import android.app.Application
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicInteger

/**
 * One UI compact volume glass: edit only Samsung's native blur tint.
 * No replacement views, audio hooks, dialog resizing or overlays.
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
            val panel = Class.forName(COMPACT_PANEL, false, param.defaultClassLoader)
            val effect = panel.declaredFields.firstOrNull { it.name == "blurEffect" }?.type
                ?: run {
                    log(Log.WARN, TAG, "GLASS_UNAVAILABLE: Samsung blurEffect field missing")
                    return
                }
            val method = effect.declaredMethods.firstOrNull {
                it.name == "setRealTimeBlur" &&
                    it.parameterTypes.size == 4 &&
                    View::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[2] == Float::class.javaPrimitiveType &&
                    it.parameterTypes[3] == Int::class.javaPrimitiveType
            } ?: run {
                log(Log.WARN, TAG, "GLASS_UNAVAILABLE: native blur signature changed")
                return
            }

            val calls = AtomicInteger(0)
            hook(method).setId("liquidvolume.compact.glass").intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                if (view == null || !belongsToCompactPanel(view)) {
                    return@intercept chain.proceed()
                }

                val args = chain.args.toTypedArray()
                val originalTint = args[1] as? Int ?: return@intercept chain.proceed()
                // Samsung's previous tint was 0x80C9C9CC. Replace the gray tint
                // BEFORE it creates/configures the real BackgroundBlurDrawable.
                args[1] = GLASS_TINT
                val result = chain.proceed(args)

                // Some Samsung builds may override the color after the blur call.
                // Update the SAME native drawable, without adding any view/foreground.
                applyNativeTint(view)
                view.post { applyNativeTint(view) }

                if (calls.incrementAndGet() <= 5) {
                    log(Log.INFO, TAG, "GLASS_TINT_APPLIED original=0x${originalTint.toUInt().toString(16)} new=0x${GLASS_TINT.toUInt().toString(16)}")
                }
                result
            }
            log(Log.INFO, TAG, "GLASS_HOOK_INSTALLED: compact Samsung panel only")
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "GLASS_HOOK_FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun belongsToCompactPanel(view: View): Boolean {
        var current: View? = view
        var levels = 0
        while (current != null && levels++ < 12) {
            if (current.javaClass.name == COMPACT_PANEL) return true
            current = current.parent as? View
        }
        return false
    }

    private fun applyNativeTint(view: View) {
        val native = view.background ?: return
        if (native.javaClass.name != NATIVE_BLUR_DRAWABLE) return
        try {
            native.javaClass.getMethod("setColor", Int::class.javaPrimitiveType)
                .invoke(native, GLASS_TINT)
            // Drawable alpha is BLUR REGION alpha, not the tint's alpha.
            native.alpha = 255
            native.invalidateSelf()
            view.invalidate()
        } catch (e: Throwable) {
            log(Log.WARN, TAG, "GLASS_NATIVE_COLOR_UNAVAILABLE: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "LiquidVolume"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val COMPACT_PANEL = "com.android.systemui.volume.view.standard.VolumePanelView"
        private const val NATIVE_BLUR_DRAWABLE =
            "com.android.internal.graphics.drawable.BackgroundBlurDrawable"
        private const val GLASS_TINT: Int = 0x58FFFFFF
    }
}
