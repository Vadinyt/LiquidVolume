package com.liquidvolume.module

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

/**
 * Design studio, not an alternate system-volume UI.
 * The real compact panel remains owned by Samsung SystemUI.
 */
class MainActivity : Activity() {

    private val density get() = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 12, 22)
        window.navigationBarColor = Color.rgb(8, 12, 22)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(52f), dp(20f), dp(16f))
            setBackgroundColor(Color.rgb(8, 12, 22))
        }

        root.addView(TextView(this).apply {
            text = "Liquid Volume"
            setTextColor(Color.WHITE)
            textSize = 27f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "LIQUID GLASS  /  DESIGN STUDIO"
            setTextColor(0xFF9BAEC9.toInt())
            textSize = 11f
            letterSpacing = 0.14f
            setPadding(0, dp(4f), 0, dp(16f))
        })

        val preview = LiquidGlassPreviewView(this).apply {
            clipToOutline = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(25f).toFloat()
                setColor(0xFF15213A.toInt())
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(435f)
        ))

        val backgroundToggle = TextView(this).apply {
            text = "Voir sur fond noir"
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextColor(0xFFE8F2FF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(15f).toFloat()
                setColor(0xFF202D47.toInt())
                setStroke(dp(1f), 0xFF4C607C.toInt())
            }
            setOnClickListener {
                preview.showDarkBackdrop = !preview.showDarkBackdrop
                text = if (preview.showDarkBackdrop) {
                    "Voir sur fond coloré"
                } else {
                    "Voir sur fond noir"
                }
            }
        }
        root.addView(backgroundToggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)
        ).apply { topMargin = dp(12f) })

        root.addView(TextView(this).apply {
            text = "Compact Volume  •  Frosted Pearl"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(0, dp(19f), 0, dp(6f))
        })
        root.addView(TextView(this).apply {
            text = "Glisse sur la capsule pour prévisualiser le niveau. " +
                "Le panneau réel conserve les icônes, le flou et les commandes audio Samsung. " +
                "Le fond noir permet de comparer les deux rendus."
            textSize = 13f
            setTextColor(0xFFB4C2D5.toInt())
            setLineSpacing(dp(3f).toFloat(), 1f)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root)
        }
        setContentView(scroll)
    }
}
