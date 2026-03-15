package com.github.nyxiereal.viewquests

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.SerializedName
import com.facebook.drawee.view.SimpleDraweeView

data class CollectibleItem(
    val type: Int,
    val id: String,
    @SerializedName("sku_id") val skuId: String,
    val asset: String,
    val label: String
)

data class CollectiblePurchase(
    @SerializedName("sku_id") val skuId: String,
    val name: String,
    val summary: String,
    val type: Int,
    @SerializedName("purchase_type") val purchaseType: Int,
    @SerializedName("purchased_at") val purchasedAt: String,
    @SerializedName("expires_at") val expiresAt: String? = null,
    val items: List<CollectibleItem>? = null
)

class CollectiblesPage : SettingsPage() {

    private val logger = Logger("ViewQuests")

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Loading Collectibles...")
        val ctx = view.context

        Utils.threadPool.execute {
            try {
                val arr = QuestApi.getCollectibles()
                Utils.mainThread.post {
                    setActionBarTitle("Collectibles (${arr.length()})")
                    if (arr.length() == 0) {
                        addEmpty(ctx)
                    } else {
                        for (i in 0 until arr.length()) {
                            try {
                                val obj = arr.getJSONObject(i)
                                val items = mutableListOf<CollectibleItem>()
                                val itemsArr = obj.optJSONArray("items")
                                if (itemsArr != null) {
                                    for (j in 0 until itemsArr.length()) {
                                        val it = itemsArr.getJSONObject(j)
                                        items.add(CollectibleItem(
                                            type = it.optInt("type"),
                                            id = it.optString("id"),
                                            skuId = it.optString("sku_id"),
                                            asset = it.optString("asset"),
                                            label = it.optString("label")
                                        ))
                                    }
                                }
                                val c = CollectiblePurchase(
                                    skuId = obj.optString("sku_id"),
                                    name = obj.optString("name"),
                                    summary = obj.optString("summary"),
                                    type = obj.optInt("type"),
                                    purchaseType = obj.optInt("purchase_type"),
                                    purchasedAt = obj.optString("purchased_at"),
                                    expiresAt = obj.optString("expires_at").takeIf { it.isNotEmpty() && it != "null" },
                                    items = items
                                )
                                addCollectibleCard(ctx, c)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed fetching collectibles", e)
                Utils.mainThread.post {
                    setActionBarTitle("Collectibles")
                    subText(ctx, "Failed to load: ${e.message}", Pad(16, 32, 16, 32), Color.parseColor("#ED4245")).apply {
                        gravity = Gravity.CENTER
                        linearLayout.addView(this)
                    }
                }
            }
        }
    }

    private fun addEmpty(ctx: Context) {
        subText(ctx, "No collectibles yet.", Pad(16, 32, 16, 32)).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addCollectibleCard(ctx: Context, c: CollectiblePurchase) {
        val container = card(ctx)

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(DimenUtils.dpToPx(12), DimenUtils.dpToPx(12), DimenUtils.dpToPx(12), DimenUtils.dpToPx(8))
        }

        val imgSize = DimenUtils.dpToPx(56)
        val imageView = SimpleDraweeView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(imgSize, imgSize).apply {
                setMargins(0, 0, DimenUtils.dpToPx(12), 0)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2D35"))
                cornerRadius = DimenUtils.dpToPx(8).toFloat()
            }
        }

        val imageUrl = getImageUrl(c)
        if (imageUrl != null) {
            imageView.setImageURI(imageUrl)
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameLabel = headerText(ctx, c.name, Pad(0, 0, 0, 2)).apply {
            textSize = 14f
        }

        val typeLabel = subText(ctx, getTypeName(c.type), Pad(0, 2, 0, 2)).apply {
            setTextColor(getTypeColor(c.type))
            textSize = 11f
        }

        val purchaseLabel = subText(ctx, getPurchaseTypeName(c.purchaseType), Pad(0, 2, 0, 0)).apply {
            setTextColor(Color.parseColor("#72767D"))
            textSize = 10f
        }

        textCol.addView(nameLabel)
        textCol.addView(typeLabel)
        textCol.addView(purchaseLabel)
        topRow.addView(imageView)
        topRow.addView(textCol)
        container.addView(topRow)

        if (c.summary.isNotEmpty()) {
            divider(ctx).apply { container.addView(this) }
            subText(ctx, c.summary, Pad(14, 6, 14, 6)).apply {
                setTextColor(Color.parseColor("#B5BAC1"))
                textSize = 11f
                container.addView(this)
            }
        }

        if (!c.items.isNullOrEmpty()) {
            divider(ctx).apply { container.addView(this) }
            labelText(ctx, "Items:", Pad(14, 8, 14, 4)).apply {
                setTextColor(Color.parseColor("#5865F2"))
                textSize = 11f
                container.addView(this)
            }
            c.items.forEach { item ->
                val itemRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(DimenUtils.dpToPx(20), DimenUtils.dpToPx(3), DimenUtils.dpToPx(14), DimenUtils.dpToPx(3))
                }
                val itemImgSize = DimenUtils.dpToPx(32)
                val itemImg = SimpleDraweeView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(itemImgSize, itemImgSize).apply {
                        setMargins(0, 0, DimenUtils.dpToPx(8), 0)
                    }
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#2C2D35"))
                        cornerRadius = DimenUtils.dpToPx(6).toFloat()
                    }
                }
                val itemImageUrl = getItemImageUrl(item)
                if (itemImageUrl != null) itemImg.setImageURI(itemImageUrl)

                val itemLbl = subText(ctx, item.label.ifEmpty { getItemTypeName(item.type) }, Pad(0, 0, 0, 0)).apply {
                    textSize = 11f
                    setTextColor(Color.parseColor("#B5BAC1"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                }
                itemRow.addView(itemImg)
                itemRow.addView(itemLbl)
                container.addView(itemRow)
            }
        }

        divider(ctx).apply { container.addView(this) }
        val expiryText = if (c.expiresAt != null) "Expires: ${fmtDate(ctx, c.expiresAt)}" else "Never expires"
        subText(ctx, "Purchased: ${fmtDate(ctx, c.purchasedAt)}  ·  $expiryText", Pad(14, 6, 14, 10)).apply {
            setTextColor(Color.parseColor("#72767D"))
            textSize = 10f
            container.addView(this)
        }

        linearLayout.addView(container)
    }

    private fun getImageUrl(c: CollectiblePurchase): String? {
        c.items?.forEach { item ->
            val url = getItemImageUrl(item)
            if (url != null) return url
        }
        return null
    }

    private fun getItemImageUrl(item: CollectibleItem): String? {
        if (item.asset.isNotEmpty() && item.asset != "null") {
            return when {
                item.asset.startsWith("http") -> item.asset
                item.type == 0 -> "https://cdn.discordapp.com/avatar-decoration-presets/${item.asset}.png?size=160&passthrough=true"
                item.type == 1 -> "https://cdn.discordapp.com/profile-effects/${item.asset}.png"
                else -> "https://cdn.discordapp.com/collectibles/${item.asset}.png"
            }
        }
        return null
    }

    private fun getTypeName(type: Int) = when (type) {
        0 -> "Avatar Decoration"
        1 -> "Profile Effect"
        2 -> "Bundle"
        3000 -> "Badge"
        else -> "Collectible"
    }

    private fun getTypeColor(type: Int) = when (type) {
        0 -> Color.parseColor("#B675F0")
        1 -> Color.parseColor("#5865F2")
        2 -> Color.parseColor("#FAA61A")
        3000 -> Color.parseColor("#23A55A")
        else -> Color.parseColor("#72767D")
    }

    private fun getPurchaseTypeName(type: Int) = when (type) {
        1 -> "Direct Purchase"
        10 -> "Quest Reward"
        else -> "Gift / Other"
    }

    private fun getItemTypeName(type: Int) = when (type) {
        0 -> "Avatar Decoration"
        1 -> "Profile Effect"
        2 -> "Badge"
        else -> "Item"
    }
}
