package com.example.cuzdan.util

import android.util.Log
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.local.entity.AssetType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceSyncManager @Inject constructor(
    private val repository: AssetRepository
) {
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    data class SyncStatus(
        val lastUpdate: Long = 0,
        val isOffline: Boolean = false
    )

    fun startPolling() {
        if (syncJob?.isActive == true) return

        Log.d("PriceSyncManager", "Starting real-time polling (5s interval)")
        syncJob = scope.launch {
            while (isActive) {
                try {
                    // Update Crypto
                    repository.refreshCryptoPrices().collect { /* result handled in repo/db */ }
                    
                    // Update BIST, Currency, Gold (Yahoo)
                    repository.refreshYahooPrices().collect { /* result handled in repo/db */ }

                    // Update market lists (incl. USDTRY for conversions)
                    repository.refreshMarketAssets(AssetType.DOVIZ).collect { /* result handled in repo/db */ }
                    
                    // Update TEFAS Funds
                    repository.refreshOwnedFundPrices().collect { /* result handled in repo/db */ }
                    
                    _syncStatus.value = SyncStatus(lastUpdate = System.currentTimeMillis(), isOffline = false)
                    Log.d("PriceSyncManager", "Batch price update completed")
                } catch (e: Exception) {
                    _syncStatus.value = _syncStatus.value.copy(isOffline = true)
                    Log.e("PriceSyncManager", "Polling error: ${e.message}")
                }
                
                delay(60000) // 1 minute interval (more sustainable for Yahoo)
            }
        }
    }

    fun stopPolling() {
        Log.d("PriceSyncManager", "Stopping real-time polling")
        syncJob?.cancel()
        syncJob = null
    }
}
