package com.example.cuzdan.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unified interceptor for handling network retries and HTTP 429 (Too Many Requests).
 * Implements exponential backoff for 429 errors.
 */
class NetworkInterceptor : Interceptor {

    private val maxRetries = 3
    private val initialDelayMs = 2000L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response
        var tryCount = 0

        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            // Initial network failure retry logic if needed, but usually handled by OkHttp
            // Or custom retry for IOException
            return handleRetry(chain, request, e)
        }

        while (!response.isSuccessful && response.code == 429 && tryCount < maxRetries) {
            tryCount++
            val backoffDelay = initialDelayMs * tryCount // Simple exponential-ish: 2s, 4s, 6s
            
            Log.d("NetworkInterceptor", "429 Too Many Requests for ${request.url}. Retry $tryCount/$maxRetries after ${backoffDelay}ms")
            
            response.close()
            
            try {
                TimeUnit.MILLISECONDS.sleep(backoffDelay)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during backoff", e)
            }
            
            response = chain.proceed(request)
        }

        return response
    }

    private fun handleRetry(chain: Interceptor.Chain, request: okhttp3.Request, exception: IOException): Response {
        var lastException = exception
        var tryCount = 0
        
        while (tryCount < maxRetries) {
            tryCount++
            Log.d("NetworkInterceptor", "Network failure (${lastException.message}). Retry $tryCount/$maxRetries...")
            
            try {
                TimeUnit.MILLISECONDS.sleep(initialDelayMs)
                return chain.proceed(request)
            } catch (e: IOException) {
                lastException = e
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during network retry", e)
            }
        }
        throw lastException
    }
}
