package com.rhyan57.svclone

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.discord.models.guild.Guild
import com.discord.widgets.guilds.profile.WidgetGuildProfileSheet

@AliucordPlugin
class SvClone : Plugin() {

    override fun start(context: Context) {
        clearNotifications(context)
        checkPendingProgress(context)

        commands.registerCommand(
            "clone-server",
            "Clonar servidor do Discord",
            listOf(
                Utils.createCommandOption(CommandsAPI.OptionType.STRING, "server_id", "ID do servidor", null, true),
                Utils.createCommandOption(CommandsAPI.OptionType.STRING, "token", "Token Discord (opcional)", null, false)
            )
        ) { ctx ->
            val serverId = ctx.getRequiredString("server_id")
            val token = ctx.getStringOrDefault("token", "")
            Utils.mainThread.post {
                CloneDialog.show(ctx.context, serverId, token)
            }
            CommandsAPI.CommandResult("Abrindo diálogo...", null, false)
        }

        patcher.after<WidgetGuildProfileSheet>("onViewCreated", View::class.java, android.os.Bundle::class.java) { param ->
            val rootView = param.args[0] as? View ?: return@after
            val sheet = param.thisObject as? WidgetGuildProfileSheet ?: return@after
            
            try {
                val guild: Guild? = sheet.javaClass.getDeclaredField("guild").let { f ->
                    f.isAccessible = true
                    f.get(sheet) as? Guild
                }
                
                guild?.let { g ->
                    addCloneButton(rootView.context, rootView, g.getId().toString())
                }
            } catch (e: Exception) {
                logger.error("Erro ao adicionar botão:", e)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addCloneButton(context: Context, rootView: View, guildId: String) {
        try {
            val parent = rootView.findViewById<LinearLayout>(
                Utils.getResId("guild_profile_sheet_actions_container", "id")
            ) ?: return

            val button = TextView(context).apply {
                text = "📋 Clone Server"
                setTextColor(Color.parseColor("#FFFFFF"))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(24, 18, 24, 18)
                
                background = GradientDrawable().apply {
                    cornerRadius = 10f
                    setColor(Color.parseColor("#5865F2"))
                }
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 8, 16, 8)
                }
                
                setOnClickListener {
                    CloneDialog.show(context, guildId, "")
                }
            }

            parent.addView(button)
        } catch (e: Exception) {
            logger.error("Erro ao adicionar botão clone:", e)
        }
    }

    private fun clearNotifications(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(12345)
        } catch (e: Exception) {
            logger.error("Erro ao limpar notificações:", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun checkPendingProgress(context: Context) {
        val pendingState = ProgressStateManager.loadProgress(context) ?: return
        
        if (pendingState.isComplete) {
            ProgressStateManager.clearProgress(context)
            return
        }

        Utils.mainThread.post {
            val messageText = buildString {
                append("Você tem uma clonagem em andamento:\n\n")
                append("Servidor: ${pendingState.serverName}\n")
                append("Origem: ${pendingState.sourceGuildId}\n")
                
                val completed = mutableListOf<String>()
                if (pendingState.settingsCloned) completed.add("Settings")
                if (pendingState.iconCloned) completed.add("Icon")
                if (pendingState.bannerCloned) completed.add("Banner")
                if (pendingState.rolesCloned) completed.add("Roles")
                if (pendingState.channelsCloned) completed.add("Channels")
                if (pendingState.emojisCloned) completed.add("Emojis")
                if (pendingState.stickersCloned) completed.add("Stickers")
                if (pendingState.soundsCloned) completed.add("Sounds")
                if (pendingState.messagesCloned) completed.add("Messages")
                if (pendingState.bansCloned) completed.add("Bans")
                if (pendingState.eventsCloned) completed.add("Events")
                if (pendingState.autoModCloned) completed.add("AutoMod")
                if (pendingState.onboardingCloned) completed.add("Onboarding")
                if (pendingState.welcomeCloned) completed.add("Welcome")
                
                if (completed.isNotEmpty()) {
                    append("\nConcluído: ${completed.joinToString(", ")}")
                }
            }

            AlertDialog.Builder(context)
                .setTitle("Continuar Clonagem?")
                .setMessage(messageText)
                .setPositiveButton("Continuar") { _, _ ->
                    CloneDialog.showWithProgress(context, pendingState)
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    ProgressStateManager.clearProgress(context)
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
        patcher.unpatchAll()
        clearNotifications(context)
    }
}
