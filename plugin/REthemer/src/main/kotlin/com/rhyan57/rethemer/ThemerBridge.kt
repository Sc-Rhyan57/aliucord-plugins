package com.rhyan57.rethemer

import com.aliucord.Constants
import com.aliucord.PluginManager
import org.json.JSONObject
import java.io.File

object ThemerBridge {

    private val themesDir = File(Constants.BASE_PATH, "themes")

    fun applyThemeToThemer(theme: DiscordTheme, name: String): Boolean {
        return try {
            if (!themesDir.exists()) themesDir.mkdirs()

            val safeName = name.replace(" ", "_")
            val (simpleColors, colors) = DiscordThemes.toThemerColors(theme)

            val json = JSONObject().apply {
                put("manifest", JSONObject().apply {
                    put("name", safeName)
                    put("author", "REthemer")
                    put("version", "1.0.0")
                })
                put("background", JSONObject())
                put("fonts", JSONObject())
                put("raws", JSONObject())
                put("simple_colors", JSONObject().apply {
                    simpleColors.forEach { (k, v) -> put(k, v) }
                })
                put("colors", JSONObject().apply {
                    colors.forEach { (k, v) -> put(k, v) }
                })
                put("drawable_tints", JSONObject())
            }

            val file = File(themesDir, "$safeName.json")
            file.writeText(json.toString(4))

            val themerSettings = PluginManager.plugins["Themer"]?.settings
            if (themerSettings != null) {
                themesDir.listFiles()
                    ?.filter { it.name.endsWith(".json") }
                    ?.forEach { f ->
                        val n = f.name.removeSuffix(".json")
                        themerSettings.setBool("$n-enabled", n == safeName)
                    }
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    fun listInstalledThemes(): List<String> {
        if (!themesDir.exists()) return emptyList()
        return themesDir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?: emptyList()
    }
}
