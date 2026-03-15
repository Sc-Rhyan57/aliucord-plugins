package com.github.nyxiereal.viewquests

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.view.*
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
    @SerializedName("user_status") val userStatus: QuestUserStatus? = null,
    val rawJson: JSONObject? = null
)

class QuestsPage : SettingsPage() {

    private val logger = Logger("ViewQuests")
    private var allQuests = listOf<Quest>()
    private var sortMode = 0
    private var filterOrbs = false
    private var filterDecor = false
    private var filterInGame = false
    private var filterWatch = false
    private var filterPlay = false

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Quests")
        val ctx = view.context
        showLoadingVideo(ctx)
        Utils.threadPool.execute {
            try {
                val json = QuestApi.getQuests()
                val arr = json.optJSONArray("quests")
                val list = mutableListOf<Quest>()
                if (arr != null) for (i in 0 until arr.length()) {
                    try { list.add(parseQuest(arr.getJSONObject(i))) } catch (_: Exception) {}
                }
                val orbs = QuestApi.getOrbBalance()
                Utils.mainThread.post {
                    allQuests = list
                    setActionBarTitle("Quests  \u25e6 $orbs Orbs")
                    renderQuests(ctx)
                }
            } catch (e: Exception) {
                logger.error("Failed fetching quests", e)
                Utils.mainThread.post { setActionBarTitle("Quests"); renderQuests(ctx) }
            }
        }
    }

    private fun showLoadingVideo(ctx: Context) {
        linearLayout.removeAllViews()
        val container = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(300))
            setBackgroundColor(Color.TRANSPARENT)
        }
        val vv = android.widget.VideoView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(DimenUtils.dpToPx(180), DimenUtils.dpToPx(180)).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(vv)
        linearLayout.addView(container)
        vv.setVideoURI(Uri.parse("https://discord.com/assets/7ba7fcf2c4710bb7.webm"))
        vv.setOnPreparedListener { mp -> mp.isLooping = true; vv.start() }
        vv.setOnErrorListener { _, _, _ -> true }
    }

    private fun renderQuests(ctx: Context) {
        linearLayout.removeAllViews()
        addCollectiblesBtn(ctx)
        addFilterBtn(ctx)
        var list = allQuests.toMutableList()
        if (filterOrbs) list = list.filter { q -> q.config.rewardsConfig.rewards.any { it.type == 4 } }.toMutableList()
        if (filterDecor) list = list.filter { q -> q.config.rewardsConfig.rewards.any { it.type == 3 } }.toMutableList()
        if (filterInGame) list = list.filter { q -> q.config.rewardsConfig.rewards.any { it.type != 4 && it.type != 3 } }.toMutableList()
        if (filterWatch) list = list.filter { q -> q.config.taskConfig.tasks.keys.any { it.contains("WATCH") } }.toMutableList()
        if (filterPlay) list = list.filter { q -> q.config.taskConfig.tasks.keys.any { it.contains("PLAY") || it.contains("STREAM") } }.toMutableList()
        list = when (sortMode) {
            1 -> list.sortedByDescending { parseIsoMs(it.config.startsAt) }.toMutableList()
            2 -> list.sortedBy { parseIsoMs(it.config.expiresAt) }.toMutableList()
            3 -> list.filter { it.userStatus?.enrolledAt != null }.toMutableList()
            else -> list
        }
        if (list.isEmpty()) {
            subText(ctx, "No quests available.", Pad(16, 32, 16, 32)).apply {
                gravity = Gravity.CENTER; linearLayout.addView(this)
            }
        } else {
            list.forEach { addQuestCard(ctx, it) }
        }
    }

    private fun parseIsoMs(iso: String): Long = try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.parse(iso.substringBefore('.'))?.time ?: 0L
    } catch (_: Exception) { 0L }

    private fun addCollectiblesBtn(ctx: Context) {
        TextView(ctx, null, 0, com.lytefast.flexinput.R.i.UiKit_Settings_Item_Icon).apply {
            text = "View Collectibles"
            setTextColor(Color.parseColor("#F2F3F5"))
            setCompoundDrawablesWithIntrinsicBounds(
                Utils.tintToTheme(ctx.getDrawable(com.lytefast.flexinput.R.e.ic_gift_24dp)),
                null, null, null
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2D31"))
                cornerRadius = DimenUtils.dpToPx(10).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(4), DimenUtils.dpToPx(8), DimenUtils.dpToPx(4))
            }
            setOnClickListener { Utils.openPageWithProxy(ctx, CollectiblesPage()) }
            linearLayout.addView(this)
        }
    }

    private fun addFilterBtn(ctx: Context) {
        TextView(ctx).apply {
            text = "Filters"
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2D31"))
                cornerRadius = DimenUtils.dpToPx(10).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(38)).apply {
                setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(2), DimenUtils.dpToPx(8), DimenUtils.dpToPx(6))
            }
            setOnClickListener { showFiltersDialog(ctx) }
            linearLayout.addView(this)
        }
    }

    private fun showFiltersDialog(ctx: Context) {
        val d = android.app.Dialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
            setPadding(DimenUtils.dpToPx(20), DimenUtils.dpToPx(20), DimenUtils.dpToPx(20), DimenUtils.dpToPx(16))
        }

        fun sectionLabel(text: String) = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#72767D"))
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(DimenUtils.dpToPx(4), DimenUtils.dpToPx(12), 0, DimenUtils.dpToPx(6))
        }

        fun groupCard(block: LinearLayout.() -> Unit): LinearLayout {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E1F26"))
                    cornerRadius = DimenUtils.dpToPx(12).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                block()
            }
        }

        fun radioItem(label: String, selected: Boolean, onTap: () -> Unit): LinearLayout {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(14), DimenUtils.dpToPx(16), DimenUtils.dpToPx(14))
                isClickable = true
                isFocusable = true
                addView(TextView(ctx).apply {
                    text = label; setTextColor(Color.parseColor("#F2F3F5")); textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(20), DimenUtils.dpToPx(20))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (selected) Color.parseColor("#5865F2") else Color.TRANSPARENT)
                        setStroke(DimenUtils.dpToPx(2), if (selected) Color.parseColor("#5865F2") else Color.parseColor("#4E5058"))
                    }
                })
                setOnClickListener { onTap() }
            }
        }

        fun checkItem(label: String, checked: Boolean, onTap: (Boolean) -> Unit): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(14), DimenUtils.dpToPx(16), DimenUtils.dpToPx(14))
                isClickable = true
                isFocusable = true
            }
            val lbl = TextView(ctx).apply {
                text = label; setTextColor(Color.parseColor("#F2F3F5")); textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val box = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(20), DimenUtils.dpToPx(20))
                background = GradientDrawable().apply {
                    cornerRadius = DimenUtils.dpToPx(4).toFloat()
                    setColor(if (checked) Color.parseColor("#5865F2") else Color.TRANSPARENT)
                    setStroke(DimenUtils.dpToPx(2), if (checked) Color.parseColor("#5865F2") else Color.parseColor("#4E5058"))
                }
            }
            row.addView(lbl); row.addView(box)
            row.setOnClickListener { onTap(!checked) }
            return row
        }

        var tmpSort = sortMode
        var tmpOrbs = filterOrbs; var tmpDecor = filterDecor; var tmpInGame = filterInGame
        var tmpWatch = filterWatch; var tmpPlay = filterPlay

        fun rebuild() {
            root.removeAllViews()
            root.addView(TextView(ctx).apply {
                text = "Filters"; setTextColor(Color.WHITE); textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, DimenUtils.dpToPx(4))
            })

            root.addView(sectionLabel("Sort by"))
            root.addView(groupCard {
                listOf("Suggestions", "Most recent", "Expiring soon", "Active").forEachIndexed { i, lbl ->
                    addView(radioItem(lbl, tmpSort == i) { tmpSort = i; d.dismiss(); sortMode = tmpSort; renderQuests(ctx) })
                }
            })

            root.addView(sectionLabel("Rewards"))
            root.addView(groupCard {
                addView(checkItem("Orbs", tmpOrbs) { v -> tmpOrbs = v; d.dismiss(); filterOrbs = tmpOrbs; filterDecor = tmpDecor; filterInGame = tmpInGame; filterWatch = tmpWatch; filterPlay = tmpPlay; renderQuests(ctx) })
                addView(checkItem("Avatar decoration", tmpDecor) { v -> tmpDecor = v; d.dismiss(); filterOrbs = tmpOrbs; filterDecor = tmpDecor; filterInGame = tmpInGame; filterWatch = tmpWatch; filterPlay = tmpPlay; renderQuests(ctx) })
                addView(checkItem("In-game rewards", tmpInGame) { v -> tmpInGame = v; d.dismiss(); filterOrbs = tmpOrbs; filterDecor = tmpDecor; filterInGame = tmpInGame; filterWatch = tmpWatch; filterPlay = tmpPlay; renderQuests(ctx) })
            })

            root.addView(sectionLabel("Quest type"))
            root.addView(groupCard {
                addView(checkItem("Watch", tmpWatch) { v -> tmpWatch = v; d.dismiss(); filterOrbs = tmpOrbs; filterDecor = tmpDecor; filterInGame = tmpInGame; filterWatch = tmpWatch; filterPlay = tmpPlay; renderQuests(ctx) })
                addView(checkItem("Play", tmpPlay) { v -> tmpPlay = v; d.dismiss(); filterOrbs = tmpOrbs; filterDecor = tmpDecor; filterInGame = tmpInGame; filterWatch = tmpWatch; filterPlay = tmpPlay; renderQuests(ctx) })
            })

            val btnRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, DimenUtils.dpToPx(16), 0, 0)
            }
            btnRow.addView(TextView(ctx).apply {
                text = "Reset"
                setTextColor(Color.parseColor("#72767D")); textSize = 14f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(48), 1f).apply { setMargins(0, 0, DimenUtils.dpToPx(8), 0) }
                background = GradientDrawable().apply { setColor(Color.parseColor("#1E1F26")); cornerRadius = DimenUtils.dpToPx(24).toFloat() }
                setOnClickListener {
                    sortMode = 0; filterOrbs = false; filterDecor = false; filterInGame = false; filterWatch = false; filterPlay = false
                    d.dismiss(); renderQuests(ctx)
                }
            })
            btnRow.addView(TextView(ctx).apply {
                text = "Done"
                setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 14f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(48), 2f)
                background = GradientDrawable().apply { setColor(Color.parseColor("#5865F2")); cornerRadius = DimenUtils.dpToPx(24).toFloat() }
                setOnClickListener {
                    sortMode = tmpSort; filterOrbs = tmpOrbs; filterDecor = tmpDecor; filterInGame = tmpInGame; filterWatch = tmpWatch; filterPlay = tmpPlay
                    d.dismiss(); renderQuests(ctx)
                }
            })
            root.addView(btnRow)
        }

        rebuild()
        d.setContentView(ScrollView(ctx).apply { addView(root) })
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        d.show()
    }

    private fun parseQuest(q: JSONObject): Quest {
        val id = q.optString("id")
        val cfg = q.getJSONObject("config")
        val msgs = cfg.getJSONObject("messages")
        val tasksObj = cfg.getJSONObject("task_config").getJSONObject("tasks")
        val tasks = mutableMapOf<String, QuestTaskModel>()
        for (key in tasksObj.keys()) {
            val t = tasksObj.getJSONObject(key)
            tasks[key] = QuestTaskModel(t.optString("event_name"), t.optInt("target"), t.optString("title").takeIf { it.isNotEmpty() })
        }
        val rewardsArr = cfg.getJSONObject("rewards_config").getJSONArray("rewards")
        val rewards = mutableListOf<QuestReward>()
        for (i in 0 until rewardsArr.length()) {
            val r = rewardsArr.getJSONObject(i)
            rewards.add(QuestReward(r.optInt("type"), QuestRewardMsg(r.getJSONObject("messages").optString("name_with_article")), r.optInt("orb_quantity")))
        }
        val us = q.optJSONObject("user_status")
        val userStatus = if (us != null) {
            val pMap = mutableMapOf<String, QuestTaskProgress>()
            val pObj = us.optJSONObject("progress")
            if (pObj != null) for (key in pObj.keys()) {
                val p = pObj.getJSONObject(key)
                pMap[key] = QuestTaskProgress(p.optString("event_name"), p.optInt("value"), p.optString("completed_at").takeIf { it.isNotEmpty() && it != "null" })
            }
            QuestUserStatus(
                us.optString("enrolled_at").takeIf { it.isNotEmpty() && it != "null" },
                us.optString("completed_at").takeIf { it.isNotEmpty() && it != "null" },
                us.optString("claimed_at").takeIf { it.isNotEmpty() && it != "null" },
                pMap
            )
        } else null
        return Quest(id, QuestConfig(cfg.optString("starts_at"), cfg.optString("expires_at"),
            QuestMessages(msgs.optString("quest_name"), msgs.optString("game_title"), msgs.optString("game_publisher")),
            QuestTaskConfig(tasks), QuestRewardsConfig(rewards)), userStatus, q)
    }

    private fun addQuestCard(ctx: Context, quest: Quest) {
        val cfg = quest.config
        val us = quest.userStatus
        val taskEntry = cfg.taskConfig.tasks.entries.firstOrNull()
        val taskName = taskEntry?.key ?: ""
        val target = taskEntry?.value?.target ?: 0
        val done = us?.progress?.get(taskName)?.value ?: 0
        val pct = if (target > 0) (done * 100 / target).coerceIn(0, 100) else 0
        val isMobile = taskName in listOf("WATCH_VIDEO_ON_MOBILE", "WATCH_VIDEO", "PLAY_ACTIVITY")
        val isVideo = taskName in listOf("WATCH_VIDEO_ON_MOBILE", "WATCH_VIDEO")
        val isClaimed = us?.claimedAt != null
        val isCompleted = us?.completedAt != null

        val c = card(ctx)

        val topFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(140))
        }

        val bannerImg = com.facebook.drawee.view.SimpleDraweeView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            getBannerUrl(quest)?.let { setImageURI(it) } ?: setBackgroundColor(Color.parseColor("#1E1F26"))
        }

        val bannerGrad = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#CC0E0F13"), Color.TRANSPARENT)
            )
        }

        val ringSize = DimenUtils.dpToPx(56)
        val ringFrame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ringSize + DimenUtils.dpToPx(8), ringSize + DimenUtils.dpToPx(8)).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(DimenUtils.dpToPx(12), 0, 0, -DimenUtils.dpToPx(4))
            }
        }

        val ringIcon = com.facebook.drawee.view.SimpleDraweeView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ringSize, ringSize).apply { gravity = Gravity.CENTER }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E1F26"))
            }
        }
        getRewardIconUrl(quest)?.let { ringIcon.setImageURI(it) }

        val ringColor = when {
            isClaimed -> Color.parseColor("#23A55A")
            isCompleted -> Color.parseColor("#FAA61A")
            else -> Color.parseColor("#5865F2")
        }

        val ringView = object : View(ctx) {
            override fun onDraw(canvas: Canvas) {
                val s = DimenUtils.dpToPx(3).toFloat()
                val r = RectF(s / 2, s / 2, width - s / 2, height - s / 2)
                canvas.drawArc(r, -90f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = s; color = Color.parseColor("#3B3D44")
                })
                if (pct > 0) canvas.drawArc(r, -90f, 360f * pct / 100f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = s; strokeCap = Paint.Cap.ROUND; color = ringColor
                })
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(ringSize + DimenUtils.dpToPx(8), ringSize + DimenUtils.dpToPx(8)).apply { gravity = Gravity.CENTER }
        }

        ringFrame.addView(ringIcon); ringFrame.addView(ringView)

        val expiryTv = TextView(ctx).apply {
            text = "Ends ${getShortExpiry(cfg.expiresAt)}"
            setTextColor(Color.parseColor("#B5BAC1")); textSize = 10f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, DimenUtils.dpToPx(12), DimenUtils.dpToPx(8))
            }
        }

        topFrame.addView(bannerImg); topFrame.addView(bannerGrad); topFrame.addView(ringFrame); topFrame.addView(expiryTv)
        c.addView(topFrame)

        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DimenUtils.dpToPx(14), DimenUtils.dpToPx(10), DimenUtils.dpToPx(14), DimenUtils.dpToPx(4))
        }

        val pubRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pubRow.addView(TextView(ctx).apply {
            text = "${cfg.messages.gamePublisher}"
            setTextColor(Color.parseColor("#23A55A")); textSize = 10f
            setPadding(DimenUtils.dpToPx(6), DimenUtils.dpToPx(2), DimenUtils.dpToPx(6), DimenUtils.dpToPx(2))
            background = GradientDrawable().apply { setColor(Color.parseColor("#23A55A22")); cornerRadius = DimenUtils.dpToPx(4).toFloat() }
        })
        pubRow.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        info.addView(pubRow)

        info.addView(TextView(ctx).apply {
            text = "QUEST: ${cfg.messages.questName.uppercase()}"
            setTextColor(Color.parseColor("#5865F2")); textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
            setPadding(0, DimenUtils.dpToPx(8), 0, DimenUtils.dpToPx(2))
        })

        val rewardLabel = cfg.rewardsConfig.rewards.firstOrNull()?.let { r ->
            if (r.type == 4 && r.orbQuantity > 0) "\u25e6 ${r.orbQuantity} Discord Orbs"
            else r.messages.nameWithArticle.removePrefix("a ").removePrefix("an ").removePrefix("um ").removePrefix("uma ")
        } ?: cfg.messages.questName

        info.addView(TextView(ctx).apply {
            text = rewardLabel; setTextColor(Color.parseColor("#F2F3F5")); textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, DimenUtils.dpToPx(4))
        })

        info.addView(subText(ctx, describeTask(taskName, taskEntry?.value), Pad(0, 2, 0, 8)).apply {
            setTextColor(Color.parseColor("#B5BAC1")); textSize = 12f
        })

        if (target > 0) {
            val progRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, DimenUtils.dpToPx(2), 0, DimenUtils.dpToPx(8))
            }
            val barContainer = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(4), 1f).apply {
                    setMargins(0, 0, DimenUtils.dpToPx(8), 0)
                }
                background = GradientDrawable().apply { setColor(Color.parseColor("#3B3D44")); cornerRadius = DimenUtils.dpToPx(2).toFloat() }
            }
            val barFill = View(ctx).apply {
                background = GradientDrawable().apply { setColor(ringColor); cornerRadius = DimenUtils.dpToPx(2).toFloat() }
                layoutParams = FrameLayout.LayoutParams((0), DimenUtils.dpToPx(4))
            }
            barContainer.addView(barFill)
            barContainer.post {
                val totalW = barContainer.width
                barFill.layoutParams = FrameLayout.LayoutParams((totalW * pct / 100).coerceAtLeast(0), DimenUtils.dpToPx(4))
            }
            progRow.addView(barContainer)
            progRow.addView(TextView(ctx).apply {
                text = "$pct%"
                setTextColor(ringColor); textSize = 10f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            info.addView(progRow)
        }

        c.addView(info)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(4), DimenUtils.dpToPx(12), DimenUtils.dpToPx(12))
        }

        when {
            isClaimed -> {
                makeBtn(ctx, "Reward Received", "#2B2D31", 1f, false).apply { btnRow.addView(this) }
            }
            isCompleted -> {
                makeBtn(ctx, "Claim Reward", "#5865F2", 1f).apply {
                    setOnClickListener { handleClaim(ctx, quest) }; btnRow.addView(this)
                }
            }
            !isMobile -> {
                makeBtn(ctx, "Desktop Only", "#3B3D44", 1f, false).apply { btnRow.addView(this) }
            }
            else -> {
                makeBtn(ctx, "Auto Complete", "#5865F2", if (isVideo) 0.52f else 1f).apply {
                    setOnClickListener { confirmAutoComplete(ctx, quest) }; btnRow.addView(this)
                }
                if (isVideo) {
                    btnRow.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(6), 0) })
                    makeBtn(ctx, "Watch Video", "#2B2D31", 0.48f).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#2B2D31")); cornerRadius = DimenUtils.dpToPx(12).toFloat()
                            setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44"))
                        }
                        setOnClickListener { openVideoPlayer(ctx, quest) }; btnRow.addView(this)
                    }
                }
            }
        }

        btnRow.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(6), 0) })
        btnRow.addView(makeBtn(ctx, "...", "#2B2D31", -1f).apply {
            layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(42), DimenUtils.dpToPx(42))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2D31")); cornerRadius = DimenUtils.dpToPx(12).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44"))
            }
            setOnClickListener { showMoreMenu(ctx, quest) }
        })

        c.addView(btnRow)
        linearLayout.addView(c)
    }

    private fun makeBtn(ctx: Context, label: String, colorHex: String, weight: Float, enabled: Boolean = true): TextView {
        return TextView(ctx).apply {
            text = label; gravity = Gravity.CENTER; textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); isEnabled = enabled; alpha = if (enabled) 1f else 0.5f
            background = GradientDrawable().apply { setColor(Color.parseColor(colorHex)); cornerRadius = DimenUtils.dpToPx(12).toFloat() }
            layoutParams = if (weight > 0) LinearLayout.LayoutParams(0, DimenUtils.dpToPx(42), weight)
                           else LinearLayout.LayoutParams(DimenUtils.dpToPx(42), DimenUtils.dpToPx(42))
        }
    }

    private fun showMoreMenu(ctx: Context, quest: Quest) {
        val d = android.app.Dialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
            setPadding(DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8))
        }
        fun menuItem(label: String, onClick: () -> Unit) {
            root.addView(TextView(ctx).apply {
                text = label; setTextColor(Color.parseColor("#F2F3F5")); textSize = 14f
                setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(14), DimenUtils.dpToPx(16), DimenUtils.dpToPx(14))
                background = android.graphics.drawable.StateListDrawable()
                isClickable = true; isFocusable = true
                setOnClickListener { d.dismiss(); onClick() }
            })
        }
        menuItem("Watch now") {
            val link = quest.rawJson?.optJSONObject("config")?.optJSONObject("cta_config")?.optString("link")?.takeIf { it.isNotEmpty() && it != "null" }
            if (link != null) {
                try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(link)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
            }
        }
        menuItem("Copy name") {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("quest", quest.config.messages.questName))
            showToast(ctx, "Name copied!")
        }
        d.setContentView(root)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.75f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM or Gravity.END)
        }
        d.show()
    }

    private fun showToast(ctx: Context, msg: String, error: Boolean = false) {
        val t = android.widget.Toast(ctx)
        t.view = TextView(ctx).apply {
            text = msg; setTextColor(Color.WHITE); textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(DimenUtils.dpToPx(18), DimenUtils.dpToPx(12), DimenUtils.dpToPx(18), DimenUtils.dpToPx(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (error) "#ED4245" else "#23A55A"))
                cornerRadius = DimenUtils.dpToPx(20).toFloat()
            }
        }
        t.duration = android.widget.Toast.LENGTH_SHORT
        t.show()
    }

    private fun confirmAutoComplete(ctx: Context, quest: Quest) {
        val target = quest.config.taskConfig.tasks.entries.firstOrNull()?.value?.target ?: 0
        val d = android.app.Dialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
            setPadding(DimenUtils.dpToPx(24), DimenUtils.dpToPx(24), DimenUtils.dpToPx(24), DimenUtils.dpToPx(20))
        }
        root.addView(TextView(ctx).apply {
            text = "Auto Complete"; setTextColor(Color.WHITE); textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, 0, 0, DimenUtils.dpToPx(12))
        })
        root.addView(TextView(ctx).apply {
            text = "This will spoof progress to complete: ${quest.config.messages.questName}\n\nEstimated time: ${fmtSecs(target)}\n\nThis may violate Discord ToS. Continue?"
            setTextColor(Color.parseColor("#B5BAC1")); textSize = 13f; setLineSpacing(0f, 1.4f)
            setPadding(0, 0, 0, DimenUtils.dpToPx(20))
        })
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(TextView(ctx).apply {
            text = "Cancel"; setTextColor(Color.parseColor("#72767D")); textSize = 14f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(46), 1f).apply { setMargins(0, 0, DimenUtils.dpToPx(8), 0) }
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E1F26")); cornerRadius = DimenUtils.dpToPx(10).toFloat() }
            setOnClickListener { d.dismiss() }
        })
        btnRow.addView(TextView(ctx).apply {
            text = "Complete"; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 14f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(46), 2f)
            background = GradientDrawable().apply { setColor(Color.parseColor("#5865F2")); cornerRadius = DimenUtils.dpToPx(10).toFloat() }
            setOnClickListener { d.dismiss(); startAutoComplete(ctx, quest) }
        })
        root.addView(btnRow)
        d.setContentView(root)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        d.show()
    }

    private fun startAutoComplete(ctx: Context, quest: Quest) {
        val statusTv = TextView(ctx).apply {
            text = "Starting..."; setTextColor(Color.parseColor("#B5BAC1")); textSize = 12f
            setPadding(DimenUtils.dpToPx(20), DimenUtils.dpToPx(16), DimenUtils.dpToPx(20), DimenUtils.dpToPx(8))
        }
        val barContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(4)).apply {
                setMargins(DimenUtils.dpToPx(20), DimenUtils.dpToPx(4), DimenUtils.dpToPx(20), DimenUtils.dpToPx(16))
            }
            background = GradientDrawable().apply { setColor(Color.parseColor("#3B3D44")); cornerRadius = DimenUtils.dpToPx(2).toFloat() }
        }
        val barFill = View(ctx).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#5865F2")); cornerRadius = DimenUtils.dpToPx(2).toFloat() }
            layoutParams = FrameLayout.LayoutParams(0, DimenUtils.dpToPx(4))
        }
        barContainer.addView(barFill)

        val d = android.app.Dialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
        }
        root.addView(TextView(ctx).apply {
            text = "Completing quest..."
            setTextColor(Color.WHITE); textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(DimenUtils.dpToPx(20), DimenUtils.dpToPx(20), DimenUtils.dpToPx(20), DimenUtils.dpToPx(8))
        })
        root.addView(statusTv); root.addView(barContainer)
        root.addView(TextView(ctx).apply {
            text = "Cancel"; setTextColor(Color.parseColor("#72767D")); textSize = 14f; gravity = Gravity.CENTER
            setPadding(0, DimenUtils.dpToPx(4), 0, DimenUtils.dpToPx(16))
            setOnClickListener { d.dismiss() }
        })
        d.setContentView(root)
        d.setCancelable(false)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        d.show()

        fun upd(msg: String, pct: Int = -1) = Utils.mainThread.post {
            statusTv.text = msg
            if (pct >= 0) {
                barContainer.post {
                    val w = barContainer.width
                    barFill.layoutParams = FrameLayout.LayoutParams((w * pct / 100).coerceAtLeast(0), DimenUtils.dpToPx(4))
                }
            }
        }

        Utils.threadPool.execute {
            try {
                val taskEntry = quest.config.taskConfig.tasks.entries.firstOrNull()
                val taskName = taskEntry?.key ?: return@execute
                val target = taskEntry.value.target.toLong()
                val questId = quest.id
                if (quest.userStatus?.enrolledAt == null) { upd("Enrolling...", 0); QuestApi.enrollQuest(questId); Thread.sleep(800) }
                when (taskName) {
                    "WATCH_VIDEO", "WATCH_VIDEO_ON_MOBILE" -> {
                        var done = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()

                        var enrolledAtMs = quest.userStatus?.enrolledAt?.let { parseIsoMs(it) }
                            ?: (System.currentTimeMillis() - 30_000L)

                        if (quest.userStatus?.enrolledAt == null) {
                            upd("Enrolling...", 0)
                            val enrolledAt = QuestApi.enrollQuest(questId)
                            if (enrolledAt != null) {
                                enrolledAtMs = parseIsoMs(enrolledAt)
                            } else {
                                val status = QuestApi.getQuestStatus(questId)
                                val fromStatus = status.optJSONObject("user_status")
                                    ?.optString("enrolled_at")
                                    ?.takeIf { it.isNotEmpty() && it != "null" }
                                if (fromStatus != null) enrolledAtMs = parseIsoMs(fromStatus)
                            }
                            Thread.sleep(1000)
                        }

                        val speed = 7L
                        val maxFuture = 10L
                        val intervalMs = 1000L

                        while (done < target) {
                            val maxAllowed = (System.currentTimeMillis() - enrolledAtMs) / 1000 + maxFuture
                            val diff = maxAllowed - done
                            val nextTs = done + speed
                            if (diff >= speed) {
                                val sendTs = minOf(target.toDouble(), nextTs.toDouble() + Math.random())
                                val res = QuestApi.sendVideoProgress(questId, sendTs)
                                done = minOf(target, nextTs)
                                val p = if (target > 0) (done * 100 / target).toInt() else 0
                                upd("Video: ${done}s / ${target}s ($p%)", p)
                                if (res.optString("completed_at").isNotEmpty()) break
                            }
                            if (nextTs >= target) break
                            Thread.sleep(intervalMs)
                        }
                        QuestApi.sendVideoProgress(questId, target.toDouble())
                    }
                    "PLAY_ACTIVITY" -> {
                        val channelId = QuestApi.getFirstDmChannelId() ?: throw Exception("No DM channel found. Open a DM in Discord first.")
                        val streamKey = "call:$channelId:1"
                        var done = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
                        while (done < target) {
                            val res = QuestApi.sendHeartbeat(questId, streamKey, false)
                            done = res.optJSONObject("progress")?.optJSONObject("PLAY_ACTIVITY")?.optLong("value", done) ?: done
                            val p = if (target > 0) (done * 100 / target).toInt() else 0
                            upd("Activity: ${done}s / ${target}s (~${(target - done) / 60}min left)", p)
                            Thread.sleep(20_000)
                        }
                        QuestApi.sendHeartbeat(questId, streamKey, true)
                    }
                }
                Thread.sleep(800)
                upd("Claiming reward...", 99)
                val (code, claimJson) = QuestApi.claimReward(questId)
                Utils.mainThread.post {
                    d.dismiss()
                    handleClaimResult(ctx, quest, code, claimJson)
                }
            } catch (e: Exception) {
                Utils.mainThread.post { d.dismiss(); showToast(ctx, "Error: ${e.message}", true) }
            }
        }
    }

    private fun openVideoPlayer(ctx: Context, quest: Quest) {
        val taskName = quest.config.taskConfig.tasks.keys.firstOrNull() ?: "WATCH_VIDEO"
        val target = (quest.config.taskConfig.tasks[taskName]?.target ?: 0).toLong()
        val questId = quest.id

        val videoUrl: String? = quest.rawJson?.let { raw -> QuestApi.extractVideoUrl(questId, raw) }

        val d = android.app.Dialog(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F26"))
        }

        val videoView = android.widget.VideoView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(220))
        }
        root.addView(videoView)

        val statusTv = TextView(ctx).apply {
            text = if (videoUrl != null) "Loading video..." else "Video unavailable. Use Auto Complete."
            setTextColor(Color.parseColor("#B5BAC1")); textSize = 11f
            setPadding(DimenUtils.dpToPx(14), DimenUtils.dpToPx(8), DimenUtils.dpToPx(14), DimenUtils.dpToPx(4))
        }
        root.addView(statusTv)

        val barContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(4)).apply {
                setMargins(DimenUtils.dpToPx(14), DimenUtils.dpToPx(2), DimenUtils.dpToPx(14), DimenUtils.dpToPx(10))
            }
            background = GradientDrawable().apply { setColor(Color.parseColor("#3B3D44")); cornerRadius = DimenUtils.dpToPx(2).toFloat() }
        }
        val barFill = View(ctx).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#5865F2")); cornerRadius = DimenUtils.dpToPx(2).toFloat() }
            layoutParams = FrameLayout.LayoutParams(0, DimenUtils.dpToPx(4))
        }
        val initPct = if (target > 0) ((quest.userStatus?.progress?.get(taskName)?.value ?: 0) * 100 / target).toInt() else 0
        barContainer.addView(barFill)
        barContainer.post { barFill.layoutParams = FrameLayout.LayoutParams((barContainer.width * initPct / 100).coerceAtLeast(0), DimenUtils.dpToPx(4)) }
        root.addView(barContainer)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(4), DimenUtils.dpToPx(12), DimenUtils.dpToPx(12))
        }
        val closeBtn = TextView(ctx).apply {
            text = "Close"; setTextColor(Color.parseColor("#72767D")); textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(42), 1f).apply { setMargins(0, 0, DimenUtils.dpToPx(6), 0) }
            background = GradientDrawable().apply { setColor(Color.parseColor("#2B2D31")); cornerRadius = DimenUtils.dpToPx(10).toFloat(); setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44")) }
        }
        val claimBtn = TextView(ctx).apply {
            text = "Claim Reward"; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(42), 2f)
            background = GradientDrawable().apply { setColor(Color.parseColor("#5865F2")); cornerRadius = DimenUtils.dpToPx(10).toFloat() }
        }
        btnRow.addView(closeBtn); btnRow.addView(claimBtn)
        root.addView(btnRow)

        var spoofActive = false
        var spoofThread: Thread? = null
        var spoofDone = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
        var enrolled = quest.userStatus?.enrolledAt != null

        closeBtn.setOnClickListener { spoofActive = false; spoofThread?.interrupt(); videoView.stopPlayback(); d.dismiss() }
        claimBtn.setOnClickListener { spoofActive = false; spoofThread?.interrupt(); videoView.stopPlayback(); d.dismiss(); handleClaim(ctx, quest) }

        if (videoUrl != null) {
            videoView.setVideoURI(Uri.parse(videoUrl))
            val mc = android.widget.MediaController(ctx)
            mc.setAnchorView(videoView)
            videoView.setMediaController(mc)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.start()
                statusTv.text = "Watching & syncing..."
                spoofActive = true
                spoofThread = Thread {
                    try {
                        var enrolledAtMs = quest.userStatus?.enrolledAt?.let { parseIsoMs(it) }
                            ?: (System.currentTimeMillis() - 30_000L)
                        if (!enrolled) {
                            val ea = QuestApi.enrollQuest(questId)
                            enrolled = true
                            if (ea != null) {
                                enrolledAtMs = parseIsoMs(ea)
                            } else {
                                val st = QuestApi.getQuestStatus(questId)
                                val ea2 = st.optJSONObject("user_status")?.optString("enrolled_at")?.takeIf { it.isNotEmpty() && it != "null" }
                                if (ea2 != null) enrolledAtMs = parseIsoMs(ea2)
                            }
                        }
                        val speed = 7L; val maxFuture = 10L
                        while (spoofActive && spoofDone < target) {
                            Thread.sleep(7_000)
                            if (!spoofActive) break
                            val maxAllowed = (System.currentTimeMillis() - enrolledAtMs) / 1000 + maxFuture
                            val diff = maxAllowed - spoofDone
                            val nextTs = spoofDone + speed
                            if (diff >= speed) {
                                try {
                                    val sendTs = minOf(target.toDouble(), nextTs.toDouble() + Math.random())
                                    val res = QuestApi.sendVideoProgress(questId, sendTs)
                                    spoofDone = minOf(target, nextTs)
                                    val p = if (target > 0) (spoofDone * 100 / target).toInt() else 0
                                    Utils.mainThread.post {
                                        statusTv.text = "${spoofDone}s / ${target}s ($p%)"
                                        barContainer.post { barFill.layoutParams = FrameLayout.LayoutParams((barContainer.width * p / 100).coerceAtLeast(0), DimenUtils.dpToPx(4)) }
                                    }
                                    if (res.optString("completed_at").isNotEmpty() || spoofDone >= target) {
                                        Utils.mainThread.post { statusTv.text = "Completed! Tap Claim Reward."; barContainer.post { barFill.layoutParams = FrameLayout.LayoutParams(barContainer.width, DimenUtils.dpToPx(4)) } }
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
                spoofThread!!.start()
            }
            videoView.setOnErrorListener { _, _, _ -> statusTv.text = "Video unavailable. Use Auto Complete."; false }
        }

        d.setContentView(root)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.94f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        d.show()
    }

    private fun handleClaim(ctx: Context, quest: Quest) {
        Utils.threadPool.execute {
            try {
                val (code, json) = QuestApi.claimReward(quest.id)
                Utils.mainThread.post { handleClaimResult(ctx, quest, code, json) }
            } catch (e: Exception) {
                Utils.mainThread.post { showToast(ctx, "Error: ${e.message}", true) }
            }
        }
    }

    private fun handleClaimResult(ctx: Context, quest: Quest, code: Int, json: JSONObject) {
        when {
            code == 200 -> { showToast(ctx, "Reward claimed!"); renderQuests(ctx) }
            code == 400 && json.optString("captcha_sitekey").isNotEmpty() -> {
                val sitekey = json.optString("captcha_sitekey")
                val rqtoken = json.optString("captcha_rqtoken").takeIf { it.isNotEmpty() }
                val rqdata = json.optString("captcha_rqdata").takeIf { it.isNotEmpty() }
                CaptchaDialog.show(ctx, sitekey, rqdata, object : CaptchaDialog.CaptchaCallback {
                    override fun onSolved(captchaKey: String) {
                        Utils.threadPool.execute {
                            val (c2, _) = QuestApi.claimReward(quest.id, captchaKey, rqtoken)
                            Utils.mainThread.post {
                                if (c2 == 200) { showToast(ctx, "Reward claimed!"); renderQuests(ctx) }
                                else showToast(ctx, "Claim failed (code $c2)", true)
                            }
                        }
                    }
                    override fun onCancel() {}
                })
            }
            else -> showToast(ctx, "Claim failed (code $code)", true)
        }
    }

    private fun getBannerUrl(quest: Quest): String? {
        val cfg = quest.rawJson?.optJSONObject("config") ?: return null
        val assets = cfg.optJSONObject("assets")
        if (assets != null) {
            for (key in listOf("quest_bar_hero", "hero", "logotype", "game_tile", "thumbnail")) {
                val v = assets.optString(key, "").takeIf { it.isNotEmpty() && it != "null" } ?: continue
                if (v.contains(".mp4") || v.contains(".m3u8") || v.contains(".webm")) continue
                return when {
                    v.startsWith("http") -> v
                    v.startsWith("quests/") -> "https://cdn.discordapp.com/$v"
                    else -> "https://cdn.discordapp.com/quests/${quest.id}/$v"
                }
            }
        }
        val appId = cfg.optJSONObject("application")?.optString("id")?.takeIf { it.isNotEmpty() && it != "null" }
        if (appId != null) return "https://cdn.discordapp.com/app-assets/$appId/store/header.jpg"
        return null
    }

    private fun getRewardIconUrl(quest: Quest): String? {
        val cfg = quest.rawJson?.optJSONObject("config") ?: return null
        val assets = cfg.optJSONObject("assets")
        if (assets != null) {
            for (key in listOf("reward_generic_android", "reward", "collectible_preview", "quest_reward", "reward_tile")) {
                val v = assets.optString(key, "").takeIf { it.isNotEmpty() && it != "null" } ?: continue
                return when {
                    v.startsWith("http") -> v
                    else -> "https://cdn.discordapp.com/quests/${quest.id}/$v"
                }
            }
        }
        val skuId = cfg.optJSONObject("rewards_config")?.optJSONArray("rewards")
            ?.optJSONObject(0)?.optString("sku_id")?.takeIf { it.isNotEmpty() && it != "null" }
        if (skuId != null) return "https://cdn.discordapp.com/collectibles-assets/$skuId/icon.png?size=80"
        return null
    }

    private fun getShortExpiry(iso: String): String = try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val ts = sdf.parse(iso.substringBefore('.'))?.time ?: return ""
        java.text.SimpleDateFormat("d/M", java.util.Locale.getDefault()).format(java.util.Date(ts))
    } catch (_: Exception) { "" }

    private fun describeTask(name: String, task: QuestTaskModel?): String {
        val t = task ?: return name
        return when (name) {
            "STREAM_ON_DESKTOP" -> "Stream on Desktop for ${fmtSecs(t.target)}"
            "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2" -> "Play on Desktop for ${fmtSecs(t.target)}"
            "WATCH_VIDEO" -> "Watch video for ${fmtSecs(t.target)}"
            "WATCH_VIDEO_ON_MOBILE" -> "Watch video on mobile for ${fmtSecs(t.target)}"
            "PLAY_ACTIVITY" -> "Play activity for ${fmtSecs(t.target)}"
            "PLAY_ON_PLAYSTATION" -> "Play on PlayStation for ${fmtSecs(t.target)}"
            "PLAY_ON_XBOX" -> "Play on Xbox for ${fmtSecs(t.target)}"
            else -> t.title ?: name
        }
    }

    private fun fmtSecs(secs: Int): String {
        val m = secs / 60
        return if (m < 60) "${m}min" else "${m / 60}h${if (m % 60 > 0) " ${m % 60}min" else ""}"
    }
}
