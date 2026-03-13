package com.example.cuzdan.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AssetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeOtherAssets()
        refreshPrices()
    }

    private fun observeOtherAssets() {
        viewModelScope.launch {
            repository.getOtherAssets().collectLatest { assets ->
                _uiState.value = DashboardUiState.Success(assets)
            }
        }
    }

    fun refreshPrices() {
        viewModelScope.launch {
            repository.refreshYahooPrices().collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        if (_uiState.value !is DashboardUiState.Success) {
                            _uiState.value = DashboardUiState.Loading
                        }
                    }
                    is Resource.Success -> {
                        // SSOT sayesinde veriler otomatik güncellenecek
                    }
                    is Resource.Error -> {
                        _uiState.value = DashboardUiState.Error(resource.message ?: "Veriler güncellenemedi")
                    }
                }
            }
        }
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val assets: List<Asset>) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}