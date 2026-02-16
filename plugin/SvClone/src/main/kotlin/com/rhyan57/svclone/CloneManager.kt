package com.rhyan57.svclone

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CloneManager(
    private val ctx: Context,
    private val api: DiscordApiClient,
    private val onLog: (String) -> Unit,
    private val onProgress: (Float) -> Unit,
    private val onComplete: (Boolean, String) -> Unit
) {

    fun execute(state: ProgressState) {
        Thread {
            try {
                val sourceGuild = api.getGuild(state.sourceGuildId) ?: run {
                    onComplete(false, "Nao foi possivel obter dados do servidor de origem.")
                    return@Thread
                }

                val serverName = sourceGuild.optString("name", "servidor")
                val totalSteps = countSteps(state)
                var currentStep = 0

                fun tick(label: String) {
                    currentStep++
                    onLog(label)
                    onProgress(currentStep.toFloat() / totalSteps.toFloat())
                }

                val targetGuildId: String
                if (state.targetGuildId.isEmpty()) {
                    onLog("Criando novo servidor...")
                    val newGuild = api.createGuild(sourceGuild.optString("name", "Servidor Clonado"))
                        ?: run { onComplete(false, "Falha ao criar servidor destino."); return@Thread }
                    targetGuildId = newGuild.getString("id")
                } else {
                    targetGuildId = state.targetGuildId
                }

                val updatedState = state.copy(targetGuildId = targetGuildId, serverName = serverName)
                ProgressStateManager.saveProgress(ctx, updatedState)

                val mediaBytesMap = mutableMapOf<String, Pair<ByteArray, String>>()

                if (state.cloneSettings && !state.settingsCloned) {
                    onLog("Clonando configuracoes do servidor...")
                    val settingsBody = JSONObject()
                    sourceGuild.optString("name").takeIf { it.isNotEmpty() }?.let { settingsBody.put("name", it) }
                    sourceGuild.optString("description").takeIf { it.isNotEmpty() }?.let { settingsBody.put("description", it) }
                    sourceGuild.optString("preferred_locale").takeIf { it.isNotEmpty() }?.let { settingsBody.put("preferred_locale", it) }
                    sourceGuild.optInt("verification_level").let { settingsBody.put("verification_level", it) }
                    sourceGuild.optInt("default_message_notifications").let { settingsBody.put("default_message_notifications", it) }
                    sourceGuild.optInt("explicit_content_filter").let { settingsBody.put("explicit_content_filter", it) }
                    api.modifyGuild(targetGuildId, settingsBody)
                    tick("Configuracoes clonadas")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(settingsCloned = true))
                }

                if (state.cloneIcon && !state.iconCloned) {
                    val iconHash = sourceGuild.optString("icon")
                    if (iconHash.isNotEmpty() && iconHash != "null") {
                        onLog("Baixando icone do servidor...")
                        val animated = iconHash.startsWith("a_")
                        val ext = if (animated) "gif" else "png"
                        val url = "https://cdn.discordapp.com/icons/${state.sourceGuildId}/$iconHash.$ext?size=4096"
                        val mime = if (animated) "image/gif" else "image/png"
                        val bytes = api.downloadBytes(url)
                        if (bytes != null) {
                            val dataUrl = api.bytesToBase64DataUrl(bytes, mime)
                            val body = JSONObject().put("icon", dataUrl)
                            api.modifyGuild(targetGuildId, body)
                            mediaBytesMap["icon.$ext"] = Pair(bytes, mime)
                            tick("Icone clonado")
                        } else {
                            tick("Icone: falhou ao baixar")
                        }
                    }
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(iconCloned = true))
                }

                if (state.cloneBanner && !state.bannerCloned) {
                    val bannerHash = sourceGuild.optString("banner")
                    if (bannerHash.isNotEmpty() && bannerHash != "null") {
                        onLog("Baixando banner do servidor...")
                        val animated = bannerHash.startsWith("a_")
                        val ext = if (animated) "gif" else "png"
                        val url = "https://cdn.discordapp.com/banners/${state.sourceGuildId}/$bannerHash.$ext?size=4096"
                        val mime = if (animated) "image/gif" else "image/png"
                        val bytes = api.downloadBytes(url)
                        if (bytes != null) {
                            val dataUrl = api.bytesToBase64DataUrl(bytes, mime)
                            val body = JSONObject().put("banner", dataUrl)
                            api.modifyGuild(targetGuildId, body)
                            mediaBytesMap["banner.$ext"] = Pair(bytes, mime)
                            tick("Banner clonado")
                        } else {
                            tick("Banner: falhou ao baixar")
                        }
                    }
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(bannerCloned = true))
                }

                val roleIdMap = mutableMapOf<String, String>()

                if (state.cloneRoles && !state.rolesCloned) {
                    onLog("Clonando cargos...")
                    val roles = api.getRoles(state.sourceGuildId)
                    val sortedRoles = (0 until roles.length())
                        .map { roles.getJSONObject(it) }
                        .filter { !it.optBoolean("managed", false) && it.optString("name") != "@everyone" }
                        .sortedBy { it.optInt("position", 0) }

                    for (role in sortedRoles) {
                        val roleBody = JSONObject().apply {
                            put("name", role.optString("name", "cargo"))
                            put("color", role.optInt("color", 0))
                            put("hoist", role.optBoolean("hoist", false))
                            put("mentionable", role.optBoolean("mentionable", false))
                            put("permissions", role.optString("permissions", "0"))
                        }
                        val created = api.createRole(targetGuildId, roleBody)
                        if (created != null) {
                            roleIdMap[role.getString("id")] = created.getString("id")
                        }
                        Thread.sleep(300)
                    }
                    tick("${sortedRoles.size} cargos clonados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(rolesCloned = true))
                }

                if (state.cloneChannels && !state.channelsCloned) {
                    onLog("Clonando canais...")
                    val channels = api.getChannels(state.sourceGuildId)
                    val channelList = (0 until channels.length()).map { channels.getJSONObject(it) }

                    val existingChannels = api.getChannels(targetGuildId)
                    for (i in 0 until existingChannels.length()) {
                        val ch = existingChannels.getJSONObject(i)
                        try { api.deleteDefaultChannel(targetGuildId, ch.getString("id")) } catch (e: Exception) {}
                        Thread.sleep(200)
                    }

                    val categoryIdMap = mutableMapOf<String, String>()
                    val categories = channelList.filter { it.optInt("type") == 4 }.sortedBy { it.optInt("position") }
                    for (cat in categories) {
                        val body = buildChannelBody(cat, null, roleIdMap)
                        val created = api.createChannel(targetGuildId, body)
                        if (created != null) {
                            categoryIdMap[cat.getString("id")] = created.getString("id")
                            applyPermissionOverwrites(cat, created.getString("id"), roleIdMap)
                        }
                        Thread.sleep(300)
                    }

                    val otherChannels = channelList.filter { it.optInt("type") != 4 }.sortedBy { it.optInt("position") }
                    for (ch in otherChannels) {
                        val parentId = ch.optString("parent_id").takeIf { it.isNotEmpty() && it != "null" }
                        val mappedParentId = parentId?.let { categoryIdMap[it] }
                        val body = buildChannelBody(ch, mappedParentId, roleIdMap)
                        val created = api.createChannel(targetGuildId, body)
                        if (created != null) {
                            applyPermissionOverwrites(ch, created.getString("id"), roleIdMap)
                        }
                        Thread.sleep(300)
                    }

                    tick("${channelList.size} canais clonados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(channelsCloned = true))
                }

                if (state.cloneEmojis && !state.emojisCloned) {
                    onLog("Clonando emojis...")
                    val emojis = api.getEmojis(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until emojis.length()) {
                        val emoji = emojis.getJSONObject(i)
                        val name = emoji.optString("name", "emoji$i")
                        val id = emoji.optString("id")
                        val animated = emoji.optBoolean("animated", false)
                        val ext = if (animated) "gif" else "png"
                        val url = "https://cdn.discordapp.com/emojis/$id.$ext?size=128"
                        val mime = if (animated) "image/gif" else "image/png"
                        val bytes = api.downloadBytes(url) ?: continue
                        val dataUrl = api.bytesToBase64DataUrl(bytes, mime)
                        val created = api.createEmoji(targetGuildId, name, dataUrl, JSONArray())
                        if (created != null) {
                            count++
                            mediaBytesMap["emojis/$name.$ext"] = Pair(bytes, mime)
                        }
                        Thread.sleep(600)
                    }
                    tick("$count emojis clonados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(emojisCloned = true))
                }

                if (state.cloneStickers && !state.stickersCloned) {
                    onLog("Clonando stickers...")
                    val stickers = api.getStickers(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until stickers.length()) {
                        val sticker = stickers.getJSONObject(i)
                        val name = sticker.optString("name", "sticker$i")
                        val id = sticker.optString("id")
                        val formatType = sticker.optInt("format_type", 1)
                        val ext = when (formatType) { 2 -> "apng"; 3 -> "lottie"; else -> "png" }
                        val mime = when (formatType) { 2 -> "image/apng"; 3 -> "application/json"; else -> "image/png" }
                        val url = "https://media.discordapp.net/stickers/$id.$ext"
                        val bytes = api.downloadBytes(url) ?: continue
                        val description = sticker.optString("description", name)
                        val tags = sticker.optString("tags", name)
                        val created = api.createSticker(targetGuildId, name, description, tags, bytes, mime)
                        if (created != null) {
                            count++
                            mediaBytesMap["stickers/$name.$ext"] = Pair(bytes, mime)
                        }
                        Thread.sleep(800)
                    }
                    tick("$count stickers clonados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(stickersCloned = true))
                }

                if (state.saveMidia && mediaBytesMap.isNotEmpty()) {
                    onLog("Salvando midia no ZIP...")
                    saveMidiaZip(serverName, mediaBytesMap)
                    tick("Midia salva")
                }

                ProgressStateManager.saveProgress(ctx, updatedState.copy(isComplete = true))
                onComplete(true, "Servidor clonado com sucesso! ID do novo servidor: $targetGuildId")

            } catch (e: Exception) {
                onComplete(false, "Erro: ${e.message}")
            }
        }.start()
    }

    private fun countSteps(state: ProgressState): Int {
        var steps = 0
        if (state.cloneSettings && !state.settingsCloned) steps++
        if (state.cloneIcon && !state.iconCloned) steps++
        if (state.cloneBanner && !state.bannerCloned) steps++
        if (state.cloneRoles && !state.rolesCloned) steps++
        if (state.cloneChannels && !state.channelsCloned) steps++
        if (state.cloneEmojis && !state.emojisCloned) steps++
        if (state.cloneStickers && !state.stickersCloned) steps++
        if (state.saveMidia) steps++
        return steps.coerceAtLeast(1)
    }

    private fun buildChannelBody(channel: JSONObject, parentId: String?, roleIdMap: Map<String, String>): JSONObject {
        return JSONObject().apply {
            put("name", channel.optString("name", "canal"))
            put("type", channel.optInt("type", 0))
            put("position", channel.optInt("position", 0))
            parentId?.let { put("parent_id", it) }
            val topic = channel.optString("topic")
            if (topic.isNotEmpty() && topic != "null") put("topic", topic)
            if (channel.optInt("type") == 2) {
                put("bitrate", channel.optInt("bitrate", 64000))
                put("user_limit", channel.optInt("user_limit", 0))
            }
            put("nsfw", channel.optBoolean("nsfw", false))
            val rateLimitPerUser = channel.optInt("rate_limit_per_user", 0)
            if (rateLimitPerUser > 0) put("rate_limit_per_user", rateLimitPerUser)
        }
    }

    private fun applyPermissionOverwrites(source: JSONObject, newChannelId: String, roleIdMap: Map<String, String>) {
        val overwrites = source.optJSONArray("permission_overwrites") ?: return
        for (i in 0 until overwrites.length()) {
            val ow = overwrites.getJSONObject(i)
            val sourceId = ow.optString("id")
            val type = ow.optInt("type")
            val targetId = if (type == 0) roleIdMap[sourceId] ?: continue else sourceId
            val owBody = JSONObject().apply {
                put("allow", ow.optString("allow", "0"))
                put("deny", ow.optString("deny", "0"))
                put("type", type)
            }
            try {
                api.modifyChannelPermissions(newChannelId, targetId, owBody)
                Thread.sleep(200)
            } catch (e: Exception) {}
        }
    }

    private fun saveMidiaZip(serverName: String, files: Map<String, Pair<ByteArray, String>>) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val dateStr = sdf.format(Date())
            val safeName = serverName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "$dateStr-$safeName.zip"
            val dir = File(Environment.getExternalStorageDirectory(), "Aliucord/SvClone/Midia")
            dir.mkdirs()
            val zipFile = File(dir, fileName)
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for ((entryName, pair) in files) {
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(pair.first)
                    zos.closeEntry()
                }
            }
            onLog("ZIP salvo em: ${zipFile.absolutePath}")
        } catch (e: Exception) {
            onLog("Erro ao salvar ZIP: ${e.message}")
        }
    }
}
