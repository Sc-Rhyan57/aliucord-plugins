package com.rhyan57.rethemer

import com.aliucord.Constants
import org.json.JSONObject
import java.io.File

object ThemerBridge {

    private val themesDir = File(Constants.BASE_PATH, "themes")

    fun exportTheme(theme: DiscordTheme, name: String): File? {
        if (!themesDir.exists()) themesDir.mkdirs()
        val simpleColors = DiscordThemes.toThemerSimpleColors(theme)
        val json = JSONObject().apply {
            put("manifest", JSONObject().apply {
                put("name", name)
                put("author", "rhyan57 / REthemer")
                put("version", "1.0.0")
            })
            put("background", JSONObject())
            put("fonts", JSONObject())
            put("raws", JSONObject())
            put("simple_colors", JSONObject().apply {
                simpleColors.forEach { (k, v) -> put(k, v) }
            })
            put("colors", JSONObject())
            put("drawable_tints", JSONObject())
        }
        val file = File(themesDir, "${name.replace(" ", "_")}.json")
        return try {
            file.writeText(json.toString(4))
            file
        } catch (_: Exception) { null }
    }

    fun listInstalledThemes(): List<String> {
        if (!themesDir.exists()) return emptyList()
        return themesDir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?: emptyList()
    }

    fun applyThemeToThemer(theme: DiscordTheme, name: String): Boolean {
        val file = exportTheme(theme, name) ?: return false
        return file.exists()
    }

    fun activateTheme(themeName: String): Boolean {
        val file = File(themesDir, "$themeName.json")
        return file.exists()
    }
}
