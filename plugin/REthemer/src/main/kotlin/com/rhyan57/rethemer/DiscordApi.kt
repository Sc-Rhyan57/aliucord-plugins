package com.rhyan57.rethemer

import com.aliucord.Http
import com.aliucord.Logger
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

    // Apply a Nitro theme via settings-proto
    fun applyTheme(protoPayload: String): Pair<Boolean, String> {
        log.info("applyTheme: payload=$protoPayload")
        return try {
            val req = headers(Http.Request.newDiscordRNRequest("/users/@me/settings-proto/1", "PATCH"))
            req.setHeader("Content-Type", "application/json")
            req.setRequestBody(JSONObject().put("settings", protoPayload).toString())
            val res = req.execute()
            val code = res.statusCode
            val body = try { res.json(JSONObject::class.java).toString() } catch (_: Exception) { "parse_error" }
            log.info("applyTheme: HTTP $code body=$body")
            (code in 200..299) to body
        } catch (e: Exception) {
            log.error("applyTheme exception", e)
            false to (e.message ?: "unknown error")
        }
    }

    // Update profile gradient colors (theme_colors)
    fun updateProfileGradient(primaryColor: Int, accentColor: Int): Pair<Boolean, String> {
        val p = primaryColor and 0xFFFFFF
        val a = accentColor and 0xFFFFFF
        log.info("updateProfileGradient: primary=#%06X accent=#%06X".format(p, a))
        return try {
            val req = headers(Http.Request.newDiscordRNRequest("/users/@me/profile", "PATCH"))
            req.setHeader("Content-Type", "application/json")
            val body = JSONObject().put("theme_colors", JSONArray().put(p).put(a)).toString()
            log.info("updateProfileGradient body: $body")
            req.setRequestBody(body)
            val res = req.execute()
            val code = res.statusCode
            val resp = try { res.json(JSONObject::class.java).toString() } catch (_: Exception) { "parse_error" }
            log.info("updateProfileGradient: HTTP $code resp=$resp")
            (code in 200..299) to resp
        } catch (e: Exception) {
            log.error("updateProfileGradient exception", e)
            false to (e.message ?: "unknown error")
        }
    }

    // Get current user profile to read existing theme_colors
    fun getCurrentProfile(): JSONObject? {
        return try {
            val me = com.discord.stores.StoreStream.getUsers().me
            val uid = me.id.toString()
            log.info("getCurrentProfile: uid=$uid")
            val req = headers(Http.Request.newDiscordRNRequest("/users/$uid/profile?with_mutual_guilds=false&with_mutual_friends_count=false", "GET"))
            val res = req.execute()
            val code = res.statusCode
            val json = try { res.json(JSONObject::class.java) } catch (e: Exception) { log.error("parse error", e); null }
            log.info("getCurrentProfile: HTTP $code json=$json")
            if (code in 200..299) json else null
        } catch (e: Exception) {
            log.error("getCurrentProfile exception", e)
            null
        }
    }
}
