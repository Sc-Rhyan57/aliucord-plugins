package com.github.nyxiereal.viewquests

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.SerializedName
import org.json.JSONObject

data class QuestTaskModel(
    @SerializedName("event_name") val eventName: String,
    val target: Int,
    val title: String? = null
)

data class QuestRewardMsg(@SerializedName("name_with_article") val nameWithArticle: String)
data class QuestReward(val type: Int, val messages: QuestRewardMsg, @SerializedName("orb_quantity") val orbQuantity: Int = 0)
data class QuestRewardsConfig(val rewards: List<QuestReward>)
data class QuestTaskConfig(val tasks: Map<String, QuestTaskModel>)
data class QuestMessages(
    @SerializedName("quest_name") val questName: String,
    @SerializedName("game_title") val gameTitle: String,
    @SerializedName("game_publisher") val gamePublisher: String
)
data class QuestConfig(
    @SerializedName("starts_at") val startsAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    val messages: QuestMessages,
    @SerializedName("task_config") val taskConfig: QuestTaskConfig,
    @SerializedName("rewards_config") val rewardsConfig: QuestRewardsConfig
)
data class QuestTaskProgress(
    @SerializedName("event_name") val eventName: String,
    val value: Int,
    @SerializedName("completed_at") val completedAt: String? = null
)
data class QuestUserStatus(
    @SerializedName("enrolled_at") val enrolledAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("claimed_at") val claimedAt: String? = null,
    val progress: Map<String, QuestTaskProgress>? = null
)
data class Quest(
    val id: String,
    val config: QuestConfig,
    @SerializedName("user_status") val userStatus: QuestUserStatus? = null
)
data class QuestsResponse(val quests: List<Quest>)

class QuestsPage : SettingsPage() {

    private val logger = Logger("ViewQuests")

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Loading Quests...")
        val ctx = view.context

        addCollectiblesBtn(ctx)

        Utils.threadPool.execute {
            try {
                val json = QuestApi.getQuests()
                val arr = json.optJSONArray("quests")
                val questList = mutableListOf<Quest>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        try {
                            val q = arr.getJSONObject(i)
                            questList.add(parseQuest(q))
                        } catch (_: Exception) {}
                    }
                }
                val orbs = QuestApi.getOrbBalance()
                Utils.mainThread.post {
                    setActionBarTitle("Quests  ⬡ $orbs Orbs")
                    if (questList.isEmpty()) {
                        addEmptyView(ctx)
                    } else {
                        questList.forEach { addQuestCard(ctx, it) }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed fetching quests", e)
                Utils.mainThread.post {
                    setActionBarTitle("Quests")
                    addErrorView(ctx, e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun parseQuest(q: JSONObject): Quest {
        val id = q.optString("id")
        val cfg = q.getJSONObject("config")
        val msgs = cfg.getJSONObject("messages")
        val taskCfgObj = cfg.getJSONObject("task_config")
        val tasksObj = taskCfgObj.getJSONObject("tasks")
        val tasks = mutableMapOf<String, QuestTaskModel>()
        for (key in tasksObj.keys()) {
            val t = tasksObj.getJSONObject(key)
            tasks[key] = QuestTaskModel(
                eventName = t.optString("event_name"),
                target = t.optInt("target"),
                title = t.optString("title").takeIf { it.isNotEmpty() }
            )
        }
        val rewardsCfg = cfg.getJSONObject("rewards_config")
        val rewardsArr = rewardsCfg.getJSONArray("rewards")
        val rewards = mutableListOf<QuestReward>()
        for (i in 0 until rewardsArr.length()) {
            val r = rewardsArr.getJSONObject(i)
            val rMsgs = r.getJSONObject("messages")
            rewards.add(QuestReward(
                type = r.optInt("type"),
                messages = QuestRewardMsg(rMsgs.optString("name_with_article")),
                orbQuantity = r.optInt("orb_quantity")
            ))
        }
        val us = q.optJSONObject("user_status")
        val userStatus = if (us != null) {
            val progressMap = mutableMapOf<String, QuestTaskProgress>()
            val progObj = us.optJSONObject("progress")
            if (progObj != null) {
                for (key in progObj.keys()) {
                    val p = progObj.getJSONObject(key)
                    progressMap[key] = QuestTaskProgress(
                        eventName = p.optString("event_name"),
                        value = p.optInt("value"),
                        completedAt = p.optString("completed_at").takeIf { it.isNotEmpty() && it != "null" }
                    )
                }
            }
            QuestUserStatus(
                enrolledAt = us.optString("enrolled_at").takeIf { it.isNotEmpty() && it != "null" },
                completedAt = us.optString("completed_at").takeIf { it.isNotEmpty() && it != "null" },
                claimedAt = us.optString("claimed_at").takeIf { it.isNotEmpty() && it != "null" },
                progress = progressMap
            )
        } else null
        return Quest(
            id = id,
            config = QuestConfig(
                startsAt = cfg.optString("starts_at"),
                expiresAt = cfg.optString("expires_at"),
                messages = QuestMessages(msgs.optString("quest_name"), msgs.optString("game_title"), msgs.optString("game_publisher")),
                taskConfig = QuestTaskConfig(tasks),
                rewardsConfig = QuestRewardsConfig(rewards)
            ),
            userStatus = userStatus
        )
    }

    private fun addCollectiblesBtn(ctx: Context) {
        headerText(ctx, "📦 View Collectibles", Pad(16, 8, 16, 4)).apply {
            setTextColor(Color.parseColor("#5865F2"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F220"))
                cornerRadius = DimenUtils.dpToPx(8).toFloat()
            }
            setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(10), DimenUtils.dpToPx(16), DimenUtils.dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(4), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8)) }
            setOnClickListener { Utils.openPageWithProxy(ctx, CollectiblesPage()) }
            linearLayout.addView(this)
        }
    }

    private fun addEmptyView(ctx: Context) {
        subText(ctx, "No quests available right now.", Pad(16, 32, 16, 32)).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addErrorView(ctx: Context, msg: String) {
        subText(ctx, "Failed to load quests:\n$msg", Pad(16, 32, 16, 32), Color.parseColor("#ED4245")).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addQuestCard(ctx: Context, quest: Quest) {
        val cfg = quest.config
        val us = quest.userStatus
        val c = card(ctx)

        headerText(ctx, cfg.messages.questName, Pad(16, 14, 16, 4)).apply { c.addView(this) }

        subText(ctx, "${cfg.messages.gameTitle} · ${cfg.messages.gamePublisher}", Pad(16, 2, 16, 8)).apply {
            setTextColor(Color.parseColor("#72767D"))
            c.addView(this)
        }

        divider(ctx).apply { c.addView(this) }

        cfg.taskConfig.tasks.entries.forEach { (name, task) ->
            val progress = us?.progress?.get(name)
            val done = progress?.value ?: 0
            val total = task.target
            val desc = describeTask(name, task)
            val pct = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0

            subText(ctx, "📋 $desc", Pad(16, 8, 16, 4)).apply { c.addView(this) }

            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(4), DimenUtils.dpToPx(16), DimenUtils.dpToPx(4))
                val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = pct
                    layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(6), 1f).apply {
                        setMargins(0, DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), 0)
                    }
                }
                val label = subText(ctx, "${fmtSecs(done)} / ${fmtSecs(total)}", Pad(0, 0, 0, 0)).apply {
                    textSize = 10f
                    setTextColor(Color.parseColor("#72767D"))
                }
                addView(bar); addView(label)
                c.addView(this)
            }
        }

        cfg.rewardsConfig.rewards.forEach { reward ->
            val rName = reward.messages.nameWithArticle.removePrefix("a ").removePrefix("an ")
            val rLabel = if (reward.type == 4 && reward.orbQuantity > 0) "⬡ ${reward.orbQuantity} Orbs" else "🎁 $rName"
            labelText(ctx, rLabel, Pad(16, 6, 16, 4)).apply {
                setTextColor(Color.parseColor("#B675F0"))
                c.addView(this)
            }
        }

        val statusColor = when {
            us?.claimedAt != null -> Color.parseColor("#23A55A")
            us?.completedAt != null -> Color.parseColor("#FAA61A")
            us?.enrolledAt != null -> Color.parseColor("#5865F2")
            else -> Color.parseColor("#72767D")
        }
        val statusLabel = when {
            us?.claimedAt != null -> "✅ Reward Claimed"
            us?.completedAt != null -> "⚡ Completed – Ready to Claim"
            us?.enrolledAt != null -> "🎮 In Progress"
            else -> "⭕ Not Enrolled"
        }
        subText(ctx, statusLabel, Pad(16, 6, 16, 6), statusColor).apply { c.addView(this) }

        subText(ctx, "Expires: ${fmtDate(ctx, cfg.expiresAt)}", Pad(16, 2, 16, 10)).apply {
            setTextColor(Color.parseColor("#72767D"))
            textSize = 10f
            c.addView(this)
        }

        addActionButtons(ctx, quest, c)

        linearLayout.addView(c)
    }

    private fun addActionButtons(ctx: Context, quest: Quest, container: LinearLayout) {
        val us = quest.userStatus
        val task = quest.config.taskConfig.tasks.entries.firstOrNull()
        val taskName = task?.key ?: ""
        val isMobileTask = taskName in listOf("WATCH_VIDEO_ON_MOBILE", "WATCH_VIDEO", "PLAY_ACTIVITY")
        val isVideoTask = taskName in listOf("WATCH_VIDEO_ON_MOBILE", "WATCH_VIDEO")

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(4), DimenUtils.dpToPx(12), DimenUtils.dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (us?.claimedAt == null && us?.completedAt != null) {
            makeBtn(ctx, "Claim Reward", "#23A55A", weight = 1f).apply {
                setOnClickListener { handleClaim(ctx, quest) }
                row.addView(this)
            }
        } else if (us?.claimedAt == null && isMobileTask) {
            makeBtn(ctx, "Auto Complete", "#5865F2", weight = if (isVideoTask) 0.5f else 1f).apply {
                setOnClickListener { confirmAutoComplete(ctx, quest) }
                row.addView(this)
            }
            if (isVideoTask) {
                makeBtn(ctx, "Watch Video", "#2D2F36", weight = 0.5f).apply {
                    setOnClickListener { openVideoPlayer(ctx, quest) }
                    row.addView(this)
                }
            }
        } else if (us?.claimedAt == null && !isMobileTask) {
            makeBtn(ctx, "Desktop Only", "#72767D", weight = 1f, enabled = false).apply {
                row.addView(this)
            }
        }

        if (row.childCount > 0) container.addView(row)
    }

    private fun makeBtn(ctx: Context, label: String, colorHex: String, weight: Float = 1f, enabled: Boolean = true): TextView {
        return TextView(ctx).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            isEnabled = enabled
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = DimenUtils.dpToPx(8).toFloat()
                if (!enabled) alpha = 120
            }
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(38), weight).apply {
                setMargins(DimenUtils.dpToPx(4), 0, DimenUtils.dpToPx(4), 0)
            }
            setPadding(0, 0, 0, 0)
        }
    }

    private fun confirmAutoComplete(ctx: Context, quest: Quest) {
        AlertDialog.Builder(ctx)
            .setTitle("Auto Complete: ${quest.config.messages.questName}")
            .setMessage("This will automatically complete the quest by spoofing progress.\n\nThis may violate Discord ToS. Proceed?")
            .setPositiveButton("Complete") { _, _ -> startAutoComplete(ctx, quest) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startAutoComplete(ctx: Context, quest: Quest) {
        val progressDialog = AlertDialog.Builder(ctx)
            .setTitle("Completing quest...")
            .setMessage("Starting...")
            .setCancelable(false)
            .setNegativeButton("Cancel", null)
            .create()
        progressDialog.show()

        Utils.threadPool.execute {
            try {
                val taskEntry = quest.config.taskConfig.tasks.entries.firstOrNull()
                val taskName = taskEntry?.key ?: return@execute
                val target = taskEntry.value.target.toLong()
                val questId = quest.id

                fun upd(msg: String) = Utils.mainThread.post {
                    progressDialog.setMessage(msg)
                }

                if (quest.userStatus?.enrolledAt == null) {
                    upd("Enrolling in quest...")
                    QuestApi.enrollQuest(questId)
                    Thread.sleep(1000)
                }

                when (taskName) {
                    "WATCH_VIDEO", "WATCH_VIDEO_ON_MOBILE" -> {
                        val speed = 7L
                        val interval = 1000L
                        var done = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
                        val enrollMs = System.currentTimeMillis() - 30_000L
                        upd("Watching video: 0s / ${target}s")
                        while (done < target) {
                            val maxAllowed = (System.currentTimeMillis() - enrollMs) / 1000 + 10
                            if (maxAllowed - done >= speed) {
                                val nextTs = (done + speed).coerceAtMost(target)
                                val res = QuestApi.sendVideoProgress(questId, nextTs.toDouble())
                                done = nextTs
                                upd("Video: ${done}s / ${target}s")
                                if (res.optString("completed_at").isNotEmpty()) break
                            }
                            Thread.sleep(interval)
                        }
                        QuestApi.sendVideoProgress(questId, target.toDouble())
                    }
                    "PLAY_ACTIVITY" -> {
                        val channelId = QuestApi.getFirstDmChannelId() ?: throw Exception("No DM channel found. Open a DM in Discord first.")
                        val streamKey = "call:$channelId:1"
                        var done = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
                        upd("Spoofing activity: ${done}s / ${target}s")
                        while (done < target) {
                            val res = QuestApi.sendHeartbeat(questId, streamKey, false)
                            val newVal = res.optJSONObject("progress")?.optJSONObject("PLAY_ACTIVITY")?.optLong("value", done) ?: done
                            done = newVal
                            upd("Activity: ${done}s / ${target}s (~${((target - done) / 60)} min left)")
                            Thread.sleep(20_000)
                        }
                        QuestApi.sendHeartbeat(questId, streamKey, true)
                    }
                }

                Thread.sleep(1000)
                upd("Attempting to claim reward...")
                val (code, claimJson) = QuestApi.claimReward(questId)
                Utils.mainThread.post {
                    progressDialog.dismiss()
                    if (code == 200) {
                        Utils.showToast("✅ Reward claimed!", false)
                        Utils.openPageWithProxy(ctx, QuestsPage())
                    } else if (code == 400 && claimJson.optString("captcha_sitekey").isNotEmpty()) {
                        val sitekey = claimJson.optString("captcha_sitekey")
                        val rqtoken = claimJson.optString("captcha_rqtoken").takeIf { it.isNotEmpty() }
                        val rqdata = claimJson.optString("captcha_rqdata").takeIf { it.isNotEmpty() }
                        CaptchaDialog.show(ctx, sitekey, rqdata, object : CaptchaDialog.CaptchaCallback {
                            override fun onSolved(captchaKey: String) {
                                Utils.threadPool.execute {
                                    val (code2, _) = QuestApi.claimReward(questId, captchaKey, rqtoken)
                                    Utils.mainThread.post {
                                        if (code2 == 200) {
                                            Utils.showToast("✅ Reward claimed!", false)
                                            Utils.openPageWithProxy(ctx, QuestsPage())
                                        } else {
                                            Utils.showToast("⚠️ Claim failed (code $code2)", true)
                                        }
                                    }
                                }
                            }
                            override fun onCancel() {}
                        })
                    } else {
                        Utils.showToast("Quest completed! Claim reward manually in Discord.", false)
                        Utils.openPageWithProxy(ctx, QuestsPage())
                    }
                }
            } catch (e: Exception) {
                Utils.mainThread.post {
                    progressDialog.dismiss()
                    Utils.showToast("Error: ${e.message}", true)
                }
            }
        }
    }

    private fun openVideoPlayer(ctx: Context, quest: Quest) {
        val taskEntry = quest.config.taskConfig.tasks.entries.firstOrNull()
        val taskName = taskEntry?.key ?: "WATCH_VIDEO"
        val target = (taskEntry?.value?.target ?: 0).toLong()
        val questId = quest.id

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F26"))
        }

        val videoView = android.widget.VideoView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(220)
            )
        }

        val statusText = subText(ctx, "Loading...", Pad(16, 8, 16, 4)).apply {
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 11f
        }

        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = ((quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong().let {
                if (target > 0) (it * 100 / target).toInt() else 0
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(6)
            ).apply { setMargins(DimenUtils.dpToPx(16), DimenUtils.dpToPx(4), DimenUtils.dpToPx(16), DimenUtils.dpToPx(8)) }
        }

        container.addView(videoView)
        container.addView(statusText)
        container.addView(progressBar)

        var spoofThread: Thread? = null
        var spoofSeconds = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
        var spoofActive = false
        var enrolled = quest.userStatus?.enrolledAt != null
        var dialog: AlertDialog? = null

        val videoUrl = getQuestVideoUrl(quest)

        if (videoUrl != null) {
            videoView.setVideoURI(Uri.parse(videoUrl))
            val mediaController = android.widget.MediaController(ctx)
            mediaController.setAnchorView(videoView)
            videoView.setMediaController(mediaController)

            videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.start()
                statusText.text = "▶ Watching & syncing progress..."
                spoofActive = true

                spoofThread = Thread {
                    try {
                        if (!enrolled) {
                            QuestApi.enrollQuest(questId)
                            enrolled = true
                        }
                        val enrollMs = System.currentTimeMillis() - 10_000L

                        while (spoofActive && spoofSeconds < target) {
                            Thread.sleep(7_000)
                            if (!spoofActive) break

                            val maxAllowed = (System.currentTimeMillis() - enrollMs) / 1000 + 10
                            val next = (spoofSeconds + 7).coerceAtMost(target).coerceAtMost(maxAllowed)

                            try {
                                val res = QuestApi.sendVideoProgress(questId, next.toDouble())
                                spoofSeconds = next
                                val pct = if (target > 0) (spoofSeconds * 100 / target).toInt() else 0
                                val completed = res.optString("completed_at").isNotEmpty()
                                Utils.mainThread.post {
                                    progressBar.progress = pct
                                    statusText.text = "▶ ${spoofSeconds}s / ${target}s  ($pct%)"
                                }
                                if (completed || spoofSeconds >= target) {
                                    Utils.mainThread.post {
                                        statusText.text = "✅ Completed! Tap Claim Reward."
                                        progressBar.progress = 100
                                    }
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
                spoofThread!!.start()
            }

            videoView.setOnErrorListener { _, _, _ ->
                statusText.text = "Video unavailable. Use Auto Complete instead."
                false
            }
        } else {
            statusText.text = "No video URL found. Use Auto Complete."
        }

        dialog = AlertDialog.Builder(ctx)
            .setTitle("▶ ${quest.config.messages.questName}")
            .setView(container)
            .setNegativeButton("Close") { d, _ ->
                spoofActive = false
                spoofThread?.interrupt()
                videoView.stopPlayback()
                d.dismiss()
            }
            .setPositiveButton("Claim Reward") { d, _ ->
                spoofActive = false
                spoofThread?.interrupt()
                videoView.stopPlayback()
                d.dismiss()
                handleClaim(ctx, quest)
            }
            .create()

        dialog.show()
    }

    private fun getQuestVideoUrl(quest: Quest): String? {
        val appId = try {
            val json = QuestApi.getQuests()
            val arr = json.optJSONArray("quests") ?: return null
            for (i in 0 until arr.length()) {
                val q = arr.getJSONObject(i)
                if (q.optString("id") == quest.id) {
                    val cfg = q.optJSONObject("config")
                    val assets = cfg?.optJSONObject("assets")
                    if (assets != null) {
                        for (key in assets.keys()) {
                            val v = assets.optString(key)
                            if (v.endsWith(".mp4") || v.contains("video")) return v
                        }
                    }
                    return cfg?.optJSONObject("application")?.optString("id")
                }
            }
            null
        } catch (_: Exception) { null }
        return if (appId != null) "https://cdn.discordapp.com/app-assets/$appId/quests/video.mp4" else null
    }

    private fun handleClaim(ctx: Context, quest: Quest) {
        Utils.threadPool.execute {
            try {
                val (code, json) = QuestApi.claimReward(quest.id)
                Utils.mainThread.post {
                    when {
                        code == 200 -> {
                            Utils.showToast("✅ Reward claimed!", false)
                            Utils.openPageWithProxy(ctx, QuestsPage())
                        }
                        code == 400 && json.optString("captcha_sitekey").isNotEmpty() -> {
                            val sitekey = json.optString("captcha_sitekey")
                            val rqtoken = json.optString("captcha_rqtoken").takeIf { it.isNotEmpty() }
                            val rqdata = json.optString("captcha_rqdata").takeIf { it.isNotEmpty() }
                            CaptchaDialog.show(ctx, sitekey, rqdata, object : CaptchaDialog.CaptchaCallback {
                                override fun onSolved(captchaKey: String) {
                                    Utils.threadPool.execute {
                                        val (code2, _) = QuestApi.claimReward(quest.id, captchaKey, rqtoken)
                                        Utils.mainThread.post {
                                            if (code2 == 200) {
                                                Utils.showToast("✅ Reward claimed!", false)
                                                Utils.openPageWithProxy(ctx, QuestsPage())
                                            } else {
                                                Utils.showToast("⚠️ Claim failed (code $code2)", true)
                                            }
                                        }
                                    }
                                }
                                override fun onCancel() {}
                            })
                        }
                        else -> Utils.showToast("Failed to claim (code $code)", true)
                    }
                }
            } catch (e: Exception) {
                Utils.mainThread.post { Utils.showToast("Error: ${e.message}", true) }
            }
        }
    }

    private fun describeTask(name: String, task: QuestTaskModel): String = when (name) {
        "STREAM_ON_DESKTOP" -> "Stream on Desktop for ${fmtSecs(task.target)}"
        "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2" -> "Play on Desktop for ${fmtSecs(task.target)}"
        "PLAY_ON_XBOX" -> "Play on Xbox for ${fmtSecs(task.target)}"
        "PLAY_ON_PLAYSTATION" -> "Play on PlayStation for ${fmtSecs(task.target)}"
        "WATCH_VIDEO" -> "Watch video for ${fmtSecs(task.target)}"
        "WATCH_VIDEO_ON_MOBILE" -> "Watch video on mobile for ${fmtSecs(task.target)}"
        "PLAY_ACTIVITY" -> "Play activity for ${fmtSecs(task.target)}"
        else -> task.title ?: name
    }

    private fun fmtSecs(secs: Int): String {
        val m = secs / 60
        return if (m < 60) "${m}m" else "${m / 60}h${if (m % 60 > 0) " ${m % 60}m" else ""}"
    }
}
