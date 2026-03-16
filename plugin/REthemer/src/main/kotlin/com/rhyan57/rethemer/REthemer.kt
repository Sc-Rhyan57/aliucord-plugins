package com.rhyan57.rethemer

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.utils.DimenUtils
import com.discord.widgets.settings.WidgetSettings
import com.lytefast.flexinput.R

@AliucordPlugin
class REthemer : Plugin() {

    override fun start(context: Context) {
        patchAppearanceSettings()
        patchProfileEditPage()
    }

    private fun patchAppearanceSettings() {
        val appearanceClass = try {
            Class.forName("com.discord.widgets.settings.appearance.WidgetSettingsAppearance")
        } catch (_: Exception) {
            try { Class.forName("com.discord.widgets.settings.WidgetSettingsAppearance") }
            catch (_: Exception) { null }
        }

        if (appearanceClass != null) {
            patcher.patch(
                appearanceClass.getDeclaredMethod("onViewBound", View::class.java),
                Hook { callFrame ->
                    val view = callFrame.args[0] as View
                    val ctx = view.context
                    addThemeRowToAppearance(view, ctx)
                }
            )
        } else {
            patcher.patch(
                WidgetSettings::class.java.getDeclaredMethod("onViewBound", View::class.java),
                Hook { callFrame ->
                    val view = callFrame.args[0] as View
                    val ctx = view.context
                    addThemeEntryToSettings(view, ctx)
                }
            )
        }
    }

    private fun addThemeRowToAppearance(rootView: View, ctx: Context) {
        val themeRowId = Utils.getResId("appearance_settings_theme_row", "id")
        val themeRow = if (themeRowId != 0) rootView.findViewById<View>(themeRowId) else null
        if (themeRow != null) {
            val currentThemeName = getCurrentThemeName()
            val themeLabel = TextView(ctx).apply {
                text = currentThemeName
                setTextColor(Color.parseColor("#5865F2"))
                textSize = 12f
                setPadding(DimenUtils.dpToPx(8), 0, DimenUtils.dpToPx(8), 0)
            }
            try {
                val parent = themeRow as? LinearLayout ?: themeRow.parent as? LinearLayout
                parent?.addView(themeLabel)
            } catch (_: Exception) {}
            themeRow.setOnClickListener { Utils.openPageWithProxy(ctx, AppThemePage()) }
        }
    }

    private fun addThemeEntryToSettings(rootView: View, ctx: Context) {
        val scrollId = Utils.getResId("nested_scroll_view", "id")
        val scroll = rootView.findViewById<androidx.core.widget.NestedScrollView>(scrollId) ?: return
        val layout = scroll.getChildAt(0) as? LinearLayout ?: return
        if (layout.findViewWithTag<View>("rethemer_entry") != null) return
        val currentTheme = getCurrentThemeName()
        val entry = buildThemeEntry(ctx, currentTheme)
        val appearanceIdx = findAppearanceIndex(layout)
        layout.addView(entry, (appearanceIdx + 1).coerceAtMost(layout.childCount))
    }

    private fun buildThemeEntry(ctx: Context, currentThemeName: String): View {
        val row = LinearLayout(ctx).apply {
            tag = "rethemer_entry"
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2D31"))
                cornerRadius = DimenUtils.dpToPx(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DimenUtils.dpToPx(52)
            ).apply {
                setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(4), DimenUtils.dpToPx(8), DimenUtils.dpToPx(4))
            }
            setPadding(DimenUtils.dpToPx(16), 0, DimenUtils.dpToPx(16), 0)
        }
        val titleLabel = TextView(ctx).apply {
            text = "Theme"
            setTextColor(Color.parseColor("#F2F3F5"))
            textSize = 15f
            typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueLabel = TextView(ctx).apply {
            text = currentThemeName
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 13f
        }
        val arrow = TextView(ctx).apply {
            text = " ›"
            setTextColor(Color.parseColor("#72767D"))
            textSize = 18f
        }
        row.addView(titleLabel)
        row.addView(valueLabel)
        row.addView(arrow)
        row.setOnClickListener { Utils.openPageWithProxy(it.context, AppThemePage()) }
        return row
    }

    private fun findAppearanceIndex(layout: LinearLayout): Int {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            val resName = try {
                child.context.resources.getResourceEntryName(child.id)
            } catch (_: Exception) { null }
            if (resName?.contains("appearance", true) == true || resName?.contains("theme", true) == true) return i
        }
        return 0
    }

    private fun patchProfileEditPage() {
        val profileClass = try {
            Class.forName("com.discord.widgets.settings.profile.WidgetSettingsProfile")
        } catch (_: Exception) { null } ?: return

        val methodName = profileClass.declaredMethods.firstOrNull { m ->
            m.name.contains("onViewBound", true) || m.parameterTypes.firstOrNull() == View::class.java
        }?.name ?: "onViewBound"

        try {
            patcher.patch(
                profileClass.getDeclaredMethod(methodName, View::class.java),
                Hook { callFrame ->
                    val view = callFrame.args[0] as View
                    val ctx = view.context
                    addProfileGradientEntry(view, ctx)
                }
            )
        } catch (_: Exception) {}
    }

    private fun addProfileGradientEntry(rootView: View, ctx: Context) {
        val scroll = findNestedScrollView(rootView) ?: return
        val layout = scroll.getChildAt(0) as? LinearLayout ?: return
        if (layout.findViewWithTag<View>("rethemer_gradient_entry") != null) return
        val entry = buildGradientEntry(ctx)
        val idx = findThemeColorsIndex(layout)
        layout.addView(entry, (idx + 1).coerceAtMost(layout.childCount))
    }

    private fun findNestedScrollView(view: View): androidx.core.widget.NestedScrollView? {
        if (view is androidx.core.widget.NestedScrollView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findNestedScrollView(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun buildGradientEntry(ctx: Context): View {
        val row = LinearLayout(ctx).apply {
            tag = "rethemer_gradient_entry"
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2D31"))
                cornerRadius = DimenUtils.dpToPx(8).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DimenUtils.dpToPx(52)
            ).apply {
                setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(4), DimenUtils.dpToPx(8), DimenUtils.dpToPx(4))
            }
            setPadding(DimenUtils.dpToPx(16), 0, DimenUtils.dpToPx(16), 0)
        }
        val gradientPreview = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(32), DimenUtils.dpToPx(32)).apply {
                setMargins(0, 0, DimenUtils.dpToPx(12), 0)
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(Color.parseColor("#5865F2"), Color.parseColor("#EB459E"))
            ).apply { cornerRadius = DimenUtils.dpToPx(8).toFloat() }
        }
        val label = TextView(ctx).apply {
            text = "Profile Theme"
            setTextColor(Color.parseColor("#F2F3F5"))
            textSize = 15f
            typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val arrow = TextView(ctx).apply {
            text = "›"
            setTextColor(Color.parseColor("#72767D"))
            textSize = 18f
        }
        row.addView(gradientPreview)
        row.addView(label)
        row.addView(arrow)
        row.setOnClickListener { Utils.openPageWithProxy(it.context, ProfileGradientPage()) }
        return row
    }

    private fun findThemeColorsIndex(layout: LinearLayout): Int {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            val resName = try { child.context.resources.getResourceEntryName(child.id) } catch (_: Exception) { null }
            if (resName?.contains("theme_color", true) == true || resName?.contains("accent_color", true) == true) return i
        }
        return (layout.childCount / 2).coerceAtLeast(0)
    }

    private fun getCurrentThemeName(): String {
        return try {
            val theme = com.discord.stores.StoreStream.getUserSettingsSystem().theme
            when (theme) {
                "dark" -> "Dark"; "light" -> "Light"; "midnight" -> "Midnight"
                else -> "Dark"
            }
        } catch (_: Exception) { "Dark" }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}
