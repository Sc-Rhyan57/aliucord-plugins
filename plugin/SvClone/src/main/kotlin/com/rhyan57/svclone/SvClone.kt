package com.rhyan57.svclone

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.ReflectUtils
import com.discord.api.commands.ApplicationCommandType
import com.discord.databinding.WidgetGuildProfileSheetBinding
import com.discord.models.guild.Guild
import com.discord.stores.StoreStream
import com.discord.widgets.guilds.profile.WidgetGuildProfileSheet
import com.discord.widgets.guilds.profile.WidgetGuildProfileSheetViewModel
import com.lytefast.flexinput.R

@AliucordPlugin
class SvClone : Plugin() {
    
    private val profileButtonId = View.generateViewId()

    override fun start(context: Context) {
        TokenManager.initialize(settings)
        clearNotifications(context)
        checkPendingProgress(context)

        commands.registerCommand(
            "clone-server",
            "Clonar servidor do Discord",
            listOf(
                Utils.createCommandOption(ApplicationCommandType.STRING, "server_id", "ID do servidor (opcional)", null, false),
                Utils.createCommandOption(ApplicationCommandType.STRING, "token", "Token Discord (opcional)", null, false)
            )
        ) { ctx ->
            val serverId = ctx.getStringOrDefault("server_id", "")
            val token = ctx.getStringOrDefault("token", "")
            Utils.mainThread.post {
                CloneDialog.show(ctx.context, serverId, token, settings)
            }
            CommandsAPI.CommandResult("Abrindo diálogo de clonagem...", null, false)
        }

        commands.registerCommand(
            "add-token",
            "Adicionar token para rotação de contas",
            listOf(
                Utils.createCommandOption(ApplicationCommandType.STRING, "token", "Token Discord", null, true)
            )
        ) { ctx ->
            val token = ctx.getRequiredString("token")
            TokenManager.addToken(settings, token)
            CommandsAPI.CommandResult("✅ Token adicionado com sucesso!", null, false)
        }

        commands.registerCommand(
            "list-tokens",
            "Listar todos os tokens salvos"
        ) { ctx ->
            val tokens = TokenManager.getTokens()
            if (tokens.isEmpty()) {
                CommandsAPI.CommandResult("❌ Nenhum token salvo.", null, false)
            } else {
                val message = tokens.mapIndexed { index, token ->
                    "${index + 1}. ${TokenManager.getTokenInfo(token)}"
                }.joinToString("\n")
                CommandsAPI.CommandResult("📋 Tokens salvos:\n$message", null, false)
            }
        }

        patchWidgetGuildProfileSheet()
    }

    private fun patchWidgetGuildProfileSheet() {
        try {
            patcher.after<WidgetGuildProfileSheet>(
                "configureTabItems",
                Long::class.javaPrimitiveType,
                WidgetGuildProfileSheetViewModel.TabItems::class.java,
                Boolean::class.javaPrimitiveType
            ) { param ->
                try {
                    val sheet = param.thisObject as? WidgetGuildProfileSheet ?: return@after
                    val bindingMethod = ReflectUtils.getMethodByArgs(WidgetGuildProfileSheet::class.java, "getBinding")
                    val binding = bindingMethod.invoke(sheet) as? WidgetGuildProfileSheetBinding ?: return@after
                    
                    val rootView = binding.f.rootView as? ViewGroup ?: return@after
                    val actionsCard = rootView.findViewById<CardView>(
                        Utils.getResId("guild_profile_sheet_secondary_actions", "id")
                    ) ?: return@after
                    
                    val actionsLayout = actionsCard.getChildAt(0) as? LinearLayout ?: return@after
                    
                    if (actionsLayout.findViewById<View>(profileButtonId) != null) {
                        return@after
                    }
                    
                    val guildId = param.args[0] as? Long ?: return@after
                    val guild = StoreStream.getGuilds().getGuild(guildId) ?: return@after
                    
                    addCloneButton(actionsLayout, guild)
                } catch (e: Exception) {
                    logger.error("Erro ao adicionar botão no perfil:", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Erro ao aplicar patch:", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addCloneButton(container: LinearLayout, guild: Guild) {
        val context = container.context
        
        val button = TextView(context, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
            id = profileButtonId
            text = "📋 Clonar Servidor"
            setTextColor(Color.WHITE)
            val pd = DimenUtils.getDefaultPadding()
            setPadding(pd, pd, pd, pd)
            typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
            
            layoutParams = container.getChildAt(0).layoutParams
            
            setOnClickListener {
                val currentToken = TokenManager.getCurrentToken() ?: ""
                CloneDialog.show(context, guild.getId().toString(), currentToken, settings)
            }
        }
        
        container.addView(button)
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

        Utils.mainThread.postDelayed({
            try {
                val activity = Utils.appActivity ?: return@postDelayed
                
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
                
                val messageText = buildString {
                    append("Você tem uma clonagem em andamento:\n\n")
                    append("Servidor: ${pendingState.serverName}\n")
                    append("Origem: ${pendingState.sourceGuildId}\n")
                    if (completed.isNotEmpty()) {
                        append("\n✅ Concluído: ${completed.joinToString(", ")}")
                    }
                }

                AlertDialog.Builder(activity)
                    .setTitle("Continuar Clonagem?")
                    .setMessage(messageText)
                    .setPositiveButton("Continuar") { _, _ ->
                        CloneDialog.showWithProgress(activity, pendingState, settings)
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        ProgressStateManager.clearProgress(activity)
                    }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                logger.error("Erro ao mostrar diálogo de progresso:", e)
            }
        }, 1000)
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
        patcher.unpatchAll()
        clearNotifications(context)
    }
}
