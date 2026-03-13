package com.example.cuzdan.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * API limitlerine (HTTP 429) karşı otomatik yeniden deneme mekanizması.
 * Planlanan 2-3 saniye bekleme süresi ile isteği tekrar gönderir.
 */
class RetryInterceptor : Interceptor {
    
    private val maxRetries = 3
    private val retryDelayMs = 2000L

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0

        while (!response.isSuccessful && response.code == 429 && tryCount < maxRetries) {
            tryCount++
            
            // Log veya uyarı mekanizması buraya eklenebilir
            println("RetryInterceptor: 429 hatası alındı. Deneme $tryCount / $maxRetries. Bekleniyor...")
            
            try {
                TimeUnit.MILLISECONDS.sleep(retryDelayMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException(e)
            }

            // İsteği tekrar dene
            response.close()
            response = chain.proceed(request)
        }

        return response
    }
}
