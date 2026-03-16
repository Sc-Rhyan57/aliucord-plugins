package com.rhyan57.rethemer

import com.aliucord.Http
import com.aliucord.Logger
import com.discord.stores.StoreStream
import org.json.JSONArray
import org.json.JSONObject

object DiscordApi {
    private val log = Logger("REthemer/API")

    fun hasNitro(): Boolean {
        return try {
            val me = StoreStream.getUsers().me
            var premiumType = 0
            for (field in me.javaClass.declaredFields + me.javaClass.superclass?.declaredFields.orEmpty()) {
                if (field.name == "premiumType" || field.name == "premium_type") {
                    field.isAccessible = true
                    premiumType = (field.get(me) as? Int) ?: 0
                    break
                }
            }
            if (premiumType == 0) {
                for (method in me.javaClass.methods + me.javaClass.declaredMethods) {
                    if (method.name.lowercase().contains("premium") && method.parameterCount == 0) {
                        method.isAccessible = true
                        val v = method.invoke(me)
                        if (v is Int && v > 0) { log.info("hasNitro via method ${method.name}: $v"); return true }
                        if (v is Boolean && v) { log.info("hasNitro via method ${method.name}: true"); return true }
                    }
                }
                log.info("hasNitro: premiumType field not found directly, trying API fallback")
                return hasNitroViaApi()
            }
            log.info("hasNitro: premiumType=$premiumType")
            premiumType > 0
        } catch (e: Exception) {
            log.error("hasNitro local failed, trying API", e)
            hasNitroViaApi()
        }
    }

    private fun hasNitroViaApi(): Boolean {
        return try {
            val req = Http.Request.newDiscordRNRequest("/users/@me", "GET")
            val res = req.execute()
            if (res.statusCode !in 200..299) {
                log.warn("hasNitroViaApi HTTP ${res.statusCode}")
                return false
            }
            val json = res.json(JSONObject::class.java)
            val pt = json.optInt("premium_type", 0)
            log.info("hasNitroViaApi: premium_type=$pt")
            pt > 0
        } catch (e: Exception) {
            log.error("hasNitroViaApi failed", e)
            false
        }
    }

    fun applyTheme(protoPayload: String): Pair<Boolean, String> {
        return try {
            val req = Http.Request.newDiscordRNRequest("/users/@me/settings-proto/1", "PATCH")
            req.setHeader("Content-Type", "application/json")
            val body = JSONObject().put("settings", protoPayload).toString().toByteArray(Charsets.UTF_8)
            req.conn.doOutput = true
            req.conn.outputStream.write(body)
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
            val req = Http.Request.newDiscordRNRequest("/users/@me/profile", "PATCH")
            req.setHeader("Content-Type", "application/json")
            val body = JSONObject().put("theme_colors", JSONArray().put(p).put(a)).toString().toByteArray(Charsets.UTF_8)
            req.conn.doOutput = true
            req.conn.outputStream.write(body)
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
            val req = Http.Request.newDiscordRNRequest("/users/$uid/profile?with_mutual_guilds=false&with_mutual_friends_count=false", "GET")
            val res = req.execute()
            if (res.statusCode in 200..299) res.json(JSONObject::class.java) else null
        } catch (e: Exception) {
            log.error("getCurrentProfile exception", e)
            null
        }
    }
}
