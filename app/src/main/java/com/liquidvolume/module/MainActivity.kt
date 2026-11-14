package com.liquidvolume.module

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Button

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
            dp(475f)
        ))

        val levelLabel = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFE8F2FF.toInt())
            setPadding(0, dp(12f), 0, dp(4f))
        }
        root.addView(levelLabel)
        preview.onLevelChanged = { step ->
            val percent = kotlin.math.round(step / 1.5f).toInt()
            levelLabel.text = if (step == 0) "Aperçu : muet" else "Aperçu : $percent %  ·  $step / 150"
        }
        preview.setPreviewStep(savedInstanceState?.getInt("previewStep") ?: 0)
        studioPreview = preview
        fun buttonRow(labels: List<String>, action: (Int) -> Unit) {
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                labels.forEachIndexed { index, label ->
                    addView(Button(this@MainActivity).apply {
                        text = label
                        textSize = 12f
                        isAllCaps = false
                        setTextColor(0xFFE8F2FF.toInt())
                        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF202D47.toInt())
                        setPadding(0, 0, 0, 0)
                        setOnClickListener { action(index) }
                    }, LinearLayout.LayoutParams(0, dp(48f), 1f).apply { setMargins(dp(2f), 0, dp(2f), 0) })
                }
            })
        }
        buttonRow(listOf("− 1 cran", "+ 1 cran")) {
            preview.setPreviewStep(preview.previewStep + if (it == 0) -1 else 1)
        }
        val presets = listOf(0, 1, 5, 33, 100)
        buttonRow(presets.map { "$it %" }) {
            preview.setPreviewStep(kotlin.math.round(presets[it] * 1.5f).toInt())
        }

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

        val nativeSizeToggle = TextView(this).apply {
            text = "Voir au format compact"
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextColor(0xFFC8DCF6.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(15f).toFloat()
                setColor(0xFF111C31.toInt())
                setStroke(dp(1f), 0xFF3B5170.toInt())
            }
            setOnClickListener {
                preview.useNativeDimensions = !preview.useNativeDimensions
                text = if (preview.useNativeDimensions) {
                    "Revenir à l’aperçu agrandi"
                } else {
                    "Voir au format compact"
                }
            }
        }
        root.addView(nativeSizeToggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42f)
        ).apply { topMargin = dp(8f) })

        root.addView(TextView(this).apply {
            text = "Liquid Glass  •  Aperçu agrandi"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(0, dp(19f), 0, dp(6f))
        })
        root.addView(TextView(this).apply {
            text = "Glisse sur la capsule ou utilise les crans pour comparer les faibles niveaux. " +
                "Le haut-parleur et le remplissage correspondent au style du panneau actuel. " +
                "Cet aperçu simule le volume sans modifier le son du téléphone. " +
                "Le flou réel reste celui du panneau système."
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

    private var studioPreview: LiquidGlassPreviewView? = null
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("previewStep", studioPreview?.previewStep ?: 0)
        super.onSaveInstanceState(outState)
    }
}

