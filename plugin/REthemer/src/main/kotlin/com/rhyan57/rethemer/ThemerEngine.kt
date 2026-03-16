package com.rhyan57.rethemer

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Window
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.aliucord.Utils
import com.aliucord.api.PatcherAPI
import com.aliucord.patcher.Hook
import com.aliucord.patcher.PreHook
import com.discord.utilities.color.ColorCompat
import com.google.android.material.textfield.TextInputLayout
import com.lytefast.flexinput.R
import de.robv.android.xposed.XposedBridge
import java.io.File

private val colorToName  = HashMap<Int, String>()
private val colorsByName = HashMap<String, Int>()
private val colorsById   = HashMap<Int, Int>()
private val drawableTints= HashMap<Int, Int>()
private val attrMap      = HashMap<Int, Int>()

object ThemerEngine {

    var active = false

    fun init(ctx: Context) {
        R.c::class.java.declaredFields.forEach {
            try {
                val color = ctx.getColor(it.getInt(null))
                if (color != 0) colorToName[color] = it.name
            } catch (_: Throwable) {}
        }
    }

    fun clean() {
        colorsByName.clear(); colorsById.clear(); drawableTints.clear(); attrMap.clear()
        active = false
    }

    fun getColorReplacement(color: Int) = colorToName[color]?.let { colorsByName[it] }
    fun getColorForId(id: Int)          = colorsById[id]
    fun getColorForName(name: String)   = colorsByName[name]
    fun getDrawableTintForId(id: Int)   = drawableTints[id]
    fun getAttrForId(id: Int)           = attrMap[id]

    fun putColor(name: String, color: Int) {
        val id = Utils.getResId(name, "color")
        if (id != 0) { colorsById[id] = color; colorsByName[name] = color }
        else when (name) {
            "statusbar", "input_background", "active_channel", "blocked_bg" -> colorsByName[name] = color
        }
    }

    fun putColors(names: Array<String>, color: Int) = names.forEach { putColor(it, color) }

    fun putDrawableTint(name: String, color: Int) {
        val id = Utils.getResId(name, "drawable")
        if (id != 0) drawableTints[id] = color
    }

    fun putAttrByName(name: String, color: Int) {
        val id = Utils.getResId(name, "attr")
        if (id != 0) attrMap[id] = color
    }

    fun putAttrs(attrs: Array<String>, color: Int) {
        attrs.forEach {
            if (it.startsWith("__alpha_10_")) {
                putAttrByName(it.substring(11), ColorUtils.setAlphaComponent(color, 0x1a))
            } else putAttrByName(it, color)
        }
    }

    fun applySimpleColors(simpleColors: Map<String, Int>) {
        simpleColors.forEach { (key, value) ->
            when (key) {
                "accent" -> {
                    putColors(SIMPLE_ACCENT_NAMES, value)
                    putAttrs(SIMPLE_ACCENT_ATTRS, value)
                    putDrawableTint("ic_nitro_rep", value)
                    putDrawableTint("drawable_voice_indicator_speaking", value)
                }
                "background" -> {
                    putColors(SIMPLE_BG_NAMES, value)
                    putAttrs(SIMPLE_BG_ATTRS, value)
                }
                "background_secondary" -> {
                    putColors(SIMPLE_BG_SECONDARY_NAMES, value)
                    putAttrs(SIMPLE_BG_SECONDARY_ATTRS, value)
                    putDrawableTint("drawable_overlay_channels_selected_dark", value)
                    putDrawableTint("drawable_overlay_channels_selected_light", value)
                    putDrawableTint("drawable_overlay_channels_active_dark", value)
                    putDrawableTint("drawable_overlay_channels_active_light", value)
                }
                "mention_highlight" -> {
                    putColor("status_yellow_500", ColorUtils.setAlphaComponent(value, 0xff))
                    putAttrByName("theme_chat_mentioned_me", value)
                }
                "active_channel" -> {
                    putDrawableTint("drawable_overlay_channels_selected_dark", value)
                    putDrawableTint("drawable_overlay_channels_selected_light", value)
                    putDrawableTint("drawable_overlay_channels_active_dark", value)
                    putDrawableTint("drawable_overlay_channels_active_light", value)
                    putColor(key, value)
                }
                "statusbar", "input_background", "blocked_bg" -> putColor(key, value)
            }
        }
    }

    fun applyColors(colors: Map<String, Int>) {
        colors.forEach { (key, value) ->
            putColor(key, value)
            putAttrByName(key, value)
        }
        colors["brand_500"]?.let { putDrawableTint("ic_nitro_rep", it) }
    }

    fun addPatches(patcher: PatcherAPI) {
        deoptimize()

        patcher.patch(
            Resources::class.java.getDeclaredMethod("getColor", Int::class.javaPrimitiveType, Resources.Theme::class.java),
            PreHook { param ->
                getColorForId(param.args[0] as Int)?.let { param.result = it }
            }
        )

        val loadDrawableTarget = try {
            Resources::class.java.getDeclaredMethod("loadDrawable", TypedValue::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Resources.Theme::class.java)
        } catch (_: NoSuchMethodException) {
            Resources::class.java.getDeclaredMethod("loadDrawable", TypedValue::class.java, Int::class.javaPrimitiveType!!, Resources.Theme::class.java)
        }
        patcher.patch(loadDrawableTarget, PreHook { param ->
            val value = param.args[0] as TypedValue
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                getColorReplacement(value.data)?.let { value.data = it }
            }
        })

        patcher.patch(
            ColorDrawable::class.java.getDeclaredMethod("setColor", Int::class.javaPrimitiveType),
            PreHook { param ->
                val color = param.args[0] as Int
                getColorReplacement(color)?.let { param.args[0] = it; return@PreHook }
                val isBlocked = color == 0xff34373c.toInt()
                if (isBlocked) getColorForName("blocked_bg")?.let { param.args[0] = it }
            }
        )

        patcher.patch(
            ColorStateList::class.java.getDeclaredMethod("getColorForState", IntArray::class.java, Int::class.javaPrimitiveType),
            Hook { param ->
                getColorReplacement(param.result as Int)?.let { param.result = it }
            }
        )

        patcher.patch(
            Resources::class.java.getDeclaredMethod("getDrawableForDensity", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Resources.Theme::class.java),
            Hook { param ->
                getDrawableTintForId(param.args[0] as Int)?.let { (param.result as Drawable?)?.setTint(it) }
            }
        )

        patcher.patch(
            ColorCompat::class.java.getDeclaredMethod("getThemedColor", Context::class.java, Int::class.javaPrimitiveType),
            PreHook { cf ->
                getAttrForId(cf.args[1] as Int)?.let { cf.result = it }
            }
        )

        patcher.patch(
            ColorCompat::class.java.getDeclaredMethod("setStatusBarColor", Window::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
            PreHook { param ->
                getColorForName("statusbar")?.let { param.args[1] = it }
            }
        )

        patcher.patch(
            TextInputLayout::class.java.getDeclaredMethod("calculateBoxBackgroundColor"),
            PreHook { param ->
                getColorForName("input_background")?.let { param.result = it }
            }
        )

        active = true
    }

    private fun deoptimize() {
        listOf(
            RippleDrawable::class.java to "updateRipplePaint",
            TextView::class.java      to "updateTextColors",
            Drawable::class.java      to "updateBlendModeFilter",
            GradientDrawable::class.java to "updateLocalState",
            android.view.View::class.java to "setBackgroundColor"
        ).forEach { (clazz, name) ->
            clazz.declaredMethods.filter { it.name == name }.forEach {
                try { XposedBridge.deoptimizeMethod(it) } catch (_: Throwable) {}
            }
        }
    }
}

private val SIMPLE_ACCENT_NAMES = arrayOf(
    "link", "link_light", "brand_new", "brand_new_230", "brand_new_360",
    "brand_new_500", "brand_new_530", "brand_new_560", "brand_new_600",
    "uikit_btn_bg_color_selector_brand", "uikit_btn_bg_color_selector_secondary_dark",
    "uikit_btn_compound_color_selector_dark", "uikit_btn_compound_color_selector_light"
)

private val SIMPLE_BG_NAMES = arrayOf(
    "dark_grey_2", "primary_600", "primary_660", "primary_800",
    "primary_dark_600", "primary_dark_630", "primary_dark_800"
)

private val SIMPLE_BG_SECONDARY_NAMES = arrayOf(
    "primary_500", "primary_630", "primary_700", "primary_dark_660",
    "primary_dark_700", "input_background", "statusbar", "active_channel"
)

private val SIMPLE_ACCENT_ATTRS = arrayOf(
    "color_brand", "brand_new_500", "colorControlBrandForeground",
    "colorControlActivated", "colorTextLink",
    "__alpha_10_theme_chat_mention_background", "theme_chat_mention_foreground"
)

private val SIMPLE_BG_ATTRS = arrayOf(
    "colorSurface", "colorBackgroundFloating", "colorTabsBackground",
    "theme_chat_spoiler_inapp_bg", "primary_600", "primary_660", "primary_800"
)

private val SIMPLE_BG_SECONDARY_ATTRS = arrayOf(
    "colorBackgroundTertiary", "colorBackgroundSecondary", "primary_700", "theme_chat_spoiler_bg"
)
