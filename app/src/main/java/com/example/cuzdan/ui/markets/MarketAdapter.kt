package com.example.cuzdan.ui.markets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cuzdan.R
import com.example.cuzdan.databinding.ItemMarketPriceBinding
import com.example.cuzdan.data.local.entity.MarketAsset
import com.example.cuzdan.util.calculateProfitLossPercentage
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import coil.load
import coil.transform.CircleCropTransformation
import android.view.View
import com.example.cuzdan.util.HapticManager

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
            textMarketName.text = getLocalizedAssetName(item.name, root.context)
            
            if (item.fullName != null && item.fullName != item.name) {
                textMarketFullName.visibility = android.view.View.VISIBLE
                if (textMarketFullName.text != item.fullName) {
                    textMarketFullName.text = item.fullName
                }
                if (!textMarketFullName.isSelected) {
                    textMarketFullName.isSelected = true // Enable marquee
                }
            } else {
                textMarketFullName.visibility = android.view.View.GONE
            }
            
            val hideSymbol = shouldHideSymbol(item)
            textMarketSymbol.text = if (hideSymbol) "" else item.symbol
            textMarketSymbol.visibility = if (hideSymbol) android.view.View.GONE else android.view.View.VISIBLE
            
            val formattedPrice = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 4
            }.format(item.currentPrice)
            
            val symbol = when(item.currency) {
                "USD" -> "$"
                "EUR" -> "€"
                else -> "₺"
            }
            textMarketPrice.text = if (item.currency == "USD") "$symbol$formattedPrice" else "$formattedPrice $symbol"
            
            if (showChange) {
                textMarketChange.visibility = android.view.View.VISIBLE
                val sign = item.dailyChangePercentage.setScale(2, java.math.RoundingMode.HALF_UP).signum()
                textMarketChange.text = String.format("%%%+.2f", item.dailyChangePercentage)
                textMarketChange.setTextColor(root.context.getColor(
                    when {
                        sign > 0 -> R.color.accent_green
                        sign < 0 -> R.color.accent_red
                        else -> R.color.white
                    }
                ))
            } else {
                textMarketChange.visibility = android.view.View.GONE
            }

            // Set Icon/Logo
            val iconUrl = getAssetIconUrl(item)
            if (iconUrl != null) {
                imageMarketIcon.load(iconUrl) {
                    crossfade(true)
                    placeholder(getAssetIconPlaceholder(item))
                    error(getAssetIconPlaceholder(item))
                    transformations(CircleCropTransformation())
                }
            } else {
                imageMarketIcon.setImageResource(getAssetIconPlaceholder(item))
            }

            root.setOnClickListener {
                HapticManager.tap(it)
                onItemClick(item, viewIconBg, textMarketName)
            }

            imageMarketFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_star 
                else R.drawable.ic_star_outline
            )

            imageMarketFavorite.setOnClickListener {
                onFavoriteClick?.invoke(item)
            }
        }
    }

    private fun shouldHideSymbol(item: MarketAsset): Boolean {
        val sym = item.symbol.uppercase()
        val name = item.name.uppercase()
        // Hide technical codes if they are redundant or ugly
        return when {
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.NAKIT -> true
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.DOVIZ -> true
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.FON -> true
            sym.contains("=X") || sym.endsWith(".IS") -> true
            sym == "GRAM_ALTIN" || sym == name -> true
            // Hide if symbol is just a technical code for crypto/commodities
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.KRIPTO && sym.endsWith("USDT") && sym.replace("USDT", "") == name -> true
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.EMTIA && (sym.endsWith("=F") || sym.contains("=")) -> true
            else -> false
        }
    }

    private fun getAssetIconUrl(item: MarketAsset): String? {
        val sym = item.symbol.uppercase()
        return when {
            // Currency Flags
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.NAKIT || item.assetType == com.example.cuzdan.data.local.entity.AssetType.DOVIZ -> {
                val code = if (sym.contains("/")) sym.take(3) else sym
                when(code) {
                    "USD" -> "https://flagcdn.com/w80/us.png"
                    "EUR" -> "https://flagcdn.com/w80/eu.png"
                    "GBP" -> "https://flagcdn.com/w80/gb.png"
                    "CHF" -> "https://flagcdn.com/w80/ch.png"
                    "JPY" -> "https://flagcdn.com/w80/jp.png"
                    "AUD" -> "https://flagcdn.com/w80/au.png"
                    "CAD" -> "https://flagcdn.com/w80/ca.png"
                    "TRY" -> "https://flagcdn.com/w80/tr.png"
                    else -> null
                }
            }
            // Crypto Logos
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.KRIPTO -> {
                val coin = sym.replace("USDT", "")
                "https://static.binance.com/assets/asset/symbol/${coin.lowercase()}@2x.png"
            }
            // BIST Stock Logos
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.BIST -> {
                val stock = sym.replace(".IS", "")
                "https://s3-symbol-logo.tradingview.com/turkish--${stock.lowercase()}.svg"
            }
            else -> null
        }
    }

    private fun getAssetIconPlaceholder(item: MarketAsset): Int {
        val sym = item.symbol.uppercase()
        return when {
            sym == "TRY" || sym == "TL" -> R.drawable.ic_tl
            sym == "USD" || sym.startsWith("USD") -> R.drawable.ic_usd
            sym == "EUR" || sym.startsWith("EUR") -> R.drawable.ic_eur
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.KRIPTO -> R.drawable.ic_crypto
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.BIST -> R.drawable.ic_bist
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.FON -> R.drawable.ic_funds
            item.assetType == com.example.cuzdan.data.local.entity.AssetType.EMTIA -> R.drawable.ic_currency
            else -> R.drawable.ic_asset_placeholder
        }
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
        return when {
            name == "Türk Lirası" -> context.getString(R.string.currency_try).replace(" (₺)", "")
            name == "Amerikan Doları" || name == "US Dollar" -> "Amerikan Doları"
            name == "Euro" -> "Euro"
            name == "İngiliz Sterlini" || name == "British Pound" -> "İngiliz Sterlini"
            name == "İsviçre Frangı" || name == "Swiss Franc" -> "İsviçre Frangı"
            name == "Japon Yeni" || name == "Japanese Yen" -> "Japon Yeni"
            name == "Avustralya Doları" || name == "Australian Dollar" -> "Avustralya Doları"
            name == "Kanada Doları" || name == "Canadian Dollar" -> "Kanada Doları"
            name == "Gold" || name.contains("Gold", true) -> "Altın"
            name == "Silver" || name.contains("Silver", true) -> "Gümüş"
            name == "Copper" || name.contains("Copper", true) -> "Bakır"
            name == "Platinum" || name.contains("Platinum", true) -> "Platin"
            name == "Palladium" || name.contains("Palladium", true) -> "Paladyum"
            else -> name
        }
    }
}
