package com.rhyan57.rethemer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
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
            text = "INSTALLED THEMES"; setTextColor(Color.parseColor("#72767D")); textSize = 10f
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
            setPadding(dpT(ctx, 16), dpT(ctx, 12), dpT(ctx, 16), dpT(ctx, 6))
        })

        val themes = ThemerBridge.listInstalledThemes()
        if (themes.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = "No themes installed yet."
                setTextColor(Color.parseColor("#72767D")); textSize = 13f; gravity = Gravity.CENTER
                setPadding(dpT(ctx, 16), dpT(ctx, 24), dpT(ctx, 16), dpT(ctx, 24))
            })
        } else {
            val prefs = try { SettingsAPI("REthemer") } catch (_: Exception) { null }
            val active = prefs?.getString("active_theme", null)
            themes.forEach { themeName ->
                val isActive = themeName == active
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpT(ctx, 56)).also {
                        it.setMargins(dpT(ctx, 12), dpT(ctx, 2), dpT(ctx, 12), dpT(ctx, 2))
                    }
                    background = GradientDrawable().apply {
                        setColor(if (isActive) Color.parseColor("#1E3A1F") else Color.parseColor("#1E1F26"))
                        cornerRadius = dpT(ctx, 10).toFloat()
                    }
                    setPadding(dpT(ctx, 14), 0, dpT(ctx, 14), 0)
                }
                row.addView(TextView(ctx).apply {
                    text = themeName.replace("_", " ")
                    setTextColor(if (isActive) Color.parseColor("#23A55A") else Color.parseColor("#F2F3F5"))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(ctx).apply {
                    text = if (isActive) "Active" else "Apply"
                    setTextColor(if (isActive) Color.parseColor("#23A55A") else Color.parseColor("#5865F2"))
                    textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                    setOnClickListener {
                        val applied = ThemerBridge.applyThemeFromFile(themeName)
                        if (applied) {
                            prefs?.setString("active_theme", themeName)
                            toast(ctx, "Theme '$themeName' applied! Restarting…")
                            Utils.appActivity.recreate()
                        } else {
                            toast(ctx, "Failed to apply theme.", true)
                        }
                    }
                })
                root.addView(row)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun toast(ctx: Context, msg: String, error: Boolean = false) {
        val t = Toast(ctx)
        t.view = TextView(ctx).apply {
            text = msg; setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dpT(ctx, 16), dpT(ctx, 12), dpT(ctx, 16), dpT(ctx, 12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (error) "#ED4245" else "#23A55A")); cornerRadius = dpT(ctx, 20).toFloat()
            }
        }
        t.duration = Toast.LENGTH_LONG; t.show()
    }
}
