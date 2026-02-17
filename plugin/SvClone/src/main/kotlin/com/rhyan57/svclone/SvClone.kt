package com.rhyan57.svclone

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
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
                return@registerCommand CommandResult(
                    "Nao foi possivel obter ID do servidor!", null, false
                )
            }

            val token = try {
                ctx2.getString("token") ?: RestAPI.AppHeadersProvider.INSTANCE.authToken
            } catch (e: Exception) {
                return@registerCommand CommandResult(
                    "Nao foi possivel obter seu token. Informe manualmente.", null, false
                )
            }

            try {
                CloneDialog.show(Utils.appActivity, guildId, token)
            } catch (e: Exception) {
                return@registerCommand CommandResult(
                    "Erro ao abrir dialogo: ${e.message}", null, false
                )
            }

            CommandResult("Abrindo Clone Guild...", null, false)
        }

        patcher.patch(
            WidgetGuildProfileSheet::class.java.getDeclaredMethod(
                "onViewCreated",
                View::class.java,
                Bundle::class.java
            ),
            Hook { param ->
                val sheet = param.thisObject as WidgetGuildProfileSheet
                val rootView = param.args[0] as View

                val guildId = try {
                    val field = WidgetGuildProfileSheet::class.java.getDeclaredField("guildId")
                    field.isAccessible = true
                    field.getLong(sheet)
                } catch (e: Exception) {
                    StoreStream.getGuildSelected().selectedGuildId
                }

                val token = try {
                    RestAPI.AppHeadersProvider.INSTANCE.authToken
                } catch (e: Exception) {
                    ""
                }

                val btnContainer = LinearLayout(rootView.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 8, 32, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val btn = TextView(rootView.context).apply {
                    text = "Clone Guild"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#5865F2"))
                    setPadding(32, 24, 32, 24)
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 8, 0, 4) }
                    setOnClickListener {
                        try {
                            CloneDialog.show(Utils.appActivity, guildId.toString(), token)
                        } catch (e: Exception) {
                            Utils.showToast("Erro ao abrir Clone Dialog: ${e.message}", true)
                        }
                    }
                }

                val notice = TextView(rootView.context).apply {
                    text = "bettercloner.vercel.app"
                    setTextColor(Color.parseColor("#5865F2"))
                    textSize = 10f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 8) }
                }

                btnContainer.addView(btn)
                btnContainer.addView(notice)

                try {
                    val parent = rootView.parent as? LinearLayout ?: rootView as? LinearLayout
                    parent?.addView(btnContainer, parent.childCount)
                } catch (e: Exception) {
                    logger.error("Erro ao adicionar botao ao perfil", e)
                }
            }
        )
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
                                    Utils.showToast("Erro ao retomar: ${e.message}", true)
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
