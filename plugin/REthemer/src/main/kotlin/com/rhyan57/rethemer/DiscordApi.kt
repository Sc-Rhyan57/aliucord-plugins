package com.rhyan57.rethemer

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DiscordApi {

    private const val BASE = "https://discord.com/api/v9"
    private val superProps = "eyJvcyI6IkFuZHJvaWQiLCJicm93c2VyIjoiQW5kcm9pZCBNb2JpbGUiLCJkZXZpY2UiOiJBbmRyb2lkIiwic3lzdGVtX2xvY2FsZSI6InB0LUJSIiwiaGFzX2NsaWVudF9tb2RzIjpmYWxzZSwiYnJvd3Nlcl91c2VyX2FnZW50IjoiTW96aWxsYS81LjAgKEFuZHJvaWQgMTI7IE1vYmlsZTsgcnY6MTQ4LjApIEdlY2tvLzE0OC4wIEZpcmVmb3gvMTQ4LjAiLCJicm93c2VyX3ZlcnNpb24iOiIxNDguMCIsIm9zX3ZlcnNpb24iOiIxMiIsInJlZmVycmVyIjoiIiwicmVmZXJyaW5nX2RvbWFpbiI6IiIsInJlZmVycmVyX2N1cnJlbnQiOiIiLCJyZWZlcnJpbmdfZG9tYWluX2N1cnJlbnQiOiIiLCJyZWxlYXNlX2NoYW5uZWwiOiJzdGFibGUiLCJjbGllbnRfYnVpbGRfbnVtYmVyIjo1MTA3MzMsImNsaWVudF9ldmVudF9zb3VyY2UiOm51bGx9"

    private fun token(): String = try {
        com.discord.utilities.rest.RestAPI.AppHeadersProvider.INSTANCE.authToken ?: ""
    } catch (_: Exception) { "" }

    private fun conn(endpoint: String, method: String = "GET"): HttpURLConnection =
        (URL("$BASE$endpoint").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", token())
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android 12; Mobile; rv:148.0) Gecko/148.0 Firefox/148.0")
            setRequestProperty("X-Super-Properties", superProps)
            setRequestProperty("X-Discord-Locale", "pt-BR")
            setRequestProperty("X-Discord-Timezone", "America/Sao_Paulo")
            setRequestProperty("X-Debug-Options", "bugReporterEnabled")
            connectTimeout = 15000
            readTimeout = 15000
        }

    private fun readBody(c: HttpURLConnection): String =
        (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.readText() ?: ""

    fun applyTheme(protoPayload: String): Boolean {
        val c = conn("/users/@me/settings-proto/1", "PATCH")
        return try {
            c.doOutput = true
            val body = JSONObject().put("settings", protoPayload).toString().toByteArray()
            c.setRequestProperty("Content-Length", body.size.toString())
            c.outputStream.use { it.write(body) }
            c.responseCode in 200..299
        } catch (_: Exception) { false }
        finally { c.disconnect() }
    }

    fun getCurrentThemeProto(): String? {
        val c = conn("/users/@me/settings-proto/1")
        return try {
            val body = readBody(c)
            JSONObject(body).optString("settings").takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
        finally { c.disconnect() }
    }

    fun updateProfileGradient(primaryColor: Int, accentColor: Int): Boolean {
        val c = conn("/users/@me/profile", "PATCH")
        return try {
            c.doOutput = true
            val themeColors = JSONArray().put(primaryColor and 0xFFFFFF).put(accentColor and 0xFFFFFF)
            val body = JSONObject().put("theme_colors", themeColors).toString().toByteArray()
            c.setRequestProperty("Content-Length", body.size.toString())
            c.outputStream.use { it.write(body) }
            c.responseCode in 200..299
        } catch (_: Exception) { false }
        finally { c.disconnect() }
    }

    fun getCurrentProfile(): JSONObject? {
        val c = conn("/users/@me/profile")
        return try {
            val body = readBody(c)
            if (c.responseCode in 200..299) JSONObject(body) else null
        } catch (_: Exception) { null }
        finally { c.disconnect() }
    }
}
