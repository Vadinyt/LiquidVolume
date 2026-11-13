package com.liquidvolume.module

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

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
            setPadding(dp(20f), dp(18f), dp(20f), dp(16f))
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

        root.addView(LiquidGlassPreviewView(this).apply {
            clipToOutline = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(25f).toFloat()
                setColor(0xFF15213A.toInt())
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(475f)
        ))

        root.addView(TextView(this).apply {
            text = "Compact Volume  •  Frosted Pearl"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(0, dp(19f), 0, dp(6f))
        })
        root.addView(TextView(this).apply {
            text = "Glisse sur la capsule pour voir le remplissage. " +
                "Aperçu visuel uniquement : aucun changement du volume réel dans cet écran. " +
                "La version stable de SystemUI reste inchangée."
            textSize = 13f
            setTextColor(0xFFB4C2D5.toInt())
            setLineSpacing(dp(3f).toFloat(), 1f)
        })

        setContentView(root)
    }
}
