package com.example.cuzdan.ui.assets

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.databinding.FragmentSymbolSearchBinding
import com.example.cuzdan.ui.markets.MarketAdapter
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal

@AndroidEntryPoint
class SymbolSearchFragment : Fragment() {

    private var _binding: FragmentSymbolSearchBinding? = null
    private val binding get() = _binding!!
    
    private var assetType: String? = null
    private lateinit var adapter: MarketAdapter
    
    // Predefined popular symbols
    private val popularSymbols = mapOf(
        "KRIPTO" to listOf(
            Asset(symbol = "BTCUSDT", name = "Bitcoin", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.KRIPTO),
            Asset(symbol = "ETHUSDT", name = "Ethereum", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.KRIPTO),
            Asset(symbol = "SOLUSDT", name = "Solana", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.KRIPTO),
            Asset(symbol = "AVAXUSDT", name = "Avalanche", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.KRIPTO)
        ),
        "BIST" to listOf(
            Asset(symbol = "THYAO.IS", name = "Türk Hava Yolları", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.BIST),
            Asset(symbol = "SASA.IS", name = "Sasa Polyester", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.BIST),
            Asset(symbol = "EREGL.IS", name = "Ereğli Demir Çelik", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.BIST),
            Asset(symbol = "KCHOL.IS", name = "Koç Holding", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.BIST)
        ),
        "DOVIZ" to listOf(
            Asset(symbol = "TRY=X", name = "USD/TRY", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.DOVIZ),
            Asset(symbol = "EURTRY=X", name = "EUR/TRY", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.DOVIZ),
            Asset(symbol = "GBPTRY=X", name = "GBP/TRY", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.DOVIZ)
        ),
        "ALTIN" to listOf(
            Asset(symbol = "GC=F", name = "Ons Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.ALTIN),
            Asset(symbol = "GRAM_ALTIN", name = "Gram Altın", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.ALTIN),
            Asset(symbol = "SI=F", name = "Gümüş", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.ALTIN)
        ),
        "FON" to listOf(
            Asset(symbol = "MAC", name = "Marmara Capital Hisse Senedi Fonu", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.FON),
            Asset(symbol = "TTEE", name = "Teb Portföy BIST Teknoloji Fonu", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.FON),
            Asset(symbol = "IJP", name = "İş Portföy Yabancı Hisse Senedi Fonu", amount = BigDecimal.ZERO, averageBuyPrice = BigDecimal.ZERO, currentPrice = BigDecimal.ZERO, assetType = AssetType.FON)
        )
    )

    private var currentList: List<Asset> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSymbolSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearch()
        
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        loadSymbols()
    }

    private fun setupRecyclerView() {
        adapter = MarketAdapter()
        binding.recyclerSymbols.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSymbols.adapter = adapter
        
        // Bu adapter'a bir tıklama özelliği eklememişiz normal MarketAdapter'da, 
        // ama varlık eklemek için tıklama lazım. 
        // Not: Gerçek uygulamada yeni bir AssetAddBottomSheet açılmalı.
    }

    private fun setupSearch() {
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadSymbols() {
        assetType = arguments?.getString("assetType")
        currentList = popularSymbols[assetType] ?: emptyList()
        adapter.setItems(currentList)
        binding.textTitle.text = "${assetType ?: ""} Varlıkları"
    }

    private fun filter(query: String) {
        val filtered = currentList.filter {
            it.name.contains(query, ignoreCase = true) || it.symbol.contains(query, ignoreCase = true)
        }
        adapter.setItems(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
