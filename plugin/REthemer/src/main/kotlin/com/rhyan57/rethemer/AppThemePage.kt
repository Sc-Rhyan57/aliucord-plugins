package com.rhyan57.rethemer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aliucord.Logger
import com.aliucord.fragments.SettingsPage

private fun dp(ctx: Context, n: Int) = (n * ctx.resources.displayMetrics.density + 0.5f).toInt()

class AppThemePage : SettingsPage() {
    private val log = Logger("REthemer/AppThemePage")
    private var selectedId = -1
    private var selectedLabel: TextView? = null
    private var chipRow: LinearLayout? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("App Theme")
        val ctx = view.context
        log.info("AppThemePage: onViewBound")
        buildUI(ctx)
    }

    private fun buildUI(ctx: Context) {
        val preview = makePreviewCard(ctx)
        linearLayout.addView(preview)

        selectedLabel = TextView(ctx).apply {
            text = "Select a theme below"
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 8))
        }
        linearLayout.addView(selectedLabel)

        val hScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(ctx, 8), 0, dp(ctx, 8), dp(ctx, 12)) }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 8))
        }
        chipRow = row
        DiscordThemes.THEMES.forEachIndexed { i, theme ->
            val chip = makeChip(ctx, theme, i)
            row.addView(chip)
        }
        hScroll.addView(row)
        linearLayout.addView(hScroll)

        val applyBtn = TextView(ctx).apply {
            text = "Apply Theme"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F2"))
                cornerRadius = dp(ctx, 14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 48)
            ).also { it.setMargins(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 8)) }
            setOnClickListener { applyTheme(ctx) }
        }
        linearLayout.addView(applyBtn)

        val note = TextView(ctx).apply {
            text = "Changing theme affects all devices. Restart Aliucord after applying."
            setTextColor(Color.parseColor("#72767D"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 16))
        }
        linearLayout.addView(note)
    }

    private fun makePreviewCard(ctx: Context): FrameLayout {
        return FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 160)
            ).also { it.setMargins(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 4)) }
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR,
                intArrayOf(Color.parseColor("#5865F2"), Color.parseColor("#EB459E"))
            ).apply { cornerRadius = dp(ctx, 14).toFloat() }

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            inner.addView(TextView(ctx).apply {
                text = "Theme Preview"
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            inner.addView(TextView(ctx).apply {
                text = "Select a theme to preview it"
                setTextColor(Color.parseColor("#DDDDDD"))
                textSize = 13f
                gravity = Gravity.CENTER
            })
            addView(inner)
        }
    }

    private fun makeChip(ctx: Context, theme: DiscordTheme, idx: Int): LinearLayout {
        val sz = dp(ctx, 52)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(ctx, 4), 0, dp(ctx, 4), 0) }

            val swatch = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(sz, sz)
                background = GradientDrawable(GradientDrawable.Orientation.BL_TR,
                    intArrayOf(theme.primaryColor, theme.secondaryColor)
                ).apply { cornerRadius = dp(ctx, 12).toFloat() }
            }
            val lbl = TextView(ctx).apply {
                text = theme.name.take(8)
                setTextColor(Color.parseColor("#B5BAC1"))
                textSize = 9f
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 3), 0, 0)
            }
            addView(swatch)
            addView(lbl)

            setOnClickListener {
                selectedId = theme.id
                selectedLabel?.text = theme.name
                log.info("Selected theme: ${theme.name}")
                chipRow?.let { row ->
                    for (i in 0 until row.childCount) {
                        val child = row.getChildAt(i) as? LinearLayout ?: continue
                        val sw = child.getChildAt(0) ?: continue
                        (sw.background as? GradientDrawable)?.setStroke(
                            if (i == idx) dp(ctx, 3) else 0,
                            Color.WHITE
                        )
                    }
                }
            }
        }
    }

    private fun applyTheme(ctx: Context) {
        val theme = DiscordThemes.THEMES.find { it.id == selectedId }
        if (theme == null) {
            toast(ctx, "Select a theme first", true)
            return
        }
        log.info("Applying theme: ${theme.name} proto=${theme.protoPayload}")
        val d = AlertDialog.Builder(ctx)
            .setTitle("Applying theme…")
            .setMessage("Sending '${theme.name}' to Discord…")
            .setCancelable(false)
            .create()
        d.show()
        com.aliucord.Utils.threadPool.execute {
            val (ok, resp) = DiscordApi.applyTheme(theme.protoPayload)
            log.info("applyTheme result: ok=$ok resp=$resp")
            com.aliucord.Utils.mainThread.post {
                d.dismiss()
                toast(ctx, if (ok) "Theme '${theme.name}' applied! Restart to see changes." else "Failed: $resp", !ok)
            }
        }
    }

    private fun toast(ctx: Context, msg: String, error: Boolean = false) {
        val t = Toast(ctx)
        t.view = TextView(ctx).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
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
