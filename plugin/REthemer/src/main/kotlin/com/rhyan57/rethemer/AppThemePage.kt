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
    private var previewCard: FrameLayout? = null
    private var previewLabel: TextView? = null
    private var chipRow: LinearLayout? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("App Theme")
        buildUI(view.context)
    }

    private fun buildUI(ctx: Context) {
        val root = linearLayout

        val card = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 190)
            ).also { it.setMargins(dp(ctx, 12), dp(ctx, 16), dp(ctx, 12), dp(ctx, 8)) }
            background = GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(Color.parseColor("#5865F2"), Color.parseColor("#EB459E"))
            ).apply { cornerRadius = dp(ctx, 16).toFloat() }

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val title = TextView(ctx).apply {
                text = "App Theme"
                setTextColor(Color.WHITE)
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val subtitle = TextView(ctx).apply {
                text = "Select a theme below to preview"
                setTextColor(Color.parseColor("#CCFFFFFF"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 4), 0, 0)
            }
            previewLabel = subtitle
            inner.addView(title)
            inner.addView(subtitle)
            addView(inner)
            previewCard = this
        }
        root.addView(card)

        val themeNameTv = TextView(ctx).apply {
            text = "This will change the theme across all your devices."
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 16), dp(ctx, 6), dp(ctx, 16), dp(ctx, 4))
        }
        root.addView(themeNameTv)

        val hScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, 0) }
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
        }
        chipRow = row

        DiscordThemes.THEMES.forEachIndexed { idx, theme ->
            val sz = dp(ctx, 60)
            val chip = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp(ctx, 4), 0, dp(ctx, 4), 0) }

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
                    themeNameTv.text = theme.name
                    updatePreview(ctx, theme)
                    highlightChip(idx)
                }
            }
            row.addView(chip)
        }

        hScroll.addView(row)
        root.addView(hScroll)

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
            ).also { it.setMargins(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 4)) }
            setOnClickListener { applyTheme(ctx) }
        }
        root.addView(applyBtn)

        root.addView(TextView(ctx).apply {
            text = "With Nitro, syncs to all devices. Without Nitro, applied client-side via Themer."
            setTextColor(Color.parseColor("#72767D"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), dp(ctx, 4), dp(ctx, 20), dp(ctx, 16))
        })
    }

    private fun updatePreview(ctx: Context, theme: DiscordTheme) {
        previewCard?.background = GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(theme.primaryColor, theme.secondaryColor)
        ).apply { cornerRadius = dp(ctx, 16).toFloat() }
        previewLabel?.text = if (theme.isDark) "Dark theme" else "Light theme"
    }

    private fun highlightChip(selectedIdx: Int) {
        chipRow?.let { row ->
            for (i in 0 until row.childCount) {
                val chip = row.getChildAt(i) as? LinearLayout ?: continue
                val swatch = chip.getChildAt(0) ?: continue
                val bg = swatch.background as? GradientDrawable ?: continue
                bg.setStroke(
                    if (i == selectedIdx) dp(chip.context, 3) else 0,
                    Color.WHITE
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
            .setTitle("Applying…")
            .setMessage("Applying '${theme.name}'…")
            .setCancelable(false)
            .create()
        dialog.show()

        Utils.threadPool.execute {
            val hasNitro = DiscordApi.hasNitro()
            log.info("applyTheme: hasNitro=$hasNitro theme=${theme.name}")

            if (hasNitro) {
                val (ok, resp) = DiscordApi.applyTheme(theme.protoPayload)
                log.info("proto: ok=$ok resp=$resp")
            }

            ThemerBridge.applyThemeToThemer(theme, theme.name)

            Utils.mainThread.post {
                dialog.dismiss()
                val msg = if (hasNitro)
                    "Theme '${theme.name}' applied! Restart to see changes."
                else
                    "Theme '${theme.name}' applied client-side! Restart to see changes."
                toast(ctx, msg)
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
