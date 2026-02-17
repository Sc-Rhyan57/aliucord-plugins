package com.rhyan57.svclone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import com.aliucord.Logger
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
    private val api: DiscordApiClient
) {

    private val logger = Logger("SvClone")
    private val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "svclone_progress"
    private val notificationId = 12345

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SvClone Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progresso da clonagem" }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, text: String, progress: Int) {
        val notification = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun finishNotification(success: Boolean, message: String) {
        val notification = NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(if (success) "Clonagem Concluída" else "Clonagem Falhou")
            .setContentText(message)
            .setSmallIcon(if (success) android.R.drawable.ic_dialog_info else android.R.drawable.ic_dialog_alert)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    fun execute(state: ProgressState) {
        CloneSession.start()
        Thread {
            try {
                logger.info("Iniciando clonagem do servidor ${state.sourceGuildId}")
                CloneSession.log("[INICIO] Iniciando processo de clonagem...")

                val sourceGuild = api.getGuild(state.sourceGuildId)
                if (sourceGuild == null) {
                    CloneSession.log("[ERRO] Não foi possível obter dados do servidor")
                    CloneSession.complete(false, "Não foi possível obter dados do servidor de origem.")
                    return@Thread
                }

                val serverName = sourceGuild.optString("name", "servidor")
                CloneSession.log("[INFO] Servidor: $serverName")

                val totalSteps = countSteps(state)
                var currentStep = 0

                fun tick(label: String) {
                    currentStep++
                    val progress = currentStep.toFloat() / totalSteps.toFloat()
                    val pct = (progress * 100).toInt()
                    CloneSession.log(label)
                    CloneSession.updateProgress(progress)
                    updateNotification("Clonando: $serverName", label, pct)
                }

                val targetGuildId: String
                if (state.targetGuildId.isEmpty()) {
                    CloneSession.log("[CRIAR] Criando novo servidor...")
                    val newGuild = api.createGuild(sourceGuild.optString("name", "Servidor Clonado"))
                    if (newGuild == null) {
                        finishNotification(false, "Falha ao criar servidor")
                        CloneSession.complete(false, "Falha ao criar servidor destino.")
                        return@Thread
                    }
                    targetGuildId = newGuild.optString("id")
                    if (targetGuildId.isEmpty()) {
                        finishNotification(false, "Falha ao criar servidor")
                        CloneSession.complete(false, "Falha ao criar servidor destino: ID inválido.")
                        return@Thread
                    }
                    CloneSession.log("[OK] Servidor criado: $targetGuildId")
                } else {
                    targetGuildId = state.targetGuildId
                    CloneSession.log("[INFO] Usando servidor existente: $targetGuildId")
                }

                var updatedState = state.copy(targetGuildId = targetGuildId, serverName = serverName)
                ProgressStateManager.saveProgress(ctx, updatedState)

                val mediaBytesMap = mutableMapOf<String, Pair<ByteArray, String>>()

                val roleIdMap = mutableMapOf<String, String>()
                if (state.rolesCloned && state.savedRoleIdMap.isNotEmpty() && state.savedRoleIdMap != "{}") {
                    try {
                        val j = JSONObject(state.savedRoleIdMap)
                        j.keys().forEach { k -> roleIdMap[k] = j.getString(k) }
                        CloneSession.log("[INFO] Mapa de cargos restaurado (${roleIdMap.size})")
                    } catch (e: Exception) {
                        logger.warn("Erro ao restaurar roleIdMap: ${e.message}")
                    }
                }

                val channelIdMap = mutableMapOf<String, String>()
                if (state.channelsCloned && state.savedChannelIdMap.isNotEmpty() && state.savedChannelIdMap != "{}") {
                    try {
                        val j = JSONObject(state.savedChannelIdMap)
                        j.keys().forEach { k -> channelIdMap[k] = j.getString(k) }
                        CloneSession.log("[INFO] Mapa de canais restaurado (${channelIdMap.size})")
                    } catch (e: Exception) {
                        logger.warn("Erro ao restaurar channelIdMap: ${e.message}")
                    }
                }

                if (state.cloneSettings && !state.settingsCloned) {
                    CloneSession.log("[SETTINGS] Clonando configurações...")
                    val settingsBody = JSONObject()
                    sourceGuild.optString("name").takeIf { it.isNotEmpty() }?.let { settingsBody.put("name", it) }
                    sourceGuild.optString("description").takeIf { it.isNotEmpty() }?.let { settingsBody.put("description", it) }
                    sourceGuild.optString("preferred_locale").takeIf { it.isNotEmpty() }?.let { settingsBody.put("preferred_locale", it) }
                    settingsBody.put("verification_level", sourceGuild.optInt("verification_level"))
                    settingsBody.put("default_message_notifications", sourceGuild.optInt("default_message_notifications"))
                    settingsBody.put("explicit_content_filter", sourceGuild.optInt("explicit_content_filter"))
                    api.modifyGuild(targetGuildId, settingsBody)
                    updatedState = updatedState.copy(settingsCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] Configurações aplicadas")
                }

                if (state.cloneIcon && !state.iconCloned) {
                    val iconHash = sourceGuild.optString("icon")
                    if (iconHash.isNotEmpty() && iconHash != "null") {
                        CloneSession.log("[ICON] Baixando ícone...")
                        val animated = iconHash.startsWith("a_")
                        val ext = if (animated) "gif" else "png"
                        val url = "https://cdn.discordapp.com/icons/${state.sourceGuildId}/$iconHash.$ext?size=4096"
                        val mime = if (animated) "image/gif" else "image/png"
                        val bytes = api.downloadBytes(url)
                        if (bytes != null) {
                            api.modifyGuild(targetGuildId, JSONObject().put("icon", api.bytesToBase64DataUrl(bytes, mime)))
                            mediaBytesMap["icon.$ext"] = Pair(bytes, mime)
                            tick("[OK] Ícone clonado")
                        } else {
                            tick("[AVISO] Ícone: falhou")
                        }
                    }
                    updatedState = updatedState.copy(iconCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                }

                if (state.cloneBanner && !state.bannerCloned) {
                    val bannerHash = sourceGuild.optString("banner")
                    if (bannerHash.isNotEmpty() && bannerHash != "null") {
                        CloneSession.log("[BANNER] Baixando banner...")
                        val animated = bannerHash.startsWith("a_")
                        val ext = if (animated) "gif" else "png"
                        val url = "https://cdn.discordapp.com/banners/${state.sourceGuildId}/$bannerHash.$ext?size=4096"
                        val mime = if (animated) "image/gif" else "image/png"
                        val bytes = api.downloadBytes(url)
                        if (bytes != null) {
                            api.modifyGuild(targetGuildId, JSONObject().put("banner", api.bytesToBase64DataUrl(bytes, mime)))
                            mediaBytesMap["banner.$ext"] = Pair(bytes, mime)
                            tick("[OK] Banner clonado")
                        } else {
                            tick("[AVISO] Banner: falhou")
                        }
                    }
                    updatedState = updatedState.copy(bannerCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                }

                if (state.cloneRoles && !state.rolesCloned) {
                    CloneSession.log("[ROLES] Clonando cargos...")
                    val roles = api.getRoles(state.sourceGuildId)
                    val rolesList = mutableListOf<JSONObject>()
                    for (i in 0 until roles.length()) rolesList.add(roles.getJSONObject(i))

                    val sortedRoles = rolesList
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
                            val newId = created.optString("id")
                            val oldId = role.optString("id")
                            if (newId.isNotEmpty() && oldId.isNotEmpty()) {
                                roleIdMap[oldId] = newId
                            }
                        }
                        Thread.sleep(150)
                    }

                    val roleMapJson = JSONObject().also { jo -> roleIdMap.forEach { (k, v) -> jo.put(k, v) } }.toString()
                    updatedState = updatedState.copy(rolesCloned = true, savedRoleIdMap = roleMapJson)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] ${sortedRoles.size} cargos criados")
                }

                if (state.cloneChannels && !state.channelsCloned) {
                    CloneSession.log("[CHANNELS] Clonando canais...")
                    val channels = api.getChannels(state.sourceGuildId)
                    val channelList = mutableListOf<JSONObject>()
                    for (i in 0 until channels.length()) channelList.add(channels.getJSONObject(i))

                    val existingChannels = api.getChannels(targetGuildId)
                    for (i in 0 until existingChannels.length()) {
                        val ch = existingChannels.getJSONObject(i)
                        val chId = ch.optString("id")
                        if (chId.isNotEmpty()) {
                            try { api.deleteDefaultChannel(targetGuildId, chId) } catch (e: Exception) {}
                            Thread.sleep(150)
                        }
                    }

                    val categoryIdMap = mutableMapOf<String, String>()
                    val categories = channelList.filter { it.optInt("type") == 4 }.sortedBy { it.optInt("position") }
                    for (cat in categories) {
                        val body = buildChannelBody(cat, null, roleIdMap)
                        val created = api.createChannel(targetGuildId, body)
                        if (created != null) {
                            val newId = created.optString("id")
                            val oldId = cat.optString("id")
                            if (newId.isNotEmpty() && oldId.isNotEmpty()) {
                                categoryIdMap[oldId] = newId
                                channelIdMap[oldId] = newId
                                applyPermissionOverwrites(cat, newId, roleIdMap)
                            }
                        }
                        Thread.sleep(150)
                    }

                    val otherChannels = channelList.filter { it.optInt("type") != 4 }.sortedBy { it.optInt("position") }
                    for (ch in otherChannels) {
                        val parentId = ch.optString("parent_id").takeIf { it.isNotEmpty() && it != "null" }
                        val mappedParentId = parentId?.let { categoryIdMap[it] }
                        val body = buildChannelBody(ch, mappedParentId, roleIdMap)
                        val created = api.createChannel(targetGuildId, body)
                        if (created != null) {
                            val newId = created.optString("id")
                            val oldId = ch.optString("id")
                            if (newId.isNotEmpty() && oldId.isNotEmpty()) {
                                channelIdMap[oldId] = newId
                                applyPermissionOverwrites(ch, newId, roleIdMap)
                            }
                        }
                        Thread.sleep(150)
                    }

                    val channelMapJson = JSONObject().also { jo -> channelIdMap.forEach { (k, v) -> jo.put(k, v) } }.toString()
                    updatedState = updatedState.copy(channelsCloned = true, savedChannelIdMap = channelMapJson)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] ${channelList.size} canais criados")
                }

                if (state.cloneEmojis && !state.emojisCloned) {
                    CloneSession.log("[EMOJIS] Clonando emojis...")
                    val emojis = api.getEmojis(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until emojis.length()) {
                        val emoji = emojis.getJSONObject(i)
                        val name = emoji.optString("name", "emoji$i")
                        val id = emoji.optString("id").takeIf { it.isNotEmpty() } ?: continue
                        val animated = emoji.optBoolean("animated", false)
                        val ext = if (animated) "gif" else "png"
                        val mime = if (animated) "image/gif" else "image/png"
                        val bytes = api.downloadBytes("https://cdn.discordapp.com/emojis/$id.$ext?size=128") ?: continue
                        val created = api.createEmoji(targetGuildId, name, api.bytesToBase64DataUrl(bytes, mime), JSONArray())
                        if (created != null && created.optString("id").isNotEmpty()) {
                            count++
                            mediaBytesMap["emojis/$name.$ext"] = Pair(bytes, mime)
                        }
                        Thread.sleep(300)
                    }
                    updatedState = updatedState.copy(emojisCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] $count emojis criados")
                }

                if (state.cloneStickers && !state.stickersCloned) {
                    CloneSession.log("[STICKERS] Clonando stickers...")
                    val stickers = api.getStickers(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until stickers.length()) {
                        val sticker = stickers.getJSONObject(i)
                        val name = sticker.optString("name", "sticker$i")
                        val id = sticker.optString("id").takeIf { it.isNotEmpty() } ?: continue
                        val formatType = sticker.optInt("format_type", 1)
                        if (formatType == 3) continue
                        val ext = if (formatType == 2) "apng" else "png"
                        val mime = if (formatType == 2) "image/apng" else "image/png"
                        val bytes = api.downloadBytes("https://media.discordapp.net/stickers/$id.$ext") ?: continue
                        val created = api.createSticker(
                            targetGuildId, name,
                            sticker.optString("description", name),
                            sticker.optString("tags", name),
                            bytes, mime
                        )
                        if (created != null && created.optString("id").isNotEmpty()) {
                            count++
                            mediaBytesMap["stickers/$name.$ext"] = Pair(bytes, mime)
                        }
                        Thread.sleep(400)
                    }
                    updatedState = updatedState.copy(stickersCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] $count stickers criados")
                }

                if (state.cloneSounds && !state.soundsCloned) {
                    CloneSession.log("[SOUNDS] Clonando sons...")
                    val sounds = api.getSoundboardSounds(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until sounds.length()) {
                        val sound = sounds.getJSONObject(i)
                        val name = sound.optString("name", "sound$i")
                        val soundId = sound.optString("sound_id").takeIf { it.isNotEmpty() } ?: continue
                        val bytes = api.downloadBytes("https://cdn.discordapp.com/soundboard-sounds/$soundId") ?: continue
                        val created = api.createSoundboardSound(
                            targetGuildId, name,
                            api.bytesToBase64DataUrl(bytes, "audio/ogg"),
                            sound.optDouble("volume", 1.0),
                            sound.optString("emoji_id").takeIf { it != "null" && it.isNotEmpty() },
                            sound.optString("emoji_name").takeIf { it != "null" && it.isNotEmpty() }
                        )
                        if (created != null) {
                            count++
                            mediaBytesMap["sounds/$name.ogg"] = Pair(bytes, "audio/ogg")
                        }
                        Thread.sleep(500)
                    }
                    updatedState = updatedState.copy(soundsCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] $count sons criados")
                }

                if (state.cloneMessages && !state.messagesCloned) {
                    CloneSession.log("[MESSAGES] Clonando mensagens...")
                    var totalMessages = 0
                    for ((sourceChannelId, targetChannelId) in channelIdMap) {
                        try {
                            val messages = mutableListOf<JSONObject>()
                            var lastId: String? = null
                            repeat(5) {
                                val batch = api.getChannelMessages(sourceChannelId, 100, lastId)
                                if (batch.length() == 0) return@repeat
                                for (i in 0 until batch.length()) messages.add(batch.getJSONObject(i))
                                lastId = batch.getJSONObject(batch.length() - 1).optString("id").takeIf { it.isNotEmpty() }
                                Thread.sleep(300)
                            }
                            if (messages.isEmpty()) continue
                            val webhook = api.createWebhook(targetChannelId, "Clone") ?: continue
                            val webhookId = webhook.optString("id").takeIf { it.isNotEmpty() } ?: continue
                            val webhookToken = webhook.optString("token").takeIf { it.isNotEmpty() } ?: continue
                            messages.reverse()
                            for (msg in messages) {
                                if (msg.optInt("type", 0) != 0) continue
                                var content = msg.optString("content", "")
                                content = content.replace(Regex("<@&(\\d+)>")) { match ->
                                    roleIdMap[match.groupValues[1]]?.let { "<@&$it>" } ?: match.value
                                }
                                val author = msg.optJSONObject("author") ?: continue
                                val username = author.optString("global_name").takeIf { it.isNotEmpty() } ?: author.optString("username")
                                val userId = author.optString("id")
                                val avatarHash = author.optString("avatar")
                                val avatarUrl = if (avatarHash.isNotEmpty() && avatarHash != "null" && userId.isNotEmpty()) {
                                    "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png"
                                } else null
                                val attachmentBytes = mutableListOf<ByteArray>()
                                msg.optJSONArray("attachments")?.let { atts ->
                                    for (j in 0 until minOf(atts.length(), 3)) {
                                        val attUrl = atts.getJSONObject(j).optString("url").takeIf { it.isNotEmpty() } ?: continue
                                        api.downloadBytes(attUrl)?.let { attachmentBytes.add(it) }
                                    }
                                }
                                api.sendWebhookMessage(webhookId, webhookToken, content, username, avatarUrl, msg.optJSONArray("embeds"), attachmentBytes)
                                totalMessages++
                                Thread.sleep(800)
                            }
                            api.deleteWebhook(webhookId)
                            Thread.sleep(300)
                        } catch (e: Exception) {
                            logger.warn("Erro ao clonar mensagens do canal: ${e.message}")
                        }
                    }
                    updatedState = updatedState.copy(messagesCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] $totalMessages mensagens clonadas")
                }

                if (state.cloneBans && !state.bansCloned) {
                    CloneSession.log("[BANS] Clonando banimentos...")
                    val bans = api.getBans(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until bans.length()) {
                        val ban = bans.getJSONObject(i)
                        val userId = ban.optJSONObject("user")?.optString("id")?.takeIf { it.isNotEmpty() } ?: continue
                        val reason = ban.optString("reason").takeIf { it != "null" && it.isNotEmpty() }
                        api.createBan(targetGuildId, userId, reason)
                        count++
                        Thread.sleep(500)
                    }
                    updatedState = updatedState.copy(bansCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] $count banimentos criados")
                }

                if (state.cloneEvents && !state.eventsCloned) {
                    CloneSession.log("[EVENTS] Clonando eventos...")
                    val events = api.getScheduledEvents(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i)
                        val startTime = event.optString("scheduled_start_time").takeIf { it.isNotEmpty() && it != "null" } ?: continue
                        val eventBody = JSONObject().apply {
                            put("name", event.optString("name", "Evento"))
                            put("privacy_level", event.optInt("privacy_level", 2))
                            put("scheduled_start_time", startTime)
                            put("entity_type", event.optInt("entity_type", 3))
                            event.optString("description").takeIf { it != "null" && it.isNotEmpty() }?.let { put("description", it) }
                            event.optString("scheduled_end_time").takeIf { it != "null" && it.isNotEmpty() }?.let { put("scheduled_end_time", it) }
                            if (event.optInt("entity_type") == 3) {
                                event.optJSONObject("entity_metadata")?.optString("location")?.let {
                                    put("entity_metadata", JSONObject().put("location", it))
                                }
                            }
                        }
                        api.createScheduledEvent(targetGuildId, eventBody)
                        count++
                        Thread.sleep(500)
                    }
                    updatedState = updatedState.copy(eventsCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] $count eventos criados")
                }

                if (state.cloneAutoMod && !state.autoModCloned) {
                    CloneSession.log("[AUTOMOD] Clonando AutoMod...")
                    val rules = api.getAutoModRules(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until rules.length()) {
                        val rule = rules.getJSONObject(i)
                        val ruleBody = JSONObject().apply {
                            put("name", rule.optString("name", "Regra"))
                            put("event_type", rule.optInt("event_type", 1))
                            put("trigger_type", rule.optInt("trigger_type", 1))
                            put("trigger_metadata", rule.optJSONObject("trigger_metadata") ?: JSONObject())
                            put("actions", rule.optJSONArray("actions") ?: JSONArray())
                            put("enabled", rule.optBoolean("enabled", true))
                            rule.optJSONArray("exempt_roles")?.let { arr ->
                                val mapped = JSONArray()
                                for (j in 0 until arr.length()) roleIdMap[arr.getString(j)]?.let { mapped.put(it) }
                                if (mapped.length() > 0) put("exempt_roles", mapped)
                            }
                            rule.optJSONArray("exempt_channels")?.let { arr ->
                                val mapped = JSONArray()
                                for (j in 0 until arr.length()) channelIdMap[arr.getString(j)]?.let { mapped.put(it) }
                                if (mapped.length() > 0) put("exempt_channels", mapped)
                            }
                        }
                        api.createAutoModRule(targetGuildId, ruleBody)
                        count++
                        Thread.sleep(300)
                    }
                    updatedState = updatedState.copy(autoModCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                    tick("[OK] $count regras AutoMod criadas")
                }

                if (state.cloneOnboarding && !state.onboardingCloned) {
                    CloneSession.log("[ONBOARDING] Clonando Onboarding...")
                    val onboarding = api.getOnboarding(state.sourceGuildId)
                    if (onboarding != null && onboarding.optBoolean("enabled", false)) {
                        val onboardingBody = JSONObject().apply {
                            put("enabled", true)
                            onboarding.optJSONArray("prompts")?.let { prompts ->
                                val mappedPrompts = JSONArray()
                                for (i in 0 until prompts.length()) {
                                    val prompt = prompts.getJSONObject(i)
                                    val mappedPrompt = JSONObject(prompt.toString())
                                    prompt.optJSONArray("options")?.let { options ->
                                        val mappedOptions = JSONArray()
                                        for (j in 0 until options.length()) {
                                            val option = options.getJSONObject(j)
                                            val mappedOption = JSONObject(option.toString())
                                            option.optJSONArray("channel_ids")?.let { ids ->
                                                val newIds = JSONArray()
                                                for (k in 0 until ids.length()) channelIdMap[ids.getString(k)]?.let { newIds.put(it) }
                                                mappedOption.put("channel_ids", newIds)
                                            }
                                            option.optJSONArray("role_ids")?.let { ids ->
                                                val newIds = JSONArray()
                                                for (k in 0 until ids.length()) roleIdMap[ids.getString(k)]?.let { newIds.put(it) }
                                                mappedOption.put("role_ids", newIds)
                                            }
                                            mappedOptions.put(mappedOption)
                                        }
                                        mappedPrompt.put("options", mappedOptions)
                                    }
                                    mappedPrompts.put(mappedPrompt)
                                }
                                put("prompts", mappedPrompts)
                            }
                            onboarding.optJSONArray("default_channel_ids")?.let { ids ->
                                val newIds = JSONArray()
                                for (i in 0 until ids.length()) channelIdMap[ids.getString(i)]?.let { newIds.put(it) }
                                put("default_channel_ids", newIds)
                            }
                        }
                        api.updateOnboarding(targetGuildId, onboardingBody)
                        tick("[OK] Onboarding clonado")
                    } else {
                        tick("[INFO] Onboarding não habilitado")
                    }
                    updatedState = updatedState.copy(onboardingCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                }

                if (state.cloneWelcome && !state.welcomeCloned) {
                    CloneSession.log("[WELCOME] Clonando Welcome Screen...")
                    val welcome = api.getWelcomeScreen(state.sourceGuildId)
                    if (welcome != null) {
                        val welcomeBody = JSONObject().apply {
                            put("enabled", true)
                            welcome.optString("description").takeIf { it != "null" && it.isNotEmpty() }?.let { put("description", it) }
                            welcome.optJSONArray("welcome_channels")?.let { channels ->
                                val newChannels = JSONArray()
                                for (i in 0 until channels.length()) {
                                    val wc = channels.getJSONObject(i)
                                    val newChannelId = channelIdMap[wc.optString("channel_id")] ?: continue
                                    newChannels.put(JSONObject().apply {
                                        put("channel_id", newChannelId)
                                        put("description", wc.optString("description", ""))
                                        wc.optString("emoji_id").takeIf { it != "null" && it.isNotEmpty() }?.let { put("emoji_id", it) }
                                        wc.optString("emoji_name").takeIf { it != "null" && it.isNotEmpty() }?.let { put("emoji_name", it) }
                                    })
                                }
                                put("welcome_channels", newChannels)
                            }
                        }
                        api.updateWelcomeScreen(targetGuildId, welcomeBody)
                        tick("[OK] Welcome Screen clonado")
                    } else {
                        tick("[INFO] Welcome Screen não configurado")
                    }
                    updatedState = updatedState.copy(welcomeCloned = true)
                    ProgressStateManager.saveProgress(ctx, updatedState)
                }

                if (state.saveMidia && mediaBytesMap.isNotEmpty()) {
                    CloneSession.log("[ZIP] Salvando mídia...")
                    saveMidiaZip(serverName, mediaBytesMap)
                    tick("[OK] Mídia salva")
                }

                ProgressStateManager.saveProgress(ctx, updatedState.copy(isComplete = true))
                finishNotification(true, "Servidor $serverName clonado!")
                CloneSession.complete(true, "[SUCESSO] Servidor clonado! ID: $targetGuildId")

            } catch (e: Exception) {
                logger.error("Erro durante clonagem", e)
                finishNotification(false, "Erro: ${e.message}")
                CloneSession.complete(false, "[ERRO] ${e.message}")
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
        if (state.cloneSounds && !state.soundsCloned) steps++
        if (state.cloneMessages && !state.messagesCloned) steps++
        if (state.cloneBans && !state.bansCloned) steps++
        if (state.cloneEvents && !state.eventsCloned) steps++
        if (state.cloneAutoMod && !state.autoModCloned) steps++
        if (state.cloneOnboarding && !state.onboardingCloned) steps++
        if (state.cloneWelcome && !state.welcomeCloned) steps++
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
            channel.optJSONArray("available_tags")?.let { if (it.length() > 0) put("available_tags", it) }
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
                Thread.sleep(100)
            } catch (e: Exception) {
                logger.warn("Erro ao aplicar permissões: ${e.message}")
            }
        }
    }

    private fun saveMidiaZip(serverName: String, files: Map<String, Pair<ByteArray, String>>) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val safeName = serverName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val dir = File(Environment.getExternalStorageDirectory(), "Aliucord/SvClone/Midia")
            dir.mkdirs()
            val zipFile = File(dir, "${sdf.format(Date())}-$safeName.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for ((entryName, pair) in files) {
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(pair.first)
                    zos.closeEntry()
                }
            }
            CloneSession.log("[OK] ZIP: ${zipFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Erro ao salvar ZIP", e)
            CloneSession.log("[ERRO] ZIP: ${e.message}")
        }
    }
}
