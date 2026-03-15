package com.github.nyxiereal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color as AColor
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.aliucord.Constants
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.discord.widgets.settings.WidgetSettings
import com.lytefast.flexinput.R
import com.github.nyxiereal.viewquests.*

@AliucordPlugin
class ViewQuests : Plugin() {

    override fun start(context: Context) {
        patcher.patch(
            WidgetSettings::class.java.getDeclaredMethod("onViewBound", View::class.java),
            Hook { callFrame ->
                val view = callFrame.args[0] as CoordinatorLayout
                val layout = (view.getChildAt(1) as NestedScrollView).getChildAt(0) as LinearLayoutCompat
                val ctx = layout.context

                val baseIndex = layout.indexOfChild(
                    layout.findViewById<TextView>(Utils.getResId("qr_scanner", "id"))
                )

                val orbBalanceView = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                    text = "Orbs: ..."
                    setPadding(Utils.dpToPx(16), Utils.dpToPx(4), Utils.dpToPx(16), Utils.dpToPx(4))
                    layout.addView(this, baseIndex + 1)
                }

                Utils.threadPool.execute {
                    try {
                        val req = Http.Request.newDiscordRNRequest("/users/@me/virtual-currency/balance", "GET")
                        val res = req.execute()
                        val json = res.json(org.json.JSONObject::class.java)
                        val balance = json.optInt("balance", -1)
                        Utils.mainThread.post {
                            if (balance >= 0) orbBalanceView.text = "⬡ $balance Orbs"
                            else orbBalanceView.visibility = View.GONE
                        }
                    } catch (_: Exception) {
                        Utils.mainThread.post { orbBalanceView.visibility = View.GONE }
                    }
                }

                TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                    text = "Quests"
                    typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
                    setCompoundDrawablesWithIntrinsicBounds(
                        Utils.tintToTheme(ctx.getDrawable(R.e.ic_gift_24dp)),
                        null, null, null
                    )
                    post {
                        val w = paint.measureText(text.toString())
                        val animator = ValueAnimator.ofFloat(0f, 360f).apply {
                            duration = 2500
                            repeatCount = ValueAnimator.INFINITE
                            addUpdateListener { anim ->
                                val hue = anim.animatedValue as Float
                                val colors = intArrayOf(
                                    AColor.HSVToColor(floatArrayOf(hue % 360f, 0.7f, 1f)),
                                    AColor.HSVToColor(floatArrayOf((hue + 90f) % 360f, 0.7f, 1f)),
                                    AColor.HSVToColor(floatArrayOf((hue + 180f) % 360f, 0.7f, 1f)),
                                    AColor.HSVToColor(floatArrayOf((hue + 270f) % 360f, 0.7f, 1f))
                                )
                                paint.shader = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
                                invalidate()
                            }
                        }
                        animator.start()
                    }
                    setOnClickListener { Utils.openPageWithProxy(ctx, QuestsPage()) }
                    layout.addView(this, baseIndex + 2)
                }
            }
        )
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}
