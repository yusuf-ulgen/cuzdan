package com.example.cuzdan.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.PriceAlert
import com.example.cuzdan.data.repository.AssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AlertsUiState(
    val active: List<PriceAlert> = emptyList(),
    val triggered: List<PriceAlert> = emptyList()
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    repository: AssetRepository
) : ViewModel() {
    val uiState: StateFlow<AlertsUiState> =
        repository.getAllPriceAlerts()
            .map { all ->
                AlertsUiState(
                    active = all.filter { it.isEnabled && !it.isTriggered },
                    triggered = all.filter { it.isTriggered }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())
}

