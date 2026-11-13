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
            text = "Liquid Volume · 0.2.0-dev"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Phase 1: interface analysis\n\nTarget: Samsung SystemUI\nFramework: modern LibXposed API 102\n\nDiagnostic build only. Check LSPosed logs for LiquidVolume."
            textSize = 16f
            setPadding(0, 28, 0, 0)
        })
        setContentView(root)
    }
}
