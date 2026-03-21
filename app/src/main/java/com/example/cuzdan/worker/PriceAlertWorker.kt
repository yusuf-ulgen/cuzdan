package com.example.cuzdan.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cuzdan.data.local.entity.PriceAlertCondition
import com.example.cuzdan.data.repository.AssetRepository
import com.example.cuzdan.util.NotificationHelper
import com.example.cuzdan.util.formatCurrency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.math.BigDecimal

@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AssetRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val activeAlerts = repository.getActivePriceAlerts()
            if (activeAlerts.isEmpty()) return Result.success()

            activeAlerts.forEach { alert ->
                // Refresh price for the asset
                // Note: Simplified logic, we assume repository has a way to get latest price once
                val currentPrice = repository.getYahooPriceOnce(alert.symbol) 
                    ?: repository.getLatestPrice(alert.symbol).toString().toBigDecimalOrNull() // Fallback to DB

                if (currentPrice != null) {
                    val isTriggered = when (alert.condition) {
                        PriceAlertCondition.ABOVE -> currentPrice >= alert.targetPrice
                        PriceAlertCondition.BELOW -> currentPrice <= alert.targetPrice
                    }

                    if (isTriggered) {
                        val conditionText = if (alert.condition == PriceAlertCondition.ABOVE) "üzerine çıktı" else "altına düştü"
                        NotificationHelper.showPriceAlertNotification(
                            applicationContext,
                            "Fiyat Alarmı: ${alert.name}",
                            "${alert.symbol} fiyatı ${alert.targetPrice.formatCurrency()} $conditionText. Güncel: ${currentPrice.formatCurrency()}",
                            alert.id.toInt()
                        )
                        repository.markAlertAsTriggered(alert.id)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
