package com.rhyan57.rethemer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.discord.widgets.settings.WidgetSettings
import com.lytefast.flexinput.R

@AliucordPlugin
class REthemer : Plugin() {

    val log = Logger("REthemer")

    override fun start(context: Context) {
        log.info("REthemer starting, patching WidgetSettings.onViewBound")

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
                    log.info("REthemer: qr_scanner found=${qrView != null} baseIndex=$baseIndex")

                    val insertAt = if (baseIndex >= 0) baseIndex + 1 else layout.childCount

                    layout.addView(View(ctx).apply {
                        tag = "rethemer_injected"
                        visibility = View.GONE
                        layoutParams = LinearLayoutCompat.LayoutParams(0, 0)
                    }, insertAt)

                    val themeBtn = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                        text = "App Theme"
                        typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
                        setCompoundDrawablesWithIntrinsicBounds(
                            Utils.tintToTheme(ctx.getDrawable(R.e.ic_theme_24dp)),
                            null, null, null
                        )
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
                        setOnClickListener {
                            log.info("App Theme clicked")
                            Utils.openPageWithProxy(ctx, AppThemePage())
                        }
                    }
                    layout.addView(themeBtn, insertAt + 1)

                    val gradBtn = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                        text = "Profile Gradient"
                        typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
                        setCompoundDrawablesWithIntrinsicBounds(
                            Utils.tintToTheme(ctx.getDrawable(R.e.ic_accessibility_24dp)),
                            null, null, null
                        )
                        setOnClickListener {
                            log.info("Profile Gradient clicked")
                            Utils.openPageWithProxy(ctx, ProfileGradientPage())
                        }
                    }
                    layout.addView(gradBtn, insertAt + 2)

                    log.info("REthemer: injected at $insertAt (childCount=${layout.childCount})")
                } catch (e: Throwable) {
                    log.error("REthemer patch CRASH", e)
                }
            }
        )
        log.info("REthemer: patch registered")
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        log.info("REthemer stopped")
    }
}
