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
            if (code == 204) null
            else if (text.isEmpty()) null
            else if (text.startsWith("[")) JSONObject().put("array", JSONArray(text))
            else JSONObject(text)
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

    fun createGuild(name: String): JSONObject? {
        val body = JSONObject().apply { put("name", name) }
        return request("POST", "/guilds", body)
    }

    fun modifyGuild(guildId: String, body: JSONObject): JSONObject? =
        request("PATCH", "/guilds/$guildId", body)

    fun createRole(guildId: String, role: JSONObject): JSONObject? =
        request("POST", "/guilds/$guildId/roles", role)

    fun modifyRolePositions(guildId: String, positions: JSONArray): JSONArray {
        Thread.sleep(DELAY_MS)
        val conn = URL("$BASE/guilds/$guildId/roles").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Authorization", token)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "SvClone/1.0")
            conn.doOutput = true
            val bytes = positions.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(bytes) }
            val text = conn.inputStream.bufferedReader().readText()
            JSONArray(text)
        } catch (e: Exception) {
            JSONArray()
        } finally {
            conn.disconnect()
        }
    }

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

    fun guessMime(url: String): String {
        return when {
            url.contains(".gif") -> "image/gif"
            url.contains(".png") -> "image/png"
            url.contains(".jpg") || url.contains(".jpeg") -> "image/jpeg"
            url.contains(".webp") -> "image/webp"
            else -> "image/png"
        }
    }
}
