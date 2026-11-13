package com.liquidvolume.module

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** Diagnostic-only API 102 module. Does not install hooks or alter audio. */
class LiquidVolumeEntry : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "Loaded; runtime API=${getApiVersion()}, framework=${getFrameworkName()} ${getFrameworkVersion()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName != TARGET_PACKAGE) return
        val loader = param.defaultClassLoader
        val candidates = listOf(
            "com.android.systemui.volume.SamsungVolumeDialogImpl",
            "com.android.systemui.volume.VolumePanelDialogDelegate",
            "com.android.systemui.volume.VolumePanelImpl",
            "com.android.systemui.volume.VolumeDialogControllerImpl",
            "com.android.systemui.volume.panel.ui.viewmodel.VolumePanelViewModel",
            "com.android.systemui.volume.dialog.ui.binder.VolumeDialogViewBinder",
            "com.android.systemui.volume.dialog.sliders.ui.VolumeDialogSliderViewBinder",
            "com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel",
            "com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel",
            "com.samsung.systemui.splugins.volume.VolumePanelState",
            "com.samsung.systemui.splugins.volume.VolumePanelAction"
        )
        val present = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (name in candidates) {
            try {
                Class.forName(name, false, loader)
                present += name
            } catch (_: Throwable) {
                missing += name
            }
        }
        log(Log.INFO, TAG, "SystemUI class scan: ${present.size}/${candidates.size} candidates present")
        present.forEach { log(Log.INFO, TAG, "FOUND $it") }
        missing.forEach { log(Log.WARN, TAG, "MISSING $it") }
        log(Log.INFO, TAG, "Diagnostic-only build: no hooks installed; native audio path untouched")
    }
    companion object {
        private const val TAG = "LiquidVolume"
        private const val TARGET_PACKAGE = "com.android.systemui"
    }
}
