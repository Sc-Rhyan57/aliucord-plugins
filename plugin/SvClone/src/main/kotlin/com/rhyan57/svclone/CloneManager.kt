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
    private val api: DiscordApiClient,
    private val onLog: (String) -> Unit,
    private val onProgress: (Float) -> Unit,
    private val onComplete: (Boolean, String) -> Unit
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
            ).apply {
                description = "Progresso da clonagem"
            }
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
        Thread {
            try {
                logger.info("Iniciando clonagem do servidor ${state.sourceGuildId}")
                onLog("[INICIO] Iniciando processo de clonagem...")
                
                val sourceGuild = api.getGuild(state.sourceGuildId)
                if (sourceGuild == null) {
                    logger.error("Falha ao obter dados do servidor de origem", null)
                    onLog("[ERRO] Não foi possível obter dados do servidor")
                    onComplete(false, "Não foi possível obter dados do servidor de origem.")
                    return@Thread
                }

                val serverName = sourceGuild.optString("name", "servidor")
                logger.info("Nome do servidor: $serverName")
                onLog("[INFO] Servidor: $serverName")
                
                val totalSteps = countSteps(state)
                var currentStep = 0

                fun tick(label: String) {
                    currentStep++
                    val progress = currentStep.toFloat() / totalSteps.toFloat()
                    val pct = (progress * 100).toInt()
                    
                    logger.info(label)
                    onLog(label)
                    onProgress(progress)
                    updateNotification("Clonando: $serverName", label, pct)
                }

                val targetGuildId: String
                if (state.targetGuildId.isEmpty()) {
                    onLog("[CRIAR] Criando novo servidor...")
                    logger.info("Criando novo servidor")
                    
                    val newGuild = api.createGuild(sourceGuild.optString("name", "Servidor Clonado"))
                    if (newGuild == null) {
                        logger.error("Falha ao criar servidor destino", null)
                        finishNotification(false, "Falha ao criar servidor")
                        onComplete(false, "Falha ao criar servidor destino.")
                        return@Thread
                    }
                    
                    targetGuildId = newGuild.getString("id")
                    logger.info("Servidor criado com ID: $targetGuildId")
                    onLog("[OK] Servidor criado: $targetGuildId")
                } else {
                    targetGuildId = state.targetGuildId
                    logger.info("Usando servidor destino existente: $targetGuildId")
                    onLog("[INFO] Usando servidor existente")
                }

                val updatedState = state.copy(targetGuildId = targetGuildId, serverName = serverName)
                ProgressStateManager.saveProgress(ctx, updatedState)

                val mediaBytesMap = mutableMapOf<String, Pair<ByteArray, String>>()
                val roleIdMap = mutableMapOf<String, String>()
                val channelIdMap = mutableMapOf<String, String>()

                if (state.cloneSettings && !state.settingsCloned) {
                    onLog("[SETTINGS] Clonando configurações...")
                    logger.info("Clonando configurações")
                    
                    val settingsBody = JSONObject()
                    sourceGuild.optString("name").takeIf { it.isNotEmpty() }?.let { settingsBody.put("name", it) }
                    sourceGuild.optString("description").takeIf { it.isNotEmpty() }?.let { settingsBody.put("description", it) }
                    sourceGuild.optString("preferred_locale").takeIf { it.isNotEmpty() }?.let { settingsBody.put("preferred_locale", it) }
                    sourceGuild.optInt("verification_level").let { settingsBody.put("verification_level", it) }
                    sourceGuild.optInt("default_message_notifications").let { settingsBody.put("default_message_notifications", it) }
                    sourceGuild.optInt("explicit_content_filter").let { settingsBody.put("explicit_content_filter", it) }
                    api.modifyGuild(targetGuildId, settingsBody)
                    tick("[OK] Configurações aplicadas")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(settingsCloned = true))
                }

                if (state.cloneIcon && !state.iconCloned) {
                    val iconHash = sourceGuild.optString("icon")
                    if (iconHash.isNotEmpty() && iconHash != "null") {
                        onLog("[ICON] Baixando ícone...")
                        logger.info("Baixando ícone: $iconHash")
                        
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
                            tick("[OK] Ícone clonado")
                        } else {
                            tick("[AVISO] Ícone: falhou")
                        }
                    }
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(iconCloned = true))
                }

                if (state.cloneBanner && !state.bannerCloned) {
                    val bannerHash = sourceGuild.optString("banner")
                    if (bannerHash.isNotEmpty() && bannerHash != "null") {
                        onLog("[BANNER] Baixando banner...")
                        logger.info("Baixando banner: $bannerHash")
                        
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
                            tick("[OK] Banner clonado")
                        } else {
                            tick("[AVISO] Banner: falhou")
                        }
                    }
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(bannerCloned = true))
                }

                if (state.cloneRoles && !state.rolesCloned) {
                    onLog("[ROLES] Clonando cargos...")
                    logger.info("Clonando cargos")
                    
                    val roles = api.getRoles(state.sourceGuildId)
                    val rolesList = mutableListOf<JSONObject>()
                    for (i in 0 until roles.length()) {
                        rolesList.add(roles.getJSONObject(i))
                    }
                    
                    val sortedRoles = rolesList
                        .filter { !it.optBoolean("managed", false) && it.optString("name") != "@everyone" }
                        .sortedBy { it.optInt("position", 0) }

                    logger.info("Total de cargos: ${sortedRoles.size}")

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
                    tick("[OK] ${sortedRoles.size} cargos criados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(rolesCloned = true))
                }

                if (state.cloneChannels && !state.channelsCloned) {
                    onLog("[CHANNELS] Clonando canais...")
                    logger.info("Clonando canais")
                    
                    val channels = api.getChannels(state.sourceGuildId)
                    val channelList = mutableListOf<JSONObject>()
                    for (i in 0 until channels.length()) {
                        channelList.add(channels.getJSONObject(i))
                    }

                    logger.info("Total de canais: ${channelList.size}")

                    val existingChannels = api.getChannels(targetGuildId)
                    for (i in 0 until existingChannels.length()) {
                        val ch = existingChannels.getJSONObject(i)
                        try { 
                            api.deleteDefaultChannel(targetGuildId, ch.getString("id"))
                        } catch (e: Exception) {
                            logger.warn("Erro ao deletar canal: ${e.message}")
                        }
                        Thread.sleep(200)
                    }

                    val categoryIdMap = mutableMapOf<String, String>()
                    val categories = channelList.filter { it.optInt("type") == 4 }.sortedBy { it.optInt("position") }
                    for (cat in categories) {
                        val body = buildChannelBody(cat, null, roleIdMap)
                        val created = api.createChannel(targetGuildId, body)
                        if (created != null) {
                            val catId = created.getString("id")
                            categoryIdMap[cat.getString("id")] = catId
                            channelIdMap[cat.getString("id")] = catId
                            applyPermissionOverwrites(cat, catId, roleIdMap)
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
                            val newChId = created.getString("id")
                            channelIdMap[ch.getString("id")] = newChId
                            applyPermissionOverwrites(ch, newChId, roleIdMap)
                        }
                        Thread.sleep(300)
                    }

                    tick("[OK] ${channelList.size} canais criados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(channelsCloned = true))
                }

                if (state.cloneEmojis && !state.emojisCloned) {
                    onLog("[EMOJIS] Clonando emojis...")
                    logger.info("Clonando emojis")
                    
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
                    tick("[OK] $count emojis criados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(emojisCloned = true))
                }

                if (state.cloneStickers && !state.stickersCloned) {
                    onLog("[STICKERS] Clonando stickers...")
                    logger.info("Clonando stickers")
                    
                    val stickers = api.getStickers(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until stickers.length()) {
                        val sticker = stickers.getJSONObject(i)
                        val name = sticker.optString("name", "sticker$i")
                        val id = sticker.optString("id")
                        val formatType = sticker.optInt("format_type", 1)
                        if (formatType == 3) continue
                        val ext = when (formatType) { 2 -> "apng"; else -> "png" }
                        val mime = when (formatType) { 2 -> "image/apng"; else -> "image/png" }
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
                    tick("[OK] $count stickers criados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(stickersCloned = true))
                }

                if (state.cloneSounds && !state.soundsCloned) {
                    onLog("[SOUNDS] Clonando sons...")
                    logger.info("Clonando sons")
                    
                    val sounds = api.getSoundboardSounds(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until sounds.length()) {
                        val sound = sounds.getJSONObject(i)
                        val name = sound.optString("name", "sound$i")
                        val soundId = sound.optString("sound_id")
                        val volume = sound.optDouble("volume", 1.0)
                        val emojiId = sound.optString("emoji_id").takeIf { it != "null" && it.isNotEmpty() }
                        val emojiName = sound.optString("emoji_name").takeIf { it != "null" && it.isNotEmpty() }
                        val url = "https://cdn.discordapp.com/soundboard-sounds/$soundId"
                        val bytes = api.downloadBytes(url) ?: continue
                        val dataUrl = api.bytesToBase64DataUrl(bytes, "audio/ogg")
                        val created = api.createSoundboardSound(targetGuildId, name, dataUrl, volume, emojiId, emojiName)
                        if (created != null) {
                            count++
                            mediaBytesMap["sounds/$name.ogg"] = Pair(bytes, "audio/ogg")
                        }
                        Thread.sleep(1000)
                    }
                    tick("[OK] $count sons criados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(soundsCloned = true))
                }

                if (state.cloneMessages && !state.messagesCloned) {
                    onLog("[MESSAGES] Clonando mensagens...")
                    logger.info("Clonando mensagens")
                    
                    var totalMessages = 0
                    for ((sourceChannelId, targetChannelId) in channelIdMap) {
                        try {
                            val messages = mutableListOf<JSONObject>()
                            var lastId: String? = null
                            
                            repeat(5) {
                                val batch = api.getChannelMessages(sourceChannelId, 100, lastId)
                                if (batch.length() == 0) return@repeat
                                for (i in 0 until batch.length()) {
                                    messages.add(batch.getJSONObject(i))
                                }
                                lastId = batch.getJSONObject(batch.length() - 1).getString("id")
                                Thread.sleep(500)
                            }
                            
                            if (messages.isEmpty()) continue
                            
                            val webhook = api.createWebhook(targetChannelId, "Clone")
                            if (webhook == null) continue
                            
                            val webhookId = webhook.getString("id")
                            val webhookToken = webhook.getString("token")
                            
                            messages.reverse()
                            
                            for (msg in messages) {
                                if (msg.optInt("type", 0) != 0) continue
                                
                                var content = msg.optString("content", "")
                                
                                content = content.replace(Regex("<@&(\\d+)>")) { match ->
                                    val oldRoleId = match.groupValues[1]
                                    val newRoleId = roleIdMap[oldRoleId]
                                    if (newRoleId != null) "<@&$newRoleId>" else match.value
                                }
                                
                                val author = msg.getJSONObject("author")
                                val username = author.optString("global_name") ?: author.optString("username")
                                val avatarHash = author.optString("avatar")
                                val userId = author.getString("id")
                                val avatarUrl = if (avatarHash.isNotEmpty() && avatarHash != "null") {
                                    "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png"
                                } else null
                                
                                val embeds = msg.optJSONArray("embeds")
                                val attachments = msg.optJSONArray("attachments")
                                val attachmentBytes = mutableListOf<ByteArray>()
                                
                                if (attachments != null) {
                                    for (j in 0 until minOf(attachments.length(), 3)) {
                                        val att = attachments.getJSONObject(j)
                                        val attUrl = att.getString("url")
                                        api.downloadBytes(attUrl)?.let { attachmentBytes.add(it) }
                                    }
                                }
                                
                                api.sendWebhookMessage(webhookId, webhookToken, content, username, avatarUrl, embeds, attachmentBytes)
                                totalMessages++
                                Thread.sleep(1000)
                            }
                            
                            api.deleteWebhook(webhookId)
                            Thread.sleep(500)
                        } catch (e: Exception) {
                            logger.warn("Erro ao clonar mensagens do canal: ${e.message}")
                        }
                    }
                    
                    tick("[OK] $totalMessages mensagens clonadas")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(messagesCloned = true))
                }

                if (state.cloneBans && !state.bansCloned) {
                    onLog("[BANS] Clonando banimentos...")
                    logger.info("Clonando banimentos")
                    
                    val bans = api.getBans(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until bans.length()) {
                        val ban = bans.getJSONObject(i)
                        val user = ban.getJSONObject("user")
                        val userId = user.getString("id")
                        val reason = ban.optString("reason").takeIf { it != "null" && it.isNotEmpty() }
                        api.createBan(targetGuildId, userId, reason)
                        count++
                        Thread.sleep(1000)
                    }
                    tick("[OK] $count banimentos criados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(bansCloned = true))
                }

                if (state.cloneEvents && !state.eventsCloned) {
                    onLog("[EVENTS] Clonando eventos...")
                    logger.info("Clonando eventos")
                    
                    val events = api.getScheduledEvents(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until events.length()) {
                        val event = events.getJSONObject(i)
                        val eventBody = JSONObject().apply {
                            put("name", event.getString("name"))
                            put("privacy_level", event.optInt("privacy_level", 2))
                            put("scheduled_start_time", event.getString("scheduled_start_time"))
                            put("entity_type", event.getInt("entity_type"))
                            event.optString("description").takeIf { it != "null" && it.isNotEmpty() }?.let {
                                put("description", it)
                            }
                            event.optString("scheduled_end_time").takeIf { it != "null" && it.isNotEmpty() }?.let {
                                put("scheduled_end_time", it)
                            }
                            if (event.optInt("entity_type") == 3) {
                                val metadata = event.optJSONObject("entity_metadata")
                                metadata?.optString("location")?.let {
                                    put("entity_metadata", JSONObject().put("location", it))
                                }
                            }
                        }
                        api.createScheduledEvent(targetGuildId, eventBody)
                        count++
                        Thread.sleep(1000)
                    }
                    tick("[OK] $count eventos criados")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(eventsCloned = true))
                }

                if (state.cloneAutoMod && !state.autoModCloned) {
                    onLog("[AUTOMOD] Clonando AutoMod...")
                    logger.info("Clonando AutoMod")
                    
                    val rules = api.getAutoModRules(state.sourceGuildId)
                    var count = 0
                    for (i in 0 until rules.length()) {
                        val rule = rules.getJSONObject(i)
                        val ruleBody = JSONObject().apply {
                            put("name", rule.getString("name"))
                            put("event_type", rule.getInt("event_type"))
                            put("trigger_type", rule.getInt("trigger_type"))
                            put("trigger_metadata", rule.getJSONObject("trigger_metadata"))
                            put("actions", rule.getJSONArray("actions"))
                            put("enabled", rule.getBoolean("enabled"))
                            
                            val exemptRoles = rule.optJSONArray("exempt_roles")
                            if (exemptRoles != null && exemptRoles.length() > 0) {
                                val mappedRoles = JSONArray()
                                for (j in 0 until exemptRoles.length()) {
                                    val oldRoleId = exemptRoles.getString(j)
                                    roleIdMap[oldRoleId]?.let { mappedRoles.put(it) }
                                }
                                put("exempt_roles", mappedRoles)
                            }
                            
                            val exemptChannels = rule.optJSONArray("exempt_channels")
                            if (exemptChannels != null && exemptChannels.length() > 0) {
                                val mappedChannels = JSONArray()
                                for (j in 0 until exemptChannels.length()) {
                                    val oldChannelId = exemptChannels.getString(j)
                                    channelIdMap[oldChannelId]?.let { mappedChannels.put(it) }
                                }
                                put("exempt_channels", mappedChannels)
                            }
                        }
                        api.createAutoModRule(targetGuildId, ruleBody)
                        count++
                        Thread.sleep(500)
                    }
                    tick("[OK] $count regras AutoMod criadas")
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(autoModCloned = true))
                }

                if (state.cloneOnboarding && !state.onboardingCloned) {
                    onLog("[ONBOARDING] Clonando Onboarding...")
                    logger.info("Clonando Onboarding")
                    
                    val onboarding = api.getOnboarding(state.sourceGuildId)
                    if (onboarding != null && onboarding.optBoolean("enabled", false)) {
                        val onboardingBody = JSONObject().apply {
                            put("enabled", true)
                            
                            val prompts = onboarding.optJSONArray("prompts")
                            if (prompts != null) {
                                val mappedPrompts = JSONArray()
                                for (i in 0 until prompts.length()) {
                                    val prompt = prompts.getJSONObject(i)
                                    val mappedPrompt = JSONObject(prompt.toString())
                                    
                                    val options = prompt.optJSONArray("options")
                                    if (options != null) {
                                        val mappedOptions = JSONArray()
                                        for (j in 0 until options.length()) {
                                            val option = options.getJSONObject(j)
                                            val mappedOption = JSONObject(option.toString())
                                            
                                            val channelIds = option.optJSONArray("channel_ids")
                                            if (channelIds != null) {
                                                val newChannelIds = JSONArray()
                                                for (k in 0 until channelIds.length()) {
                                                    val oldId = channelIds.getString(k)
                                                    channelIdMap[oldId]?.let { newChannelIds.put(it) }
                                                }
                                                mappedOption.put("channel_ids", newChannelIds)
                                            }
                                            
                                            val roleIds = option.optJSONArray("role_ids")
                                            if (roleIds != null) {
                                                val newRoleIds = JSONArray()
                                                for (k in 0 until roleIds.length()) {
                                                    val oldId = roleIds.getString(k)
                                                    roleIdMap[oldId]?.let { newRoleIds.put(it) }
                                                }
                                                mappedOption.put("role_ids", newRoleIds)
                                            }
                                            
                                            mappedOptions.put(mappedOption)
                                        }
                                        mappedPrompt.put("options", mappedOptions)
                                    }
                                    
                                    mappedPrompts.put(mappedPrompt)
                                }
                                put("prompts", mappedPrompts)
                            }
                            
                            val defaultChannelIds = onboarding.optJSONArray("default_channel_ids")
                            if (defaultChannelIds != null) {
                                val newDefaultIds = JSONArray()
                                for (i in 0 until defaultChannelIds.length()) {
                                    val oldId = defaultChannelIds.getString(i)
                                    channelIdMap[oldId]?.let { newDefaultIds.put(it) }
                                }
                                put("default_channel_ids", newDefaultIds)
                            }
                        }
                        api.updateOnboarding(targetGuildId, onboardingBody)
                        tick("[OK] Onboarding clonado")
                    } else {
                        tick("[INFO] Onboarding não habilitado")
                    }
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(onboardingCloned = true))
                }

                if (state.cloneWelcome && !state.welcomeCloned) {
                    onLog("[WELCOME] Clonando Welcome Screen...")
                    logger.info("Clonando Welcome Screen")
                    
                    val welcome = api.getWelcomeScreen(state.sourceGuildId)
                    if (welcome != null) {
                        val welcomeBody = JSONObject().apply {
                            put("enabled", true)
                            welcome.optString("description").takeIf { it != "null" && it.isNotEmpty() }?.let {
                                put("description", it)
                            }
                            
                            val channels = welcome.optJSONArray("welcome_channels")
                            if (channels != null) {
                                val newChannels = JSONArray()
                                for (i in 0 until channels.length()) {
                                    val wc = channels.getJSONObject(i)
                                    val oldChannelId = wc.getString("channel_id")
                                    val newChannelId = channelIdMap[oldChannelId]
                                    if (newChannelId != null) {
                                        newChannels.put(JSONObject().apply {
                                            put("channel_id", newChannelId)
                                            put("description", wc.getString("description"))
                                            wc.optString("emoji_id").takeIf { it != "null" && it.isNotEmpty() }?.let {
                                                put("emoji_id", it)
                                            }
                                            wc.optString("emoji_name").takeIf { it != "null" && it.isNotEmpty() }?.let {
                                                put("emoji_name", it)
                                            }
                                        })
                                    }
                                }
                                put("welcome_channels", newChannels)
                            }
                        }
                        api.updateWelcomeScreen(targetGuildId, welcomeBody)
                        tick("[OK] Welcome Screen clonado")
                    } else {
                        tick("[INFO] Welcome Screen não configurado")
                    }
                    ProgressStateManager.saveProgress(ctx, updatedState.copy(welcomeCloned = true))
                }

                if (state.saveMidia && mediaBytesMap.isNotEmpty()) {
                    onLog("[ZIP] Salvando mídia...")
                    logger.info("Salvando ${mediaBytesMap.size} arquivos")
                    saveMidiaZip(serverName, mediaBytesMap)
                    tick("[OK] Mídia salva")
                }

                ProgressStateManager.saveProgress(ctx, updatedState.copy(isComplete = true))
                logger.info("Clonagem concluída!")
                finishNotification(true, "Servidor $serverName clonado!")
                onComplete(true, "[SUCESSO] Servidor clonado! ID: $targetGuildId")

            } catch (e: Exception) {
                logger.error("Erro durante clonagem", e)
                finishNotification(false, "Erro: ${e.message}")
                onComplete(false, "[ERRO] ${e.message}")
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
            
            val availableTags = channel.optJSONArray("available_tags")
            if (availableTags != null && availableTags.length() > 0) {
                put("available_tags", availableTags)
            }
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
            } catch (e: Exception) {
                logger.warn("Erro ao aplicar permissões: ${e.message}")
            }
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
            logger.info("ZIP salvo: ${zipFile.absolutePath}")
            onLog("[OK] ZIP: ${zipFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Erro ao salvar ZIP", e)
            onLog("[ERRO] ZIP: ${e.message}")
        }
    }
}
