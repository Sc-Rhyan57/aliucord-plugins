package com.rhyan57.svclone

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class DiscordApiClient(private val token: String) {

    companion object {
        private const val BASE = "https://discord.com/api/v10"
        private const val DELAY_MS = 200L
        private const val MAX_RETRIES = 6
    }

    private fun openConn(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", token)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "SvClone/1.0")
            connectTimeout = 20000; readTimeout = 20000
        }

    private fun readText(conn: HttpURLConnection): String {
        val code = conn.responseCode
        return (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
    }

    private fun handleRateLimit(text: String) {
        val secs = try { JSONObject(text).optDouble("retry_after", 2.0) } catch (_: Exception) { 2.0 }
        CloneSession.addLog("[RATE LIMIT] Aguardando ${secs.toInt() + 1}s...")
        Thread.sleep((secs * 1000).toLong() + 1000)
    }

    private fun request(method: String, endpoint: String, body: JSONObject? = null): JSONObject? {
        repeat(MAX_RETRIES) {
            Thread.sleep(DELAY_MS)
            val conn = openConn("$BASE$endpoint")
            try {
                conn.requestMethod = method
                if (body != null) {
                    conn.doOutput = true
                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    conn.setRequestProperty("Content-Length", bytes.size.toString())
                    conn.outputStream.use { it.write(bytes) }
                }
                val code = conn.responseCode
                val text = readText(conn)
                if (code == 429) { handleRateLimit(text); return@repeat }
                return when {
                    code == 204 -> null
                    text.isEmpty() -> null
                    text.startsWith("[") -> JSONObject().put("array", JSONArray(text))
                    else -> JSONObject(text)
                }
            } catch (_: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun requestArray(endpoint: String): JSONArray {
        repeat(MAX_RETRIES) {
            Thread.sleep(DELAY_MS)
            val conn = openConn("$BASE$endpoint")
            try {
                conn.requestMethod = "GET"
                val code = conn.responseCode
                val text = readText(conn)
                if (code == 429) { handleRateLimit(text); return@repeat }
                return if (text.startsWith("[")) JSONArray(text) else JSONArray()
            } catch (_: Exception) {
                return JSONArray()
            } finally {
                conn.disconnect()
            }
        }
        return JSONArray()
    }

    fun getGuild(guildId: String) = request("GET", "/guilds/$guildId?with_counts=false")
    fun getRoles(guildId: String) = requestArray("/guilds/$guildId/roles")
    fun getChannels(guildId: String) = requestArray("/guilds/$guildId/channels")
    fun getEmojis(guildId: String) = requestArray("/guilds/$guildId/emojis")
    fun getStickers(guildId: String) = requestArray("/guilds/$guildId/stickers")
    fun getSoundboardSounds(guildId: String) = requestArray("/guilds/$guildId/soundboard-sounds")
    fun getBans(guildId: String) = requestArray("/guilds/$guildId/bans?limit=1000")
    fun getScheduledEvents(guildId: String) = requestArray("/guilds/$guildId/scheduled-events?with_user_count=false")
    fun getAutoModRules(guildId: String) = requestArray("/guilds/$guildId/auto-moderation/rules")
    fun getOnboarding(guildId: String) = request("GET", "/guilds/$guildId/onboarding")
    fun getWelcomeScreen(guildId: String) = request("GET", "/guilds/$guildId/welcome-screen")

    fun getChannelMessages(channelId: String, limit: Int = 100, before: String? = null): JSONArray {
        val params = "?limit=$limit${if (before != null) "&before=$before" else ""}"
        return requestArray("/channels/$channelId/messages$params")
    }

    fun getForumThreads(channelId: String): JSONArray {
        return try {
            val active = request("GET", "/channels/$channelId/threads/active")
            val archived = request("GET", "/channels/$channelId/threads/archived/public?limit=100")
            val threads = JSONArray()
            active?.optJSONArray("threads")?.let { for (i in 0 until it.length()) threads.put(it.getJSONObject(i)) }
            archived?.optJSONArray("threads")?.let { for (i in 0 until it.length()) threads.put(it.getJSONObject(i)) }
            threads
        } catch (_: Exception) { JSONArray() }
    }

    fun createGuild(name: String) = request("POST", "/guilds", JSONObject().put("name", name))
    fun modifyGuild(guildId: String, body: JSONObject) = request("PATCH", "/guilds/$guildId", body)
    fun createRole(guildId: String, role: JSONObject) = request("POST", "/guilds/$guildId/roles", role)
    fun deleteDefaultChannel(guildId: String, channelId: String) { request("DELETE", "/channels/$channelId") }
    fun createChannel(guildId: String, channel: JSONObject) = request("POST", "/guilds/$guildId/channels", channel)

    fun modifyChannelPermissions(channelId: String, overwriteId: String, body: JSONObject) {
        repeat(MAX_RETRIES) {
            Thread.sleep(DELAY_MS)
            val conn = URL("$BASE/channels/$channelId/permissions/$overwriteId").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Authorization", token)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "SvClone/1.0")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code == 429) { handleRateLimit(readText(conn)); return@repeat }
                return
            } catch (_: Exception) { return } finally { conn.disconnect() }
        }
    }

    fun createEmoji(guildId: String, name: String, imageBase64: String, roles: JSONArray) =
        request("POST", "/guilds/$guildId/emojis", JSONObject().put("name", name).put("image", imageBase64).put("roles", roles))

    fun createSticker(guildId: String, name: String, description: String, tags: String, fileBytes: ByteArray, mimeType: String): JSONObject? {
        repeat(MAX_RETRIES) {
            Thread.sleep(DELAY_MS)
            val boundary = "----SvCloneBoundary${System.currentTimeMillis()}"
            val conn = URL("$BASE/guilds/$guildId/stickers").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", token)
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.setRequestProperty("User-Agent", "SvClone/1.0")
                conn.doOutput = true
                val out: OutputStream = conn.outputStream
                fun wf(f: String, v: String) {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$f\"\r\n\r\n".toByteArray())
                    out.write("$v\r\n".toByteArray())
                }
                wf("name", name); wf("description", description); wf("tags", tags)
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"file\"; filename=\"sticker.${if (mimeType.contains("png")) "png" else "gif"}\"\r\n".toByteArray())
                out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                out.write(fileBytes); out.write("\r\n--$boundary--\r\n".toByteArray()); out.flush()
                val code = conn.responseCode; val text = readText(conn)
                if (code == 429) { handleRateLimit(text); return@repeat }
                return JSONObject(text)
            } catch (_: Exception) { return null } finally { conn.disconnect() }
        }
        return null
    }

    fun createSoundboardSound(guildId: String, name: String, soundBase64: String, volume: Double, emojiId: String?, emojiName: String?) =
        request("POST", "/guilds/$guildId/soundboard-sounds", JSONObject().apply {
            put("name", name); put("sound", soundBase64); put("volume", volume)
            if (emojiId != null) put("emoji_id", emojiId)
            if (emojiName != null) put("emoji_name", emojiName)
        })

    fun createBan(guildId: String, userId: String, reason: String?) =
        request("PUT", "/guilds/$guildId/bans/$userId", JSONObject().apply {
            put("delete_message_seconds", 0)
            if (reason != null) put("reason", reason)
        })

    fun createScheduledEvent(guildId: String, event: JSONObject) = request("POST", "/guilds/$guildId/scheduled-events", event)
    fun createAutoModRule(guildId: String, rule: JSONObject) = request("POST", "/guilds/$guildId/auto-moderation/rules", rule)
    fun updateOnboarding(guildId: String, onboarding: JSONObject) = request("PUT", "/guilds/$guildId/onboarding", onboarding)
    fun updateWelcomeScreen(guildId: String, w: JSONObject) = request("PATCH", "/guilds/$guildId/welcome-screen", w)
    fun createWebhook(channelId: String, name: String) = request("POST", "/channels/$channelId/webhooks", JSONObject().put("name", name))
    fun deleteWebhook(webhookId: String) { request("DELETE", "/webhooks/$webhookId") }

    fun sendWebhookMessage(webhookId: String, webhookToken: String, content: String, username: String, avatarUrl: String?, embeds: JSONArray?, attachments: List<ByteArray>): JSONObject? {
        repeat(MAX_RETRIES) {
            Thread.sleep(DELAY_MS)
            val boundary = "----WebhookBoundary${System.currentTimeMillis()}"
            val conn = URL("$BASE/webhooks/$webhookId/$webhookToken?wait=true").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true
                val out = conn.outputStream
                val payload = JSONObject().apply {
                    put("content", content); put("username", username)
                    if (avatarUrl != null) put("avatar_url", avatarUrl)
                    if (embeds != null && embeds.length() > 0) put("embeds", embeds)
                }
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".toByteArray())
                out.write("Content-Type: application/json\r\n\r\n".toByteArray())
                out.write(payload.toString().toByteArray()); out.write("\r\n".toByteArray())
                attachments.forEachIndexed { i, bytes ->
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"files[$i]\"; filename=\"file$i\"\r\n".toByteArray())
                    out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                    out.write(bytes); out.write("\r\n".toByteArray())
                }
                out.write("--$boundary--\r\n".toByteArray()); out.flush()
                val code = conn.responseCode; val text = readText(conn)
                if (code == 429) { handleRateLimit(text); return@repeat }
                return JSONObject(text)
            } catch (_: Exception) { return null } finally { conn.disconnect() }
        }
        return null
    }

    fun createForumThread(channelId: String, name: String, message: String, autoArchiveDuration: Int, appliedTags: JSONArray?): JSONObject? {
        val body = JSONObject().apply {
            put("name", name); put("auto_archive_duration", autoArchiveDuration)
            put("message", JSONObject().put("content", message))
            if (appliedTags != null && appliedTags.length() > 0) put("applied_tags", appliedTags)
        }
        return request("POST", "/channels/$channelId/threads", body)
    }

    fun addReaction(channelId: String, messageId: String, emoji: String) {
        try { request("PUT", "/channels/$channelId/messages/$messageId/reactions/${java.net.URLEncoder.encode(emoji, "UTF-8")}/@me") } catch (_: Exception) {}
    }

    fun downloadBytes(urlStr: String): ByteArray? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000; conn.readTimeout = 20000
            conn.inputStream.use { it.readBytes() }
        } catch (_: Exception) { null }
    }

    fun bytesToBase64DataUrl(bytes: ByteArray, mimeType: String) =
        "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}
