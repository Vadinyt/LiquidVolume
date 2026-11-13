package com.liquidvolume.module

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Liquid Volume " + BuildConfig.VERSION_NAME
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Compact Samsung volume panel\n\nNative background blur with translucent white tint.\n\nScope: System UI (com.android.systemui)\nFramework: LibXposed API 102\n\nEnable the module in LSPosed and restart SystemUI or your phone to apply changes."
            textSize = 16f
            setPadding(0, 28, 0, 0)
        })
        setContentView(root)
    }
}
