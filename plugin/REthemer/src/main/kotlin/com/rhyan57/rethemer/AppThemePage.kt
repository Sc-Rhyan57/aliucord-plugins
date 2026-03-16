package com.rhyan57.rethemer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage

private fun dp(ctx: Context, n: Int) = (n * ctx.resources.displayMetrics.density + 0.5f).toInt()

class AppThemePage : SettingsPage() {
    private val log = Logger("REthemer/AppThemePage")
    private var selectedTheme: DiscordTheme? = null
    private var previewGradient: View? = null
    private var previewTitle: TextView? = null
    private var previewSub: TextView? = null
    private var chipRow: LinearLayout? = null
    private var themeNameLabel: TextView? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("App Theme")
        buildUI(view.context)
    }

    private fun buildUI(ctx: Context) {
        val root = linearLayout

        val previewCard = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 180)
            ).also { it.setMargins(dp(ctx, 12), dp(ctx, 16), dp(ctx, 12), dp(ctx, 4)) }
            background = GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(Color.parseColor("#5865F2"), Color.parseColor("#EB459E"))
            ).apply { cornerRadius = dp(ctx, 16).toFloat() }

            val overlay = View(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#22000000"))
                    cornerRadius = dp(ctx, 16).toFloat()
                }
            }

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(dp(ctx, 20), 0, dp(ctx, 20), 0)
            }
            previewTitle = TextView(ctx).apply {
                text = "App Theme"
                setTextColor(Color.WHITE)
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            previewSub = TextView(ctx).apply {
                text = "Select a theme below to preview"
                setTextColor(Color.parseColor("#CCFFFFFF"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 4), 0, 0)
            }
            inner.addView(previewTitle)
            inner.addView(previewSub)
            addView(overlay)
            addView(inner)
            previewGradient = this
        }
        root.addView(previewCard)

        themeNameLabel = TextView(ctx).apply {
            text = "Select a theme"
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 2))
        }
        root.addView(themeNameLabel)

        val hScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 8)) }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8))
        }
        chipRow = row

        DiscordThemes.THEMES.forEachIndexed { idx, theme ->
            val chip = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp(ctx, 5), 0, dp(ctx, 5), 0) }

                val sz = dp(ctx, 56)
                val swatch = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(sz, sz)
                    background = GradientDrawable(
                        GradientDrawable.Orientation.BL_TR,
                        intArrayOf(theme.primaryColor, theme.secondaryColor)
                    ).apply { cornerRadius = dp(ctx, 14).toFloat() }
                }
                val lbl = TextView(ctx).apply {
                    text = theme.name.take(9)
                    setTextColor(Color.parseColor("#B5BAC1"))
                    textSize = 9f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(ctx, 3), 0, 0)
                }
                addView(swatch)
                addView(lbl)

                setOnClickListener {
                    selectedTheme = theme
                    themeNameLabel?.text = theme.name
                    updatePreview(ctx, theme)
                    highlightChip(idx)
                }
            }
            row.addView(chip)
        }
        hScroll.addView(row)
        root.addView(hScroll)

        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            ).also { it.setMargins(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 12)) }
            setBackgroundColor(Color.parseColor("#2E3035"))
        }
        root.addView(divider)

        val applyBtn = TextView(ctx).apply {
            text = "Apply Theme"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F2"))
                cornerRadius = dp(ctx, 14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 50)
            ).also { it.setMargins(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 6)) }
            setOnClickListener { applyTheme(ctx) }
        }
        root.addView(applyBtn)

        root.addView(TextView(ctx).apply {
            text = "With Nitro, syncs to all devices. Without Nitro, applied client-side via Themer."
            setTextColor(Color.parseColor("#72767D"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), 0, dp(ctx, 20), dp(ctx, 16))
        })
    }

    private fun updatePreview(ctx: Context, theme: DiscordTheme) {
        previewGradient?.background = GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(theme.primaryColor, theme.secondaryColor)
        ).apply { cornerRadius = dp(ctx, 16).toFloat() }
        previewTitle?.text = theme.name
        previewSub?.text = if (theme.isDark) "Dark theme" else "Light theme"
    }

    private fun highlightChip(selectedIdx: Int) {
        chipRow?.let { row ->
            for (i in 0 until row.childCount) {
                val chip = row.getChildAt(i) as? LinearLayout ?: continue
                val swatch = chip.getChildAt(0) ?: continue
                val bg = swatch.background as? GradientDrawable ?: continue
                bg.setStroke(
                    if (i == selectedIdx) dp(chip.context, 3) else 0,
                    if (i == selectedIdx) Color.WHITE else Color.TRANSPARENT
                )
            }
        }
    }

    private fun applyTheme(ctx: Context) {
        val theme = selectedTheme
        if (theme == null) {
            toast(ctx, "Select a theme first", true)
            return
        }

        if (theme.id == 0) {
            Utils.openPageWithProxy(ctx, ThemerManagerPage())
            return
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Applying theme…")
            .setMessage("'${theme.name}' is being applied…")
            .setCancelable(false)
            .create()
        dialog.show()

        Utils.threadPool.execute {
            val hasNitro = DiscordApi.hasNitro()
            log.info("applyTheme: hasNitro=$hasNitro theme=${theme.name}")

            if (hasNitro) {
                val (ok, resp) = DiscordApi.applyTheme(theme.protoPayload)
                log.info("proto result: ok=$ok resp=$resp")
                ThemerBridge.applyThemeToThemer(theme, theme.name)
                Utils.mainThread.post {
                    dialog.dismiss()
                    if (ok) {
                        toast(ctx, "Theme '${theme.name}' applied! Restart to see changes.")
                    } else {
                        toast(ctx, "Sync failed, applied client-side. Restart to see changes.")
                    }
                }
            } else {
                ThemerBridge.applyThemeToThemer(theme, theme.name)
                Utils.mainThread.post {
                    dialog.dismiss()
                    toast(ctx, "Theme '${theme.name}' applied! Restart to see changes.")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun toast(ctx: Context, msg: String, error: Boolean = false) {
        val t = Toast(ctx)
        t.view = TextView(ctx).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (error) "#ED4245" else "#23A55A"))
                cornerRadius = dp(ctx, 20).toFloat()
            }
        }
        t.duration = Toast.LENGTH_LONG
        t.show()
    }
}
