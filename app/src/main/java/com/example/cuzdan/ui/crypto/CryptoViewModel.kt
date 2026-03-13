package com.example.cuzdan.ui.crypto

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
class CryptoViewModel @Inject constructor(
    private val repository: AssetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CryptoUiState>(CryptoUiState.Loading)
    val uiState: StateFlow<CryptoUiState> = _uiState.asStateFlow()

    init {
        observeCryptoAssets()
        refreshPrices()
    }

    private fun observeCryptoAssets() {
        viewModelScope.launch {
            repository.getCryptoAssets().collectLatest { assets ->
                _uiState.value = CryptoUiState.Success(assets)
            }
        }
    }

    fun refreshPrices() {
        viewModelScope.launch {
            repository.refreshCryptoPrices().collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        // Eğer zaten veri varsa (Success), tekrar loading göstermeyebiliriz
                        if (_uiState.value !is CryptoUiState.Success) {
                            _uiState.value = CryptoUiState.Loading
                        }
                    }
                    is Resource.Success -> {
                        // Veriler zaten observeCryptoAssets ile otomatik güncelleniyor (SSOT)
                        // Burada sadece SwipeRefresh'i durdurmak için bir şeyler yapılabilir 
                        // veya Success state teyit edilebilir.
                    }
                    is Resource.Error -> {
                        _uiState.value = CryptoUiState.Error(resource.message ?: "Bilinmeyen bir hata oluştu")
                    }
                }
            }
        }
    }
}

sealed class CryptoUiState {
    object Loading : CryptoUiState()
    data class Success(val assets: List<Asset>) : CryptoUiState()
    data class Error(val message: String) : CryptoUiState()
}
