package com.rhyan57.rethemer

import android.graphics.Color

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
        DiscordTheme(0,  "Custom",             Color.parseColor("#5865F2"), Color.parseColor("#7289DA"), true,  "agoIAhABGgISACAB"),
        DiscordTheme(1,  "Mint Apple",          Color.parseColor("#3BA55D"), Color.parseColor("#2D7D46"), true,  "agwIAhABGgQSAggBIAE="),
        DiscordTheme(2,  "Citrus Sherbert",     Color.parseColor("#F8A532"), Color.parseColor("#E67E22"), true,  "agwIAhABGgQSAggCIAE="),
        DiscordTheme(3,  "Retro Raincloud",     Color.parseColor("#8E9297"), Color.parseColor("#72767D"), true,  "agwIAhABGgQSAggDIAE="),
        DiscordTheme(4,  "Hanami",              Color.parseColor("#E91E8C"), Color.parseColor("#C2185B"), true,  "agwIAhABGgQSAggEIAE="),
        DiscordTheme(5,  "Sunrise",             Color.parseColor("#FF6B35"), Color.parseColor("#F7C59F"), false, "agwIAhABGgQSAggFIAE="),
        DiscordTheme(6,  "Cotton Candy",        Color.parseColor("#FF73FA"), Color.parseColor("#7289DA"), false, "agwIAhABGgQSAggGIAE="),
        DiscordTheme(7,  "Lofi Vibes",          Color.parseColor("#4F545C"), Color.parseColor("#36393F"), true,  "agwIAhABGgQSAggHIAE="),
        DiscordTheme(8,  "Desert Khaki",        Color.parseColor("#C9B99A"), Color.parseColor("#A99A8A"), false, "agwIARABGgQSAggIIAE="),
        DiscordTheme(9,  "Sunset",              Color.parseColor("#FF7043"), Color.parseColor("#E040FB"), true,  "agwIARABGgQSAggJIAE="),
        DiscordTheme(10, "Chroma Glow",         Color.parseColor("#5865F2"), Color.parseColor("#EB459E"), true,  "agwIARABGgQSAggKIAE="),
        DiscordTheme(11, "Forest",              Color.parseColor("#2D6A4F"), Color.parseColor("#1B4332"), true,  "agwIARABGgQSAggLIAE="),
        DiscordTheme(12, "Crimson",             Color.parseColor("#ED4245"), Color.parseColor("#A12D2F"), true,  "agwIARABGgQSAggMIAE="),
        DiscordTheme(13, "Midnight Blurple",    Color.parseColor("#5865F2"), Color.parseColor("#4752C4"), true,  "agwIARABGgQSAggNIAE="),
        DiscordTheme(14, "Mars",                Color.parseColor("#C0392B"), Color.parseColor("#922B21"), true,  "agwIARABGgQSAggOIAE="),
        DiscordTheme(15, "Dusk",                Color.parseColor("#7289DA"), Color.parseColor("#4E5D94"), true,  "agwIARABGgQSAggPIAE="),
        DiscordTheme(17, "Under The Sea",       Color.parseColor("#1ABC9C"), Color.parseColor("#148F77"), true,  "agwIARABGgQSAggRIAE="),
        DiscordTheme(18, "Retro Storm",         Color.parseColor("#9B59B6"), Color.parseColor("#6C3483"), true,  "agwIARABGgQSAggSIAE="),
        DiscordTheme(19, "Neon Nights",         Color.parseColor("#00D2FF"), Color.parseColor("#7B2FF7"), true,  "agwIARABGgQSAggTIAE="),
        DiscordTheme(20, "Strawberry Lemonade", Color.parseColor("#FF6B6B"), Color.parseColor("#FFE66D"), false, "agwIARABGgQSAggUIAE="),
        DiscordTheme(21, "Aurora",              Color.parseColor("#6DD5FA"), Color.parseColor("#2980B9"), false, "agwIARABGgQSAggVIAE="),
        DiscordTheme(22, "Sepia",               Color.parseColor("#C4A882"), Color.parseColor("#8D6E63"), false, "agwIARABGgQSAggWIAE="),
        DiscordTheme(23, "Cobalt Dusk",         Color.parseColor("#1E3A5F"), Color.parseColor("#0D1F3C"), true,  "agwIARABGgQSAggXIAE="),
        DiscordTheme(24, "Chromatic Glow",      Color.parseColor("#9C27B0"), Color.parseColor("#E040FB"), true,  "agwIARABGgQSAggYIAE="),
        DiscordTheme(25, "Retro Rainforest",    Color.parseColor("#27AE60"), Color.parseColor("#145A32"), true,  "agwIARABGgQSAggZIAE="),
        DiscordTheme(26, "Ocean Breeze",        Color.parseColor("#0099CC"), Color.parseColor("#005580"), true,  "agwIARABGgQSAggaIAE="),
        DiscordTheme(27, "Volcanic",            Color.parseColor("#FF4500"), Color.parseColor("#8B0000"), true,  "agwIARABGgQSAgghIAE="),
        DiscordTheme(28, "Twilight",            Color.parseColor("#4A148C"), Color.parseColor("#880E4F"), true,  "agwIARABGgQSAggcIAE=")
    )

    fun toThemerColors(theme: DiscordTheme): Pair<Map<String, Int>, Map<String, Int>> {
        val p = theme.primaryColor
        val s = theme.secondaryColor

        fun blend(color: Int, base: Int, amount: Float): Int {
            val r = (Color.red(color) * amount + Color.red(base) * (1f - amount)).toInt().coerceIn(0, 255)
            val g = (Color.green(color) * amount + Color.green(base) * (1f - amount)).toInt().coerceIn(0, 255)
            val b = (Color.blue(color) * amount + Color.blue(base) * (1f - amount)).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }

        val black = Color.rgb(0, 0, 0)
        val white = Color.rgb(255, 255, 255)
        val discordBg    = Color.rgb(54,  57,  63)
        val discordBgSec = Color.rgb(47,  49,  54)
        val discordBgTer = Color.rgb(32,  34,  37)

        val accent: Int
        val bg: Int
        val bgSec: Int
        val bgTer: Int

        if (theme.isDark) {
            accent = p
            bg     = blend(p, discordBgTer, 0.30f)
            bgSec  = blend(p, discordBgSec, 0.25f)
            bgTer  = blend(p, discordBg,    0.20f)
        } else {
            accent = p
            bg     = blend(p, white, 0.85f)
            bgSec  = blend(p, white, 0.75f)
            bgTer  = blend(p, white, 0.65f)
        }

        val accentDark   = blend(p, black, 0.80f)
        val accentDarker = blend(p, black, 0.70f)
        val accentLight  = blend(p, white, 0.80f)

        val mentionHighlight = Color.argb(0x30, Color.red(p), Color.green(p), Color.blue(p))

        val simpleColors = mapOf(
            "accent"             to accent,
            "background"         to bg,
            "background_secondary" to bgSec,
            "mention_highlight"  to mentionHighlight,
            "active_channel"     to blend(p, black, 0.60f),
            "statusbar"          to bgTer,
            "input_background"   to bgSec
        )

        val colors = mapOf(
            "brand_new"          to accent,
            "brand_new_230"      to blend(accent, white, 0.60f),
            "brand_new_360"      to accent,
            "brand_new_500"      to accent,
            "brand_new_530"      to accentDark,
            "brand_new_560"      to accentDark,
            "brand_new_600"      to accentDarker,
            "link"               to accent,
            "link_light"         to accentLight,
            "primary_500"        to bgSec,
            "primary_600"        to bg,
            "primary_630"        to bgSec,
            "primary_660"        to bgSec,
            "primary_700"        to bgSec,
            "primary_800"        to bg,
            "primary_dark_600"   to bg,
            "primary_dark_630"   to bgSec,
            "primary_dark_660"   to bgSec,
            "primary_dark_700"   to bgSec,
            "primary_dark_800"   to bg,
            "dark_grey_2"        to bg
        )

        return simpleColors to colors
    }
}
