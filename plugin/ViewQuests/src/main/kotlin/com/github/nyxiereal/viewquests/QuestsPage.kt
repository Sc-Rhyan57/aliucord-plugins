package com.github.nyxiereal.viewquests

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
        setActionBarTitle("Carregando Quests...")
        val ctx = view.context
        addCollectiblesBtn(ctx)
        Utils.threadPool.execute {
            try {
                val json = QuestApi.getQuests()
                val arr = json.optJSONArray("quests")
                val questList = mutableListOf<Quest>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        try { questList.add(parseQuest(arr.getJSONObject(i))) } catch (_: Exception) {}
                    }
                }
                val orbs = QuestApi.getOrbBalance()
                Utils.mainThread.post {
                    allQuests = questList
                    setActionBarTitle("Quests  ⬡ $orbs Orbs")
                    addFilterBtn(ctx)
                    renderQuests(ctx)
                }
            } catch (e: Exception) {
                logger.error("Failed fetching quests", e)
                Utils.mainThread.post {
                    setActionBarTitle("Quests")
                    addError(ctx, e.message ?: "Erro desconhecido")
                }
            }
        }
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
        if (list.isEmpty()) addEmpty(ctx)
        else list.forEach { addQuestCard(ctx, it) }
    }

    private fun parseIsoMs(iso: String): Long = try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        sdf.parse(iso.substringBefore('.'))?.time ?: 0L
    } catch (_: Exception) { 0L }

    private fun addFilterBtn(ctx: Context) {
        if (linearLayout.findViewWithTag<View>("filter_btn_tag") != null) return
        val btn = TextView(ctx).apply {
            tag = "filter_btn_tag"
            text = "⚙ Filtros"
            setTextColor(Color.parseColor("#5865F2"))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F215"))
                cornerRadius = DimenUtils.dpToPx(10).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#5865F240"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(38)).apply {
                setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(2), DimenUtils.dpToPx(8), DimenUtils.dpToPx(6))
            }
            setPadding(0, 0, 0, 0)
            setOnClickListener { showFiltersDialog(ctx) }
        }
        linearLayout.addView(btn)
    }

    private fun showFiltersDialog(ctx: Context) {
        val dialog = AlertDialog.Builder(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2B2D31"))
            setPadding(DimenUtils.dpToPx(20), DimenUtils.dpToPx(20), DimenUtils.dpToPx(20), DimenUtils.dpToPx(8))
        }

        fun sectionLabel(text: String) = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#72767D"))
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, DimenUtils.dpToPx(12), 0, DimenUtils.dpToPx(6))
        }

        fun optionCard(vararg items: Pair<String, () -> Boolean>): LinearLayout {
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E1F26"))
                    cornerRadius = DimenUtils.dpToPx(12).toFloat()
                }
                setPadding(DimenUtils.dpToPx(4), DimenUtils.dpToPx(4), DimenUtils.dpToPx(4), DimenUtils.dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, DimenUtils.dpToPx(4))
                }
            }
        }

        fun radioRow(label: String, selected: Boolean, onClick: () -> Unit): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(DimenUtils.dpToPx(14), DimenUtils.dpToPx(14), DimenUtils.dpToPx(14), DimenUtils.dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val lbl = TextView(ctx).apply {
                text = label; setTextColor(Color.parseColor("#F2F3F5")); textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val dot = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(20), DimenUtils.dpToPx(20))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (selected) Color.parseColor("#5865F2") else Color.TRANSPARENT)
                    setStroke(DimenUtils.dpToPx(2), if (selected) Color.parseColor("#5865F2") else Color.parseColor("#4E5058"))
                }
            }
            row.addView(lbl); row.addView(dot)
            row.setOnClickListener { onClick() }
            return row
        }

        fun checkRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(DimenUtils.dpToPx(14), DimenUtils.dpToPx(14), DimenUtils.dpToPx(14), DimenUtils.dpToPx(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
            row.setOnClickListener { onChange(!checked) }
            return row
        }

        val d = android.app.Dialog(ctx)

        val sortCard = optionCard()
        val sortRows = mutableListOf<LinearLayout>()
        val sortLabels = listOf("Sugestões", "Mais recentes", "Expira em Breve", "Ativas")
        sortLabels.forEachIndexed { idx, lbl ->
            val row = radioRow(lbl, sortMode == idx) { sortMode = idx; d.dismiss(); renderQuests(ctx) }
            sortRows.add(row); sortCard.addView(row)
        }

        val rewardCard = optionCard()
        var cOrbs = filterOrbs; var cDecor = filterDecor; var cInGame = filterInGame
        val rewardRows = mutableListOf<LinearLayout>()
        listOf(Triple("Orbs", { cOrbs }, { v: Boolean -> cOrbs = v }),
               Triple("Decoração de avatar", { cDecor }, { v: Boolean -> cDecor = v }),
               Triple("Recompensas no jogo", { cInGame }, { v: Boolean -> cInGame = v })
        ).forEach { (lbl, get, set) ->
            val row = checkRow(lbl, get()) { v -> set(v); rewardRows.clear() }
            rewardRows.add(row); rewardCard.addView(row)
        }

        val taskCard = optionCard()
        var cWatch = filterWatch; var cPlay = filterPlay
        taskCard.addView(checkRow("Assistir", cWatch) { v -> cWatch = v })
        taskCard.addView(checkRow("Jogar", cPlay) { v -> cPlay = v })

        container.addView(TextView(ctx).apply { text = "Filtros"; setTextColor(Color.parseColor("#F2F3F5")); textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, DimenUtils.dpToPx(4)) })
        container.addView(sectionLabel("Ordenar por"))
        container.addView(sortCard)
        container.addView(sectionLabel("Recompensas"))
        container.addView(rewardCard)
        container.addView(sectionLabel("Tipo de missão"))
        container.addView(taskCard)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, DimenUtils.dpToPx(16), 0, DimenUtils.dpToPx(8))
        }
        val resetBtn = TextView(ctx).apply {
            text = "Redefinir"
            setTextColor(Color.parseColor("#72767D"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(48), 1f).apply { setMargins(0, 0, DimenUtils.dpToPx(8), 0) }
            background = GradientDrawable().apply { setColor(Color.parseColor("#1E1F26")); cornerRadius = DimenUtils.dpToPx(24).toFloat() }
            setOnClickListener {
                sortMode = 0; filterOrbs = false; filterDecor = false; filterInGame = false; filterWatch = false; filterPlay = false
                d.dismiss(); renderQuests(ctx)
            }
        }
        val doneBtn = TextView(ctx).apply {
            text = "Pronto"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(48), 2f)
            background = GradientDrawable().apply { setColor(Color.parseColor("#5865F2")); cornerRadius = DimenUtils.dpToPx(24).toFloat() }
            setOnClickListener {
                filterOrbs = cOrbs; filterDecor = cDecor; filterInGame = cInGame; filterWatch = cWatch; filterPlay = cPlay
                d.dismiss(); renderQuests(ctx)
            }
        }
        btnRow.addView(resetBtn); btnRow.addView(doneBtn)
        container.addView(btnRow)

        d.setContentView(container)
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout((ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(android.view.Gravity.BOTTOM)
        }
        d.show()
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

    private fun addCollectiblesBtn(ctx: Context) {
        TextView(ctx, null, 0, com.lytefast.flexinput.R.i.UiKit_Settings_Item_Icon).apply {
            text = "Ver Colecionáveis"
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
                setMargins(DimenUtils.dpToPx(8), DimenUtils.dpToPx(4), DimenUtils.dpToPx(8), DimenUtils.dpToPx(8))
            }
            setOnClickListener { Utils.openPageWithProxy(ctx, CollectiblesPage()) }
            linearLayout.addView(this)
        }
    }

    private fun addEmpty(ctx: Context) {
        subText(ctx, "Nenhuma quest disponível no momento.", Pad(16, 32, 16, 32)).apply {
            gravity = android.view.Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addError(ctx: Context, msg: String) {
        subText(ctx, "Falha ao carregar:\n$msg", Pad(16, 32, 16, 32), Color.parseColor("#ED4245")).apply {
            gravity = android.view.Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addQuestCard(ctx: Context, quest: Quest) {
        val cfg = quest.config
        val us = quest.userStatus
        val c = card(ctx)

        val taskEntry = cfg.taskConfig.tasks.entries.firstOrNull()
        val taskName = taskEntry?.key ?: ""
        val target = taskEntry?.value?.target ?: 0
        val done = us?.progress?.get(taskName)?.value ?: 0
        val pct = if (target > 0) (done * 100 / target).coerceIn(0, 100) else 0
        val isMobile = taskName in listOf("WATCH_VIDEO_ON_MOBILE", "WATCH_VIDEO", "PLAY_ACTIVITY")
        val isVideo = taskName in listOf("WATCH_VIDEO_ON_MOBILE", "WATCH_VIDEO")

        val bannerUrl = getBannerUrl(quest)

        val topSection = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(140))
        }

        val bannerImg = com.facebook.drawee.view.SimpleDraweeView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            if (bannerUrl != null) setImageURI(bannerUrl)
            else setBackgroundColor(Color.parseColor("#1E1F26"))
        }

        val bannerOverlay = View(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#CC0E0F13"), Color.TRANSPARENT)
            )
        }

        val rewardIconSize = DimenUtils.dpToPx(52)
        val rewardIconContainer = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(rewardIconSize + DimenUtils.dpToPx(6), rewardIconSize + DimenUtils.dpToPx(6)).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                setMargins(DimenUtils.dpToPx(12), 0, 0, DimenUtils.dpToPx(-DimenUtils.dpToPx(4)))
            }
        }

        val rewardIcon = com.facebook.drawee.view.SimpleDraweeView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(rewardIconSize, rewardIconSize).apply {
                gravity = android.view.Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E1F26"))
                setStroke(DimenUtils.dpToPx(2), Color.parseColor("#5865F2"))
            }
        }

        val progressRingView = object : View(ctx) {
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                val stroke = DimenUtils.dpToPx(3).toFloat()
                val rect = RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2)
                val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = stroke
                    color = Color.parseColor("#3B3D44")
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
                    color = if (us?.completedAt != null) Color.parseColor("#23A55A") else Color.parseColor("#5865F2")
                }
                canvas.drawArc(rect, -90f, 360f, false, trackPaint)
                canvas.drawArc(rect, -90f, 360f * pct / 100f, false, fillPaint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(rewardIconSize + DimenUtils.dpToPx(6), rewardIconSize + DimenUtils.dpToPx(6)).apply {
                gravity = android.view.Gravity.CENTER
            }
        }

        val rewardImgUrl = getRewardIconUrl(quest)
        if (rewardImgUrl != null) rewardIcon.setImageURI(rewardImgUrl)

        rewardIconContainer.addView(rewardIcon)
        rewardIconContainer.addView(progressRingView)

        val expiryBadge = TextView(ctx).apply {
            val expiryText = "Termina em ${getShortExpiry(cfg.expiresAt)}"
            text = expiryText
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 10f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, DimenUtils.dpToPx(12), DimenUtils.dpToPx(8))
            }
        }

        topSection.addView(bannerImg)
        topSection.addView(bannerOverlay)
        topSection.addView(rewardIconContainer)
        topSection.addView(expiryBadge)
        c.addView(topSection)

        val infoSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DimenUtils.dpToPx(14), DimenUtils.dpToPx(10), DimenUtils.dpToPx(14), DimenUtils.dpToPx(2))
        }

        val publisherRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val publisherBadge = TextView(ctx).apply {
            text = "✓ ${cfg.messages.gamePublisher}"
            setTextColor(Color.parseColor("#23A55A"))
            textSize = 10f
            setPadding(DimenUtils.dpToPx(6), DimenUtils.dpToPx(2), DimenUtils.dpToPx(6), DimenUtils.dpToPx(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#23A55A22"))
                cornerRadius = DimenUtils.dpToPx(4).toFloat()
            }
        }

        val expiryLabel = TextView(ctx).apply {
            text = "Termina em ${getShortExpiry(cfg.expiresAt)}"
            setTextColor(Color.parseColor("#72767D"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = android.view.Gravity.END
            }
            gravity = android.view.Gravity.END
        }

        publisherRow.addView(publisherBadge)
        publisherRow.addView(expiryLabel)
        infoSection.addView(publisherRow)

        val missionLabel = TextView(ctx).apply {
            text = "MISSÃO: ${cfg.messages.questName.uppercase()}"
            setTextColor(Color.parseColor("#5865F2"))
            textSize = 9f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, DimenUtils.dpToPx(8), 0, DimenUtils.dpToPx(2))
        }
        infoSection.addView(missionLabel)

        val rewardTitle = cfg.rewardsConfig.rewards.firstOrNull()?.let { r ->
            if (r.type == 4 && r.orbQuantity > 0) "⬡ ${r.orbQuantity} Orbs do Discord"
            else r.messages.nameWithArticle.removePrefix("a ").removePrefix("an ").removePrefix("um ").removePrefix("uma ")
        } ?: cfg.messages.questName

        val titleView = TextView(ctx).apply {
            text = rewardTitle
            setTextColor(Color.parseColor("#F2F3F5"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, DimenUtils.dpToPx(4))
        }
        infoSection.addView(titleView)

        val descView = subText(ctx, describeTask(taskName, taskEntry?.value), Pad(0, 2, 0, 8)).apply {
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 12f
        }
        infoSection.addView(descView)

        if (pct > 0 || us?.enrolledAt != null) {
            val progressRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, DimenUtils.dpToPx(4), 0, DimenUtils.dpToPx(8))
            }
            val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                this.progress = pct
                layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(4), 1f).apply {
                    setMargins(0, 0, DimenUtils.dpToPx(8), 0)
                }
            }
            val pctLabel = TextView(ctx).apply {
                text = "$pct%"
                setTextColor(if (pct >= 100) Color.parseColor("#23A55A") else Color.parseColor("#5865F2"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            progressRow.addView(bar)
            progressRow.addView(pctLabel)
            infoSection.addView(progressRow)
        }

        c.addView(infoSection)

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(4), DimenUtils.dpToPx(12), DimenUtils.dpToPx(12))
        }

        when {
            us?.claimedAt != null -> {
                makeBtn(ctx, "✅ Recompensa Recebida", "#2B2D31", 1f, false).apply { btnRow.addView(this) }
            }
            us?.completedAt != null -> {
                makeBtn(ctx, "🎁 Ver Recompensa", "#5865F2", 1f).apply {
                    setOnClickListener { handleClaim(ctx, quest) }
                    btnRow.addView(this)
                }
            }
            !isMobile -> {
                makeBtn(ctx, "💻 Apenas no Desktop", "#3B3D44", 1f, false).apply { btnRow.addView(this) }
            }
            else -> {
                makeBtn(ctx, "⚡ Completar Auto", "#5865F2", if (isVideo) 0.52f else 1f).apply {
                    setOnClickListener { confirmAutoComplete(ctx, quest) }
                    btnRow.addView(this)
                }
                if (isVideo) {
                    val spacer = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(6), 0) }
                    btnRow.addView(spacer)
                    makeBtn(ctx, "▶ Assistir", "#2B2D31", 0.48f).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#2B2D31"))
                            cornerRadius = DimenUtils.dpToPx(12).toFloat()
                            setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44"))
                        }
                        setOnClickListener { openVideoPlayer(ctx, quest) }
                        btnRow.addView(this)
                    }
                }
            }
        }

        val moreBtn = TextView(ctx).apply {
            text = "•••"
            setTextColor(Color.parseColor("#72767D"))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(DimenUtils.dpToPx(38), DimenUtils.dpToPx(38)).apply {
                setMargins(DimenUtils.dpToPx(6), 0, 0, 0)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2B2D31"))
                cornerRadius = DimenUtils.dpToPx(10).toFloat()
                setStroke(DimenUtils.dpToPx(1), Color.parseColor("#3B3D44"))
            }
            setOnClickListener { showMoreMenu(ctx, quest) }
        }
        btnRow.addView(moreBtn)
        c.addView(btnRow)
        linearLayout.addView(c)
    }

    private fun showMoreMenu(ctx: Context, quest: Quest) {
        val items = arrayOf("🔄 Recarregar", "📋 Copiar nome")
        AlertDialog.Builder(ctx)
            .setTitle(quest.config.messages.questName)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> Utils.openPageWithProxy(ctx, QuestsPage())
                    1 -> {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("quest", quest.config.messages.questName))
                        showToast(ctx, "Nome copiado!")
                    }
                }
            }.show()
    }

    private fun makeBtn(ctx: Context, label: String, colorHex: String, weight: Float = 1f, enabled: Boolean = true): TextView {
        return TextView(ctx).apply {
            text = label
            gravity = android.view.Gravity.CENTER
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
            background = GradientDrawable().apply {
                setColor(Color.parseColor(colorHex))
                cornerRadius = DimenUtils.dpToPx(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, DimenUtils.dpToPx(42), weight)
        }
    }

    private fun showToast(ctx: Context, msg: String, error: Boolean = false) {
        val color = if (error) "#ED4245" else "#23A55A"
        val toast = android.widget.Toast(ctx)
        val tv = TextView(ctx).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(DimenUtils.dpToPx(18), DimenUtils.dpToPx(12), DimenUtils.dpToPx(18), DimenUtils.dpToPx(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(color))
                cornerRadius = DimenUtils.dpToPx(20).toFloat()
            }
        }
        toast.view = tv
        toast.duration = android.widget.Toast.LENGTH_SHORT
        toast.show()
    }

    private fun getBannerUrl(quest: Quest): String? {
        val cfg = quest.rawJson?.optJSONObject("config") ?: return null
        val assets = cfg.optJSONObject("assets")
        if (assets != null) {
            for (key in listOf("quest_bar_hero", "hero", "logotype", "game_tile", "thumbnail")) {
                val v = assets.optString(key, "").takeIf { it.isNotEmpty() && it != "null" } ?: continue
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
            for (key in listOf("reward_generic_android", "reward", "collectible_preview", "quest_reward")) {
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

    private fun getShortExpiry(iso: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val ts = sdf.parse(iso.substringBefore('.'))?.time ?: return ""
            java.text.SimpleDateFormat("d/M", java.util.Locale.getDefault()).format(java.util.Date(ts))
        } catch (_: Exception) { "" }
    }

    private fun confirmAutoComplete(ctx: Context, quest: Quest) {
        val taskEntry = quest.config.taskConfig.tasks.entries.firstOrNull()
        val taskName = taskEntry?.key ?: ""
        val target = taskEntry?.value?.target ?: 0
        AlertDialog.Builder(ctx)
            .setTitle("⚡ Auto-completar: ${quest.config.messages.questName}")
            .setMessage("Isso vai completar a quest automaticamente simulando progresso.\n\nTempo estimado: ${fmtSecs(target)}\n\nPode violar os ToS do Discord. Continuar?")
            .setPositiveButton("Completar") { _, _ -> startAutoComplete(ctx, quest) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startAutoComplete(ctx: Context, quest: Quest) {
        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        val statusTv = TextView(ctx).apply {
            text = "Iniciando..."; setTextColor(Color.parseColor("#B5BAC1")); textSize = 12f
            setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(8), DimenUtils.dpToPx(16), DimenUtils.dpToPx(4))
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F26"))
            addView(statusTv)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(DimenUtils.dpToPx(16), DimenUtils.dpToPx(4), DimenUtils.dpToPx(16), DimenUtils.dpToPx(12))
                addView(progressBar)
            })
        }
        val progressDialog = AlertDialog.Builder(ctx)
            .setTitle("Completando quest...")
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("Cancelar", null)
            .create()
        progressDialog.show()

        fun upd(msg: String, pct: Int = -1) = Utils.mainThread.post {
            statusTv.text = msg
            if (pct >= 0) progressBar.progress = pct
        }

        Utils.threadPool.execute {
            try {
                val taskEntry = quest.config.taskConfig.tasks.entries.firstOrNull()
                val taskName = taskEntry?.key ?: return@execute
                val target = taskEntry.value.target.toLong()
                val questId = quest.id

                if (quest.userStatus?.enrolledAt == null) {
                    upd("Inscrevendo na quest...", 0)
                    QuestApi.enrollQuest(questId)
                    Thread.sleep(1000)
                }

                when (taskName) {
                    "WATCH_VIDEO", "WATCH_VIDEO_ON_MOBILE" -> {
                        var done = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
                        val enrollMs = System.currentTimeMillis() - 15_000L
                        while (done < target) {
                            val maxAllowed = (System.currentTimeMillis() - enrollMs) / 1000 + 10
                            val next = (done + 7).coerceAtMost(target).coerceAtMost(maxAllowed)
                            if (maxAllowed - done >= 7) {
                                val res = QuestApi.sendVideoProgress(questId, next.toDouble())
                                done = next
                                val pct = if (target > 0) (done * 100 / target).toInt() else 0
                                upd("Vídeo: ${done}s / ${target}s ($pct%)", pct)
                                if (res.optString("completed_at").isNotEmpty()) break
                            }
                            Thread.sleep(1000)
                        }
                        QuestApi.sendVideoProgress(questId, target.toDouble())
                    }
                    "PLAY_ACTIVITY" -> {
                        val channelId = QuestApi.getFirstDmChannelId() ?: throw Exception("Nenhum DM encontrado. Abra um DM no Discord primeiro.")
                        val streamKey = "call:$channelId:1"
                        var done = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
                        while (done < target) {
                            val res = QuestApi.sendHeartbeat(questId, streamKey, false)
                            val newVal = res.optJSONObject("progress")?.optJSONObject("PLAY_ACTIVITY")?.optLong("value", done) ?: done
                            done = newVal
                            val pct = if (target > 0) (done * 100 / target).toInt() else 0
                            upd("Atividade: ${done}s / ${target}s (~${(target - done) / 60} min restantes)", pct)
                            Thread.sleep(20_000)
                        }
                        QuestApi.sendHeartbeat(questId, streamKey, true)
                    }
                }
                Thread.sleep(1000)
                upd("Reivindicando recompensa...", 99)
                val (code, claimJson) = QuestApi.claimReward(questId)
                Utils.mainThread.post {
                    progressDialog.dismiss()
                    when {
                        code == 200 -> {
                            showToast(ctx, "✅ Recompensa recebida!")
                            Utils.openPageWithProxy(ctx, QuestsPage())
                        }
                        code == 400 && claimJson.optString("captcha_sitekey").isNotEmpty() -> {
                            val sitekey = claimJson.optString("captcha_sitekey")
                            val rqtoken = claimJson.optString("captcha_rqtoken").takeIf { it.isNotEmpty() }
                            val rqdata = claimJson.optString("captcha_rqdata").takeIf { it.isNotEmpty() }
                            CaptchaDialog.show(ctx, sitekey, rqdata, object : CaptchaDialog.CaptchaCallback {
                                override fun onSolved(captchaKey: String) {
                                    Utils.threadPool.execute {
                                        val (c2, _) = QuestApi.claimReward(questId, captchaKey, rqtoken)
                                        Utils.mainThread.post {
                                            if (c2 == 200) { showToast(ctx, "✅ Recompensa recebida!"); Utils.openPageWithProxy(ctx, QuestsPage()) }
                                            else showToast(ctx, "⚠️ Falha ao reivindicar (código $c2)", true)
                                        }
                                    }
                                }
                                override fun onCancel() {}
                            })
                        }
                        else -> { showToast(ctx, "Quest concluída! Reivindique no Discord.", false); Utils.openPageWithProxy(ctx, QuestsPage()) }
                    }
                }
            } catch (e: Exception) {
                Utils.mainThread.post { progressDialog.dismiss(); showToast(ctx, "Erro: ${e.message}", true) }
            }
        }
    }

    private fun openVideoPlayer(ctx: Context, quest: Quest) {
        val taskEntry = quest.config.taskConfig.tasks.entries.firstOrNull()
        val taskName = taskEntry?.key ?: "WATCH_VIDEO"
        val target = (taskEntry?.value?.target ?: 0).toLong()
        val questId = quest.id

        val statusTv = TextView(ctx).apply {
            text = "Carregando vídeo..."
            setTextColor(Color.parseColor("#B5BAC1"))
            textSize = 11f
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(6), DimenUtils.dpToPx(12), DimenUtils.dpToPx(2))
        }
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = ((quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong().let { if (target > 0) (it * 100 / target).toInt() else 0 })
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(4)).apply {
                setMargins(DimenUtils.dpToPx(12), DimenUtils.dpToPx(2), DimenUtils.dpToPx(12), DimenUtils.dpToPx(8))
            }
        }
        val videoView = android.widget.VideoView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DimenUtils.dpToPx(210))
        }
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1F26"))
            addView(videoView)
            addView(statusTv)
            addView(bar)
        }

        var spoofThread: Thread? = null
        var spoofActive = false
        var spoofSeconds = (quest.userStatus?.progress?.get(taskName)?.value ?: 0).toLong()
        var enrolled = quest.userStatus?.enrolledAt != null
        var dialog: AlertDialog? = null

        val videoUrl: String? = quest.rawJson?.let { raw -> QuestApi.extractVideoUrl(questId, raw) }

        if (videoUrl != null) {
            videoView.setVideoURI(Uri.parse(videoUrl))
            val mc = android.widget.MediaController(ctx)
            mc.setAnchorView(videoView)
            videoView.setMediaController(mc)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.start()
                statusTv.text = "▶ Assistindo e sincronizando..."
                spoofActive = true
                spoofThread = Thread {
                    try {
                        if (!enrolled) { QuestApi.enrollQuest(questId); enrolled = true }
                        val enrollMs = System.currentTimeMillis() - 15_000L
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
                                    bar.progress = pct
                                    statusTv.text = "▶ ${spoofSeconds}s / ${target}s ($pct%)"
                                }
                                if (completed || spoofSeconds >= target) {
                                    Utils.mainThread.post { bar.progress = 100; statusTv.text = "✅ Concluído! Toque em Ver Recompensa." }
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
                spoofThread!!.start()
            }
            videoView.setOnErrorListener { _, _, _ -> statusTv.text = "Vídeo indisponível. Use Auto-completar."; false }
        } else {
            statusTv.text = "URL do vídeo não encontrada. Use Auto-completar."
        }

        dialog = AlertDialog.Builder(ctx)
            .setTitle("▶ ${quest.config.messages.questName}")
            .setView(container)
            .setNegativeButton("Fechar") { d, _ -> spoofActive = false; spoofThread?.interrupt(); videoView.stopPlayback(); d.dismiss() }
            .setPositiveButton("Ver Recompensa") { d, _ -> spoofActive = false; spoofThread?.interrupt(); videoView.stopPlayback(); d.dismiss(); handleClaim(ctx, quest) }
            .create()
        dialog.show()
    }

    private fun handleClaim(ctx: Context, quest: Quest) {
        Utils.threadPool.execute {
            try {
                val (code, json) = QuestApi.claimReward(quest.id)
                Utils.mainThread.post {
                    when {
                        code == 200 -> { showToast(ctx, "✅ Recompensa recebida!"); Utils.openPageWithProxy(ctx, QuestsPage()) }
                        code == 400 && json.optString("captcha_sitekey").isNotEmpty() -> {
                            val sitekey = json.optString("captcha_sitekey")
                            val rqtoken = json.optString("captcha_rqtoken").takeIf { it.isNotEmpty() }
                            val rqdata = json.optString("captcha_rqdata").takeIf { it.isNotEmpty() }
                            CaptchaDialog.show(ctx, sitekey, rqdata, object : CaptchaDialog.CaptchaCallback {
                                override fun onSolved(captchaKey: String) {
                                    Utils.threadPool.execute {
                                        val (c2, _) = QuestApi.claimReward(quest.id, captchaKey, rqtoken)
                                        Utils.mainThread.post {
                                            if (c2 == 200) { showToast(ctx, "✅ Recompensa recebida!"); Utils.openPageWithProxy(ctx, QuestsPage()) }
                                            else showToast(ctx, "⚠️ Falha (código $c2)", true)
                                        }
                                    }
                                }
                                override fun onCancel() {}
                            })
                        }
                        else -> showToast(ctx, "Falha ao reivindicar (código $code)", true)
                    }
                }
            } catch (e: Exception) {
                Utils.mainThread.post { showToast(ctx, "Erro: ${e.message}", true) }
            }
        }
    }

    private fun describeTask(name: String, task: QuestTaskModel?): String {
        val t = task ?: return name
        return when (name) {
            "STREAM_ON_DESKTOP" -> "Faça stream por ${fmtSecs(t.target)}"
            "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2" -> "Jogue no Desktop por ${fmtSecs(t.target)}"
            "WATCH_VIDEO" -> "Assista ao vídeo por ${fmtSecs(t.target)}"
            "WATCH_VIDEO_ON_MOBILE" -> "Assista ao vídeo no mobile por ${fmtSecs(t.target)}"
            "PLAY_ACTIVITY" -> "Jogue atividade por ${fmtSecs(t.target)}"
            "PLAY_ON_PLAYSTATION" -> "Jogue no PlayStation por ${fmtSecs(t.target)}"
            "PLAY_ON_XBOX" -> "Jogue no Xbox por ${fmtSecs(t.target)}"
            else -> t.title ?: name
        }
    }

    private fun fmtSecs(secs: Int): String {
        val m = secs / 60
        return if (m < 60) "${m}min" else "${m / 60}h${if (m % 60 > 0) " ${m % 60}min" else ""}"
    }
}
