package com.rhyan57.rethemer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage

private fun dp(ctx: Context, n: Int) = (n * ctx.resources.displayMetrics.density + 0.5f).toInt()

class ProfileGradientPage : SettingsPage() {
    private val log = Logger("REthemer/ProfileGradient")
    private var primaryColor = Color.parseColor("#5865F2")
    private var accentColor  = Color.parseColor("#EB459E")
    private var primarySwatch: View? = null
    private var accentSwatch: View? = null
    private var primaryHex: TextView? = null
    private var accentHex: TextView? = null
    private var previewView: View? = null

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Profile Gradient")
        val ctx = view.context
        log.info("ProfileGradientPage: onViewBound")
        buildUI(ctx)
        loadProfile(ctx)
    }

    private fun loadProfile(ctx: Context) {
        Utils.threadPool.execute {
            val profile = DiscordApi.getCurrentProfile()
            log.info("loadProfile response: $profile")
            val userProfile = profile?.optJSONObject("user_profile")
            log.info("user_profile: $userProfile")
            val themeColors = userProfile?.optJSONArray("theme_colors")
            log.info("theme_colors: $themeColors")
            if (themeColors != null && themeColors.length() >= 2) {
                val p = 0xFF000000.toInt() or (themeColors.getInt(0) and 0xFFFFFF)
                val a = 0xFF000000.toInt() or (themeColors.getInt(1) and 0xFFFFFF)
                log.info("Loaded colors: primary=#%06X accent=#%06X".format(p and 0xFFFFFF, a and 0xFFFFFF))
                Utils.mainThread.post {
                    primaryColor = p; accentColor = a
                    updateSwatches(ctx)
                }
            } else {
                log.info("No theme_colors found in profile")
            }
        }
    }

    private fun updateSwatches(ctx: Context) {
        primarySwatch?.background = swatchBg(ctx, primaryColor)
        accentSwatch?.background  = swatchBg(ctx, accentColor)
        primaryHex?.text = "#%06X".format(primaryColor and 0xFFFFFF)
        accentHex?.text  = "#%06X".format(accentColor  and 0xFFFFFF)
        previewView?.background = GradientDrawable(
            GradientDrawable.Orientation.BL_TR, intArrayOf(primaryColor, accentColor)
        ).apply { cornerRadius = dp(ctx, 14).toFloat() }
    }

    private fun buildUI(ctx: Context) {
        val preview = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 120)
            ).also { it.setMargins(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 8)) }
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR,
                intArrayOf(primaryColor, accentColor)
            ).apply { cornerRadius = dp(ctx, 14).toFloat() }
        }
        previewView = preview
        linearLayout.addView(preview)

        sectionLabel(ctx, "Primary Color").let { linearLayout.addView(it) }
        val (primaryRowView, primarySwatchView, primaryHexView) = colorRow(ctx, primaryColor, "Primary") { c ->
            primaryColor = c
            updateSwatches(ctx)
        }
        primarySwatch = primarySwatchView
        primaryHex = primaryHexView
        linearLayout.addView(primaryRowView)

        sectionLabel(ctx, "Accent Color").let { linearLayout.addView(it) }
        val (accentRowView, accentSwatchView, accentHexView) = colorRow(ctx, accentColor, "Accent") { c ->
            accentColor = c
            updateSwatches(ctx)
        }
        accentSwatch = accentSwatchView
        accentHex = accentHexView
        linearLayout.addView(accentRowView)

        sectionLabel(ctx, "Quick Presets").let { linearLayout.addView(it) }
        buildPresets(ctx)

        TextView(ctx).apply {
            text = "Save Profile Gradient"
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
            ).also { it.setMargins(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 4)) }
            setOnClickListener { saveGradient(ctx) }
        }.let { linearLayout.addView(it) }

        TextView(ctx).apply {
            text = "This updates your profile gradient visible to everyone."
            setTextColor(Color.parseColor("#72767D"))
            textSize = 11f; gravity = Gravity.CENTER
            setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 16))
        }.let { linearLayout.addView(it) }
    }

    private fun colorRow(ctx: Context, color: Int, label: String, onPick: (Int) -> Unit): Triple<LinearLayout, View, TextView> {
        val swatch = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40))
            background = swatchBg(ctx, color)
        }
        val hex = TextView(ctx).apply {
            text = "#%06X".format(color and 0xFFFFFF)
            setTextColor(Color.parseColor("#72767D"))
            textSize = 12f
            setPadding(0, 0, dp(ctx, 10), 0)
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F26"))
                cornerRadius = dp(ctx, 10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 58)
            ).also { it.setMargins(dp(ctx, 12), dp(ctx, 2), dp(ctx, 12), dp(ctx, 4)) }
            setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0)
            addView(TextView(ctx).apply {
                text = label
                setTextColor(Color.parseColor("#F2F3F5"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(hex)
            addView(swatch)
            setOnClickListener { showHexDialog(ctx, onPick) }
        }
        return Triple(row, swatch, hex)
    }

    private fun showHexDialog(ctx: Context, onPick: (Int) -> Unit) {
        val input = EditText(ctx).apply {
            hint = "#RRGGBB"
            setTextColor(Color.parseColor("#F2F3F5"))
            setHintTextColor(Color.parseColor("#72767D"))
            filters = arrayOf(InputFilter.LengthFilter(7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2D31"))
                cornerRadius = dp(ctx, 8).toFloat()
            }
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        val colorPreview = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 40)
            ).also { it.setMargins(0, dp(ctx, 10), 0, 0) }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F2"))
                cornerRadius = dp(ctx, 8).toFloat()
            }
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                val h = e.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                try { colorPreview.background = GradientDrawable().apply { setColor(Color.parseColor(h)); cornerRadius = dp(ctx, 8).toFloat() } } catch (_: Exception) {}
            }
        })
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 12))
            addView(TextView(ctx).apply { text = "Enter Hex Color"; setTextColor(Color.WHITE); textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(ctx, 10)) })
            addView(input)
            addView(colorPreview)
        }
        AlertDialog.Builder(ctx).setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val h = input.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                try { onPick(Color.parseColor(h)) } catch (e: Exception) { log.error("bad hex: $h", e) }
            }
            .setNegativeButton("Cancel", null).show()
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
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0)
                }
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44))
                    background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(p, a)).apply {
                        cornerRadius = dp(ctx, 10).toFloat()
                    }
                    setOnClickListener {
                        primaryColor = p; accentColor = a
                        updateSwatches(ctx)
                    }
                })
                addView(TextView(ctx).apply {
                    text = name
                    setTextColor(Color.parseColor("#72767D"))
                    textSize = 9f; gravity = Gravity.CENTER
                    setPadding(0, dp(ctx, 3), 0, 0)
                })
            }
            row.addView(col)
        }
        linearLayout.addView(row)
    }

    private fun saveGradient(ctx: Context) {
        log.info("saveGradient: primary=#%06X accent=#%06X".format(primaryColor and 0xFFFFFF, accentColor and 0xFFFFFF))
        val d = AlertDialog.Builder(ctx)
            .setTitle("Saving…")
            .setMessage("Updating your profile gradient…")
            .setCancelable(false).create()
        d.show()
        Utils.threadPool.execute {
            val (ok, resp) = DiscordApi.updateProfileGradient(primaryColor, accentColor)
            log.info("saveGradient result: ok=$ok resp=$resp")
            Utils.mainThread.post {
                d.dismiss()
                val t = Toast(ctx)
                t.view = TextView(ctx).apply {
                    text = if (ok) "Profile gradient saved!" else "Failed: $resp"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor(if (ok) "#23A55A" else "#ED4245"))
                        cornerRadius = dp(ctx, 20).toFloat()
                    }
                }
                t.duration = Toast.LENGTH_LONG
                t.show()
            }
        }
    }

    private fun sectionLabel(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text.uppercase()
        setTextColor(Color.parseColor("#72767D"))
        textSize = 10f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 4))
    }

    private fun swatchBg(ctx: Context, color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(ctx, 2), Color.parseColor("#3B3D44"))
    }
}
