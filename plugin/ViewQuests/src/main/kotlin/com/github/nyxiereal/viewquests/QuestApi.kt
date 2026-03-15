package com.github.nyxiereal.viewquests

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object QuestApi {

    private const val BASE = "https://discord.com/api/v9"
    private const val DELAY = 300L

    private val superProps = "eyJvcyI6IkFuZHJvaWQiLCJicm93c2VyIjoiQW5kcm9pZCBNb2JpbGUiLCJkZXZpY2UiOiJBbmRyb2lkIiwic3lzdGVtX2xvY2FsZSI6InB0LUJSIiwiaGFzX2NsaWVudF9tb2RzIjpmYWxzZSwiYnJvd3Nlcl91c2VyX2FnZW50IjoiTW96aWxsYS81LjAgKEFuZHJvaWQgMTI7IE1vYmlsZTsgcnY6MTQ4LjApIEdlY2tvLzE0OC4wIEZpcmVmb3gvMTQ4LjAiLCJicm93c2VyX3ZlcnNpb24iOiIxNDguMCIsIm9zX3ZlcnNpb24iOiIxMiIsInJlZmVycmVyIjoiIiwicmVmZXJyaW5nX2RvbWFpbiI6IiIsInJlZmVycmVyX2N1cnJlbnQiOiIiLCJyZWZlcnJpbmdfZG9tYWluX2N1cnJlbnQiOiIiLCJyZWxlYXNlX2NoYW5uZWwiOiJzdGFibGUiLCJjbGllbnRfYnVpbGRfbnVtYmVyIjo1MTA3MzMsImNsaWVudF9ldmVudF9zb3VyY2UiOm51bGx9"

    private fun token(): String = try {
        com.discord.utilities.rest.RestAPI.AppHeadersProvider.INSTANCE.authToken ?: ""
    } catch (_: Exception) { "" }

    private fun openConn(endpoint: String): HttpURLConnection =
        (URL("$BASE$endpoint").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", token())
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android 12; Mobile; rv:148.0) Gecko/148.0 Firefox/148.0")
            setRequestProperty("X-Super-Properties", superProps)
            setRequestProperty("X-Discord-Locale", "pt-BR")
            setRequestProperty("X-Discord-Timezone", "America/Sao_Paulo")
            setRequestProperty("X-Debug-Options", "bugReporterEnabled")
            setRequestProperty("Referer", "https://discord.com/quest-home")
            connectTimeout = 20000
            readTimeout = 20000
        }

    private fun readBody(c: HttpURLConnection): String =
        (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.readText() ?: ""

    private fun doGet(endpoint: String): Pair<Int, String> {
        Thread.sleep(DELAY)
        val c = openConn(endpoint)
        return try { c.requestMethod = "GET"; c.responseCode to readBody(c) }
        finally { c.disconnect() }
    }

    private fun doPost(endpoint: String, body: String, extra: Map<String, String> = emptyMap()): Pair<Int, String> {
        Thread.sleep(DELAY)
        val c = openConn(endpoint)
        return try {
            c.requestMethod = "POST"
            extra.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.doOutput = true
            val bytes = body.toByteArray(Charsets.UTF_8)
            c.setRequestProperty("Content-Length", bytes.size.toString())
            c.outputStream.use { it.write(bytes) }
            c.responseCode to readBody(c)
        } finally { c.disconnect() }
    }

    fun getQuests(): JSONObject {
        val (_, body) = doGet("/quests/@me")
        return try { JSONObject(body) } catch (_: Exception) { JSONObject() }
    }

    fun getOrbBalance(): Int {
        val (_, body) = doGet("/users/@me/virtual-currency/balance")
        return try { JSONObject(body).optInt("balance", 0) } catch (_: Exception) { 0 }
    }

    fun enrollQuest(questId: String): String? {
        val body = JSONObject()
            .put("location_context", JSONObject()
                .put("guild_id", "0")
                .put("channel_id", "0")
                .put("channel_type", "0"))
            .toString()
        val (code, resp) = doPost("/quests/$questId/enroll", body)
        return try {
            if (code in 200..299) {
                val j = JSONObject(resp)
                j.optString("enrolled_at").takeIf { it.isNotEmpty() && it != "null" }
                    ?: j.optJSONObject("user_status")?.optString("enrolled_at")?.takeIf { it.isNotEmpty() && it != "null" }
            } else null
        } catch (_: Exception) { null }
    }

    fun getQuestStatus(questId: String): JSONObject {
        val (_, body) = doGet("/quests/@me/$questId")
        return try { JSONObject(body) } catch (_: Exception) { JSONObject() }
    }

    fun sendVideoProgress(questId: String, timestamp: Double): JSONObject {
        val (_, body) = doPost("/quests/$questId/video-progress", JSONObject().put("timestamp", timestamp).toString())
        return try { JSONObject(body) } catch (_: Exception) { JSONObject() }
    }

    fun sendHeartbeat(questId: String, streamKey: String, terminal: Boolean): JSONObject {
        val (_, body) = doPost("/quests/$questId/heartbeat",
            JSONObject().put("stream_key", streamKey).put("terminal", terminal).toString())
        return try { JSONObject(body) } catch (_: Exception) { JSONObject() }
    }

    fun claimReward(questId: String, captchaKey: String? = null, captchaRqtoken: String? = null): Pair<Int, JSONObject> {
        val extra = mutableMapOf<String, String>()
        if (captchaKey != null) extra["X-Captcha-Key"] = captchaKey
        if (captchaRqtoken != null) extra["X-Captcha-Rqtoken"] = captchaRqtoken
        val (code, body) = doPost("/quests/$questId/claim-reward", "{}", extra)
        return code to try { JSONObject(body) } catch (_: Exception) { JSONObject() }
    }

    fun getFirstDmChannelId(): String? = try {
        val (_, body) = doGet("/users/@me/channels")
        val arr = JSONArray(body)
        if (arr.length() > 0) arr.getJSONObject(0).optString("id").takeIf { it.isNotEmpty() } else null
    } catch (_: Exception) { null }

    fun getCollectibles(): JSONArray = try {
        val (_, body) = doGet("/users/@me/collectibles-purchases")
        JSONArray(body)
    } catch (_: Exception) { JSONArray() }

    fun extractVideoUrl(questId: String, rawQuestJson: JSONObject): String? {
        val cfg = rawQuestJson.optJSONObject("config") ?: return null
        val assets = cfg.optJSONObject("assets")
        if (assets != null) {
            for (key in listOf("quest_bar_video", "video", "promo_video", "hero_video", "video_asset")) {
                val v = assets.optString(key, "").takeIf { it.isNotEmpty() && it != "null" } ?: continue
                return when {
                    v.startsWith("http") -> v
                    v.startsWith("quests/") -> "https://cdn.discordapp.com/$v"
                    else -> "https://cdn.discordapp.com/quests/$questId/$v"
                }
            }
            for (key in assets.keys()) {
                val v = assets.optString(key, "")
                if (v.contains(".mp4") || v.contains(".m3u8") || v.contains(".webm")) {
                    return when {
                        v.startsWith("http") -> v
                        v.startsWith("quests/") -> "https://cdn.discordapp.com/$v"
                        else -> "https://cdn.discordapp.com/quests/$questId/$v"
                    }
                }
            }
        }
        val appId = cfg.optJSONObject("application")?.optString("id")?.takeIf { it.isNotEmpty() && it != "null" }
            ?: return null
        return "https://cdn.discordapp.com/quests/$questId/${appId}_mx480.m3u8"
    }
}
