package com.example.cuzdan.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.PortfolioHistory
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.util.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed class ChartPeriod {
    object SevenDays : ChartPeriod()
    object OneMonth : ChartPeriod()
    object AllTime : ChartPeriod()
    data class Custom(val startDate: Long, val endDate: Long) : ChartPeriod()
}

data class ChartUiState(
    val dataPoints: List<PortfolioHistory> = emptyList(),
    val isLoading: Boolean = false,
    val period: ChartPeriod = ChartPeriod.SevenDays,
    val error: String? = null
)

@HiltViewModel
class ProfitLossChartViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val prefManager: PreferenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartUiState())
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    init {
        fetchData()
    }

    fun setPeriod(period: ChartPeriod) {
        _uiState.update { it.copy(period = period) }
        fetchData()
    }

    private fun fetchData() {
        val portfolioId = prefManager.getSelectedPortfolioId()
        val period = _uiState.value.period

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val portfolio = if (portfolioId != -1L) assetRepository.getPortfolioById(portfolioId) else null
                val creationDate = portfolio?.createdAt ?: 0L

                val data = when (period) {
                    ChartPeriod.SevenDays -> assetRepository.reconstructPortfolioHistory(portfolioId, "7d")
                    ChartPeriod.OneMonth -> assetRepository.reconstructPortfolioHistory(portfolioId, "1mo")
                    ChartPeriod.AllTime -> assetRepository.reconstructPortfolioHistory(portfolioId, "max")
                    is ChartPeriod.Custom -> {
                         assetRepository.reconstructPortfolioHistory(portfolioId, "max")
                             .filter { it.date >= period.startDate && it.date <= period.endDate }
                    }
                }.filter { it.date >= creationDate }

                _uiState.update { it.copy(dataPoints = data, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
