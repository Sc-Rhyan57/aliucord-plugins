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
        private const val DELAY_MS = 550L
    }

    private fun request(
        method: String,
        endpoint: String,
        body: JSONObject? = null
    ): JSONObject? {
        Thread.sleep(DELAY_MS)
        val conn = URL("$BASE$endpoint").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", token)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "SvClone/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            if (body != null) {
                conn.doOutput = true
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                conn.setRequestProperty("Content-Length", bytes.size.toString())
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText() ?: ""
            when {
                code == 204 -> null
                text.isEmpty() -> null
                text.startsWith("[") -> JSONObject().put("array", JSONArray(text))
                else -> JSONObject(text)
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun requestArray(endpoint: String): JSONArray {
        Thread.sleep(DELAY_MS)
        val conn = URL("$BASE$endpoint").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", token)
            conn.setRequestProperty("User-Agent", "SvClone/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText() ?: "[]"
            if (text.startsWith("[")) JSONArray(text) else JSONArray()
        } catch (e: Exception) {
            JSONArray()
        } finally {
            conn.disconnect()
        }
    }

    fun getGuild(guildId: String): JSONObject? =
        request("GET", "/guilds/$guildId?with_counts=false")

    fun getRoles(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/roles")

    fun getChannels(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/channels")

    fun getEmojis(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/emojis")

    fun getStickers(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/stickers")

    fun getSoundboardSounds(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/soundboard-sounds")

    fun getBans(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/bans?limit=1000")

    fun getScheduledEvents(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/scheduled-events?with_user_count=false")

    fun getAutoModRules(guildId: String): JSONArray =
        requestArray("/guilds/$guildId/auto-moderation/rules")

    fun getOnboarding(guildId: String): JSONObject? =
        request("GET", "/guilds/$guildId/onboarding")

    fun getWelcomeScreen(guildId: String): JSONObject? =
        request("GET", "/guilds/$guildId/welcome-screen")

    fun getChannelMessages(channelId: String, limit: Int = 100, before: String? = null): JSONArray {
        val params = "?limit=$limit${if (before != null) "&before=$before" else ""}"
        return requestArray("/channels/$channelId/messages$params")
    }

    fun getForumThreads(channelId: String): JSONArray {
        try {
            val active = request("GET", "/channels/$channelId/threads/active")
            val archived = request("GET", "/channels/$channelId/threads/archived/public?limit=100")
            
            val threads = JSONArray()
            active?.optJSONArray("threads")?.let { arr ->
                for (i in 0 until arr.length()) threads.put(arr.getJSONObject(i))
            }
            archived?.optJSONArray("threads")?.let { arr ->
                for (i in 0 until arr.length()) threads.put(arr.getJSONObject(i))
            }
            return threads
        } catch (e: Exception) {
            return JSONArray()
        }
    }

    fun createGuild(name: String): JSONObject? {
        val body = JSONObject().apply { put("name", name) }
        return request("POST", "/guilds", body)
    }

    fun modifyGuild(guildId: String, body: JSONObject): JSONObject? =
        request("PATCH", "/guilds/$guildId", body)

    fun createRole(guildId: String, role: JSONObject): JSONObject? =
        request("POST", "/guilds/$guildId/roles", role)

    fun deleteDefaultChannel(guildId: String, channelId: String) {
        request("DELETE", "/channels/$channelId")
    }

    fun createChannel(guildId: String, channel: JSONObject): JSONObject? =
        request("POST", "/guilds/$guildId/channels", channel)

    fun modifyChannelPermissions(channelId: String, overwriteId: String, body: JSONObject) {
        Thread.sleep(DELAY_MS)
        val conn = URL("$BASE/channels/$channelId/permissions/$overwriteId").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", token)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "SvClone/1.0")
            conn.doOutput = true
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(bytes) }
            conn.responseCode
        } catch (e: Exception) {
        } finally {
            conn.disconnect()
        }
    }

    fun createEmoji(guildId: String, name: String, imageBase64: String, roles: JSONArray): JSONObject? {
        val body = JSONObject().apply {
            put("name", name)
            put("image", imageBase64)
            put("roles", roles)
        }
        return request("POST", "/guilds/$guildId/emojis", body)
    }

    fun createSticker(guildId: String, name: String, description: String, tags: String, fileBytes: ByteArray, mimeType: String): JSONObject? {
        Thread.sleep(DELAY_MS)
        val boundary = "----SvCloneBoundary${System.currentTimeMillis()}"
        val conn = URL("$BASE/guilds/$guildId/stickers").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", token)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("User-Agent", "SvClone/1.0")
            conn.doOutput = true
            val out: OutputStream = conn.outputStream
            fun writeField(fieldName: String, value: String) {
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$fieldName\"\r\n\r\n".toByteArray())
                out.write("$value\r\n".toByteArray())
            }
            writeField("name", name)
            writeField("description", description)
            writeField("tags", tags)
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"file\"; filename=\"sticker.${if (mimeType.contains("png")) "png" else "gif"}\"\r\n".toByteArray())
            out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            out.write(fileBytes)
            out.write("\r\n--$boundary--\r\n".toByteArray())
            out.flush()
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: return null
            JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun createSoundboardSound(guildId: String, name: String, soundBase64: String, volume: Double, emojiId: String?, emojiName: String?): JSONObject? {
        val body = JSONObject().apply {
            put("name", name)
            put("sound", soundBase64)
            put("volume", volume)
            if (emojiId != null) put("emoji_id", emojiId)
            if (emojiName != null) put("emoji_name", emojiName)
        }
        return request("POST", "/guilds/$guildId/soundboard-sounds", body)
    }

    fun createBan(guildId: String, userId: String, reason: String?): JSONObject? {
        val body = JSONObject().apply {
            put("delete_message_seconds", 0)
            if (reason != null) put("reason", reason)
        }
        return request("PUT", "/guilds/$guildId/bans/$userId", body)
    }

    fun createScheduledEvent(guildId: String, event: JSONObject): JSONObject? =
        request("POST", "/guilds/$guildId/scheduled-events", event)

    fun createAutoModRule(guildId: String, rule: JSONObject): JSONObject? =
        request("POST", "/guilds/$guildId/auto-moderation/rules", rule)

    fun updateOnboarding(guildId: String, onboarding: JSONObject): JSONObject? =
        request("PUT", "/guilds/$guildId/onboarding", onboarding)

    fun updateWelcomeScreen(guildId: String, welcomeScreen: JSONObject): JSONObject? =
        request("PATCH", "/guilds/$guildId/welcome-screen", welcomeScreen)

    fun createWebhook(channelId: String, name: String): JSONObject? {
        val body = JSONObject().apply { put("name", name) }
        return request("POST", "/channels/$channelId/webhooks", body)
    }

    fun deleteWebhook(webhookId: String) {
        request("DELETE", "/webhooks/$webhookId")
    }

    fun sendWebhookMessage(webhookId: String, webhookToken: String, content: String, username: String, avatarUrl: String?, embeds: JSONArray?, attachments: List<ByteArray>): JSONObject? {
        Thread.sleep(DELAY_MS)
        val boundary = "----WebhookBoundary${System.currentTimeMillis()}"
        val conn = URL("$BASE/webhooks/$webhookId/$webhookToken?wait=true").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.doOutput = true
            
            val out = conn.outputStream
            
            val payload = JSONObject().apply {
                put("content", content)
                put("username", username)
                if (avatarUrl != null) put("avatar_url", avatarUrl)
                if (embeds != null && embeds.length() > 0) put("embeds", embeds)
            }
            
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".toByteArray())
            out.write("Content-Type: application/json\r\n\r\n".toByteArray())
            out.write(payload.toString().toByteArray())
            out.write("\r\n".toByteArray())
            
            attachments.forEachIndexed { index, bytes ->
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"files[$index]\"; filename=\"file$index\"\r\n".toByteArray())
                out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                out.write(bytes)
                out.write("\r\n".toByteArray())
            }
            
            out.write("--$boundary--\r\n".toByteArray())
            out.flush()
            
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: return null
            JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun createForumThread(channelId: String, name: String, message: String, autoArchiveDuration: Int, appliedTags: JSONArray?): JSONObject? {
        val body = JSONObject().apply {
            put("name", name)
            put("auto_archive_duration", autoArchiveDuration)
            put("message", JSONObject().apply {
                put("content", message)
            })
            if (appliedTags != null && appliedTags.length() > 0) {
                put("applied_tags", appliedTags)
            }
        }
        return request("POST", "/channels/$channelId/threads", body)
    }

    fun addReaction(channelId: String, messageId: String, emoji: String) {
        try {
            request("PUT", "/channels/$channelId/messages/$messageId/reactions/${java.net.URLEncoder.encode(emoji, "UTF-8")}/@me")
        } catch (e: Exception) {
        }
    }

    fun downloadBytes(urlStr: String): ByteArray? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    fun bytesToBase64DataUrl(bytes: ByteArray, mimeType: String): String {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$b64"
    }
}
