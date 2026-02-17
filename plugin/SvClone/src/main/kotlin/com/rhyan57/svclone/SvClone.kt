package com.rhyan57.svclone

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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

        Utils.threadPool.execute {
            Thread.sleep(2000)
            checkPendingProgress(context)
        }

        commands.registerCommand(
            "clone-server",
            "[ SVCLONER ] Clones the current server or the inserted server to a target server.",
            listOf(
                Utils.createCommandOption(ApplicationCommandType.STRING, "server_id", "ID do servidor (opcional)", null, false),
                Utils.createCommandOption(ApplicationCommandType.STRING, "token", "Token Discord (opcional)", null, false)
            )
        ) { ctx ->
            val serverId = ctx.getStringOrDefault("server_id", "")
            val token = ctx.getStringOrDefault("token", "")
            CloneDialog.show(ctx.context, serverId, token, settings)
            CommandsAPI.CommandResult("Displaying the Cloning menu...", null, false)
        }

        commands.registerCommand(
            "show-clone-progress",
            "[ SVCLONER ] shows the progress and console of current cloning."
        ) { ctx ->
            val state = ProgressStateManager.loadProgress(ctx.context)
            if (state == null || state.isComplete) {
                CommandsAPI.CommandResult("× Sorry, there is no ongoing process.", null, false)
            } else {
                CloneDialog.showProgressOnly(ctx.context, settings)
                CommandsAPI.CommandResult("displaying progress menu and console...", null, false)
            }
        }

        commands.registerCommand(
            "add-token",
            "[ SVCLONER ] Add token for account rotation.",
            listOf(Utils.createCommandOption(ApplicationCommandType.STRING, "token", "Token Discord", null, true))
        ) { ctx ->
            val token = ctx.getRequiredString("token")
            TokenManager.addToken(settings, token)
            CommandsAPI.CommandResult("✓ Token added successfully!", null, false)
        }

        commands.registerCommand(
            "list-tokens",
            "[ SVCLONER ] List all saved rotation tokens"
        ) { ctx ->
            val tokens = TokenManager.getTokens()
            if (tokens.isEmpty()) {
                CommandsAPI.CommandResult("× You haven't saved any tokens yet. ", null, false)
            } else {
                val message = tokens.mapIndexed { i, t -> "${i + 1}. ${TokenManager.getTokenInfo(t)}" }.joinToString("\n")
                CommandsAPI.CommandResult("> Tokens:\n$message", null, false)
            }
        }

        patchWidgetGuildProfileSheet()
    }

    private fun checkPendingProgress(context: Context) {
        val state = ProgressStateManager.loadProgress(context)
        if (state != null && !state.isComplete) {
            Utils.mainThread.post {
                showRestoreDialog(context, state)
            }
        }
    }

    private fun showRestoreDialog(context: Context, state: ProgressState) {
        val activity = Utils.appActivity ?: return

        AlertDialog.Builder(activity)
            .setTitle("SVCLONER | LOST SESSION")
            .setMessage("We detected ongoing cloning progress on the server '${state.serverName}' which was interrupted.\n\nDo you want to restore your session?")
            .setPositiveButton("Restore session") { dialog, _ ->
                dialog.dismiss()
                CloneDialog.showWithProgress(context, state, settings)
                CloneManager(context, DiscordApiClient(state.token)).execute(state)
            }
            .setNegativeButton("Discard session") { dialog, _ ->
                ProgressStateManager.clearProgress(context)
                dialog.dismiss()
                Utils.showToast("Session cancelled.", false)
            }
            .setCancelable(false)
            .show()
    }

    private fun patchWidgetGuildProfileSheet() {
        try {
            patcher.after<WidgetGuildProfileSheet>(
                "configureTabItems",
                Long::class.javaPrimitiveType!!,
                WidgetGuildProfileSheetViewModel.TabItems::class.java,
                Boolean::class.javaPrimitiveType!!
            ) { param ->
                try {
                    val sheet = param.thisObject as? WidgetGuildProfileSheet ?: return@after
                    val bindingMethod = ReflectUtils.getMethodByArgs(WidgetGuildProfileSheet::class.java, "getBinding")
                    val binding = bindingMethod.invoke(sheet) as? WidgetGuildProfileSheetBinding ?: return@after
                    val rootView = binding.f.rootView as? ViewGroup ?: return@after
                    val actionsCard = rootView.findViewById<CardView>(Utils.getResId("guild_profile_sheet_secondary_actions", "id")) ?: return@after
                    val actionsLayout = actionsCard.getChildAt(0) as? LinearLayout ?: return@after
                    if (actionsLayout.findViewById<View>(profileButtonId) != null) return@after
                    val guildId = param.args[0] as? Long ?: return@after
                    val guild = StoreStream.getGuilds().getGuild(guildId) ?: return@after
                    addCloneButton(actionsLayout, guild)
                } catch (e: Exception) {
                    logger.error("[ SVCLONER ] There was a small problem adding the clone button to the server profile: ", e)
                }
            }
        } catch (e: Exception) {
            logger.error("[ SVCLONER ] Error applying patch: ", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addCloneButton(container: LinearLayout, guild: Guild) {
        val context = container.context
        val button = TextView(context, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
            id = profileButtonId
            text = "Clone Server"
            val pd = DimenUtils.dpToPx(16)
            setPadding(pd, pd, pd, pd)
            typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
            layoutParams = container.getChildAt(0).layoutParams
            
            post {
                val textWidth = paint.measureText(text.toString())
                val animator = ValueAnimator.ofFloat(0f, 360f)
                animator.duration = 3000
                animator.repeatCount = ValueAnimator.INFINITE
                animator.addUpdateListener { animation ->
                    val hue = animation.animatedValue as Float
                    val colors = intArrayOf(
                        Color.HSVToColor(floatArrayOf(hue, 1f, 1f)),
                        Color.HSVToColor(floatArrayOf((hue + 60f) % 360f, 1f, 1f)),
                        Color.HSVToColor(floatArrayOf((hue + 120f) % 360f, 1f, 1f)),
                        Color.HSVToColor(floatArrayOf((hue + 180f) % 360f, 1f, 1f)),
                        Color.HSVToColor(floatArrayOf((hue + 240f) % 360f, 1f, 1f)),
                        Color.HSVToColor(floatArrayOf((hue + 300f) % 360f, 1f, 1f))
                    )
                    val gradient = LinearGradient(
                        0f, 0f, textWidth, 0f,
                        colors,
                        null,
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = gradient
                    invalidate()
                }
                animator.start()
            }
            
            setOnClickListener {
                try {
                    logger.info("[ SVCLONER ] Clone button clicked for guild: ${guild.getId()}")
                    val currentToken = TokenManager.getCurrentToken() ?: ""
                    val guildIdStr = guild.getId().toString()
                    logger.info("[ SVCLONER ] Opening dialog with guildId: $guildIdStr, token: ${if(currentToken.isEmpty()) "empty" else "present"}")
                    
                    Utils.mainThread.post {
                        try {
                            CloneDialog.show(Utils.appActivity ?: context, guildIdStr, currentToken, settings)
                        } catch (e: Exception) {
                            logger.error("[ SVCLONER ] Error calling CloneDialog.show: ", e)
                            Utils.showToast("Error opening clone menu: ${e.message}", true)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("[ SVCLONER ] Error in button onClick: ", e)
                    Utils.showToast("Error: ${e.message}", true)
                }
            }
        }
        container.addView(button)
        logger.info("[ SVCLONER ] Clone button added to profile")
    }

    private fun clearNotifications(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(12345)
        } catch (e: Exception) {
            logger.error("[ SVCLONER ] Error clearing notifications: ", e)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
        patcher.unpatchAll()
        CloneSession.detachUI()
        clearNotifications(context)
    }
}
