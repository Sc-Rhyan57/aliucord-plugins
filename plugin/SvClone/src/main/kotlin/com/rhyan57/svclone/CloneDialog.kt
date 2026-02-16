package com.rhyan57.svclone

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.aliucord.Utils

object CloneDialog {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(ctx: Context, sourceGuildId: String, defaultToken: String) {
        mainHandler.post {
            buildAndShow(ctx, sourceGuildId, defaultToken, null)
        }
    }

    fun showWithProgress(ctx: Context, state: ProgressState) {
        mainHandler.post {
            buildAndShow(ctx, state.sourceGuildId, state.token, state)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildAndShow(ctx: Context, sourceGuildId: String, defaultToken: String, resumeState: ProgressState?) {
        val root = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
            setBackgroundColor(Color.parseColor("#2B2D31"))
        }
        root.addView(container)

        fun label(text: String): TextView = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.06f
            setPadding(0, 0, 0, 6)
        }

        fun editText(hint: String, value: String = "", password: Boolean = false): EditText = EditText(ctx).apply {
            this.hint = hint
            setText(value)
            inputType = if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_TEXT
            setTextColor(Color.parseColor("#DBDEE1"))
            setHintTextColor(Color.parseColor("#4E5058"))
            setBackgroundColor(Color.parseColor("#1E1F22"))
            setPadding(24, 18, 24, 18)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        fun checkBox(text: String, checked: Boolean = true): CheckBox = CheckBox(ctx).apply {
            this.text = text
            isChecked = checked
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 4) }
        }

        fun divider(): View = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#3B3D44"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 18, 0, 18) }
        }

        fun sectionTitle(text: String): TextView = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#5865F2"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(0, 6, 0, 10)
        }

        val titleView = TextView(ctx).apply {
            text = "Clone Guild"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4)
        }

        val subtitleView = TextView(ctx).apply {
            text = "bettercloner.vercel.app"
            setTextColor(Color.parseColor("#5865F2"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val tokenField = editText("Token Discord", defaultToken, password = true)
        val sourceField = editText("ID do servidor de origem", resumeState?.sourceGuildId ?: sourceGuildId)
        val targetField = editText("ID do servidor destino (vazio = criar novo)")

        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(divider())
        container.addView(sectionTitle("AUTENTICACAO"))
        container.addView(label("TOKEN DISCORD"))
        container.addView(tokenField)
        container.addView(divider())
        container.addView(sectionTitle("SERVIDORES"))
        container.addView(label("SERVIDOR DE ORIGEM (ID)"))
        container.addView(sourceField)
        container.addView(label("SERVIDOR DESTINO (ID)"))
        container.addView(targetField)
        container.addView(divider())
        container.addView(sectionTitle("O QUE CLONAR"))

        val cbSettings  = checkBox("Configuracoes gerais", resumeState?.cloneSettings ?: true)
        val cbIcon      = checkBox("Icone do servidor",    resumeState?.cloneIcon ?: true)
        val cbBanner    = checkBox("Banner do servidor",   resumeState?.cloneBanner ?: true)
        val cbRoles     = checkBox("Cargos",               resumeState?.cloneRoles ?: true)
        val cbChannels  = checkBox("Canais e categorias",  resumeState?.cloneChannels ?: true)
        val cbEmojis    = checkBox("Emojis personalizados",resumeState?.cloneEmojis ?: true)
        val cbStickers  = checkBox("Stickers",             resumeState?.cloneStickers ?: true)
        val cbSaveMidia = checkBox("Salvar midia em ZIP (Aliucord/SvClone/Midia/)", resumeState?.saveMidia ?: false)

        container.addView(cbSettings)
        container.addView(cbIcon)
        container.addView(cbBanner)
        container.addView(cbRoles)
        container.addView(cbChannels)
        container.addView(cbEmojis)
        container.addView(cbStickers)
        container.addView(cbSaveMidia)
        container.addView(divider())
        container.addView(sectionTitle("PROGRESSO"))

        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 4) }
        }

        val progressLabel = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#80848E"))
            textSize = 12f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        val logView = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1E1F22"))
            setPadding(16, 12, 16, 12)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 300
            ).apply { setMargins(0, 8, 0, 0) }
        }

        container.addView(progressBar)
        container.addView(progressLabel)
        container.addView(logView)

        resumeState?.let { targetField.setText(it.targetGuildId) }

        val dialog = AlertDialog.Builder(ctx)
            .setView(root)
            .setCancelable(true)
            .create()

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Iniciar Clonagem") { _, _ -> }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancelar") { d, _ -> d.dismiss() }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveBtn.setTextColor(Color.parseColor("#5865F2"))

            positiveBtn.setOnClickListener {
                val token = tokenField.text.toString().trim()
                val srcId  = sourceField.text.toString().trim()
                val dstId  = targetField.text.toString().trim()

                if (token.isEmpty()) { Utils.showToast("Token obrigatorio!", true); return@setOnClickListener }
                if (srcId.isEmpty())  { Utils.showToast("ID de origem obrigatorio!", true); return@setOnClickListener }

                positiveBtn.isEnabled = false
                positiveBtn.text = "Clonando..."
                progressBar.visibility = View.VISIBLE
                progressLabel.visibility = View.VISIBLE
                logView.visibility = View.VISIBLE
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
                    rolesCloned     = resumeState?.rolesCloned ?: false,
                    channelsCloned  = resumeState?.channelsCloned ?: false,
                    emojisCloned    = resumeState?.emojisCloned ?: false,
                    stickersCloned  = resumeState?.stickersCloned ?: false,
                    settingsCloned  = resumeState?.settingsCloned ?: false,
                    iconCloned      = resumeState?.iconCloned ?: false,
                    bannerCloned    = resumeState?.bannerCloned ?: false
                )

                CloneManager(
                    ctx = ctx,
                    api = DiscordApiClient(token),
                    onLog = { msg ->
                        mainHandler.post { logView.text = "${logView.text}\n$msg" }
                    },
                    onProgress = { progress ->
                        mainHandler.post {
                            val pct = (progress * 100).toInt().coerceIn(0, 100)
                            progressBar.progress = pct
                            progressLabel.text = "$pct%"
                        }
                    },
                    onComplete = { success, msg ->
                        mainHandler.post {
                            progressBar.progress = if (success) 100 else progressBar.progress
                            progressLabel.text = if (success) "100% - Concluido!" else "Erro!"
                            logView.text = "${logView.text}\n\n$msg"
                            positiveBtn.isEnabled = true
                            positiveBtn.text = "Iniciar Clonagem"
                            if (success) {
                                ProgressStateManager.clearProgress(ctx)
                                Utils.showToast("Servidor clonado com sucesso!", false)
                            } else {
                                Utils.showToast("Erro na clonagem. Veja os logs.", true)
                            }
                        }
                    }
                ).execute(state)
            }
        }

        dialog.show()
        dialog.window?.setLayout(
            (ctx.resources.displayMetrics.widthPixels * 0.95f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
