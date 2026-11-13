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
        inspectVolumeFields(loader)
        inspectWindowStructure(loader)
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

    /** Identify view/dialog references without reading live instance state. */
    private fun inspectVolumeFields(loader: ClassLoader) {
        val targets = listOf(
            "com.android.systemui.volume.SamsungVolumeDialogImpl",
            "com.android.systemui.volume.VolumePanelDialogDelegate",
            "com.android.systemui.volume.VolumePanelImpl",
            "com.android.systemui.volume.dialog.ui.binder.VolumeDialogViewBinder"
        )
        for (name in targets) {
            try {
                val type = Class.forName(name, false, loader)
                val fields = type.declaredFields
                    .filter { field ->
                        val n = field.name.lowercase()
                        val t = field.type.name.lowercase()
                        listOf("view", "dialog", "window", "panel", "slider", "volume", "blur", "background")
                            .any { n.contains(it) || t.contains(it) }
                    }
                    .sortedBy { it.name }
                    .take(24)
                log(Log.INFO, TAG, "FIELD_SCAN ${type.simpleName}: ${fields.size} selected fields")
                for (field in fields) {
                    log(Log.INFO, TAG, "FIELD ${type.simpleName}.${field.name}:${field.type.name}")
                }
            } catch (e: Throwable) {
                log(Log.WARN, TAG, "FIELD_SCAN_FAILED $name: ${e.javaClass.simpleName}")
            }
        }
        log(Log.INFO, TAG, "Phase 0.4 inspection complete; no hooks installed")
    }

    /** Read-only inventory of the Samsung standard volume window and its view types. */
    private fun inspectWindowStructure(loader: ClassLoader) {
        val targets = listOf(
            "com.android.systemui.volume.view.standard.VolumePanelWindow",
            "com.android.systemui.volume.view.standard.VolumePanelView",
            "com.android.systemui.volume.view.standard.VolumePanelRowView"
        )
        for (name in targets) {
            try {
                val type = Class.forName(name, false, loader)
                log(Log.INFO, TAG, "WINDOW_CLASS_FOUND $name superclass=${type.superclass?.name}")
                type.declaredConstructors.take(5).forEach {
                    log(Log.INFO, TAG, "WINDOW_CONSTRUCTOR ${type.simpleName}(${it.parameterTypes.joinToString(",") { p -> p.simpleName }})")
                }
                type.declaredFields.sortedBy { it.name }.take(35).forEach {
                    log(Log.INFO, TAG, "WINDOW_FIELD ${type.simpleName}.${it.name}:${it.type.name}")
                }
                type.declaredMethods.filter {
                    val n = it.name.lowercase()
                    listOf("show", "dismiss", "create", "inflate", "view", "window", "background", "blur", "attach", "layout").any(n::contains)
                }.sortedBy { it.name }.take(35).forEach {
                    log(Log.INFO, TAG, "WINDOW_METHOD ${type.simpleName}.${it.name}(${it.parameterTypes.joinToString(",") { p -> p.simpleName }}):${it.returnType.simpleName}")
                }
            } catch (e: Throwable) {
                log(Log.WARN, TAG, "WINDOW_CLASS_MISSING $name: ${e.javaClass.simpleName}")
            }
        }
        log(Log.INFO, TAG, "Window structure inventory complete; visual changes disabled")
    }

    companion object {
        private const val TAG = "LiquidVolume"
        private const val TARGET_PACKAGE = "com.android.systemui"
    }
}
