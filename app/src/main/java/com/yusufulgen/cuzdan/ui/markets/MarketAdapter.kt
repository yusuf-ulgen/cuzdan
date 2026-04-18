package com.yusufulgen.cuzdan.ui.markets

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.yusufulgen.cuzdan.R
import com.yusufulgen.cuzdan.databinding.ItemMarketPriceBinding
import com.yusufulgen.cuzdan.data.local.entity.MarketAsset
import com.yusufulgen.cuzdan.util.calculateProfitLossPercentage
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import coil.load
import android.view.View
import com.yusufulgen.cuzdan.util.HapticManager

class MarketAdapter(
    private var items: List<MarketAsset> = emptyList(),
    private val showChange: Boolean = true,
    private val onItemClick: (MarketAsset, View, View) -> Unit,
    private val onFavoriteClick: ((MarketAsset) -> Unit)? = null
) : RecyclerView.Adapter<MarketAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMarketPriceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMarketPriceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = items[position]
            for (payload in payloads) {
                if (payload == "FAVORITE_CHANGED") {
                    holder.binding.imageMarketFavorite.setImageResource(
                        if (item.isFavorite) R.drawable.ic_star 
                        else R.drawable.ic_star_outline
                    )
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            ImageViewCompat.setImageTintList(imageMarketIcon, null)

            val isDoviz = item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.DOVIZ
            val isEmtia = item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.EMTIA
            val isNakit = item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.NAKIT
            val isCashTool = isNakit && item.symbol.startsWith("TOOL_")
            val placeholderRes = getAssetIconPlaceholder(item, root.context)

            if (isCashTool) {
                textMarketName.text = getLocalizedAssetName(item.name, root.context)
                // Cash tool cards: no subtitle
                textMarketFullName.visibility = View.GONE
                textMarketSymbol.visibility = View.GONE
                if (item.symbol == "TOOL_BES") {
                    textMarketName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                } else {
                    textMarketName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                }
            } else if ((isDoviz || isEmtia || isNakit) && item.fullName != null) {
                // For currencies/commodities/cash: show name only, hide redundant technical subtitle
                textMarketName.text = getLocalizedAssetName(item.fullName ?: item.name, root.context)
                textMarketFullName.visibility = android.view.View.GONE
                textMarketSymbol.visibility = android.view.View.GONE
            } else {
                textMarketName.text = getLocalizedAssetName(item.name, root.context)
                textMarketName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)

                if (item.fullName != null && item.fullName != item.name) {
                    textMarketFullName.visibility = android.view.View.VISIBLE
                    textMarketFullName.text = item.fullName
                    textMarketFullName.isSelected = true
                } else {
                    textMarketFullName.visibility = android.view.View.GONE
                }

                val hideSymbol = shouldHideSymbol(item)
                textMarketSymbol.text = if (hideSymbol) "" else item.symbol
                textMarketSymbol.visibility = if (hideSymbol) android.view.View.GONE else android.view.View.VISIBLE
            }
            
            val formattedPrice = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 4
            }.format(item.currentPrice)
            
            val symbol = when(item.currency) {
                "USD" -> "$"
                "EUR" -> "€"
                else -> "₺"
            }
            if (isCashTool) {
                // Render as an action card (same layout) instead of a price.
                val tv = TypedValue()
                val ctx = root.context
                val ok = ctx.theme.resolveAttribute(R.attr.accentColor, tv, true)
                if (ok) textMarketPrice.setTextColor(tv.data)
                textMarketPrice.text = ctx.getString(R.string.cash_tool_action)
            } else {
                // If currency is USD, symbol goes to the front.
                if (item.currency == "USD") {
                    textMarketPrice.text = "$symbol$formattedPrice"
                } else {
                    textMarketPrice.text = "$formattedPrice $symbol"
                }
                // reset to theme default color
                val tv = TypedValue()
                val ctx = root.context
                val ok = ctx.theme.resolveAttribute(R.attr.textPrimary, tv, true)
                if (ok) textMarketPrice.setTextColor(tv.data)
            }
            
            if (showChange) {
                textMarketChange.visibility = android.view.View.VISIBLE
                val scaledChange = item.dailyChangePercentage.setScale(2, java.math.RoundingMode.HALF_UP)
                val sign = scaledChange.signum()
                textMarketChange.text = String.format("%%%+.2f", item.dailyChangePercentage)
                
                val color = when {
                    sign > 0 -> R.color.accent_green
                    sign < 0 -> R.color.accent_red
                    else -> R.color.text_label
                }
                textMarketChange.setTextColor(root.context.getColor(color))
            } else {
                textMarketChange.visibility = android.view.View.GONE
            }

            // Set Icon/Logo
            val iconUrl = getAssetIconUrl(item)
            if (iconUrl != null) {
                imageMarketIcon.load(iconUrl) {
                    crossfade(true)
                    placeholder(placeholderRes)
                    error(placeholderRes)
                }
            } else {
                imageMarketIcon.setImageResource(placeholderRes)
                applyCurrencyCustomTint(imageMarketIcon, placeholderRes)
            }

            root.setOnClickListener {
                HapticManager.tap(it)
                onItemClick(item, viewIconBg, textMarketName)
            }

            if (isCashTool) {
                imageMarketFavorite.visibility = View.GONE
            } else {
                imageMarketFavorite.visibility = View.VISIBLE
                imageMarketFavorite.setImageResource(
                    if (item.isFavorite) R.drawable.ic_star 
                    else R.drawable.ic_star_outline
                )
                imageMarketFavorite.setOnClickListener {
                    onFavoriteClick?.invoke(item)
                }
            }
        }
    }

    private fun shouldHideSymbol(item: MarketAsset): Boolean {
        val sym = item.symbol.uppercase()
        val name = item.name.uppercase()
        // Hide technical codes if they are redundant or ugly
        return when {
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.NAKIT -> true
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.DOVIZ -> true
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.FON -> true
            sym.contains("=X") || sym.endsWith(".IS") -> true
            sym == "GRAM_ALTIN" || sym == name -> true
            // Hide if symbol is just a technical code for crypto/commodities
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.KRIPTO && sym.endsWith("USDT") && sym.replace("USDT", "") == name -> true
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.EMTIA && (sym.endsWith("=F") || sym.contains("=")) -> true
            else -> false
        }
    }

    private fun getAssetIconUrl(item: MarketAsset): String? {
        val sym = item.symbol.uppercase()
        return when {
            // Nakit / döviz: always use local drawables (stable, no flag CDN flicker or wrong aspect).
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.NAKIT ||
                item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.DOVIZ -> null
            // We now use local primitive icons for all asset types as requested by user.
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.KRIPTO -> null
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.BIST -> null
            else -> null
        }
    }

    /**
     * Optional overrides: add `currency_try.png`, `currency_usd.png`, `currency_eur.png`,
     * `currency_gbp.png`, `currency_chf.png`, `currency_jpy.png`, `currency_aud.png`,
     * `currency_cad.png` under `res/drawable-*` (black or dark glyphs on transparent work with [applyCurrencyCustomTint]).
     */
    private fun getAssetIconPlaceholder(item: MarketAsset, context: android.content.Context): Int {
        val sym = item.symbol.uppercase()
        val code = if (sym.contains("/")) sym.substringBefore("/") else sym
        if (item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.NAKIT ||
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.DOVIZ
        ) {
            val customName = when {
                code == "TRY" || code == "TL" -> "currency_try"
                code == "USD" || code.startsWith("USD") -> "currency_usd"
                code == "EUR" || code.startsWith("EUR") -> "currency_eur"
                code == "GBP" || code.startsWith("GBP") -> "currency_gbp"
                code == "CHF" || code.startsWith("CHF") -> "currency_chf"
                code == "JPY" || code.startsWith("JPY") -> "currency_jpy"
                code == "AUD" || code.startsWith("AUD") -> "currency_aud"
                code == "CAD" || code.startsWith("CAD") -> "currency_cad"
                else -> null
            }
            if (customName != null) {
                val id = context.resources.getIdentifier(customName, "drawable", context.packageName)
                if (id != 0) return id
            }
        }
        return when {
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.KRIPTO -> R.drawable.ic_crypto
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.BIST -> R.drawable.ic_bist
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.FON -> R.drawable.ic_funds
            item.assetType == com.yusufulgen.cuzdan.data.local.entity.AssetType.EMTIA -> R.drawable.ic_commodity
            code == "TRY" || code == "TL" -> R.drawable.ic_tl
            code == "USD" || code.startsWith("USD") -> R.drawable.ic_usd
            code == "EUR" || code.startsWith("EUR") -> R.drawable.ic_eur
            code == "GBP" || code.startsWith("GBP") -> R.drawable.ic_gbp
            code == "CHF" || code.startsWith("CHF") -> R.drawable.ic_chf
            code == "JPY" || code.startsWith("JPY") -> R.drawable.ic_jpy
            code == "AUD" || code.startsWith("AUD") -> R.drawable.ic_aud
            code == "CAD" || code.startsWith("CAD") -> R.drawable.ic_cad
            else -> R.drawable.ic_asset_placeholder
        }
    }

    private fun applyCurrencyCustomTint(imageView: ImageView, resId: Int) {
        val name = try {
            imageView.context.resources.getResourceEntryName(resId)
        } catch (_: Exception) {
            ImageViewCompat.setImageTintList(imageView, null)
            return
        }
        if (!name.startsWith("currency_")) {
            ImageViewCompat.setImageTintList(imageView, null)
            return
        }
        val tv = TypedValue()
        val ctx = imageView.context
        if (!ctx.theme.resolveAttribute(R.attr.iconTint, tv, true) || tv.resourceId == 0) {
            ImageViewCompat.setImageTintList(imageView, null)
            return
        }
        ImageViewCompat.setImageTintList(imageView, ContextCompat.getColorStateList(ctx, tv.resourceId))
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<MarketAsset>) {
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].symbol == newItems[newItemPosition].symbol &&
                       items[oldItemPosition].assetType == newItems[newItemPosition].assetType
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]
                return if (old.isFavorite != new.isFavorite && 
                    old.copy(isFavorite = new.isFavorite) == new) {
                    "FAVORITE_CHANGED"
                } else {
                    null
                }
            }
        })

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    private fun getLocalizedAssetName(name: String, context: android.content.Context): String {
        // Use string resources for locale-aware names (TR → Turkish names, EN → English names)
        val try_name = context.getString(R.string.currency_try).replace(" (₺)", "")
        val usd_name = context.getString(R.string.currency_usd).replace(" ($)", "")
        val eur_name = context.getString(R.string.currency_eur).replace(" (€)", "")
        return when {
            name == "Türk Lirası" || name == "Turkish Lira" -> try_name
            name == "Amerikan Doları" || name == "US Dollar" || name == "American Dollar" || name == "United States Dollar" -> usd_name
            name == "Euro" -> eur_name
            // These will use English names in EN locale via their own resource strings if we add them,
            // but for simple locale-aware approach, keep English names (they are the same in both)
            name == "İngiliz Sterlini" || name == "British Pound" -> context.getString(R.string.currency_gbp)
            name == "İsviçre Frangı" || name == "Swiss Franc" -> context.getString(R.string.currency_chf)
            name == "Japon Yeni" || name == "Japanese Yen" -> context.getString(R.string.currency_jpy)
            name == "Avustralya Doları" || name == "Australian Dollar" -> context.getString(R.string.currency_aud)
            name == "Kanada Doları" || name == "Canadian Dollar" -> context.getString(R.string.currency_cad)
            name == "Altın (Ons)" -> context.getString(R.string.commodity_gold_oz)
            name == "Gram Altın" -> context.getString(R.string.commodity_gram_gold)
            name == "Altın" || name == "Gold" || name.contains("Gold", true) -> context.getString(R.string.commodity_gold)
            name == "Gümüş" || name == "Silver" || name.contains("Silver", true) -> context.getString(R.string.commodity_silver)
            name == "Bakır" || name == "Copper" || name.contains("Copper", true) -> context.getString(R.string.commodity_copper)
            name == "Platin" || name == "Platinum" || name.contains("Platinum", true) -> context.getString(R.string.commodity_platinum)
            name == "Paladyum" || name == "Palladium" || name.contains("Palladium", true) -> context.getString(R.string.commodity_palladium)
            else -> name
        }
    }
}
