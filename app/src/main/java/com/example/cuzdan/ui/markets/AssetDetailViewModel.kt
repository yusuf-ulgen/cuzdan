package com.example.cuzdan.ui.markets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.local.entity.PriceAlert
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.repository.PortfolioRepository
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class TransactionType { BUY, SELL }

data class AssetDetailUiState(
    val symbol: String = "",
    val name: String = "",
    val currentPrice: BigDecimal = BigDecimal.ZERO,
    val dailyChangePercentage: BigDecimal = BigDecimal.ZERO,
    val currency: String = "TRY",
    val history: List<Pair<Long, Double>> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
    val currentAmount: BigDecimal = BigDecimal.ZERO,
    val averageBuyPrice: BigDecimal = BigDecimal.ZERO,
    val portfolioName: String = "Ana Portföy",
    val transactionType: TransactionType = TransactionType.BUY
)


@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val portfolioRepository: PortfolioRepository,
    private val prefManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDetailUiState())
    val uiState: StateFlow<AssetDetailUiState> = _uiState.asStateFlow()

    fun init(symbol: String, name: String, typeString: String, currency: String = "TRY") {
        if (_uiState.value.symbol.isNotEmpty()) return 
        
        _uiState.update { it.copy(symbol = symbol, name = name, currency = currency) }
        loadHistory(symbol)
        observeCurrentPrice(symbol)
        loadExistingAsset(symbol)
    }

    private fun loadExistingAsset(symbol: String) {
        viewModelScope.launch {
            val portfolioId = prefManager.getSelectedPortfolioId().let { if (it == -1L) 1L else it }
            val portfolio = portfolioRepository.getPortfolioById(portfolioId)
            val existing = repository.getAssetBySymbolAndPortfolioId(symbol, portfolioId)
            
            _uiState.update { it.copy(
                currentAmount = existing?.amount ?: BigDecimal.ZERO,
                averageBuyPrice = existing?.averageBuyPrice ?: BigDecimal.ZERO,
                portfolioName = portfolio?.name ?: "Ana Portföy"
            ) }
        }
    }

    fun updateRange(range: String) {
        val interval = when (range) {
            "1d" -> "1m"
            "1w" -> "30m"
            "1mo" -> "1h"
            "1y" -> "1d"
            else -> "1d"
        }
        loadHistory(_uiState.value.symbol, range, interval)
    }


    private fun loadHistory(symbol: String, range: String = "1d", interval: String = "1m") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val history = repository.getAssetHistory(symbol, range, interval)
            _uiState.update { it.copy(history = history, isLoading = false) }
        }
    }

    private fun observeCurrentPrice(symbol: String) {
        viewModelScope.launch {
            repository.getMarketAssetBySymbolFlow(symbol).collect { marketAsset ->
                if (marketAsset != null) {
                    _uiState.update { state ->
                        state.copy(
                            currentPrice = marketAsset.currentPrice,
                            dailyChangePercentage = marketAsset.dailyChangePercentage
                        )
                    }
                }
            }
        }
    }

    fun setTransactionType(type: TransactionType) {
        _uiState.update { it.copy(transactionType = type) }
    }

    fun saveAsset(enteredAmount: BigDecimal, enteredCost: BigDecimal, typeString: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val currentAmount = state.currentAmount
            val transactionType = state.transactionType
            
            val newAmount = if (transactionType == TransactionType.BUY) {
                currentAmount.add(enteredAmount)
            } else {
                if (currentAmount < enteredAmount) {
                    _uiState.update { it.copy(errorMessage = "Yetersiz bakiye! Mevcut: $currentAmount") }
                    return@launch
                }
                currentAmount.subtract(enteredAmount)
            }

            var portfolioId = prefManager.getSelectedPortfolioId()
            if (portfolioId == -1L) portfolioId = 1L 

            val assetType = AssetType.valueOf(typeString)
            val isCash = assetType == AssetType.NAKIT
            
            // Ortalama maliyet hesabı
            val newAvgCost = if (transactionType == TransactionType.BUY) {
                if (newAmount > BigDecimal.ZERO) {
                    val totalCost = (currentAmount * state.averageBuyPrice) + (enteredAmount * enteredCost)
                    totalCost.divide(newAmount, 8, java.math.RoundingMode.HALF_UP)
                } else enteredCost
            } else {
                state.averageBuyPrice // Satışta maliyet değişmez
            }

            val finalCurrentPrice = if (isCash) BigDecimal.ONE else state.currentPrice
            val finalAvgCost = if (isCash) BigDecimal.ONE else newAvgCost
            
            val asset = Asset(
                symbol = state.symbol,
                name = state.name,
                amount = newAmount,
                averageBuyPrice = finalAvgCost,
                currentPrice = finalCurrentPrice,
                dailyChangePercentage = if (isCash) BigDecimal.ZERO else state.dailyChangePercentage,
                assetType = assetType,
                portfolioId = portfolioId,
                currency = if (isCash) "TRY" else state.currency
            )

            repository.addAsset(asset)
            _uiState.update { it.copy(isSaved = true, currentAmount = newAmount, averageBuyPrice = finalAvgCost) }
        }
    }

    fun deleteAsset() {
        viewModelScope.launch {
            val state = _uiState.value
            val portfolioId = prefManager.getSelectedPortfolioId().let { if (it == -1L) 1L else it }
            val asset = repository.getAssetBySymbolAndPortfolioId(state.symbol, portfolioId)
            if (asset != null) {
                repository.deleteAsset(asset)
            }
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun setPriceAlert(alert: PriceAlert) {
        viewModelScope.launch {
            repository.insertPriceAlert(alert)
        }
    }
}
