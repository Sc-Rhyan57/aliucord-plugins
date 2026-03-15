package com.github.nyxiereal.viewquests

import com.aliucord.Http
import org.json.JSONObject

object QuestApi {

    private const val BASE = "https://discord.com/api/v9"

    private val superProps = "eyJvcyI6IkFuZHJvaWQiLCJicm93c2VyIjoiQW5kcm9pZCBNb2JpbGUiLCJkZXZpY2UiOiJBbmRyb2lkIiwic3lzdGVtX2xvY2FsZSI6InB0LUJSIiwiaGFzX2NsaWVudF9tb2RzIjpmYWxzZSwiYnJvd3Nlcl91c2VyX2FnZW50IjoiTW96aWxsYS81LjAgKEFuZHJvaWQgMTI7IE1vYmlsZTsgcnY6MTQ4LjApIEdlY2tvLzE0OC4wIEZpcmVmb3gvMTQ4LjAiLCJicm93c2VyX3ZlcnNpb24iOiIxNDguMCIsIm9zX3ZlcnNpb24iOiIxMiIsInJlZmVycmVyIjoiIiwicmVmZXJyaW5nX2RvbWFpbiI6IiIsInJlZmVycmVyX2N1cnJlbnQiOiIiLCJyZWZlcnJpbmdfZG9tYWluX2N1cnJlbnQiOiIiLCJyZWxlYXNlX2NoYW5uZWwiOiJzdGFibGUiLCJjbGllbnRfYnVpbGRfbnVtYmVyIjo1MTA3MzMsImNsaWVudF9ldmVudF9zb3VyY2UiOm51bGx9"

    fun getQuests(): JSONObject {
        val req = Http.Request.newDiscordRNRequest("/quests/@me", "GET")
        req.setHeader("X-Super-Properties", superProps)
        req.setHeader("X-Discord-Locale", "pt-BR")
        req.setHeader("X-Discord-Timezone", "America/Sao_Paulo")
        req.setHeader("X-Debug-Options", "bugReporterEnabled")
        req.setHeader("Referer", "https://discord.com/quest-home")
        return req.execute().json(JSONObject::class.java)
    }

    fun getOrbBalance(): Int {
        val req = Http.Request.newDiscordRNRequest("/users/@me/virtual-currency/balance", "GET")
        return try {
            req.execute().json(JSONObject::class.java).optInt("balance", 0)
        } catch (_: Exception) { 0 }
    }

    fun enrollQuest(questId: String): JSONObject {
        val req = Http.Request.newDiscordRNRequest("/quests/$questId/enroll", "POST")
        req.setHeader("X-Super-Properties", superProps)
        req.setHeader("Referer", "https://discord.com/quest-home")
        req.setHeader("Content-Type", "application/json")
        req.setRequestBody(JSONObject().put("platform", 0).toString())
        return req.execute().json(JSONObject::class.java)
    }

    fun sendVideoProgress(questId: String, timestamp: Double): JSONObject {
        val req = Http.Request.newDiscordRNRequest("/quests/$questId/video-progress", "POST")
        req.setHeader("X-Super-Properties", superProps)
        req.setHeader("Referer", "https://discord.com/quest-home")
        req.setHeader("Content-Type", "application/json")
        req.setRequestBody(JSONObject().put("timestamp", timestamp).toString())
        return req.execute().json(JSONObject::class.java)
    }

    fun sendHeartbeat(questId: String, streamKey: String, terminal: Boolean): JSONObject {
        val req = Http.Request.newDiscordRNRequest("/quests/$questId/heartbeat", "POST")
        req.setHeader("X-Super-Properties", superProps)
        req.setHeader("Referer", "https://discord.com/quest-home")
        req.setHeader("Content-Type", "application/json")
        req.setRequestBody(JSONObject().put("stream_key", streamKey).put("terminal", terminal).toString())
        return req.execute().json(JSONObject::class.java)
    }

    fun claimReward(questId: String, captchaKey: String? = null, captchaRqtoken: String? = null): Pair<Int, JSONObject> {
        val req = Http.Request.newDiscordRNRequest("/quests/$questId/claim-reward", "POST")
        req.setHeader("X-Super-Properties", superProps)
        req.setHeader("Referer", "https://discord.com/quest-home")
        req.setHeader("Content-Type", "application/json")
        if (captchaKey != null) req.setHeader("X-Captcha-Key", captchaKey)
        if (captchaRqtoken != null) req.setHeader("X-Captcha-Rqtoken", captchaRqtoken)
        req.setRequestBody("{}")
        val res = req.execute()
        return try {
            res.statusCode to res.json(JSONObject::class.java)
        } catch (_: Exception) {
            res.statusCode to JSONObject()
        }
    }

    fun getFirstDmChannelId(): String? {
        return try {
            val req = Http.Request.newDiscordRNRequest("/users/@me/channels", "GET")
            val arr = req.execute().json(org.json.JSONArray::class.java)
            if (arr.length() > 0) arr.getJSONObject(0).optString("id").takeIf { it.isNotEmpty() } else null
        } catch (_: Exception) { null }
    }

    fun getCollectibles(): org.json.JSONArray {
        return try {
            val req = Http.Request.newDiscordRNRequest("/users/@me/collectibles-purchases", "GET")
            req.execute().json(org.json.JSONArray::class.java)
        } catch (_: Exception) { org.json.JSONArray() }
    }
}
