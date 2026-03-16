package com.rhyan57.rethemer

import com.aliucord.Http
import com.aliucord.Logger
import com.discord.stores.StoreStream
import org.json.JSONArray
import org.json.JSONObject

object DiscordApi {
    private val log = Logger("REthemer/API")

    private val superProps = "eyJvcyI6IkFuZHJvaWQiLCJicm93c2VyIjoiQW5kcm9pZCBNb2JpbGUiLCJkZXZpY2UiOiJBbmRyb2lkIiwic3lzdGVtX2xvY2FsZSI6InB0LUJSIiwiaGFzX2NsaWVudF9tb2RzIjpmYWxzZSwiYnJvd3Nlcl91c2VyX2FnZW50IjoiTW96aWxsYS81LjAgKEFuZHJvaWQgMTI7IE1vYmlsZTsgcnY6MTQ4LjApIEdlY2tvLzE0OC4wIEZpcmVmb3gvMTQ4LjAiLCJicm93c2VyX3ZlcnNpb24iOiIxNDguMCIsIm9zX3ZlcnNpb24iOiIxMiIsInJlZmVycmVyIjoiIiwicmVmZXJyaW5nX2RvbWFpbiI6IiIsInJlZmVycmVyX2N1cnJlbnQiOiIiLCJyZWZlcnJpbmdfZG9tYWluX2N1cnJlbnQiOiIiLCJyZWxlYXNlX2NoYW5uZWwiOiJzdGFibGUiLCJjbGllbnRfYnVpbGRfbnVtYmVyIjo1MTA3MzMsImNsaWVudF9ldmVudF9zb3VyY2UiOm51bGx9"

    private fun headers(req: Http.Request): Http.Request {
        req.setHeader("X-Super-Properties", superProps)
        req.setHeader("X-Discord-Locale", "pt-BR")
        req.setHeader("X-Discord-Timezone", "America/Sao_Paulo")
        req.setHeader("X-Debug-Options", "bugReporterEnabled")
        req.setHeader("Referer", "https://discord.com/channels/@me")
        return req
    }

    private fun Http.Request.writeBody(body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        conn.doOutput = true
        conn.outputStream.write(bytes)
    }

    fun hasNitro(): Boolean {
        return try {
            StoreStream.getUsers().me.premiumType > 0
        } catch (e: Exception) {
            log.error("hasNitro failed", e)
            false
        }
    }

    fun applyTheme(protoPayload: String): Pair<Boolean, String> {
        return try {
            val req = headers(Http.Request.newDiscordRNRequest("/users/@me/settings-proto/1", "PATCH"))
            req.setHeader("Content-Type", "application/json")
            req.writeBody(JSONObject().put("settings", protoPayload).toString())
            val res = req.execute()
            val code = res.statusCode
            val resp = try { res.json(JSONObject::class.java).toString() } catch (_: Exception) { "parse_error" }
            log.info("applyTheme HTTP $code")
            (code in 200..299) to resp
        } catch (e: Exception) {
            log.error("applyTheme exception", e)
            false to (e.message ?: "error")
        }
    }

    fun updateProfileGradient(primaryColor: Int, accentColor: Int): Pair<Boolean, String> {
        val p = primaryColor and 0xFFFFFF
        val a = accentColor and 0xFFFFFF
        return try {
            val req = headers(Http.Request.newDiscordRNRequest("/users/@me/profile", "PATCH"))
            req.setHeader("Content-Type", "application/json")
            req.writeBody(JSONObject().put("theme_colors", JSONArray().put(p).put(a)).toString())
            val res = req.execute()
            val code = res.statusCode
            val resp = try { res.json(JSONObject::class.java).toString() } catch (_: Exception) { "parse_error" }
            log.info("updateProfileGradient HTTP $code")
            (code in 200..299) to resp
        } catch (e: Exception) {
            log.error("updateProfileGradient exception", e)
            false to (e.message ?: "error")
        }
    }

    fun getCurrentProfile(): JSONObject? {
        return try {
            val uid = StoreStream.getUsers().me.id.toString()
            val req = headers(Http.Request.newDiscordRNRequest("/users/$uid/profile?with_mutual_guilds=false&with_mutual_friends_count=false", "GET"))
            val res = req.execute()
            if (res.statusCode in 200..299) res.json(JSONObject::class.java) else null
        } catch (e: Exception) {
            log.error("getCurrentProfile exception", e)
            null
        }
    }
}
