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
                    "ID do servidor a clonar (obrigatorio)"
                ),
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "token",
                    "Token Discord (opcional, usa o seu por padrao)"
                )
            )
        ) { ctx2 ->
            val guildId = ctx2.getRequiredString("server_id")
            val token = ctx2.getString("token")
                ?: StoreStream.getAuthentication().authTokens?.token
                ?: return@registerCommand CommandResult(
                    "Nao foi possivel obter seu token. Informe manualmente.", null, false
                )

            Utils.threadPool.execute {
                CloneDialog.show(ctx, guildId, token)
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

                val token = StoreStream.getAuthentication().authTokens?.token ?: ""

                val btnContainer = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 8, 32, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val btn = TextView(ctx).apply {
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
                        CloneDialog.show(ctx, guildId.toString(), token)
                    }
                }

                val notice = TextView(ctx).apply {
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

                val parent = rootView.parent as? LinearLayout ?: return@Hook
                parent.addView(btnContainer, parent.childCount)
            }
        )
    }

    private fun checkPendingProgress(ctx: Context) {
        val savedState = ProgressStateManager.loadProgress(ctx) ?: return
        if (savedState.isComplete) {
            ProgressStateManager.clearProgress(ctx)
            return
        }
        Utils.threadPool.execute {
            android.app.AlertDialog.Builder(ctx)
                .setTitle("SvClone - Clonagem Pendente")
                .setMessage(
                    "Encontramos uma clonagem incompleta do servidor " +
                    "\"${savedState.serverName.ifEmpty { savedState.sourceGuildId }}\". " +
                    "Deseja continuar de onde parou?"
                )
                .setPositiveButton("Continuar") { _, _ ->
                    CloneDialog.showWithProgress(ctx, savedState)
                }
                .setNegativeButton("Descartar") { _, _ ->
                    ProgressStateManager.clearProgress(ctx)
                }
                .create()
                .show()
        }
    }

    override fun stop(ctx: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }
}
