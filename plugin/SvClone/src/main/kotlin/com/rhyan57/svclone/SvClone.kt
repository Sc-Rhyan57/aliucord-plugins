package com.rhyan57.svclone

import android.app.NotificationManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.discord.api.commands.ApplicationCommandType
import com.discord.stores.StoreStream
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.rest.RestAPI
import com.discord.widgets.guilds.profile.WidgetGuildProfileSheet
import com.lytefast.flexinput.R

@AliucordPlugin
@Suppress("unused")
class SvClone : Plugin() {

    override fun start(ctx: Context) {
        clearNotifications(ctx)
        checkPendingProgress(ctx)

        commands.registerCommand(
            "clone-server",
            "Clona um servidor Discord",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "server_id",
                    "ID do servidor a clonar (opcional)"
                ),
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "token",
                    "Token Discord (opcional)"
                )
            )
        ) { ctx2 ->
            val guildId = try {
                ctx2.getString("server_id") ?: StoreStream.getGuildSelected().selectedGuildId.toString()
            } catch (e: Exception) {
                logger.error("Erro ao obter guild ID", null)
                return@registerCommand CommandResult(
                    "Erro ao obter ID do servidor!", null, false
                )
            }

            val token = try {
                ctx2.getString("token") ?: RestAPI.AppHeadersProvider.INSTANCE.authToken
            } catch (e: Exception) {
                logger.error("Erro ao obter token", null)
                return@registerCommand CommandResult(
                    "Erro ao obter seu token", null, false
                )
            }

            try {
                CloneDialog.show(Utils.appActivity, guildId, token)
            } catch (e: Exception) {
                logger.error("Erro ao abrir dialog", e)
                return@registerCommand CommandResult(
                    "Erro ao abrir dialogo: ${e.message}", null, false
                )
            }

            CommandResult("Abrindo Clone Guild...", null, false)
        }

        patcher.after<WidgetGuildProfileSheet>("onViewCreated", View::class.java, Bundle::class.java) {
            val view = it.args[0] as View
            
            val guildId = try {
                val field = WidgetGuildProfileSheet::class.java.getDeclaredField("guildId")
                field.isAccessible = true
                field.getLong(this)
            } catch (e: Exception) {
                logger.error("Erro ao obter guildId", null)
                StoreStream.getGuildSelected().selectedGuildId
            }

            val token = try {
                RestAPI.AppHeadersProvider.INSTANCE.authToken
            } catch (e: Exception) {
                logger.error("Erro ao obter token", null)
                ""
            }

            try {
                val root = view.parent as? ViewGroup ?: return@after
                val container = root.getChildAt(0) as? LinearLayout ?: return@after
                
                val btnId = View.generateViewId()
                if (container.findViewById<TextView>(btnId) != null) return@after
                
                val btn = TextView(view.context).apply {
                    id = btnId
                    text = "Clone Server"
                    setTextColor(ColorCompat.getThemedColor(context, R.b.white))
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(20, 32, 20, 32)
                    
                    val icon = ContextCompat.getDrawable(context, R.e.ic_copy_24dp)?.mutate()
                    icon?.setTint(ColorCompat.getThemedColor(context, R.b.white))
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                    compoundDrawablePadding = 16
                    
                    background = GradientDrawable().apply {
                        cornerRadius = 10f
                        setColor(ColorCompat.getThemedColor(context, R.b.brand_500))
                    }
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(24, 10, 24, 10)
                    }
                    
                    setOnClickListener {
                        try {
                            CloneDialog.show(Utils.appActivity, guildId.toString(), token)
                        } catch (e: Exception) {
                            logger.error("Erro ao abrir clone dialog", e)
                            Utils.showToast("Erro ao abrir Clone Dialog", true)
                        }
                    }
                }

                container.addView(btn, container.childCount)
            } catch (e: Exception) {
                logger.error("Erro ao adicionar botao no perfil", null)
            }
        }
    }

    private fun clearNotifications(ctx: Context) {
        try {
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(12345)
        } catch (e: Exception) {
            logger.error("Erro ao limpar notificações", null)
        }
    }

    private fun checkPendingProgress(ctx: Context) {
        Utils.threadPool.execute {
            try {
                val savedState = ProgressStateManager.loadProgress(ctx) ?: return@execute
                if (savedState.isComplete) {
                    ProgressStateManager.clearProgress(ctx)
                    return@execute
                }
                
                Utils.mainThread.post {
                    try {
                        android.app.AlertDialog.Builder(Utils.appActivity).apply {
                            setTitle("SvClone - Sessão Anterior")
                            setMessage(
                                "Encontramos uma clonagem incompleta do servidor " +
                                "\"${savedState.serverName.ifEmpty { savedState.sourceGuildId }}\". " +
                                "Deseja continuar de onde parou?"
                            )
                            setPositiveButton("Continuar") { _, _ ->
                                try {
                                    CloneDialog.showWithProgress(Utils.appActivity, savedState)
                                } catch (e: Exception) {
                                    logger.error("Erro ao retomar clonagem", e)
                                    Utils.showToast("Erro ao retomar", true)
                                }
                            }
                            setNegativeButton("Descartar") { _, _ ->
                                ProgressStateManager.clearProgress(ctx)
                            }
                            setCancelable(true)
                            
                            create()
                        }.show()
                    } catch (e: Exception) {
                        logger.error("Erro ao mostrar dialogo de retomada", null)
                    }
                }
            } catch (e: Exception) {
                logger.error("Erro ao verificar progresso pendente", null)
            }
        }
    }

    override fun stop(ctx: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
        clearNotifications(ctx)
    }
}
