package com.rhyan57.rethemer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.aliucord.Constants
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.rn.user.RNUserProfile
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.patcher.after
import com.discord.widgets.settings.WidgetSettings
import com.discord.widgets.user.usersheet.WidgetUserSheet
import com.discord.widgets.user.usersheet.WidgetUserSheetViewModel
import com.lytefast.flexinput.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@AliucordPlugin
class REthemer : Plugin() {

    val log = Logger("REthemer")

    override fun start(context: Context) {
        ThemerEngine.init(context)
        ThemerEngine.addPatches(patcher)
        loadSavedTheme()
        patchSettingsButtons()
        patchProfileGradient()
    }

    private fun loadSavedTheme() {
        try {
            val saved = SettingsAPI("REthemer").getString("active_theme", null) ?: return
            ThemerBridge.applyThemeFromFile(saved)
        } catch (e: Exception) {
            log.error("loadSavedTheme", e)
        }
    }

    private fun patchSettingsButtons() {
        patcher.patch(
            WidgetSettings::class.java.getDeclaredMethod("onViewBound", View::class.java),
            Hook { callFrame ->
                try {
                    val view = callFrame.args[0] as CoordinatorLayout
                    val layout = (view.getChildAt(1) as NestedScrollView).getChildAt(0) as LinearLayoutCompat
                    val ctx = layout.context
                    if (layout.findViewWithTag<View>("rethemer_injected") != null) return@Hook
                    val qrView = layout.findViewById<TextView>(Utils.getResId("qr_scanner", "id"))
                    val baseIndex = if (qrView != null) layout.indexOfChild(qrView) else -1
                    val insertAt = if (baseIndex >= 0) baseIndex + 1 else layout.childCount
                    layout.addView(View(ctx).apply {
                        tag = "rethemer_injected"; visibility = View.GONE
                        layoutParams = LinearLayoutCompat.LayoutParams(0, 0)
                    }, insertAt)
                    val themeBtn = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                        text = "App Theme"
                        typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
                        setCompoundDrawablesWithIntrinsicBounds(
                            Utils.tintToTheme(ctx.getDrawable(R.e.ic_theme_24dp)), null, null, null
                        )
                        post {
                            val w = paint.measureText(text.toString())
                            ValueAnimator.ofFloat(0f, 360f).apply {
                                duration = 2500; repeatCount = ValueAnimator.INFINITE
                                addUpdateListener { anim ->
                                    val hue = anim.animatedValue as Float
                                    val colors = intArrayOf(
                                        Color.HSVToColor(floatArrayOf(hue % 360f, 0.7f, 1f)),
                                        Color.HSVToColor(floatArrayOf((hue + 120f) % 360f, 0.7f, 1f)),
                                        Color.HSVToColor(floatArrayOf((hue + 240f) % 360f, 0.7f, 1f))
                                    )
                                    paint.shader = android.graphics.LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
                                    invalidate()
                                }
                                start()
                            }
                        }
                        setOnClickListener { Utils.openPageWithProxy(ctx, AppThemePage()) }
                    }
                    layout.addView(themeBtn, insertAt + 1)
                    val gradBtn = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                        text = "Profile Gradient"
                        typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
                        setCompoundDrawablesWithIntrinsicBounds(
                            Utils.tintToTheme(ctx.getDrawable(R.e.ic_accessibility_24dp)), null, null, null
                        )
                        setOnClickListener { Utils.openPageWithProxy(ctx, ProfileGradientPage()) }
                    }
                    layout.addView(gradBtn, insertAt + 2)
                } catch (e: Throwable) {
                    log.error("settings patch crash", e)
                }
            }
        )
    }

    private fun patchProfileGradient() {
        patcher.after<WidgetUserSheet>(
            "configureDeveloperSection",
            WidgetUserSheetViewModel.ViewState.Loaded::class.java
        ) {
            val model = it.args[0] as WidgetUserSheetViewModel.ViewState.Loaded
            val profile = model.userProfile
            if (profile !is RNUserProfile) return@after

            val themeColors =
                profile.guildMemberProfile?.run { themeColors ?: accentColor?.let { c -> intArrayOf(c, c) } }
                    ?: profile.userProfile?.run { themeColors ?: accentColor?.let { c -> intArrayOf(c, c) } }
                    ?: return@after

            val primaryInt = themeColors[0]
            val accentInt  = if (themeColors.size > 1) themeColors[1] else themeColors[0]

            val binding = WidgetUserSheet.`access$getBinding$p`(this)
            val root    = try {
                var v: View = binding.D
                repeat(6) { v = (v.parent as? View) ?: v }
                v as? NestedScrollView ?: return@after
            } catch (_: Throwable) { return@after }

            fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

            root.background = object : Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun draw(canvas: Canvas) {
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    if (w <= 0f || h <= 0f) return
                    val cx = w / 2f; val cy = h / 2f
                    val angle = (135.0 * Math.PI / 180.0)
                    val diag  = sqrt(w * w + h * h) / 2f
                    val cosA  = cos(angle).toFloat()
                    val sinA  = sin(angle).toFloat()
                    paint.shader = LinearGradient(
                        cx - cosA * diag, cy - sinA * diag,
                        cx + cosA * diag, cy + sinA * diag,
                        intArrayOf(alpha(primaryInt, 0xB0), alpha(primaryInt, 0x66), alpha(accentInt, 0x99)),
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(bounds, paint)
                }
                override fun setAlpha(a: Int)  { paint.alpha = a }
                override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        ThemerEngine.clean()
    }
}
