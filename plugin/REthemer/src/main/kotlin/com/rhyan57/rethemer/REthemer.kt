package com.rhyan57.rethemer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.lytefast.flexinput.R

@AliucordPlugin
class REthemer : Plugin() {

    val log = Logger("REthemer")

    override fun start(context: Context) {
        log.info("REthemer starting")
        patchAppearanceSettings()
        patchProfileSettings()
    }

    private fun patchAppearanceSettings() {
        try {
            val appearanceClass = Class.forName("com.discord.widgets.settings.WidgetAppearanceSettings")
            val onViewBound = appearanceClass.getDeclaredMethod("onViewBound", View::class.java)
            patcher.patch(onViewBound, Hook { callFrame ->
                try {
                    val view = callFrame.args[0] as View
                    val ctx = view.context

                    val root = findLinearLayout(view) ?: run {
                        log.error("REthemer: could not find root LinearLayout in WidgetAppearanceSettings", null)
                        return@Hook
                    }

                    if (root.findViewWithTag<View>("rethemer_appearance_injected") != null) return@Hook

                    root.addView(View(ctx).apply {
                        tag = "rethemer_appearance_injected"
                        visibility = View.GONE
                        layoutParams = LinearLayout.LayoutParams(0, 0)
                    })

                    val themeBtn = buildSettingsRow(ctx, "App Theme", R.e.ic_theme_24dp, rainbow = true) {
                        log.info("App Theme clicked from Appearance")
                        Utils.openPageWithProxy(ctx, AppThemePage())
                    }

                    root.addView(themeBtn, 0)

                    log.info("REthemer: injected App Theme into WidgetAppearanceSettings")
                } catch (e: Throwable) {
                    log.error("REthemer: WidgetAppearanceSettings patch crash", e)
                }
            })
            log.info("REthemer: WidgetAppearanceSettings patched")
        } catch (e: Throwable) {
            log.error("REthemer: could not find WidgetAppearanceSettings, falling back to WidgetSettings", e)
            patchWidgetSettingsFallback()
        }
    }

    private fun patchProfileSettings() {
        try {
            val profileClass = Class.forName("com.discord.widgets.settings.profile.WidgetUserSettingsProfile")
            val onViewBound = profileClass.getDeclaredMethod("onViewBound", View::class.java)
            patcher.patch(onViewBound, Hook { callFrame ->
                try {
                    val view = callFrame.args[0] as View
                    val ctx = view.context

                    val root = findLinearLayout(view) ?: run {
                        log.error("REthemer: could not find root in WidgetUserSettingsProfile", null)
                        return@Hook
                    }

                    if (root.findViewWithTag<View>("rethemer_profile_injected") != null) return@Hook

                    root.addView(View(ctx).apply {
                        tag = "rethemer_profile_injected"
                        visibility = View.GONE
                        layoutParams = LinearLayout.LayoutParams(0, 0)
                    })

                    val gradBtn = buildSettingsRow(ctx, "Profile Gradient", R.e.ic_accessibility_24dp) {
                        log.info("Profile Gradient clicked from Profile Settings")
                        Utils.openPageWithProxy(ctx, ProfileGradientPage())
                    }

                    root.addView(gradBtn, 0)

                    log.info("REthemer: injected Profile Gradient into WidgetUserSettingsProfile")
                } catch (e: Throwable) {
                    log.error("REthemer: WidgetUserSettingsProfile patch crash", e)
                }
            })
            log.info("REthemer: WidgetUserSettingsProfile patched")
        } catch (e: Throwable) {
            log.error("REthemer: could not find WidgetUserSettingsProfile", e)
        }
    }

    private fun patchWidgetSettingsFallback() {
        try {
            val widgetSettingsClass = Class.forName("com.discord.widgets.settings.WidgetSettings")
            val onViewBound = widgetSettingsClass.getDeclaredMethod("onViewBound", View::class.java)
            patcher.patch(onViewBound, Hook { callFrame ->
                try {
                    val view = callFrame.args[0] as View
                    val ctx = view.context
                    val root = findLinearLayout(view) ?: return@Hook

                    if (root.findViewWithTag<View>("rethemer_injected") != null) return@Hook

                    val qrView = root.findViewWithTag<View?>(null)
                    val qrId = Utils.getResId("qr_scanner", "id")
                    var insertAt = root.childCount

                    if (qrId != 0) {
                        val qr = root.findViewById<View?>(qrId)
                        if (qr != null) insertAt = root.indexOfChild(qr) + 1
                    }

                    root.addView(View(ctx).apply {
                        tag = "rethemer_injected"
                        visibility = View.GONE
                        layoutParams = LinearLayout.LayoutParams(0, 0)
                    }, insertAt)

                    val themeBtn = buildSettingsRow(ctx, "App Theme", R.e.ic_theme_24dp, rainbow = true) {
                        Utils.openPageWithProxy(ctx, AppThemePage())
                    }
                    root.addView(themeBtn, insertAt + 1)

                    val gradBtn = buildSettingsRow(ctx, "Profile Gradient", R.e.ic_accessibility_24dp) {
                        Utils.openPageWithProxy(ctx, ProfileGradientPage())
                    }
                    root.addView(gradBtn, insertAt + 2)

                    log.info("REthemer: fallback injected at $insertAt")
                } catch (e: Throwable) {
                    log.error("REthemer fallback patch crash", e)
                }
            })
        } catch (e: Throwable) {
            log.error("REthemer: fallback also failed", e)
        }
    }

    private fun findLinearLayout(view: View): LinearLayout? {
        if (view is LinearLayout) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findLinearLayout(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun buildSettingsRow(
        ctx: Context,
        label: String,
        iconRes: Int,
        rainbow: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        return TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
            text = label
            typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
            setCompoundDrawablesWithIntrinsicBounds(
                Utils.tintToTheme(ctx.getDrawable(iconRes)),
                null, null, null
            )
            setOnClickListener { onClick() }
            if (rainbow) {
                post {
                    val w = paint.measureText(text.toString())
                    ValueAnimator.ofFloat(0f, 360f).apply {
                        duration = 2500
                        repeatCount = ValueAnimator.INFINITE
                        addUpdateListener { anim ->
                            val hue = anim.animatedValue as Float
                            val colors = intArrayOf(
                                Color.HSVToColor(floatArrayOf(hue % 360f, 0.7f, 1f)),
                                Color.HSVToColor(floatArrayOf((hue + 120f) % 360f, 0.7f, 1f)),
                                Color.HSVToColor(floatArrayOf((hue + 240f) % 360f, 0.7f, 1f))
                            )
                            paint.shader = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
                            invalidate()
                        }
                        start()
                    }
                }
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        log.info("REthemer stopped")
    }
}
