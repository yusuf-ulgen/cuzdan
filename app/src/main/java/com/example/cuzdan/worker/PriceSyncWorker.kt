package com.example.cuzdan.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cuzdan.data.repository.AssetRepository
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
            // Kripto fiyatlarını yenile
            repository.refreshCryptoPrices().collect { resource ->
                if (resource is Resource.Error) {
                    // Hata durumunda loglanabilir, ama işlemi durdurmuyoruz
                }
            }

            // BIST, Döviz ve Altın fiyatlarını yenile
            repository.refreshYahooPrices().collect { resource ->
                if (resource is Resource.Error) {
                    // Hata durumunda loglanabilir
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
