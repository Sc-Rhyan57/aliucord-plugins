package com.rhyan57.rethemer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.discord.utilities.colors.ColorPickerUtils
import com.lytefast.flexinput.R

class ProfileGradientPage : SettingsPage() {

    private var primaryColor = Color.parseColor("#5865F2")
    private var accentColor = Color.parseColor("#EB459E")
    private var previewView: View? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Profile Theme")
        val ctx = view.context
        loadCurrentGradient(ctx)
        buildUI(ctx)
    }

    private fun loadCurrentGradient(ctx: Context) {
        Utils.threadPool.execute {
            try {
                val profile = DiscordApi.getCurrentProfile()
                val themeColors = profile?.optJSONArray("theme_colors")
                if (themeColors != null && themeColors.length() >= 2) {
                    val p = themeColors.getInt(0)
                    val a = themeColors.getInt(1)
                    Utils.mainThread.post {
                        primaryColor = 0xFF000000.toInt() or (p and 0xFFFFFF)
                        accentColor = 0xFF000000.toInt() or (a and 0xFFFFFF)
                        rebuildPreview(ctx)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun buildUI(ctx: Context) {
        val previewFrame = buildPreviewGradient(ctx)
        previewView = previewFrame
        linearLayout.addView(previewFrame)

        linearLayout.addView(sectionLabel(ctx, "Primary Color"))
        val primaryRow = buildColorRow(ctx, "Primary", primaryColor) { newColor ->
            primaryColor = newColor
            rebuildPreview(ctx)
        }
        linearLayout.addView(primaryRow)

        linearLayout.addView(sectionLabel(ctx, "Accent Color"))
        val accentRow = buildColorRow(ctx, "Accent", accentColor) { newColor ->
            accentColor = newColor
            rebuildPreview(ctx)
        }
        linearLayout.addView(accentRow)

        val divider = View(ctx).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#2C2D35")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(1)).apply {
                setMargins(DimenUtils.dpToPx(16), DimenUtils.dpToPx(16), DimenUtils.dpToPx(16), DimenUtils.dpToPx(16))
            }
        }
        linearLayout.addView(divider)

        val saveBtn = TextView(ctx).apply {
            text = "Save Profile Theme"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F2"))
                cornerRadius = DimenUtils.dpToPx(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(48)).apply {
                setMargins(DimenUtils.dpToPx(16), 0, DimenUtils.dpToPx(16), DimenUtils.dpToPx(8))
            }
            setOnClickListener { saveGradient(ctx) }
        }
        linearLayout.addView(saveBtn)

        val note = TextView(ctx).apply {
            text = "Changes your profile gradient on Discord for everyone to see."
            setTextColor(Color.parseColor("#72767D"))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(4), DimenUtils.dpToPx(16), DimenUtils.dpToPx(16))
        }
        linearLayout.addView(note)

        val quickHeader = sectionLabel(ctx, "Quick Presets")
        linearLayout.addView(quickHeader)

        val presets = listOf(
            "Blurple" to (Color.parseColor("#5865F2") to Color.parseColor("#4752C4")),
            "Sunset" to (Color.parseColor("#FF6B35") to Color.parseColor("#E040FB")),
            "Ocean" to (Color.parseColor("#00B4D8") to Color.parseColor("#0077B6")),
            "Forest" to (Color.parseColor("#2D6A4F") to Color.parseColor("#1B4332")),
            "Crimson" to (Color.parseColor("#ED4245") to Color.parseColor("#A12D2F")),
            "Aurora" to (Color.parseColor("#6DD5FA") to Color.parseColor("#7B2FF7"))
        )

        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(4), DimenUtils.dpToPx(12), DimenUtils.dpToPx(12))
        }

        presets.forEach { (name, colors) ->
            val (p, a) = colors
            val chip = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(DimenUtils.dpToPx(3), 0, DimenUtils.dpToPx(3), 0)
                }
            }
            val swatch = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(40), DimenUtils.dpToPx(40)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(p, a)).apply {
                    cornerRadius = DimenUtils.dpToPx(10).toFloat()
                }
                setOnClickListener {
                    primaryColor = p; accentColor = a
                    rebuildPreview(ctx)
                }
            }
            val label = TextView(ctx).apply {
                text = name; setTextColor(Color.parseColor("#72767D")); textSize = 9f
                gravity = android.view.Gravity.CENTER
                setPadding(0, DimenUtils.dpToPx(3), 0, 0)
            }
            chip.addView(swatch); chip.addView(label)
            grid.addView(chip)
        }
        linearLayout.addView(grid)
    }

    private fun buildPreviewGradient(ctx: Context): View {
        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(160)).apply {
                setMargins(DimenUtils.dpToPx(12), DimenUtils.dpToPx(12), DimenUtils.dpToPx(12), DimenUtils.dpToPx(8))
            }
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(primaryColor, accentColor)).apply {
                cornerRadius = DimenUtils.dpToPx(16).toFloat()
            }
            tag = "gradient_preview"
        }
        val profileSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val avatar = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(56), DimenUtils.dpToPx(56)).apply {
                gravity = android.view.Gravity.CENTER; setMargins(0, 0, 0, DimenUtils.dpToPx(8))
            }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#23A55A")); setStroke(DimenUtils.dpToPx(3), Color.parseColor("#1E1F26")) }
        }
        val nameLabel = TextView(ctx).apply { text = "rhyan57"; setTextColor(Color.WHITE); textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val tagLabel = TextView(ctx).apply { text = "@rhyan57"; setTextColor(Color.parseColor("#DDDDDD")); textSize = 12f }
        profileSection.addView(avatar); profileSection.addView(nameLabel); profileSection.addView(tagLabel)
        frame.addView(profileSection)
        return frame
    }

    private fun rebuildPreview(ctx: Context) {
        val parent = previewView?.parent as? LinearLayout ?: return
        val index = parent.indexOfChild(previewView)
        val newPreview = buildPreviewGradient(ctx)
        previewView = newPreview
        parent.removeViewAt(index)
        parent.addView(newPreview, index)
    }

    private fun buildColorRow(ctx: Context, label: String, currentColor: Int, onColorChanged: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F26"))
                cornerRadius = DimenUtils.dpToPx(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(54)).apply {
                setMargins(DimenUtils.dpToPx(12), DimenUtils.dpToPx(4), DimenUtils.dpToPx(12), DimenUtils.dpToPx(4))
            }
            setPadding(DimenUtils.dpToPx(14), 0, DimenUtils.dpToPx(14), 0)
        }
        val lbl = TextView(ctx).apply {
            text = label; setTextColor(Color.parseColor("#F2F3F5")); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val hexLabel = TextView(ctx).apply {
            text = "#%06X".format(currentColor and 0xFFFFFF)
            setTextColor(Color.parseColor("#72767D")); textSize = 11f
            setPadding(0, 0, DimenUtils.dpToPx(10), 0)
        }
        val swatch = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(32), DimenUtils.dpToPx(32))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(currentColor); setStroke(DimenUtils.dpToPx(2), Color.parseColor("#3B3D44")) }
        }
        row.addView(lbl); row.addView(hexLabel); row.addView(swatch)
        row.setOnClickListener {
            showColorPicker(ctx, currentColor) { newColor ->
                swatch.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(newColor); setStroke(DimenUtils.dpToPx(2), Color.parseColor("#3B3D44")) }
                hexLabel.text = "#%06X".format(newColor and 0xFFFFFF)
                onColorChanged(newColor)
            }
        }
        return row
    }

    private fun showColorPicker(ctx: Context, current: Int, onPick: (Int) -> Unit) {
        try {
            val picker = ColorPickerUtils.INSTANCE.buildColorPickerDialog(
                ctx,
                Utils.getResId("color_picker_title", "string"),
                current
            )
            picker.arguments?.putBoolean("alpha", false)
            picker.setListener(object : b.k.a.a.f {
                override fun onColorReset(c: Int) {}
                override fun onColorSelected(id: Int, color: Int) { onPick(color) }
                override fun onDialogDismissed(id: Int) {}
            })
            picker.show(parentFragmentManager, "color_picker")
        } catch (_: Exception) {
            showSimpleColorInput(ctx, current, onPick)
        }
    }

    private fun showSimpleColorInput(ctx: Context, current: Int, onPick: (Int) -> Unit) {
        val input = EditText(ctx).apply {
            setText("#%06X".format(current and 0xFFFFFF))
            setTextColor(Color.parseColor("#F2F3F5"))
            setHintTextColor(Color.parseColor("#72767D"))
            hint = "#RRGGBB"
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E1F26")); cornerRadius = DimenUtils.dpToPx(8).toFloat() }
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(10), DimenUtils.dpToPx(12), DimenUtils.dpToPx(10))
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
            setPadding(DimenUtils.dpToPx(20), DimenUtils.dpToPx(16), DimenUtils.dpToPx(20), DimenUtils.dpToPx(8))
            addView(TextView(ctx).apply { text = "Enter Hex Color"; setTextColor(Color.WHITE); textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, DimenUtils.dpToPx(12)) })
            addView(input)
        }
        AlertDialog.Builder(ctx)
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val hex = input.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                try { onPick(Color.parseColor(hex)) } catch (_: Exception) {}
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveGradient(ctx: Context) {
        val d = AlertDialog.Builder(ctx).setTitle("Saving...").setMessage("Updating profile theme...").setCancelable(false).create()
        d.show()
        Utils.threadPool.execute {
            val ok = DiscordApi.updateProfileGradient(primaryColor, accentColor)
            Utils.mainThread.post {
                d.dismiss()
                val t = android.widget.Toast(ctx)
                t.view = TextView(ctx).apply {
                    text = if (ok) "Profile theme saved!" else "Failed to save"; setTextColor(Color.WHITE); textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(DimenUtils.dpToPx(18), DimenUtils.dpToPx(12), DimenUtils.dpToPx(18), DimenUtils.dpToPx(12))
                    background = GradientDrawable().apply { setColor(Color.parseColor(if (ok) "#23A55A" else "#ED4245")); cornerRadius = DimenUtils.dpToPx(20).toFloat() }
                }
                t.duration = android.widget.Toast.LENGTH_SHORT
                t.show()
            }
        }
    }

    private fun sectionLabel(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text.uppercase()
        setTextColor(Color.parseColor("#72767D"))
        textSize = 10f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(10), DimenUtils.dpToPx(16), DimenUtils.dpToPx(4))
    }
}
