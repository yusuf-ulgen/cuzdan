package com.example.cuzdan.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuzdan.data.local.entity.Asset
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.data.repository.AssetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SymbolSearchUiState(
    val results: List<Asset> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SymbolSearchViewModel @Inject constructor(
    private val repository: AssetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SymbolSearchUiState())
    val uiState: StateFlow<SymbolSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun loadInitialSymbols(type: AssetType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val marketAssets = repository.getMarketAssets(type)
                _uiState.update { it.copy(results = marketAssets, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Yükleme hatası: ${e.localizedMessage}") }
            }
        }
    }

    fun search(query: String, type: AssetType) {
        searchJob?.cancel()
        if (query.isBlank()) {
            loadInitialSymbols(type)
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // Debounce
            _uiState.update { it.copy(isLoading = true) }
            val searchResults = repository.searchAssets(query, type)
            _uiState.update { it.copy(results = searchResults, isLoading = false) }
        }
    }

    fun saveAsset(asset: Asset) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.addAsset(asset)
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            // Success event or navigation can be handled here if needed
        }
    }
}
