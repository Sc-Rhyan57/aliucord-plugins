package com.rhyan57.svclone

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.aliucord.Utils
import com.discord.utilities.color.ColorCompat
import com.lytefast.flexinput.R

object CloneDialog {

    fun show(ctx: Context, sourceGuildId: String, defaultToken: String) {
        Utils.mainThread.post {
            buildAndShow(ctx, sourceGuildId, defaultToken, null)
        }
    }

    fun showWithProgress(ctx: Context, state: ProgressState) {
        Utils.mainThread.post {
            buildAndShow(ctx, state.sourceGuildId, state.token, state)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildAndShow(ctx: Context, sourceGuildId: String, defaultToken: String, resumeState: ProgressState?) {
        val root = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(ColorCompat.getThemedColor(ctx, R.b.primary_dark_800))
        }
        root.addView(container)

        fun label(text: String): TextView = TextView(ctx).apply {
            this.text = text
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.primary_230))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 6)
        }

        fun editText(hint: String, value: String = "", password: Boolean = false): EditText = EditText(ctx).apply {
            this.hint = hint
            setText(value)
            inputType = if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_TEXT
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.primary_100))
            setHintTextColor(ColorCompat.getThemedColor(ctx, R.b.primary_500))
            setPadding(16, 14, 16, 14)
            textSize = 13f
            
            background = GradientDrawable().apply {
                cornerRadius = 8f
                setColor(ColorCompat.getThemedColor(ctx, R.b.primary_dark_700))
            }
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        }

        fun checkBox(text: String, iconRes: Int, checked: Boolean = true): CheckBox = CheckBox(ctx).apply {
            this.text = text
            isChecked = checked
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.primary_200))
            textSize = 12f
            
            val icon = ContextCompat.getDrawable(ctx, iconRes)?.mutate()
            icon?.setTint(ColorCompat.getThemedColor(ctx, R.b.primary_300))
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            compoundDrawablePadding = 12
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
        }

        fun divider(): View = View(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = 1f
                setColor(ColorCompat.getThemedColor(ctx, R.b.primary_dark_600))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 14, 0, 14) }
        }

        fun sectionTitle(text: String, iconRes: Int): TextView = TextView(ctx).apply {
            this.text = text
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.brand_500))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.1f
            setPadding(0, 6, 0, 10)
            
            val icon = ContextCompat.getDrawable(ctx, iconRes)?.mutate()
            icon?.setTint(ColorCompat.getThemedColor(ctx, R.b.brand_500))
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            compoundDrawablePadding = 10
        }

        val titleView = TextView(ctx).apply {
            text = "Clone Server"
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.white))
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }

        val tokenField = editText("Token Discord", defaultToken, password = true)
        val sourceField = editText("ID do servidor de origem", resumeState?.sourceGuildId ?: sourceGuildId)
        val targetField = editText("ID do servidor destino")

        container.addView(titleView)
        container.addView(divider())
        container.addView(sectionTitle("AUTENTICAÇÃO", R.e.ic_account_circle_24dp))
        container.addView(label("TOKEN DISCORD"))
        container.addView(tokenField)
        container.addView(divider())
        container.addView(sectionTitle("SERVIDORES", R.e.ic_sidebar_server_24dp))
        container.addView(label("SERVIDOR DE ORIGEM"))
        container.addView(sourceField)
        container.addView(label("SERVIDOR DESTINO"))
        container.addView(targetField)
        container.addView(divider())
        container.addView(sectionTitle("O QUE CLONAR", R.e.ic_check_circle_24dp))

        val cbSettings  = checkBox("Configurações gerais", R.e.ic_settings_24dp, resumeState?.cloneSettings ?: true)
        val cbIcon      = checkBox("Ícone do servidor", R.e.ic_image_24dp, resumeState?.cloneIcon ?: true)
        val cbBanner    = checkBox("Banner do servidor", R.e.ic_image_24dp, resumeState?.cloneBanner ?: true)
        val cbRoles     = checkBox("Cargos", R.e.ic_group_24dp, resumeState?.cloneRoles ?: true)
        val cbChannels  = checkBox("Canais e categorias", R.e.ic_channel_text, resumeState?.cloneChannels ?: true)
        val cbEmojis    = checkBox("Emojis personalizados", R.e.ic_emoji_picker_tab_guild, resumeState?.cloneEmojis ?: true)
        val cbStickers  = checkBox("Stickers", R.e.ic_sticker_24dp, resumeState?.cloneStickers ?: true)
        val cbSounds    = checkBox("Sons de Soundboard", R.e.ic_soundboard, resumeState?.cloneSounds ?: true)
        val cbMessages  = checkBox("Mensagens", R.e.ic_message_24dp, resumeState?.cloneMessages ?: false)
        val cbBans      = checkBox("Banimentos", R.e.ic_ban_24dp, resumeState?.cloneBans ?: false)
        val cbEvents    = checkBox("Eventos", R.e.ic_calendar_24dp, resumeState?.cloneEvents ?: false)
        val cbAutoMod   = checkBox("AutoMod", R.e.ic_shield_24dp, resumeState?.cloneAutoMod ?: false)
        val cbOnboarding = checkBox("Onboarding", R.e.ic_wave_24dp, resumeState?.cloneOnboarding ?: false)
        val cbWelcome   = checkBox("Welcome Screen", R.e.ic_star_24dp, resumeState?.cloneWelcome ?: false)
        val cbSaveMidia = checkBox("Salvar mídia em ZIP", R.e.ic_download_24dp, resumeState?.saveMidia ?: false)

        container.addView(cbSettings)
        container.addView(cbIcon)
        container.addView(cbBanner)
        container.addView(cbRoles)
        container.addView(cbChannels)
        container.addView(cbEmojis)
        container.addView(cbStickers)
        container.addView(cbSounds)
        container.addView(cbMessages)
        container.addView(cbBans)
        container.addView(cbEvents)
        container.addView(cbAutoMod)
        container.addView(cbOnboarding)
        container.addView(cbWelcome)
        container.addView(cbSaveMidia)
        container.addView(divider())

        val progressContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            visibility = View.GONE
            
            background = GradientDrawable().apply {
                cornerRadius = 10f
                setColor(ColorCompat.getThemedColor(ctx, R.b.primary_dark_700))
            }
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 14, 0, 14) }
        }

        val progressHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }

        val progressTitle = TextView(ctx).apply {
            text = "PROGRESSO"
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.brand_500))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val toggleButton = TextView(ctx).apply {
            text = "▼"
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.brand_500))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(12, 0, 12, 0)
        }

        progressHeader.addView(progressTitle)
        progressHeader.addView(toggleButton)

        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }

        val progressLabel = TextView(ctx).apply {
            text = "0%"
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.primary_400))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }

        val logScrollView = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                380
            ).apply { setMargins(0, 10, 0, 0) }
        }

        val logView = TextView(ctx).apply {
            text = ""
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.primary_200))
            textSize = 10f
            setPadding(10, 10, 10, 10)
            
            background = GradientDrawable().apply {
                cornerRadius = 6f
                setColor(ColorCompat.getThemedColor(ctx, R.b.primary_dark_800))
            }
        }

        logScrollView.addView(logView)

        var logsExpanded = true
        toggleButton.setOnClickListener {
            logsExpanded = !logsExpanded
            logScrollView.visibility = if (logsExpanded) View.VISIBLE else View.GONE
            toggleButton.text = if (logsExpanded) "▼" else "▶"
        }

        progressContainer.addView(progressHeader)
        progressContainer.addView(progressBar)
        progressContainer.addView(progressLabel)
        progressContainer.addView(logScrollView)
        
        container.addView(progressContainer)

        resumeState?.let { targetField.setText(it.targetGuildId) }

        val dialog = AlertDialog.Builder(ctx)
            .setView(root)
            .setCancelable(true)
            .create()

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Iniciar") { _, _ -> }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancelar") { d, _ -> d.dismiss() }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            
            positiveBtn.setTextColor(ColorCompat.getThemedColor(ctx, R.b.brand_500))
            negativeBtn.setTextColor(ColorCompat.getThemedColor(ctx, R.b.primary_300))

            positiveBtn.setOnClickListener {
                val token = tokenField.text.toString().trim()
                val srcId  = sourceField.text.toString().trim()
                val dstId  = targetField.text.toString().trim()

                if (token.isEmpty()) { Utils.showToast("Token obrigatório!", true); return@setOnClickListener }
                if (srcId.isEmpty())  { Utils.showToast("ID de origem obrigatório!", true); return@setOnClickListener }

                positiveBtn.isEnabled = false
                positiveBtn.text = "Clonando..."
                progressContainer.visibility = View.VISIBLE
                logView.text = ""

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

                CloneManager(
                    ctx = ctx,
                    api = DiscordApiClient(token),
                    onLog = { msg ->
                        Utils.mainThread.post { 
                            logView.text = "${logView.text}\n$msg"
                            logScrollView.post {
                                logScrollView.fullScroll(ScrollView.FOCUS_DOWN)
                            }
                        }
                    },
                    onProgress = { progress ->
                        Utils.mainThread.post {
                            val pct = (progress * 100).toInt().coerceIn(0, 100)
                            progressBar.progress = pct
                            progressLabel.text = "$pct%"
                        }
                    },
                    onComplete = { success, msg ->
                        Utils.mainThread.post {
                            progressBar.progress = if (success) 100 else progressBar.progress
                            progressLabel.text = if (success) "100%" else "Erro"
                            logView.text = "${logView.text}\n\n$msg"
                            positiveBtn.isEnabled = true
                            positiveBtn.text = "Iniciar"
                            if (success) {
                                ProgressStateManager.clearProgress(ctx)
                                Utils.showToast("Servidor clonado!", false)
                            } else {
                                Utils.showToast("Erro na clonagem", true)
                            }
                        }
                    }
                ).execute(state)
            }
        }

        try {
            dialog.show()
            dialog.window?.setLayout(
                (ctx.resources.displayMetrics.widthPixels * 0.90f).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        } catch (e: Exception) {
            Utils.showToast("Erro ao exibir dialogo: ${e.message}", true)
        }
    }
}
