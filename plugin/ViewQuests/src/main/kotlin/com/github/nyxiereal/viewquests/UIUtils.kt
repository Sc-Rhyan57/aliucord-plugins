package com.github.nyxiereal.viewquests

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants
import com.aliucord.utils.DimenUtils
import com.discord.utilities.time.TimeUtils
import com.lytefast.flexinput.R

private val fontCache = mutableMapOf<Int, Typeface?>()

fun cachedFont(ctx: Context, res: Int): Typeface? =
    fontCache.getOrPut(res) { ResourcesCompat.getFont(ctx, res) }

data class Pad(val l: Int = 16, val t: Int = 8, val r: Int = 16, val b: Int = 8)

fun mkText(ctx: Context, style: Int, text: String, pad: Pad = Pad()): TextView =
    TextView(ctx, null, 0, style).apply {
        this.text = text
        setPadding(DimenUtils.dpToPx(pad.l), DimenUtils.dpToPx(pad.t), DimenUtils.dpToPx(pad.r), DimenUtils.dpToPx(pad.b))
    }

fun headerText(ctx: Context, text: String, pad: Pad = Pad()) =
    mkText(ctx, R.i.UiKit_Settings_Item_Header, text, pad).apply {
        typeface = cachedFont(ctx, Constants.Fonts.whitney_semibold)
    }

fun subText(ctx: Context, text: String, pad: Pad = Pad(), color: Int? = null) =
    mkText(ctx, R.i.UiKit_Settings_Item_SubText, text, pad).apply {
        color?.let { setTextColor(it) }
    }

fun labelText(ctx: Context, text: String, pad: Pad = Pad()) =
    mkText(ctx, R.i.UiKit_Settings_Item_Label, text, pad).apply {
        typeface = cachedFont(ctx, Constants.Fonts.whitney_semibold)
    }

fun card(ctx: Context, cornerDp: Int = 12, marginDp: Int = 6): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.VERTICAL
    background = GradientDrawable().apply {
        setColor(Color.parseColor("#1E1F26"))
        cornerRadius = DimenUtils.dpToPx(cornerDp).toFloat()
        setStroke(DimenUtils.dpToPx(1), Color.parseColor("#2C2D35"))
    }
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(DimenUtils.dpToPx(marginDp), DimenUtils.dpToPx(4), DimenUtils.dpToPx(marginDp), DimenUtils.dpToPx(4))
    }
    setPadding(0, 0, 0, DimenUtils.dpToPx(8))
}

fun divider(ctx: Context): android.view.View = android.view.View(ctx).apply {
    background = GradientDrawable().apply { setColor(Color.parseColor("#2C2D35")) }
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(1)).apply {
        setMargins(DimenUtils.dpToPx(16), DimenUtils.dpToPx(4), DimenUtils.dpToPx(16), DimenUtils.dpToPx(4))
    }
}

fun fmtDate(ctx: Context, iso: String): String = try {
    val ts = TimeUtils.parseUTCDate(iso)
    if (ts == 0L) iso else TimeUtils.INSTANCE.renderUtcDate(ts, ctx, 2)
} catch (_: Exception) { iso }

fun fmtDuration(secs: Int): String {
    val m = secs / 60
    return if (m < 60) "$m min" else "${m / 60}h ${m % 60}min".trimEnd { it == 'm' || it == 'i' || it == 'n' || it == ' ' || it == '0' }
}
