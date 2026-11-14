package com.liquidvolume.module

import android.app.Application
import android.util.Log
import android.view.View
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** Samsung retains audio, progress, warnings, touch, accessibility and animation. */
class LiquidVolumeEntry : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "Loaded ${BuildConfig.VERSION_NAME}; framework API=${getApiVersion()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName != SYSTEM_UI || Application.getProcessName() != SYSTEM_UI) return
        val loader = param.defaultClassLoader
        try {
            val drawableClass = Class.forName("com.android.systemui.volume.view.SecRoundedCornerSeekBarDrawable", false, loader)
            val draw = drawableClass.getDeclaredMethod("draw", Canvas::class.java)
            val level = drawableClass.getDeclaredField("level").apply { isAccessible = true }
            val start = drawableClass.getDeclaredField("startColor").apply { isAccessible = true }
            val end = drawableClass.getDeclaredField("endColor").apply { isAccessible = true }
            val button = drawableClass.getDeclaredField("buttonColor").apply { isAccessible = true }
            var reportedFailure = false
            hook(draw).setId("liquidvolume.compact.lowfill").intercept { chain ->
                var handled = false
                val drawable = chain.thisObject as? Drawable
                try {
                    if (drawable != null) {
                        val renderer = LowVolumeFill.renderer(drawable)
                        if (renderer != null) handled = renderer.draw(chain.args[0] as Canvas,
                            drawable.bounds, level.getInt(drawable), start.getInt(drawable),
                            end.getInt(drawable), button.getInt(drawable))
                    }
                } catch (error: Throwable) {
                    drawable?.let { LowVolumeFill.remove(it) }
                    if (!reportedFailure) {
                        reportedFailure = true
                        log(Log.ERROR, TAG, "GLASS_LOWFILL_FAILED_NATIVE_FALLBACK", error)
                    }
                }
                // Above the low-level branch, Samsung draws exactly as before.
                if (handled) null else chain.proceed()
            }
            log(Log.INFO, TAG, "GLASS_LOWFILL_HOOK_INSTALLED")
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "GLASS_LOWFILL_HOOK_FAILED", error)
        }
        try {
            val icon = Class.forName("com.android.systemui.volume.view.icon.VolumeIcon", false, loader)
            val update = icon.declaredMethods.single { it.name == "updateIconState" && it.parameterTypes.size == 2 }
            hook(update).setId("liquidvolume.media.speaker").intercept { chain ->
                val result = chain.proceed()
                try {
                    val host = chain.thisObject as? View
                    val row = chain.args[0]
                    if (host != null && row != null) MediaSpeakerSkin.nativeUpdated(host, row)
                } catch (error: Throwable) {
                    log(Log.ERROR, TAG, "GLASS_SPEAKER_FAILED", error)
                }
                result
            }
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "GLASS_SPEAKER_HOOK_FAILED", error)
        }
        try {
            val seekClass = Class.forName("com.android.systemui.volume.view.VolumeSeekBar", false, loader)
            val enabled = seekClass.getDeclaredMethod("setEnabled", Boolean::class.javaPrimitiveType)
            hook(enabled).setId("liquidvolume.compact.fill.enabled").intercept { chain ->
                val result = chain.proceed()
                try {
                    (chain.thisObject as? View)?.let { CompactGlassStyler.onNativeEnabledChanged(it) }
                } catch (error: Throwable) {
                    log(Log.ERROR, TAG, "GLASS_ENABLED_STYLE_FAILED", error)
                }
                result
            }
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "GLASS_ENABLED_HOOK_FAILED", error)
        }
        try {
            val panelClass = Class.forName(CompactGlassStyler.PANEL_CLASS, false, loader)
            val blurClass = panelClass.getDeclaredField("blurEffect").type
            val method = blurClass.getDeclaredMethod("setRealTimeBlur", View::class.java,
                Int::class.javaPrimitiveType, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            hook(method).setId("liquidvolume.compact.liquidglass").intercept { chain ->
                // Always preserve the native call, including its accessibility gate.
                val result = chain.proceed()
                try {
                    (chain.args[0] as? View)?.let { CompactGlassStyler.schedule(it) }
                } catch (error: Throwable) {
                    log(Log.ERROR, TAG, "GLASS_SCHEDULE_FAILED", error)
                }
                result
            }
            log(Log.INFO, TAG, "GLASS_HOOK_INSTALLED backend=SemBlurInfo")
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "GLASS_BLUR_HOOK_FAILED", error)
        }
        try {
            // Styling must also run when Samsung's feature flags skip its blur call.
            // initialize is confirmed in the supplied APK; this is not a draw hook.
            val rowClass = Class.forName(CompactGlassStyler.ROW_CLASS, false, loader)
            val initialize = rowClass.declaredMethods.single {
                it.name == "initialize" && it.parameterTypes.size == 5
            }
            hook(initialize).setId("liquidvolume.compact.initialize").intercept { chain ->
                val result = chain.proceed()
                try {
                    (chain.thisObject as? View)?.let { CompactGlassStyler.schedule(it) }
                } catch (error: Throwable) {
                    log(Log.ERROR, TAG, "GLASS_INITIALIZE_SCHEDULE_FAILED", error)
                }
                result
            }
            log(Log.INFO, TAG, "GLASS_ROW_HOOK_INSTALLED")
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "GLASS_ROW_HOOK_FAILED", error)
        }
    }

    companion object {
        private const val TAG = "LiquidVolume"
        private const val SYSTEM_UI = "com.android.systemui"
    }
}
