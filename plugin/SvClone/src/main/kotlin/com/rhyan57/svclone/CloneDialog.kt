package com.rhyan57.svclone

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.discord.stores.StoreStream
import com.discord.utilities.icon.IconUtils
import com.discord.utilities.images.MGImages
import com.facebook.drawee.view.SimpleDraweeView

object CloneDialog {

    fun show(ctx: Context, sourceGuildId: String, defaultToken: String, settings: SettingsAPI) {
        Utils.mainThread.post {
            val activity = if (ctx is Activity) ctx else Utils.appActivity
            if (activity != null) {
                buildAndShow(activity, sourceGuildId, defaultToken, null, settings)
            }
        }
    }

    fun showWithProgress(ctx: Context, state: ProgressState, settings: SettingsAPI) {
        Utils.mainThread.post {
            val activity = if (ctx is Activity) ctx else Utils.appActivity
            if (activity != null) {
                buildAndShow(activity, state.sourceGuildId, state.token, state, settings)
            }
        }
    }

    fun showProgressOnly(ctx: Context, settings: SettingsAPI) {
        Utils.mainThread.post {
            val activity = if (ctx is Activity) ctx else Utils.appActivity
            if (activity == null) return@post

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.parseColor("#2B2D31"))
            }

            val titleView = TextView(activity).apply {
                setText("📊 Progresso da Clonagem")
                setTextColor(Color.WHITE)
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16)
            }

            val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = (CloneSession.currentProgress * 100).toInt()
            }

            val progressLabel = TextView(activity).apply {
                setText("${(CloneSession.currentProgress * 100).toInt()}%")
                setTextColor(Color.parseColor("#80848E"))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 0)
            }

            val logScrollView = ScrollView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 400
                ).apply { setMargins(0, 12, 0, 0) }
            }

            val logView = TextView(activity).apply {
                setText(CloneSession.logs.toString())
                setTextColor(Color.parseColor("#B5BAC1"))
                textSize = 10f
                setPadding(10, 10, 10, 10)
                background = GradientDrawable().apply {
                    cornerRadius = 6f
                    setColor(Color.parseColor("#0D0E10"))
                }
            }

            logScrollView.addView(logView)
            container.addView(titleView)
            container.addView(progressBar)
            container.addView(progressLabel)
            container.addView(logScrollView)

            CloneSession.onLog = { msg ->
                Utils.mainThread.post {
                    logView.setText("${logView.text}\n$msg")
                    logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
            CloneSession.onProgress = { p ->
                Utils.mainThread.post {
                    val pct = (p * 100).toInt().coerceIn(0, 100)
                    progressBar.progress = pct
                    progressLabel.setText("$pct%")
                }
            }

            val dialog = AlertDialog.Builder(activity)
                .setView(container)
                .setCancelable(true)
                .create()

            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "❌ Fechar") { d, _ ->
                CloneSession.onLog = null
                CloneSession.onProgress = null
                d.dismiss()
            }

            CloneSession.onComplete = { success, msg ->
                Utils.mainThread.post {
                    progressBar.progress = if (success) 100 else progressBar.progress
                    progressLabel.setText(if (success) "100%" else "Erro")
                    logView.setText("${logView.text}\n\n$msg")
                    if (success) {
                        ProgressStateManager.clearProgress(activity)
                        Utils.showToast("✅ Servidor clonado com sucesso!", false)
                    } else {
                        Utils.showToast("❌ Erro na clonagem", true)
                    }
                }
            }

            try {
                dialog.show()
                dialog.window?.setLayout(
                    (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            } catch (e: Exception) {
                Utils.showToast("Erro ao exibir progresso: ${e.message}", true)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildAndShow(ctx: Context, sourceGuildId: String, defaultToken: String, resumeState: ProgressState?, settings: SettingsAPI) {
        val rootScroll = NestedScrollView(ctx).apply { isFillViewport = true }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#2B2D31"))
        }
        rootScroll.addView(container)

        fun label(text: String): TextView = TextView(ctx).apply {
            setText(text)
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 6)
        }

        fun editText(hint: String, value: String = "", password: Boolean = false): EditText = EditText(ctx).apply {
            this.hint = hint
            setText(value)
            inputType = if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_TEXT
            setTextColor(Color.parseColor("#DBDEE1"))
            setHintTextColor(Color.parseColor("#4E5058"))
            setPadding(16, 14, 16, 14)
            textSize = 13f
            background = GradientDrawable().apply {
                cornerRadius = 8f
                setColor(Color.parseColor("#1E1F22"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        }

        fun checkBox(text: String, checked: Boolean = true): CheckBox = CheckBox(ctx).apply {
            setText(text)
            isChecked = checked
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
        }

        fun divider(): View = View(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = 1f
                setColor(Color.parseColor("#3B3D44"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 14, 0, 14) }
        }

        fun sectionTitle(text: String, expandable: Boolean = false): LinearLayout {
            val sectionLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 6, 0, 10)
            }
            val titleText = TextView(ctx).apply {
                setText(text)
                setTextColor(Color.parseColor("#5865F2"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.1f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            sectionLayout.addView(titleText)
            if (expandable) {
                val arrow = TextView(ctx).apply {
                    setText("▼")
                    setTextColor(Color.parseColor("#5865F2"))
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(12, 0, 12, 0)
                }
                sectionLayout.addView(arrow)
            }
            return sectionLayout
        }

        val titleView = TextView(ctx).apply {
            setText("🔄 Clone Server")
            setTextColor(Color.WHITE)
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }

        val tokenField = editText("Token Discord", defaultToken, password = true)
        val sourceField = editText("ID do servidor de origem", resumeState?.sourceGuildId ?: sourceGuildId)
        val targetField = editText("ID do servidor destino (deixe vazio para criar novo)", resumeState?.targetGuildId ?: "")

        val sourcePreview = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(12, 12, 12, 12)
            background = GradientDrawable().apply { cornerRadius = 8f; setColor(Color.parseColor("#1E1F22")) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        }
        val sourceIcon = SimpleDraweeView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(0, 0, 12, 0) }
        }
        val sourceInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sourceName = TextView(ctx).apply {
            setText(""); setTextColor(Color.WHITE); textSize = 14f; setTypeface(null, Typeface.BOLD)
        }
        val sourceMemberCount = TextView(ctx).apply {
            setText(""); setTextColor(Color.parseColor("#B5BAC1")); textSize = 11f
        }
        sourceInfo.addView(sourceName); sourceInfo.addView(sourceMemberCount)
        sourcePreview.addView(sourceIcon); sourcePreview.addView(sourceInfo)

        val targetPreview = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(12, 12, 12, 12)
            background = GradientDrawable().apply { cornerRadius = 8f; setColor(Color.parseColor("#1E1F22")) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        }
        val targetIcon = SimpleDraweeView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(0, 0, 12, 0) }
        }
        val targetInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val targetName = TextView(ctx).apply {
            setText(""); setTextColor(Color.WHITE); textSize = 14f; setTypeface(null, Typeface.BOLD)
        }
        val targetMemberCount = TextView(ctx).apply {
            setText(""); setTextColor(Color.parseColor("#B5BAC1")); textSize = 11f
        }
        targetInfo.addView(targetName); targetInfo.addView(targetMemberCount)
        targetPreview.addView(targetIcon); targetPreview.addView(targetInfo)

        fun updateServerPreview(guildId: String, isSource: Boolean) {
            if (guildId.isEmpty()) {
                if (isSource) sourcePreview.visibility = View.GONE else targetPreview.visibility = View.GONE
                return
            }
            Utils.threadPool.execute {
                try {
                    val guild = StoreStream.getGuilds().getGuild(guildId.toLong())
                    if (guild != null) {
                        Utils.mainThread.post {
                            if (isSource) {
                                sourceName.setText(guild.name)
                                sourceMemberCount.setText("${guild.memberCount} membros")
                                IconUtils.getForGuild(guild)?.let { MGImages.setImage(sourceIcon, it) }
                                sourcePreview.visibility = View.VISIBLE
                            } else {
                                targetName.setText(guild.name)
                                targetMemberCount.setText("${guild.memberCount} membros")
                                IconUtils.getForGuild(guild)?.let { MGImages.setImage(targetIcon, it) }
                                targetPreview.visibility = View.VISIBLE
                            }
                        }
                    }
                } catch (e: Exception) {
                    Utils.mainThread.post {
                        if (isSource) sourcePreview.visibility = View.GONE else targetPreview.visibility = View.GONE
                    }
                }
            }
        }

        sourceField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateServerPreview(s.toString().trim(), true) }
        })
        targetField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateServerPreview(s.toString().trim(), false) }
        })

        container.addView(titleView)
        container.addView(divider())
        container.addView(sectionTitle("🔐 AUTENTICAÇÃO", false))
        container.addView(label("TOKEN DISCORD"))
        container.addView(tokenField)
        container.addView(divider())
        container.addView(sectionTitle("📊 SERVIDORES", false))
        container.addView(label("SERVIDOR DE ORIGEM"))
        container.addView(sourceField)
        container.addView(sourcePreview)
        container.addView(label("SERVIDOR DESTINO"))
        container.addView(targetField)
        container.addView(targetPreview)
        container.addView(divider())

        val basicSection = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val basicTitle = sectionTitle("⚙️ BÁSICO", true)
        var basicExpanded = true
        val cbSettings  = checkBox("Configurações gerais", resumeState?.cloneSettings ?: true)
        val cbIcon      = checkBox("Ícone do servidor", resumeState?.cloneIcon ?: true)
        val cbBanner    = checkBox("Banner do servidor", resumeState?.cloneBanner ?: true)
        val cbRoles     = checkBox("Cargos", resumeState?.cloneRoles ?: true)
        val cbChannels  = checkBox("Canais e categorias", resumeState?.cloneChannels ?: true)
        basicSection.addView(cbSettings); basicSection.addView(cbIcon); basicSection.addView(cbBanner)
        basicSection.addView(cbRoles); basicSection.addView(cbChannels)
        basicTitle.setOnClickListener {
            basicExpanded = !basicExpanded
            basicSection.visibility = if (basicExpanded) View.VISIBLE else View.GONE
            (basicTitle.getChildAt(1) as? TextView)?.setText(if (basicExpanded) "▼" else "▶")
        }
        container.addView(basicTitle); container.addView(basicSection); container.addView(divider())

        val mediaSection = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val mediaTitle = sectionTitle("🎨 MÍDIA", true)
        var mediaExpanded = true
        val cbEmojis    = checkBox("Emojis personalizados", resumeState?.cloneEmojis ?: true)
        val cbStickers  = checkBox("Stickers", resumeState?.cloneStickers ?: true)
        val cbSounds    = checkBox("Sons de Soundboard", resumeState?.cloneSounds ?: true)
        val cbSaveMidia = checkBox("Salvar mídia em ZIP", resumeState?.saveMidia ?: false)
        mediaSection.addView(cbEmojis); mediaSection.addView(cbStickers)
        mediaSection.addView(cbSounds); mediaSection.addView(cbSaveMidia)
        mediaTitle.setOnClickListener {
            mediaExpanded = !mediaExpanded
            mediaSection.visibility = if (mediaExpanded) View.VISIBLE else View.GONE
            (mediaTitle.getChildAt(1) as? TextView)?.setText(if (mediaExpanded) "▼" else "▶")
        }
        container.addView(mediaTitle); container.addView(mediaSection); container.addView(divider())

        val messagesSection = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val messagesTitle = sectionTitle("💬 MENSAGENS", true)
        var messagesExpanded = false
        messagesSection.visibility = View.GONE
        val cbMessages  = checkBox("Clonar mensagens", resumeState?.cloneMessages ?: false)
        val cbSystemMessages = checkBox("Incluir mensagens do sistema", resumeState?.cloneSystemMessages ?: false)
        val cbReactions = checkBox("Clonar reações", resumeState?.cloneReactions ?: true)
        val cbConvertMentions = checkBox("Converter menções (@cargos)", resumeState?.convertMentions ?: true)
        val cbConvertEmojis = checkBox("Converter emojis customizados (requer Nitro)", resumeState?.convertCustomEmojis ?: false)
        val cbConvertLinks = checkBox("Converter links de canais/mensagens", resumeState?.convertLinks ?: false)
        val cbForumThreads = checkBox("Clonar threads de fóruns", resumeState?.cloneForumThreads ?: false)
        val messageLimitLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }
        val messageLimitLabel = TextView(ctx).apply {
            setText("Limite de mensagens: ")
            setTextColor(Color.parseColor("#B5BAC1")); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val messageLimitField = EditText(ctx).apply {
            setText((resumeState?.messageLimit ?: 100).toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#DBDEE1")); setPadding(12, 8, 12, 8); textSize = 12f
            background = GradientDrawable().apply { cornerRadius = 6f; setColor(Color.parseColor("#1E1F22")) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 0, 0, 0) }
        }
        val messageLimitHint = TextView(ctx).apply {
            setText("(-1 = ilimitado)")
            setTextColor(Color.parseColor("#80848E")); textSize = 10f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 0, 0) }
        }
        messageLimitLayout.addView(messageLimitLabel); messageLimitLayout.addView(messageLimitField); messageLimitLayout.addView(messageLimitHint)
        messagesSection.addView(cbMessages); messagesSection.addView(messageLimitLayout)
        messagesSection.addView(cbSystemMessages); messagesSection.addView(cbReactions)
        messagesSection.addView(cbConvertMentions); messagesSection.addView(cbConvertEmojis)
        messagesSection.addView(cbConvertLinks); messagesSection.addView(cbForumThreads)
        messagesTitle.setOnClickListener {
            messagesExpanded = !messagesExpanded
            messagesSection.visibility = if (messagesExpanded) View.VISIBLE else View.GONE
            (messagesTitle.getChildAt(1) as? TextView)?.setText(if (messagesExpanded) "▼" else "▶")
        }
        container.addView(messagesTitle); container.addView(messagesSection); container.addView(divider())

        val moderationSection = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val moderationTitle = sectionTitle("🛡️ MODERAÇÃO", true)
        var moderationExpanded = false
        moderationSection.visibility = View.GONE
        val cbBans      = checkBox("Banimentos", resumeState?.cloneBans ?: false)
        val cbAutoMod   = checkBox("AutoMod", resumeState?.cloneAutoMod ?: false)
        moderationSection.addView(cbBans); moderationSection.addView(cbAutoMod)
        moderationTitle.setOnClickListener {
            moderationExpanded = !moderationExpanded
            moderationSection.visibility = if (moderationExpanded) View.VISIBLE else View.GONE
            (moderationTitle.getChildAt(1) as? TextView)?.setText(if (moderationExpanded) "▼" else "▶")
        }
        container.addView(moderationTitle); container.addView(moderationSection); container.addView(divider())

        val communitySection = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val communityTitle = sectionTitle("🌟 COMUNIDADE", true)
        var communityExpanded = false
        communitySection.visibility = View.GONE
        val cbEvents    = checkBox("Eventos", resumeState?.cloneEvents ?: false)
        val cbOnboarding = checkBox("Onboarding", resumeState?.cloneOnboarding ?: false)
        val cbWelcome   = checkBox("Welcome Screen", resumeState?.cloneWelcome ?: false)
        communitySection.addView(cbEvents); communitySection.addView(cbOnboarding); communitySection.addView(cbWelcome)
        communityTitle.setOnClickListener {
            communityExpanded = !communityExpanded
            communitySection.visibility = if (communityExpanded) View.VISIBLE else View.GONE
            (communityTitle.getChildAt(1) as? TextView)?.setText(if (communityExpanded) "▼" else "▶")
        }
        container.addView(communityTitle); container.addView(communitySection); container.addView(divider())

        val progressContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            visibility = View.GONE
            background = GradientDrawable().apply { cornerRadius = 10f; setColor(Color.parseColor("#1E1F22")) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 14, 0, 14) }
        }

        val progressHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
        val progressTitle = TextView(ctx).apply {
            setText("📊 PROGRESSO")
            setTextColor(Color.parseColor("#5865F2")); textSize = 10f
            setTypeface(null, Typeface.BOLD); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggleButton = TextView(ctx).apply {
            setText("▼"); setTextColor(Color.parseColor("#5865F2")); textSize = 12f
            gravity = Gravity.CENTER; setPadding(12, 0, 12, 0)
        }
        progressHeader.addView(progressTitle); progressHeader.addView(toggleButton)

        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
        }
        val progressLabel = TextView(ctx).apply {
            setText("0%"); setTextColor(Color.parseColor("#80848E")); textSize = 11f
            gravity = Gravity.CENTER; setPadding(0, 6, 0, 0)
        }
        val logScrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 380
            ).apply { setMargins(0, 10, 0, 0) }
        }
        val logView = TextView(ctx).apply {
            setText(""); setTextColor(Color.parseColor("#B5BAC1")); textSize = 10f; setPadding(10, 10, 10, 10)
            background = GradientDrawable().apply { cornerRadius = 6f; setColor(Color.parseColor("#0D0E10")) }
        }
        logScrollView.addView(logView)

        var logsExpanded = true
        toggleButton.setOnClickListener {
            logsExpanded = !logsExpanded
            logScrollView.visibility = if (logsExpanded) View.VISIBLE else View.GONE
            toggleButton.setText(if (logsExpanded) "▼" else "▶")
        }

        progressContainer.addView(progressHeader)
        progressContainer.addView(progressBar)
        progressContainer.addView(progressLabel)
        progressContainer.addView(logScrollView)
        container.addView(progressContainer)

        if (sourceGuildId.isNotEmpty()) updateServerPreview(sourceGuildId, true)
        resumeState?.let { if (it.targetGuildId.isNotEmpty()) updateServerPreview(it.targetGuildId, false) }

        val dialog = AlertDialog.Builder(ctx)
            .setView(rootScroll)
            .setCancelable(true)
            .create()

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "▶️ Iniciar") { _, _ -> }
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "⬇️ Minimizar") { _, _ -> }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "❌ Fechar") { d, _ ->
            CloneSession.onLog = null
            CloneSession.onProgress = null
            d.dismiss()
        }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutralBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveBtn.setTextColor(Color.parseColor("#5865F2"))
            neutralBtn.setTextColor(Color.parseColor("#F0B232"))
            negativeBtn.setTextColor(Color.parseColor("#ED4245"))

            neutralBtn.setOnClickListener {
                CloneSession.onLog = null
                CloneSession.onProgress = null
                dialog.dismiss()
                Utils.showToast("Clonagem continua em background. Use /clone-progress para ver.", false)
            }

            positiveBtn.setOnClickListener {
                val token = tokenField.text.toString().trim()
                val srcId  = sourceField.text.toString().trim()
                val dstId  = targetField.text.toString().trim()

                if (token.isEmpty()) { Utils.showToast("Token obrigatório!", true); return@setOnClickListener }
                if (srcId.isEmpty())  { Utils.showToast("ID de origem obrigatório!", true); return@setOnClickListener }

                positiveBtn.isEnabled = false
                positiveBtn.text = "⏳ Clonando..."
                neutralBtn.isEnabled = true
                progressContainer.visibility = View.VISIBLE
                logView.setText("")

                CloneSession.start()

                val messageLimit = try { messageLimitField.text.toString().toInt() } catch (e: Exception) { 100 }

                val state = ProgressState(
                    sourceGuildId   = srcId,
                    targetGuildId   = dstId,
                    serverName      = "",
                    token           = token,
                    cloneRoles      = cbRoles.isChecked,
                    cloneChannels   = cbChannels.isChecked,
                    cloneEmojis     = cbEmojis.isChecked,
                    cloneStickers   = cbStickers.isChecked,
                    cloneSettings   = cbSettings.isChecked,
                    cloneIcon       = cbIcon.isChecked,
                    cloneBanner     = cbBanner.isChecked,
                    saveMidia       = cbSaveMidia.isChecked,
                    cloneSounds     = cbSounds.isChecked,
                    cloneMessages   = cbMessages.isChecked,
                    cloneBans       = cbBans.isChecked,
                    cloneEvents     = cbEvents.isChecked,
                    cloneAutoMod    = cbAutoMod.isChecked,
                    cloneOnboarding = cbOnboarding.isChecked,
                    cloneWelcome    = cbWelcome.isChecked,
                    messageLimit    = messageLimit,
                    cloneSystemMessages = cbSystemMessages.isChecked,
                    cloneReactions  = cbReactions.isChecked,
                    convertMentions = cbConvertMentions.isChecked,
                    convertCustomEmojis = cbConvertEmojis.isChecked,
                    convertLinks    = cbConvertLinks.isChecked,
                    cloneForumThreads = cbForumThreads.isChecked,
                    rolesCloned     = resumeState?.rolesCloned ?: false,
                    channelsCloned  = resumeState?.channelsCloned ?: false,
                    emojisCloned    = resumeState?.emojisCloned ?: false,
                    stickersCloned  = resumeState?.stickersCloned ?: false,
                    settingsCloned  = resumeState?.settingsCloned ?: false,
                    iconCloned      = resumeState?.iconCloned ?: false,
                    bannerCloned    = resumeState?.bannerCloned ?: false,
                    soundsCloned    = resumeState?.soundsCloned ?: false,
                    messagesCloned  = resumeState?.messagesCloned ?: false,
                    bansCloned      = resumeState?.bansCloned ?: false,
                    eventsCloned    = resumeState?.eventsCloned ?: false,
                    autoModCloned   = resumeState?.autoModCloned ?: false,
                    onboardingCloned = resumeState?.onboardingCloned ?: false,
                    welcomeCloned   = resumeState?.welcomeCloned ?: false
                )

                CloneSession.onLog = { msg ->
                    Utils.mainThread.post {
                        logView.setText("${logView.text}\n$msg")
                        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                CloneSession.onProgress = { progress ->
                    Utils.mainThread.post {
                        val pct = (progress * 100).toInt().coerceIn(0, 100)
                        progressBar.progress = pct
                        progressLabel.setText("$pct%")
                    }
                }
                CloneSession.onComplete = { success, msg ->
                    Utils.mainThread.post {
                        progressBar.progress = if (success) 100 else progressBar.progress
                        progressLabel.setText(if (success) "100%" else "Erro")
                        logView.setText("${logView.text}\n\n$msg")
                        positiveBtn.isEnabled = true
                        positiveBtn.text = "▶️ Iniciar"
                        if (success) {
                            ProgressStateManager.clearProgress(ctx)
                            Utils.showToast("✅ Servidor clonado com sucesso!", false)
                        } else {
                            Utils.showToast("❌ Erro na clonagem", true)
                        }
                    }
                }

                CloneManager(
                    ctx = ctx,
                    api = DiscordApiClient(token)
                ).execute(state)
            }
        }

        try {
            dialog.show()
            dialog.window?.setLayout(
                (ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                (ctx.resources.displayMetrics.heightPixels * 0.9f).toInt()
            )
        } catch (e: Exception) {
            Utils.showToast("Erro ao exibir dialogo: ${e.message}", true)
        }
    }
}
