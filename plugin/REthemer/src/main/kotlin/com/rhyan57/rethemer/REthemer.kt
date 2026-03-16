package com.rhyan57.rethemer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.aliucord.Constants
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.rn.user.RNUserProfile
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.patcher.after
import com.discord.widgets.settings.WidgetSettings
import com.discord.widgets.user.usersheet.WidgetUserSheet
import com.discord.widgets.user.usersheet.WidgetUserSheetViewModel
import com.lytefast.flexinput.R

@AliucordPlugin
class REthemer : Plugin() {

    val log = Logger("REthemer")

    override fun start(context: Context) {
        log.info("REthemer starting")
        ThemerEngine.init(context)
        ThemerEngine.addPatches(patcher)
        loadSavedTheme()
        patchSettingsButtons()
        patchProfileGradient()
    }

    private fun loadSavedTheme() {
        try {
            val prefs = com.aliucord.api.SettingsAPI("REthemer")
            val savedThemeName = prefs.getString("active_theme", null)
            if (savedThemeName != null) {
                ThemerBridge.applyThemeFromFile(savedThemeName)
                log.info("Loaded saved theme: $savedThemeName")
            }
        } catch (e: Exception) {
            log.error("loadSavedTheme failed", e)
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
                        tag = "rethemer_injected"
                        visibility = View.GONE
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

                    log.info("REthemer: injected at $insertAt")
                } catch (e: Throwable) {
                    log.error("REthemer settings patch crash", e)
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

            val binding = WidgetUserSheet.`access$getBinding$p`(this)
            val actionsContainer = binding.D
            val root = actionsContainer.parent.parent.parent as NestedScrollView

            fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

            val cardAlpha = 0xD0
            val cardColor = withAlpha(themeColors[0], cardAlpha)

            fun findAncestorCard(v: View): CardView? {
                var p: Any? = v.parent
                while (p is View) { if (p is CardView) return p; p = p.parent }
                return null
            }

            fun isDescendant(parent: ViewGroup, child: View): Boolean {
                var p: Any? = child.parent
                while (p is View) { if (p === parent) return true; p = p.parent }
                return false
            }

            actionsContainer.setBackgroundColor(0)
            binding.J.apply { setBackgroundColor(0); (parent as View).setBackgroundColor(0) }

            val ancestorCard = findAncestorCard(binding.h)

            listOf(binding.b, binding.R, binding.j, (binding.n.parent as CardView), (binding.B.parent as CardView)).forEach { card ->
                if (ancestorCard != null && card === ancestorCard) return@forEach
                card.setCardBackgroundColor(cardColor); card.radius = 32f; card.cardElevation = 0f; card.maxCardElevation = 0f
            }

            fun findEditButtonsContainer(start: ViewGroup): ViewGroup? {
                val q = ArrayDeque<ViewGroup>(); q.add(start)
                while (q.isNotEmpty()) {
                    val vg = q.removeFirst(); var btnCount = 0
                    for (i in 0 until vg.childCount) { val c = vg.getChildAt(i); if (c is Button && c.visibility == View.VISIBLE) btnCount++ }
                    if (btnCount >= 2) {
                        var hasHint = false
                        for (i in 0 until vg.childCount) {
                            val c = vg.getChildAt(i)
                            if (c is Button && c.id != View.NO_ID) {
                                try { val n = vg.context.resources.getResourceEntryName(c.id); if (n.contains("profile_edit") || n.contains("edit_profile")) { hasHint = true; break } } catch (_: Throwable) {}
                            }
                        }
                        if (hasHint) return vg
                    }
                    for (i in 0 until vg.childCount) { val c = vg.getChildAt(i); if (c is ViewGroup) q.add(c) }
                }
                return null
            }

            val container = findEditButtonsContainer(root as ViewGroup) ?: findEditButtonsContainer(actionsContainer)
            container?.post {
                val buttons = mutableListOf<Button>()
                for (i in 0 until container.childCount) { val c = container.getChildAt(i); if (c is Button && c.visibility == View.VISIBLE) buttons.add(c) }
                if (buttons.size >= 2) {
                    val btn1 = buttons[0]; val btn2 = buttons[1]; val parent = btn1.parent as? ViewGroup
                    if (parent != null) {
                        val idx1 = parent.indexOfChild(btn1); val idx2 = parent.indexOfChild(btn2)
                        val minIdx = minOf(idx1, idx2); val maxIdx = maxOf(idx1, idx2)
                        if (maxIdx >= 0) parent.removeViewAt(maxIdx); if (minIdx >= 0) parent.removeViewAt(minIdx)
                        val nl = android.widget.LinearLayout(btn1.context).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        }
                        parent.addView(nl, minIdx)
                        val gap = (btn1.context.resources.displayMetrics.density * 8).toInt()
                        nl.addView(btn1, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                        nl.addView(btn2, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = gap })
                        val density = btn1.context.resources.displayMetrics.density; val corner = 36f * density
                        listOf(btn1, btn2).forEach { btn ->
                            btn.background = GradientDrawable().apply { cornerRadius = corner; setColor(cardColor) }
                            try { btn.backgroundTintList = android.content.res.ColorStateList.valueOf(cardColor) } catch (_: Throwable) {}
                            btn.setPadding((12*density).toInt(), (8*density).toInt(), (12*density).toInt(), (8*density).toInt())
                            val r=(cardColor shr 16) and 0xFF; val g=(cardColor shr 8) and 0xFF; val b=cardColor and 0xFF
                            (btn as? TextView)?.setTextColor(if (0.299*r+0.587*g+0.114*b < 128) Color.WHITE else Color.BLACK)
                        }
                    }
                }
            }

            listOf(binding.h, binding.I).forEachIndexed { idx, btn ->
                val density = btn.context.resources.displayMetrics.density
                btn.background = GradientDrawable().apply { cornerRadius = 36f*density; setColor(cardColor) }
                try { btn.backgroundTintList = android.content.res.ColorStateList.valueOf(cardColor) } catch (_: Throwable) {}
                btn.setPadding((12*density).toInt(), (8*density).toInt(), (12*density).toInt(), (8*density).toInt())
                btn.layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { if (idx > 0) it.topMargin = (density*8).toInt() }
                val r=(cardColor shr 16) and 0xFF; val g=(cardColor shr 8) and 0xFF; val b=cardColor and 0xFF
                (btn as? TextView)?.setTextColor(if (0.299*r+0.587*g+0.114*b < 128) Color.WHITE else Color.BLACK)
            }

            try { if (ancestorCard != null && isDescendant(ancestorCard as ViewGroup, binding.I)) ancestorCard.setCardBackgroundColor(0) } catch (_: Throwable) {}

            binding.B.apply { boxBackgroundColor = cardColor; (parent as CardView).setCardBackgroundColor(cardColor) }
            binding.A.setBackgroundColor(0)

            val connColor = withAlpha(themeColors[1], 0xC0)
            binding.n.apply { setBackgroundColor(connColor); (parent as CardView).setCardBackgroundColor(connColor) }

            val gradColors = intArrayOf(withAlpha(themeColors[0], 0xB0), withAlpha(themeColors[0], 0xB0), withAlpha(themeColors[1], 0xB0))
            root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, gradColors).apply { cornerRadius = 0f }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        ThemerEngine.clean()
        log.info("REthemer stopped")
    }
}
