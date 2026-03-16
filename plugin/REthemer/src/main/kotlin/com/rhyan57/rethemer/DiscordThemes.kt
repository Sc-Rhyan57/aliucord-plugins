package com.rhyan57.rethemer

import android.graphics.Color
import androidx.core.graphics.ColorUtils

data class DiscordTheme(
    val id: Int,
    val name: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val isDark: Boolean,
    val protoPayload: String
)

object DiscordThemes {

    val THEMES = listOf(
        DiscordTheme(-3, "Midnight",         Color.parseColor("#000000"), Color.parseColor("#000000"), true,  ""),
        DiscordTheme(-2, "Light",             Color.parseColor("#F2F3F5"), Color.parseColor("#E3E5E8"), false, ""),
        DiscordTheme(-1, "Dark",              Color.parseColor("#313338"), Color.parseColor("#2B2D31"), true,  ""),
        DiscordTheme(0,  "Custom",            Color.parseColor("#5865F2"), Color.parseColor("#7289DA"), true,  "agoIAhABGgISACAB"),
        DiscordTheme(1,  "Mint Apple",        Color.parseColor("#3BA55D"), Color.parseColor("#2D7D46"), true,  "agwIAhABGgQSAggBIAE="),
        DiscordTheme(2,  "Citrus Sherbert",   Color.parseColor("#F8A532"), Color.parseColor("#E67E22"), true,  "agwIAhABGgQSAggCIAE="),
        DiscordTheme(3,  "Retro Raincloud",   Color.parseColor("#8E9297"), Color.parseColor("#72767D"), true,  "agwIAhABGgQSAggDIAE="),
        DiscordTheme(4,  "Hanami",            Color.parseColor("#E91E8C"), Color.parseColor("#C2185B"), true,  "agwIAhABGgQSAggEIAE="),
        DiscordTheme(5,  "Sunrise",           Color.parseColor("#FF6B35"), Color.parseColor("#F7C59F"), false, "agwIAhABGgQSAggFIAE="),
        DiscordTheme(6,  "Cotton Candy",      Color.parseColor("#FF73FA"), Color.parseColor("#7289DA"), false, "agwIAhABGgQSAggGIAE="),
        DiscordTheme(7,  "Lofi Vibes",        Color.parseColor("#4F545C"), Color.parseColor("#36393F"), true,  "agwIAhABGgQSAggHIAE="),
        DiscordTheme(8,  "Desert Khaki",      Color.parseColor("#C9B99A"), Color.parseColor("#A99A8A"), false, "agwIARABGgQSAggIIAE="),
        DiscordTheme(9,  "Sunset",            Color.parseColor("#FF7043"), Color.parseColor("#E040FB"), true,  "agwIARABGgQSAggJIAE="),
        DiscordTheme(10, "Chroma Glow",       Color.parseColor("#5865F2"), Color.parseColor("#EB459E"), true,  "agwIARABGgQSAggKIAE="),
        DiscordTheme(11, "Forest",            Color.parseColor("#2D6A4F"), Color.parseColor("#1B4332"), true,  "agwIARABGgQSAggLIAE="),
        DiscordTheme(12, "Crimson",           Color.parseColor("#ED4245"), Color.parseColor("#A12D2F"), true,  "agwIARABGgQSAggMIAE="),
        DiscordTheme(13, "Midnight Blurple",  Color.parseColor("#5865F2"), Color.parseColor("#4752C4"), true,  "agwIARABGgQSAggNIAE="),
        DiscordTheme(14, "Mars",              Color.parseColor("#C0392B"), Color.parseColor("#922B21"), true,  "agwIARABGgQSAggOIAE="),
        DiscordTheme(15, "Dusk",              Color.parseColor("#7289DA"), Color.parseColor("#4E5D94"), true,  "agwIARABGgQSAggPIAE="),
        DiscordTheme(17, "Under The Sea",     Color.parseColor("#1ABC9C"), Color.parseColor("#148F77"), true,  "agwIARABGgQSAggRIAE="),
        DiscordTheme(18, "Retro Storm",       Color.parseColor("#9B59B6"), Color.parseColor("#6C3483"), true,  "agwIARABGgQSAggSIAE="),
        DiscordTheme(19, "Neon Nights",       Color.parseColor("#00D2FF"), Color.parseColor("#7B2FF7"), true,  "agwIARABGgQSAggTIAE="),
        DiscordTheme(20, "Strawberry Lemon",  Color.parseColor("#FF6B6B"), Color.parseColor("#FFE66D"), false, "agwIARABGgQSAggUIAE="),
        DiscordTheme(21, "Aurora",            Color.parseColor("#6DD5FA"), Color.parseColor("#2980B9"), false, "agwIARABGgQSAggVIAE="),
        DiscordTheme(22, "Sepia",             Color.parseColor("#C4A882"), Color.parseColor("#8D6E63"), false, "agwIARABGgQSAggWIAE="),
        DiscordTheme(23, "Cobalt Dusk",       Color.parseColor("#1E3A5F"), Color.parseColor("#0D1F3C"), true,  "agwIARABGgQSAggXIAE="),
        DiscordTheme(24, "Chromatic Glow",    Color.parseColor("#9C27B0"), Color.parseColor("#E040FB"), true,  "agwIARABGgQSAggYIAE="),
        DiscordTheme(25, "Retro Rainforest",  Color.parseColor("#27AE60"), Color.parseColor("#145A32"), true,  "agwIARABGgQSAggZIAE="),
        DiscordTheme(26, "Ocean Breeze",      Color.parseColor("#0099CC"), Color.parseColor("#005580"), true,  "agwIARABGgQSAggaIAE="),
        DiscordTheme(27, "Volcanic",          Color.parseColor("#FF4500"), Color.parseColor("#8B0000"), true,  "agwIARABGgQSAgghIAE="),
        DiscordTheme(28, "Twilight",          Color.parseColor("#4A148C"), Color.parseColor("#880E4F"), true,  "agwIARABGgQSAggcIAE=")
    )

    fun toThemerColors(theme: DiscordTheme): Pair<Map<String, Int>, Map<String, Int>> {
        return when (theme.id) {
            -3 -> buildPalette(
                accent = Color.parseColor("#5865F2"),
                bg800 = Color.parseColor("#000000"), bg700 = Color.parseColor("#080808"),
                bg660 = Color.parseColor("#0D0D0D"),  bg630 = Color.parseColor("#111111"),
                bg600 = Color.parseColor("#141414"),  bg500 = Color.parseColor("#1A1A1A"),
                statusbar = Color.parseColor("#000000"), isDark = true
            )
            -2 -> buildPalette(
                accent = Color.parseColor("#5865F2"),
                bg800 = Color.parseColor("#FFFFFF"), bg700 = Color.parseColor("#F2F3F5"),
                bg660 = Color.parseColor("#EBEDEF"), bg630 = Color.parseColor("#E3E5E8"),
                bg600 = Color.parseColor("#FFFFFF"), bg500 = Color.parseColor("#F2F3F5"),
                statusbar = Color.parseColor("#FFFFFF"), isDark = false
            )
            -1 -> buildPalette(
                accent = Color.parseColor("#5865F2"),
                bg800 = Color.parseColor("#1E1F22"), bg700 = Color.parseColor("#2B2D31"),
                bg660 = Color.parseColor("#2E3035"), bg630 = Color.parseColor("#313338"),
                bg600 = Color.parseColor("#383A40"), bg500 = Color.parseColor("#404249"),
                statusbar = Color.parseColor("#1E1F22"), isDark = true
            )
            else -> {
                val p = theme.primaryColor
                val s = theme.secondaryColor

                fun mix(color: Int, base: Int, amount: Float): Int {
                    val r = (Color.red(color) * amount + Color.red(base) * (1f - amount)).toInt().coerceIn(0, 255)
                    val g = (Color.green(color) * amount + Color.green(base) * (1f - amount)).toInt().coerceIn(0, 255)
                    val b = (Color.blue(color) * amount + Color.blue(base) * (1f - amount)).toInt().coerceIn(0, 255)
                    return Color.rgb(r, g, b)
                }

                val base800 = if (theme.isDark) Color.rgb(20, 20, 25)   else Color.rgb(255, 255, 255)
                val base700 = if (theme.isDark) Color.rgb(30, 31, 37)   else Color.rgb(242, 243, 245)
                val base600 = if (theme.isDark) Color.rgb(49, 51, 56)   else Color.rgb(255, 255, 255)
                val str     = if (theme.isDark) 0.18f else 0.08f

                buildPalette(
                    accent    = p,
                    bg800     = mix(p, base800, str),
                    bg700     = mix(p, base700, str * 0.78f),
                    bg660     = mix(s, base700, str * 0.61f),
                    bg630     = mix(s, base700, str * 0.50f),
                    bg600     = mix(p, base600, str * 0.44f),
                    bg500     = mix(p, base600, str * 0.67f),
                    statusbar = mix(p, base800, str),
                    isDark    = theme.isDark,
                    mention   = ColorUtils.setAlphaComponent(p, if (theme.isDark) 0x30 else 0x20)
                )
            }
        }
    }

    private fun buildPalette(
        accent: Int, bg800: Int, bg700: Int, bg660: Int, bg630: Int,
        bg600: Int, bg500: Int, statusbar: Int, isDark: Boolean,
        mention: Int = ColorUtils.setAlphaComponent(accent, 0x30)
    ): Pair<Map<String, Int>, Map<String, Int>> {
        fun darken(c: Int, f: Float): Int {
            val r = (Color.red(c)   * (1 - f)).toInt().coerceIn(0, 255)
            val g = (Color.green(c) * (1 - f)).toInt().coerceIn(0, 255)
            val b = (Color.blue(c)  * (1 - f)).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }
        val accentDark   = darken(accent, 0.20f)
        val accentDarker = darken(accent, 0.35f)

        val simpleColors = mapOf(
            "accent"             to accent,
            "background"         to bg800,
            "background_secondary" to bg700,
            "mention_highlight"  to mention,
            "active_channel"     to darken(bg700, 0.08f),
            "statusbar"          to statusbar,
            "input_background"   to bg660
        )

        val colors = mapOf(
            "brand_new"          to accent,
            "brand_new_230"      to ColorUtils.setAlphaComponent(accent, 0x3A),
            "brand_new_360"      to accent,
            "brand_new_500"      to accent,
            "brand_new_530"      to accentDark,
            "brand_new_560"      to accentDark,
            "brand_new_600"      to accentDarker,
            "link"               to accent,
            "link_light"         to accentDark,
            "primary_500"        to bg500,
            "primary_600"        to bg600,
            "primary_630"        to bg630,
            "primary_660"        to bg660,
            "primary_700"        to bg700,
            "primary_800"        to bg800,
            "primary_dark_600"   to bg600,
            "primary_dark_630"   to bg630,
            "primary_dark_660"   to bg660,
            "primary_dark_700"   to bg700,
            "primary_dark_800"   to bg800,
            "dark_grey_2"        to bg800
        )

        return simpleColors to colors
    }
}
