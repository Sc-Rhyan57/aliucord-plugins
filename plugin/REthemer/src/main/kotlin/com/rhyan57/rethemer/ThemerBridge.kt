package com.rhyan57.rethemer

import com.aliucord.Constants
import org.json.JSONObject
import java.io.File

object ThemerBridge {

    val themesDir = File(Constants.BASE_PATH, "themes")

    fun applyTheme(theme: DiscordTheme): Boolean {
        return try {
            ThemerEngine.clean()
            val (simpleColors, colors) = DiscordThemes.toThemerColors(theme)
            ThemerEngine.applySimpleColors(simpleColors)
            ThemerEngine.applyColors(colors)
            saveThemeJson(theme)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveThemeJson(theme: DiscordTheme) {
        try {
            if (!themesDir.exists()) themesDir.mkdirs()
            val safeName = theme.name.replace(" ", "_")
            val (simpleColors, colors) = DiscordThemes.toThemerColors(theme)
            val json = JSONObject().apply {
                put("manifest", JSONObject().apply {
                    put("name", safeName); put("author", "REthemer"); put("version", "1.0.0")
                })
                put("background", JSONObject())
                put("fonts", JSONObject())
                put("raws", JSONObject())
                put("simple_colors", JSONObject().apply { simpleColors.forEach { (k, v) -> put(k, v) } })
                put("colors", JSONObject().apply { colors.forEach { (k, v) -> put(k, v) } })
                put("drawable_tints", JSONObject())
            }
            File(themesDir, "$safeName.json").writeText(json.toString(4))
        } catch (_: Exception) {}
    }

    fun listInstalledThemes(): List<String> {
        if (!themesDir.exists()) return emptyList()
        return themesDir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?: emptyList()
    }

    fun applyThemeFromFile(fileName: String): Boolean {
        return try {
            val file = File(themesDir, "$fileName.json")
            if (!file.exists()) return false
            val json = JSONObject(file.readText())
            ThemerEngine.clean()

            json.optJSONObject("simple_colors")?.let { sc ->
                val map = mutableMapOf<String, Int>()
                sc.keys().forEach { k -> map[k] = sc.getInt(k) }
                ThemerEngine.applySimpleColors(map)
            }

            json.optJSONObject("colors")?.let { c ->
                val map = mutableMapOf<String, Int>()
                c.keys().forEach { k -> map[k] = c.getInt(k) }
                ThemerEngine.applyColors(map)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
