package com.example.cuzdan.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.data.local.entity.AssetType
import com.example.cuzdan.util.Resource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect

@HiltWorker
class PriceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AssetRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Sadece Fon fiyatlarını yenile (Günde bir kez yeterli)
            repository.refreshMarketAssets(AssetType.FON).collect { /* handled */ }
            repository.refreshOwnedFundPrices().collect { /* handled */ }

            // Not: Kripto, BIST ve Döviz fiyatları artık real-time poller (5s) ile güncel tutulacak.

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
