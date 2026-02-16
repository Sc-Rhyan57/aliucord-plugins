package com.rhyan57.svclone

import android.content.Context
import org.json.JSONObject

data class ProgressState(
    val sourceGuildId: String,
    val targetGuildId: String,
    val serverName: String,
    val token: String,
    val cloneRoles: Boolean,
    val cloneChannels: Boolean,
    val cloneEmojis: Boolean,
    val cloneStickers: Boolean,
    val cloneSettings: Boolean,
    val cloneIcon: Boolean,
    val cloneBanner: Boolean,
    val saveMidia: Boolean,
    val rolesCloned: Boolean = false,
    val channelsCloned: Boolean = false,
    val emojisCloned: Boolean = false,
    val stickersCloned: Boolean = false,
    val settingsCloned: Boolean = false,
    val iconCloned: Boolean = false,
    val bannerCloned: Boolean = false,
    val isComplete: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object ProgressStateManager {
    private const val PREFS_NAME = "SvClone_prefs"
    private const val KEY_PROGRESS = "pending_progress"

    fun saveProgress(ctx: Context, state: ProgressState) {
        val json = JSONObject().apply {
            put("sourceGuildId", state.sourceGuildId)
            put("targetGuildId", state.targetGuildId)
            put("serverName", state.serverName)
            put("token", state.token)
            put("cloneRoles", state.cloneRoles)
            put("cloneChannels", state.cloneChannels)
            put("cloneEmojis", state.cloneEmojis)
            put("cloneStickers", state.cloneStickers)
            put("cloneSettings", state.cloneSettings)
            put("cloneIcon", state.cloneIcon)
            put("cloneBanner", state.cloneBanner)
            put("saveMidia", state.saveMidia)
            put("rolesCloned", state.rolesCloned)
            put("channelsCloned", state.channelsCloned)
            put("emojisCloned", state.emojisCloned)
            put("stickersCloned", state.stickersCloned)
            put("settingsCloned", state.settingsCloned)
            put("iconCloned", state.iconCloned)
            put("bannerCloned", state.bannerCloned)
            put("isComplete", state.isComplete)
            put("timestamp", state.timestamp)
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROGRESS, json.toString())
            .apply()
    }

    fun loadProgress(ctx: Context): ProgressState? {
        val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROGRESS, null) ?: return null
        return try {
            val j = JSONObject(raw)
            ProgressState(
                sourceGuildId = j.getString("sourceGuildId"),
                targetGuildId = j.getString("targetGuildId"),
                serverName = j.getString("serverName"),
                token = j.getString("token"),
                cloneRoles = j.getBoolean("cloneRoles"),
                cloneChannels = j.getBoolean("cloneChannels"),
                cloneEmojis = j.getBoolean("cloneEmojis"),
                cloneStickers = j.getBoolean("cloneStickers"),
                cloneSettings = j.getBoolean("cloneSettings"),
                cloneIcon = j.getBoolean("cloneIcon"),
                cloneBanner = j.getBoolean("cloneBanner"),
                saveMidia = j.getBoolean("saveMidia"),
                rolesCloned = j.optBoolean("rolesCloned", false),
                channelsCloned = j.optBoolean("channelsCloned", false),
                emojisCloned = j.optBoolean("emojisCloned", false),
                stickersCloned = j.optBoolean("stickersCloned", false),
                settingsCloned = j.optBoolean("settingsCloned", false),
                iconCloned = j.optBoolean("iconCloned", false),
                bannerCloned = j.optBoolean("bannerCloned", false),
                isComplete = j.optBoolean("isComplete", false),
                timestamp = j.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clearProgress(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PROGRESS)
            .apply()
    }
}
