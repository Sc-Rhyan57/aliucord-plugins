package com.rhyan57.svclone

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.discord.api.commands.ApplicationCommandType
import com.discord.stores.StoreStream
import com.discord.utilities.rest.RestAPI
import com.discord.widgets.guilds.profile.WidgetGuildProfileSheet

@AliucordPlugin
@Suppress("unused")
class SvClone : Plugin() {

    override fun start(ctx: Context) {
        checkPendingProgress(ctx)

        commands.registerCommand(
            "clone-server",
            "Clona um servidor Discord. Baseado em https://bettercloner.vercel.app",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "server_id",
                    "ID do servidor a clonar (opcional, usa o servidor atual)"
                ),
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "token",
                    "Token Discord (opcional, usa o seu por padrao)"
                )
            )
        ) { ctx2 ->
            val guildId = try {
                ctx2.getString("server_id") ?: StoreStream.getGuildSelected().selectedGuildId.toString()
            } catch (e: Exception) {
                logger.error("Erro ao obter guild ID", e)
                return@registerCommand CommandResult(
                    "Nao foi possivel obter ID do servidor!", null, false
                )
            }

            val token = try {
                ctx2.getString("token") ?: RestAPI.AppHeadersProvider.INSTANCE.authToken
            } catch (e: Exception) {
                logger.error("Erro ao obter token", e)
                return@registerCommand CommandResult(
                    "Nao foi possivel obter seu token. Informe manualmente.", null, false
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
                logger.error("Erro ao obter guildId", e)
                StoreStream.getGuildSelected().selectedGuildId
            }

            val token = try {
                RestAPI.AppHeadersProvider.INSTANCE.authToken
            } catch (e: Exception) {
                logger.error("Erro ao obter token", e)
                ""
            }

            try {
                val root = view.parent as? ViewGroup ?: return@after
                
                val btn = TextView(view.context).apply {
                    text = "Clone Guild"
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 40, 0, 40)
                    
                    background = GradientDrawable().apply {
                        cornerRadius = 16f
                        setColor(Color.parseColor("#5865F2"))
                    }
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(32, 16, 32, 16)
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

                val container = root.getChildAt(0) as? LinearLayout
                container?.addView(btn, container.childCount)
            } catch (e: Exception) {
                logger.error("Erro ao adicionar botao no perfil", e)
            }
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
                        android.app.AlertDialog.Builder(Utils.appActivity)
                            .setTitle("SvClone - Clonagem Pendente")
                            .setMessage(
                                "Encontramos uma clonagem incompleta do servidor " +
                                "\"${savedState.serverName.ifEmpty { savedState.sourceGuildId }}\". " +
                                "Deseja continuar de onde parou?"
                            )
                            .setPositiveButton("Continuar") { _, _ ->
                                try {
                                    CloneDialog.showWithProgress(Utils.appActivity, savedState)
                                } catch (e: Exception) {
                                    logger.error("Erro ao retomar clonagem", e)
                                    Utils.showToast("Erro ao retomar", true)
                                }
                            }
                            .setNegativeButton("Descartar") { _, _ ->
                                ProgressStateManager.clearProgress(ctx)
                            }
                            .setCancelable(true)
                            .show()
                    } catch (e: Exception) {
                        logger.error("Erro ao mostrar dialogo de retomada", e)
                    }
                }
            } catch (e: Exception) {
                logger.error("Erro ao verificar progresso pendente", e)
            }
        }
    }

    override fun stop(ctx: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }
}
