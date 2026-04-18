package com.yusufulgen.cuzdan.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yusufulgen.cuzdan.data.local.entity.PriceAlertCondition
import com.yusufulgen.cuzdan.data.repository.AssetRepository
import com.yusufulgen.cuzdan.util.NotificationHelper
import com.yusufulgen.cuzdan.util.formatCurrency
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
                    if (alert.baselinePrice == null) {
                        // İlk çalıştırma: Başlangıç fiyatını kaydet ama ötme
                        repository.updatePriceAlert(alert.copy(baselinePrice = currentPrice))
                        return@forEach
                    }

                    val isTriggered = when (alert.condition) {
                        PriceAlertCondition.ABOVE -> currentPrice >= alert.targetPrice
                        PriceAlertCondition.EQUALS -> currentPrice.compareTo(alert.targetPrice) == 0
                        PriceAlertCondition.BELOW -> currentPrice <= alert.targetPrice
                    }

                    if (isTriggered) {
                        val conditionText = when (alert.condition) {
                            PriceAlertCondition.ABOVE -> applicationContext.getString(com.yusufulgen.cuzdan.R.string.alert_above)
                            PriceAlertCondition.EQUALS -> applicationContext.getString(com.yusufulgen.cuzdan.R.string.alert_equals)
                            PriceAlertCondition.BELOW -> applicationContext.getString(com.yusufulgen.cuzdan.R.string.alert_below)
                        }
                        NotificationHelper.showPriceAlertNotification(
                            applicationContext,
                            "${alert.name} - ${alert.symbol}",
                            "${alert.targetPrice.formatCurrency()} $conditionText. ${applicationContext.getString(com.yusufulgen.cuzdan.R.string.alert_current_price)}: ${currentPrice.formatCurrency()}",
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
