package com.yusufulgen.cuzdan.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0
        val maxLimit = 3

        while (!response.isSuccessful && tryCount < maxLimit) {
            if (response.code == 429) {
                Log.d("ErrorInterceptor", "429 Too Many Requests. Retrying... $tryCount")
                tryCount++
                try {
                    Thread.sleep(2000L * tryCount) // Exponential backoff-ish
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                response.close()
                response = chain.proceed(request)
            } else {
                break
            }
        }
        return response
    }
}
