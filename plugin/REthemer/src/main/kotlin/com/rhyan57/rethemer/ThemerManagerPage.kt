package com.rhyan57.rethemer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aliucord.PluginManager
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage

private fun dpT(ctx: Context, n: Int) = (n * ctx.resources.displayMetrics.density + 0.5f).toInt()

class ThemerManagerPage : SettingsPage() {

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Themer Themes")
        buildUI(view.context)
    }

    private fun buildUI(ctx: Context) {
        val root = linearLayout

        root.addView(TextView(ctx).apply {
            text = "INSTALLED THEMER THEMES"
            setTextColor(Color.parseColor("#72767D")); textSize = 10f
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
            setPadding(dpT(ctx, 16), dpT(ctx, 12), dpT(ctx, 16), dpT(ctx, 6))
        })

        val themes = ThemerBridge.listInstalledThemes()
        if (themes.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = "No Themer themes installed.\nInstall themes via the Themer plugin."
                setTextColor(Color.parseColor("#72767D")); textSize = 13f; gravity = Gravity.CENTER
                setPadding(dpT(ctx, 16), dpT(ctx, 24), dpT(ctx, 16), dpT(ctx, 24))
            })
        } else {
            val themerSettings = PluginManager.plugins["Themer"]?.settings
            themes.forEach { themeName ->
                val isEnabled = themerSettings?.getBool("$themeName-enabled", false) == true
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpT(ctx, 56)
                    ).also { it.setMargins(dpT(ctx, 12), dpT(ctx, 2), dpT(ctx, 12), dpT(ctx, 2)) }
                    background = GradientDrawable().apply {
                        setColor(if (isEnabled) Color.parseColor("#1E3A1F") else Color.parseColor("#1E1F26"))
                        cornerRadius = dpT(ctx, 10).toFloat()
                    }
                    setPadding(dpT(ctx, 14), 0, dpT(ctx, 14), 0)
                }
                row.addView(TextView(ctx).apply {
                    text = themeName.replace("_", " ")
                    setTextColor(if (isEnabled) Color.parseColor("#23A55A") else Color.parseColor("#F2F3F5"))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(ctx).apply {
                    text = if (isEnabled) "Active" else "Apply"
                    setTextColor(if (isEnabled) Color.parseColor("#23A55A") else Color.parseColor("#5865F2"))
                    textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        if (themerSettings != null) {
                            ThemerBridge.listInstalledThemes().forEach { n ->
                                themerSettings.setBool("$n-enabled", n == themeName)
                            }
                            toast(ctx, "Theme '$themeName' activated! Restarting…")
                            Utils.appActivity.recreate()
                        } else {
                            toast(ctx, "Themer plugin not found!", true)
                        }
                    }
                })
                root.addView(row)
            }
        }

        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpT(ctx, 1)).also {
                it.setMargins(dpT(ctx, 16), dpT(ctx, 12), dpT(ctx, 16), dpT(ctx, 12))
            }
            setBackgroundColor(Color.parseColor("#2E3035"))
        })

        root.addView(TextView(ctx).apply {
            text = "Install themes via the Themer plugin in Aliucord settings."
            setTextColor(Color.parseColor("#72767D")); textSize = 11f; gravity = Gravity.CENTER
            setPadding(dpT(ctx, 16), 0, dpT(ctx, 16), dpT(ctx, 16))
        })
    }

    @Suppress("DEPRECATION")
    private fun toast(ctx: Context, msg: String, error: Boolean = false) {
        val t = Toast(ctx)
        t.view = TextView(ctx).apply {
            text = msg; setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dpT(ctx, 16), dpT(ctx, 12), dpT(ctx, 16), dpT(ctx, 12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (error) "#ED4245" else "#23A55A"))
                cornerRadius = dpT(ctx, 20).toFloat()
            }
        }
        t.duration = Toast.LENGTH_LONG; t.show()
    }
}
