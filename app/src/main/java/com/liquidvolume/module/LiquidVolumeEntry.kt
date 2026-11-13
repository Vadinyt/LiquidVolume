package com.liquidvolume.module

import android.app.Application
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
        val process = Application.getProcessName()
        if (process != TARGET_PACKAGE) {
            log(Log.INFO, TAG, "Skipping secondary process: $process")
            return
        }
        log(Log.INFO, TAG, "Main SystemUI process confirmed: $process")
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
        inspectVolumeMethods(loader)
        missing.forEach { log(Log.WARN, TAG, "MISSING $it") }
        log(Log.INFO, TAG, "Diagnostic-only build: no hooks installed; native audio path untouched")
    }
    /** Reflection-only inventory. No method invocation, hooks, or view changes. */
    private fun inspectVolumeMethods(loader: ClassLoader) {
        val targets = listOf(
            "com.android.systemui.volume.SamsungVolumeDialogImpl",
            "com.android.systemui.volume.VolumePanelDialogDelegate",
            "com.android.systemui.volume.VolumePanelImpl",
            "com.android.systemui.volume.dialog.ui.binder.VolumeDialogViewBinder",
            "com.android.systemui.volume.dialog.sliders.ui.VolumeDialogSliderViewBinder"
        )
        for (name in targets) {
            try {
                val type = Class.forName(name, false, loader)
                val matches = type.declaredMethods
                    .filter { method ->
                        val n = method.name.lowercase()
                        listOf("show", "dismiss", "bind", "inflate", "create", "update", "init", "attach").any { n.contains(it) }
                    }
                    .sortedBy { it.name }
                    .take(18)
                log(Log.INFO, TAG, "METHOD_SCAN ${type.simpleName}: ${matches.size} selected methods")
                for (method in matches) {
                    try {
                        val args = method.parameterTypes.joinToString(",") { it.simpleName }
                        log(Log.INFO, TAG, "METHOD ${type.simpleName}.${method.name}($args):${method.returnType.simpleName}")
                    } catch (e: Throwable) {
                        log(Log.WARN, TAG, "METHOD_SIGNATURE_FAILED ${type.simpleName}: ${e.javaClass.simpleName}")
                    }
                }
            } catch (e: Throwable) {
                log(Log.WARN, TAG, "METHOD_SCAN_FAILED $name: ${e.javaClass.simpleName}")
            }
        }
        log(Log.INFO, TAG, "Method inventory complete; no hooks installed")
    }

    companion object {
        private const val TAG = "LiquidVolume"
        private const val TARGET_PACKAGE = "com.android.systemui"
    }
}
