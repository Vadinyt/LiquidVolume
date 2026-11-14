package com.liquidvolume.module

import android.view.View

/** Samsung's actual backend, confirmed in the supplied Android 17 SystemUI APK. */
internal class SamsungGlassBlur {
    private val infoClass = Class.forName("android.view.SemBlurInfo")
    private val builderClass = Class.forName("android.view.SemBlurInfo\$Builder")
    private val constructor = builderClass.getConstructor(Int::class.javaPrimitiveType)
    private val radius = builderClass.getMethod("setRadius", Int::class.javaPrimitiveType)
    private val color = builderClass.getMethod("setBackgroundColor", Int::class.javaPrimitiveType)
    private val corner = builderClass.getMethod("setBackgroundCornerRadius", Float::class.javaPrimitiveType)
    private val build = builderClass.getMethod("build")
    private val setter = View::class.java.getMethod("semSetBlurInfo", infoClass)
    // Samsung View stores this privately; older framework builds expose no getter.
    // Match the verified type rather than relying on a guessed method/field name.
    private val infoField = View::class.java.declaredFields.single {
        it.type == infoClass && !java.lang.reflect.Modifier.isStatic(it.modifiers)
    }.apply { isAccessible = true }

    fun current(view: View): Any? = infoField.get(view)

    fun create(view: View): Any {
        // Mode 0 samples the live backdrop. No screenshot and no color-curve
        // preset that lifts a black backdrop into Samsung's gray material.
        val builder = constructor.newInstance(0)
        radius.invoke(builder, (28f * view.resources.displayMetrics.density).toInt().coerceIn(48, 120))
        color.invoke(builder, 0x00000000)
        corner.invoke(builder, view.width / 2f)
        return checkNotNull(build.invoke(builder))
    }

    fun set(view: View, info: Any?) {
        setter.invoke(view, info)
    }
}
