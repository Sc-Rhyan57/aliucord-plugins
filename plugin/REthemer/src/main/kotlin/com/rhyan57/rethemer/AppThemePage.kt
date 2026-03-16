package com.rhyan57.rethemer

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.lytefast.flexinput.R

class AppThemePage : SettingsPage() {

    private var selectedThemeId: Int = -1
    private var previewContainer: LinearLayout? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("App Theme")
        val ctx = view.context
        loadCurrentTheme(ctx)
        buildUI(ctx)
    }

    private fun loadCurrentTheme(ctx: Context) {
        Utils.threadPool.execute {
            val proto = DiscordApi.getCurrentThemeProto()
            if (proto != null) {
                val matched = DiscordThemes.THEMES.find { it.protoPayload == proto }
                if (matched != null) selectedThemeId = matched.id
            }
        }
    }

    private fun buildUI(ctx: Context) {
        val preview = buildPreview(ctx)
        previewContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(DimenUtils.dpToPx(12), DimenUtils.dpToPx(12), DimenUtils.dpToPx(12), DimenUtils.dpToPx(8))
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F26"))
                cornerRadius = DimenUtils.dpToPx(16).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#2C2D35"))
            }
            addView(preview)
        }
        linearLayout.addView(previewContainer)

        val selectedLabel = TextView(ctx).apply {
            text = getSelectedName()
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, DimenUtils.dpToPx(4), 0, DimenUtils.dpToPx(4))
        }
        linearLayout.addView(selectedLabel)

        val scrollRow = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(16))
            }
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(DimenUtils.dpToPx(4), DimenUtils.dpToPx(8), DimenUtils.dpToPx(4), DimenUtils.dpToPx(8))
        }

        DiscordThemes.THEMES.forEach { theme ->
            val chip = buildThemeChip(ctx, theme, selectedLabel) { selected ->
                updatePreview(ctx, selected)
                selectedLabel.text = selected.name
                selectedThemeId = selected.id
            }
            chipRow.addView(chip)
        }

        scrollRow.addView(chipRow)
        linearLayout.addView(scrollRow)

        val applyBtn = TextView(ctx).apply {
            text = "Apply Theme"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F2"))
                cornerRadius = DimenUtils.dpToPx(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(48)).apply {
                setMargins(DimenUtils.dpToPx(16), DimenUtils.dpToPx(4), DimenUtils.dpToPx(16), DimenUtils.dpToPx(8))
            }
            setOnClickListener { applySelectedTheme(ctx) }
        }
        linearLayout.addView(applyBtn)

        val divider = View(ctx).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#2C2D35")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(1)).apply {
                setMargins(DimenUtils.dpToPx(16), DimenUtils.dpToPx(12), DimenUtils.dpToPx(16), DimenUtils.dpToPx(12))
            }
        }
        linearLayout.addView(divider)

        val exportHeader = buildSectionHeader(ctx, "Export as Themer Theme")
        linearLayout.addView(exportHeader)

        val exportBtn = TextView(ctx).apply {
            text = "Export Current Theme to Themer"
            setTextColor(Color.parseColor("#5865F2"))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F215"))
                cornerRadius = DimenUtils.dpToPx(12).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#5865F240"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(44)).apply {
                setMargins(DimenUtils.dpToPx(16), DimenUtils.dpToPx(4), DimenUtils.dpToPx(16), DimenUtils.dpToPx(8))
            }
            setOnClickListener { exportTheme(ctx) }
        }
        linearLayout.addView(exportBtn)

        val noteView = TextView(ctx).apply {
            text = "This will change the theme across all your devices."
            setTextColor(Color.parseColor("#72767D"))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(8), DimenUtils.dpToPx(16), DimenUtils.dpToPx(16))
        }
        linearLayout.addView(noteView)
    }

    private fun buildPreview(ctx: Context): View {
        val previewFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(200))
            background = GradientDrawable().apply {
                cornerRadius = DimenUtils.dpToPx(12).toFloat()
            }
        }
        val current = DiscordThemes.THEMES.firstOrNull { it.id == selectedThemeId } ?: DiscordThemes.THEMES.first()
        applyGradientToView(previewFrame, current)

        val previewCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC1E1F26"))
                cornerRadius = DimenUtils.dpToPx(10).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
                setMargins(DimenUtils.dpToPx(12), 0, DimenUtils.dpToPx(12), DimenUtils.dpToPx(12))
            }
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(10), DimenUtils.dpToPx(12), DimenUtils.dpToPx(10))
        }

        val msgHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val dot = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#23A55A")) }
            layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(8), DimenUtils.dpToPx(8)).apply { setMargins(0, 0, DimenUtils.dpToPx(6), 0) }
        }
        val userLabel = TextView(ctx).apply {
            text = "Wumpus"; setTextColor(Color.WHITE); textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val timeLabel = TextView(ctx).apply {
            text = "Hoje às 12:00"; setTextColor(Color.parseColor("#72767D")); textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = android.view.Gravity.END }
            gravity = android.view.Gravity.END
        }
        msgHeader.addView(dot); msgHeader.addView(userLabel); msgHeader.addView(timeLabel)
        previewCard.addView(msgHeader)

        val msgText = TextView(ctx).apply {
            text = "Try ${current.name} — looks great!"; setTextColor(Color.parseColor("#B5BAC1")); textSize = 12f
            setPadding(0, DimenUtils.dpToPx(4), 0, 0)
        }
        previewCard.addView(msgText)
        previewFrame.addView(previewCard)
        previewFrame.tag = "preview_frame"
        return previewFrame
    }

    private fun updatePreview(ctx: Context, theme: DiscordTheme) {
        val container = previewContainer ?: return
        Utils.mainThread.post {
            container.removeAllViews()
            val newPreview = buildPreviewForTheme(ctx, theme)
            container.addView(newPreview)
        }
    }

    private fun buildPreviewForTheme(ctx: Context, theme: DiscordTheme): View {
        val previewFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(200))
        }
        applyGradientToView(previewFrame, theme)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#CC1E1F26")); cornerRadius = DimenUtils.dpToPx(10).toFloat() }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
                setMargins(DimenUtils.dpToPx(12), 0, DimenUtils.dpToPx(12), DimenUtils.dpToPx(12))
            }
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(10), DimenUtils.dpToPx(12), DimenUtils.dpToPx(10))
        }
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val dot = View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#23A55A")) }; layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(8), DimenUtils.dpToPx(8)).apply { setMargins(0, 0, DimenUtils.dpToPx(6), 0) } }
        val name = TextView(ctx).apply { text = "Wumpus"; setTextColor(Color.WHITE); textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        header.addView(dot); header.addView(name)
        card.addView(header)
        val msg = TextView(ctx).apply { text = "Try ${theme.name} — looks great!"; setTextColor(Color.parseColor("#B5BAC1")); textSize = 12f; setPadding(0, DimenUtils.dpToPx(4), 0, 0) }
        card.addView(msg)
        previewFrame.addView(card)
        return previewFrame
    }

    private fun applyGradientToView(view: View, theme: DiscordTheme) {
        view.background = GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(theme.primaryColor, blendColors(theme.primaryColor, theme.secondaryColor, 0.5f), theme.secondaryColor)
        ).apply { cornerRadius = DimenUtils.dpToPx(12).toFloat() }
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt()
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt()
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt()
        return Color.rgb(r.coerceIn(0,255), g.coerceIn(0,255), b.coerceIn(0,255))
    }

    private fun buildThemeChip(ctx: Context, theme: DiscordTheme, label: TextView, onSelect: (DiscordTheme) -> Unit): View {
        val isSelected = theme.id == selectedThemeId
        val chipSize = DimenUtils.dpToPx(48)
        val chip = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(chipSize + DimenUtils.dpToPx(8), chipSize + DimenUtils.dpToPx(8)).apply {
                setMargins(DimenUtils.dpToPx(4), 0, DimenUtils.dpToPx(4), 0)
            }
        }
        val inner = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(chipSize, chipSize).apply { gravity = android.view.Gravity.CENTER }
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(theme.primaryColor, theme.secondaryColor)).apply {
                cornerRadius = DimenUtils.dpToPx(12).toFloat()
            }
        }
        if (isSelected) {
            val ring = View(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(chipSize + DimenUtils.dpToPx(4), chipSize + DimenUtils.dpToPx(4)).apply { gravity = android.view.Gravity.CENTER }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = DimenUtils.dpToPx(14).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(DimenUtils.dpToPx(2), Color.WHITE)
                }
            }
            chip.addView(ring)
        }
        chip.addView(inner)
        chip.setOnClickListener { onSelect(theme) }
        return chip
    }

    private fun applySelectedTheme(ctx: Context) {
        val theme = DiscordThemes.THEMES.find { it.id == selectedThemeId }
            ?: DiscordThemes.THEMES.first()
        val d = AlertDialog.Builder(ctx)
            .setTitle("Applying ${theme.name}...")
            .setMessage("Syncing with Discord...")
            .setCancelable(false)
            .create()
        d.show()
        Utils.threadPool.execute {
            val ok = DiscordApi.applyTheme(theme.protoPayload)
            if (ok) ThemerBridge.applyThemeToThemer(theme, theme.name)
            Utils.mainThread.post {
                d.dismiss()
                showCustomToast(ctx, if (ok) "Theme applied!" else "Failed to apply theme", !ok)
            }
        }
    }

    private fun exportTheme(ctx: Context) {
        val theme = DiscordThemes.THEMES.find { it.id == selectedThemeId }
            ?: DiscordThemes.THEMES.first()
        val input = EditText(ctx).apply {
            setText(theme.name)
            setTextColor(Color.parseColor("#F2F3F5"))
            setHintTextColor(Color.parseColor("#72767D"))
            hint = "Theme name"
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E1F26")); cornerRadius = DimenUtils.dpToPx(8).toFloat() }
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(10), DimenUtils.dpToPx(12), DimenUtils.dpToPx(10))
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
            setPadding(DimenUtils.dpToPx(20), DimenUtils.dpToPx(16), DimenUtils.dpToPx(20), DimenUtils.dpToPx(8))
            addView(TextView(ctx).apply { text = "Export as Themer Theme"; setTextColor(Color.WHITE); textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, DimenUtils.dpToPx(12)) })
            addView(input)
        }
        AlertDialog.Builder(ctx)
            .setView(container)
            .setPositiveButton("Export") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { theme.name }
                val file = ThemerBridge.exportTheme(theme, name)
                showCustomToast(ctx, if (file != null) "Exported to themes/$name.json\nRestart required for Themer" else "Export failed", file == null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildSectionHeader(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text.uppercase()
        setTextColor(Color.parseColor("#72767D"))
        textSize = 10f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(8), DimenUtils.dpToPx(16), DimenUtils.dpToPx(4))
    }

    private fun getSelectedName(): String =
        DiscordThemes.THEMES.find { it.id == selectedThemeId }?.name ?: "Default"

    private fun showCustomToast(ctx: Context, msg: String, error: Boolean = false) {
        val t = android.widget.Toast(ctx)
        t.view = TextView(ctx).apply {
            text = msg; setTextColor(Color.WHITE); textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(DimenUtils.dpToPx(18), DimenUtils.dpToPx(12), DimenUtils.dpToPx(18), DimenUtils.dpToPx(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (error) "#ED4245" else "#23A55A"))
                cornerRadius = DimenUtils.dpToPx(20).toFloat()
            }
        }
        t.duration = android.widget.Toast.LENGTH_SHORT
        t.show()
    }
}
