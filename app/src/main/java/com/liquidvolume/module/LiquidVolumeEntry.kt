package com.liquidvolume.module

import android.app.Application
import android.util.Log
import android.view.View
import android.widget.SeekBar
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
            val seekClass = Class.forName("com.android.systemui.volume.view.VolumeSeekBar", false, loader)
            val track = seekClass.getDeclaredMethod("trackTouchEvent", Float::class.javaPrimitiveType)
            val touchedX = seekClass.getDeclaredField("touchedX").apply { isAccessible = true }
            val currentProgress = seekClass.getDeclaredField("currentProgress").apply { isAccessible = true }
            val stream = seekClass.getDeclaredField("stream").apply { isAccessible = true }
            var reportedFailure = false
            hook(track).setId("liquidvolume.compact.precision").intercept { chain ->
                val nativeDelta = chain.args[0] as Float
                var delta = nativeDelta
                try {
                    val seek = chain.thisObject as? SeekBar
                    if (seek != null && CompactGlassStyler.ownsSeek(seek) &&
                        stream.getInt(seek) == 3 && kotlin.math.abs(seek.rotation - 270f) < 1f) {
                        delta = PrecisionDrag.remap(nativeDelta,
                            touchedX.getFloat(seek) - seek.paddingLeft,
                            (seek.width - seek.paddingLeft - seek.paddingRight).toFloat(),
                            currentProgress.getInt(seek), seek.min, seek.max)
                    }
                } catch (error: Throwable) {
                    if (!reportedFailure) {
                        reportedFailure = true
                        log(Log.ERROR, TAG, "GLASS_PRECISION_FAILED_NATIVE_FALLBACK", error)
                    }
                }
                // Exactly one original call, with native tracking and listeners.
                // Never catch/retry an exception raised by Samsung's own method.
                if (delta == nativeDelta) chain.proceed()
                else chain.proceed(arrayOf<Any>(delta))
            }
            log(Log.INFO, TAG, "GLASS_PRECISION_HOOK_INSTALLED")
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "GLASS_PRECISION_HOOK_FAILED", error)
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
