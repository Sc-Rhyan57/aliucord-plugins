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
import com.discord.utilities.colors.ColorPickerUtils

private fun dp(ctx: Context, n: Int) = (n * ctx.resources.displayMetrics.density + 0.5f).toInt()

class ProfileGradientPage : SettingsPage() {
    private val log = Logger("REthemer/ProfileGradient")
    private var primaryColor = Color.parseColor("#5865F2")
    private var accentColor  = Color.parseColor("#EB459E")
    private var primarySwatch: View? = null
    private var accentSwatch:  View? = null
    private var primaryHex: TextView? = null
    private var accentHex:  TextView? = null
    private var previewView: View? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Profile Gradient")
        buildUI(view.context)
        loadProfile(view.context)
    }

    private fun loadProfile(ctx: Context) {
        Utils.threadPool.execute {
            val profile    = DiscordApi.getCurrentProfile()
            val themeColors = profile?.optJSONObject("user_profile")?.optJSONArray("theme_colors")
            if (themeColors != null && themeColors.length() >= 2) {
                val p = 0xFF000000.toInt() or (themeColors.getInt(0) and 0xFFFFFF)
                val a = 0xFF000000.toInt() or (themeColors.getInt(1) and 0xFFFFFF)
                Utils.mainThread.post { primaryColor = p; accentColor = a; updateSwatches(ctx) }
            }
        }
    }

    private fun updateSwatches(ctx: Context) {
        primarySwatch?.background = ovalBg(ctx, primaryColor)
        accentSwatch?.background  = ovalBg(ctx, accentColor)
        primaryHex?.text = "#%06X".format(primaryColor and 0xFFFFFF)
        accentHex?.text  = "#%06X".format(accentColor  and 0xFFFFFF)
        previewView?.background = GradientDrawable(
            GradientDrawable.Orientation.BL_TR, intArrayOf(primaryColor, accentColor)
        ).apply { cornerRadius = dp(ctx, 16).toFloat() }
    }

    private fun buildUI(ctx: Context) {
        val root = linearLayout

        previewView = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 130)
            ).also { it.setMargins(dp(ctx, 12), dp(ctx, 16), dp(ctx, 12), dp(ctx, 10)) }
            background = GradientDrawable(
                GradientDrawable.Orientation.BL_TR, intArrayOf(primaryColor, accentColor)
            ).apply { cornerRadius = dp(ctx, 16).toFloat() }
        }
        root.addView(previewView)

        sectionLabel(ctx, "Primary Color").let { root.addView(it) }
        val (rowP, swP, hexP) = colorRow(ctx, primaryColor, "Primary") { openColorPicker(ctx, primaryColor, false) }
        primarySwatch = swP; primaryHex = hexP; root.addView(rowP)

        sectionLabel(ctx, "Accent Color").let { root.addView(it) }
        val (rowA, swA, hexA) = colorRow(ctx, accentColor, "Accent") { openColorPicker(ctx, accentColor, true) }
        accentSwatch = swA; accentHex = hexA; root.addView(rowA)

        sectionLabel(ctx, "Quick Presets").let { root.addView(it) }
        buildPresets(ctx)

        root.addView(TextView(ctx).apply {
            text = "Save Profile Gradient"
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setColor(Color.parseColor("#5865F2")); cornerRadius = dp(ctx, 14).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 50)
            ).also { it.setMargins(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 4)) }
            setOnClickListener { saveGradient(ctx) }
        })

        root.addView(TextView(ctx).apply {
            text = "Requires Nitro to sync across devices."
            setTextColor(Color.parseColor("#72767D")); textSize = 11f; gravity = Gravity.CENTER
            setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 16))
        })
    }

    private fun openColorPicker(ctx: Context, initialColor: Int, isAccent: Boolean) {
        try {
            val picker = ColorPickerUtils.INSTANCE.buildColorPickerDialog(
                ctx, Utils.getResId("color_picker_title", "string"), initialColor
            )
            picker.arguments?.putBoolean("alpha", false)
            picker.k = object : b.k.a.a.f {
                override fun onColorReset(color: Int) {}
                override fun onColorSelected(id: Int, color: Int) {
                    if (isAccent) accentColor = color else primaryColor = color
                    updateSwatches(ctx)
                }
                override fun onDialogDismissed(id: Int) {}
            }
            picker.show(parentFragmentManager, if (isAccent) "accent" else "primary")
        } catch (e: Exception) {
            log.error("ColorPicker failed", e)
            showHexFallback(ctx, initialColor, isAccent)
        }
    }

    private fun showHexFallback(ctx: Context, initialColor: Int, isAccent: Boolean) {
        val input = EditText(ctx).apply {
            setText("#%06X".format(initialColor and 0xFFFFFF))
            setTextColor(Color.parseColor("#F2F3F5"))
            background = GradientDrawable().apply { setColor(Color.parseColor("#2B2D31")); cornerRadius = dp(ctx, 8).toFloat() }
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        AlertDialog.Builder(ctx)
            .setTitle(if (isAccent) "Accent Color" else "Primary Color")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val h = input.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                try { val c = Color.parseColor(h); if (isAccent) accentColor = c else primaryColor = c; updateSwatches(ctx) }
                catch (e: Exception) { log.error("bad hex", e) }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun colorRow(ctx: Context, color: Int, label: String, onClick: () -> Unit): Triple<LinearLayout, View, TextView> {
        val swatch = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42))
            background = ovalBg(ctx, color)
        }
        val hex = TextView(ctx).apply {
            text = "#%06X".format(color and 0xFFFFFF)
            setTextColor(Color.parseColor("#72767D")); textSize = 12f
            setPadding(0, 0, dp(ctx, 10), 0)
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E1F26")); cornerRadius = dp(ctx, 10).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 60)
            ).also { it.setMargins(dp(ctx, 12), dp(ctx, 2), dp(ctx, 12), dp(ctx, 4)) }
            setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0)
            addView(TextView(ctx).apply {
                text = label; setTextColor(Color.parseColor("#F2F3F5")); textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(hex); addView(swatch)
            setOnClickListener { onClick() }
        }
        return Triple(row, swatch, hex)
    }

    private fun buildPresets(ctx: Context) {
        val presets = listOf(
            "Blurple" to (Color.parseColor("#5865F2") to Color.parseColor("#4752C4")),
            "Pink"    to (Color.parseColor("#EB459E") to Color.parseColor("#A12D6A")),
            "Ocean"   to (Color.parseColor("#00B4D8") to Color.parseColor("#0077B6")),
            "Forest"  to (Color.parseColor("#57F287") to Color.parseColor("#2D6A4F")),
            "Crimson" to (Color.parseColor("#ED4245") to Color.parseColor("#A12D2F")),
            "Aurora"  to (Color.parseColor("#7B2FF7") to Color.parseColor("#6DD5FA"))
        )
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 12))
        }
        presets.forEach { (name, colors) ->
            val (p, a) = colors
            row.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0)
                }
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 46), dp(ctx, 46))
                    background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(p, a)).apply { cornerRadius = dp(ctx, 12).toFloat() }
                    setOnClickListener { primaryColor = p; accentColor = a; updateSwatches(ctx) }
                })
                addView(TextView(ctx).apply {
                    text = name; setTextColor(Color.parseColor("#72767D")); textSize = 9f; gravity = Gravity.CENTER
                    setPadding(0, dp(ctx, 3), 0, 0)
                })
            })
        }
        linearLayout.addView(row)
    }

    private fun saveGradient(ctx: Context) {
        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Saving…").setMessage("Updating your profile gradient…").setCancelable(false).create()
        dialog.show()
        Utils.threadPool.execute {
            val hasNitro = DiscordApi.hasNitro()
            if (!hasNitro) {
                Utils.mainThread.post {
                    dialog.dismiss()
                    toast(ctx, "Nitro is required to set a profile gradient.", true)
                }
                return@execute
            }
            val (ok, resp) = DiscordApi.updateProfileGradient(primaryColor, accentColor)
            Utils.mainThread.post {
                dialog.dismiss()
                toast(ctx, if (ok) "Profile gradient saved!" else "Failed: $resp", !ok)
            }
        }
    }

    private fun sectionLabel(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text.uppercase(); setTextColor(Color.parseColor("#72767D")); textSize = 10f
        typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
        setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 4))
    }

    private fun ovalBg(ctx: Context, color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(ctx, 2), Color.parseColor("#3B3D44"))
    }

    @Suppress("DEPRECATION")
    private fun toast(ctx: Context, msg: String, error: Boolean = false) {
        val t = Toast(ctx)
        t.view = TextView(ctx).apply {
            text = msg; setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (error) "#ED4245" else "#23A55A"))
                cornerRadius = dp(ctx, 20).toFloat()
            }
        }
        t.duration = Toast.LENGTH_LONG; t.show()
    }
}
