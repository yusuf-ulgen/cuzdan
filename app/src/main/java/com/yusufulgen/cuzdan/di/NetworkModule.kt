package com.yusufulgen.cuzdan.di

import com.yusufulgen.cuzdan.data.remote.api.BinanceApi
import com.yusufulgen.cuzdan.data.remote.api.YahooFinanceApi
import com.yusufulgen.cuzdan.data.remote.api.TefasApi
import com.yusufulgen.cuzdan.data.remote.interceptor.NetworkInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Simple in-memory CookieJar for Tefas to handle session and security cookies.
     */
    @Singleton
    private class TefasCookieJar : CookieJar {
        private val cookies = java.util.concurrent.CopyOnWriteArrayList<Cookie>()

        override fun saveFromResponse(url: HttpUrl, responseCookies: List<Cookie>) {
            if (responseCookies.isEmpty()) return
            synchronized(this) {
                val newNames = responseCookies.map { it.name }.toSet()
                // Safely remove existing cookies with same name
                val iterator = cookies.iterator()
                val toRemove = mutableListOf<Cookie>()
                while (iterator.hasNext()) {
                    val c = iterator.next()
                    if (c.name in newNames) toRemove.add(c)
                }
                cookies.removeAll(toRemove)
                cookies.addAll(responseCookies)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return synchronized(this) {
                cookies.filter { it.matches(url) }
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(NetworkInterceptor())
            .build()
    }

    @Provides
    @Singleton
    @Named("TefasOkHttpClient")
    fun provideTefasOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .cookieJar(TefasCookieJar()) // Critical for WAF bypass
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(NetworkInterceptor())
            .build()
    }

    @Provides
    @Singleton
    @Named("BinanceRetrofit")
    fun provideBinanceRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("YahooRetrofit")
    fun provideYahooRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://query2.finance.yahoo.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("TefasRetrofit")
    fun provideTefasRetrofit(@Named("TefasOkHttpClient") okHttpClient: OkHttpClient): Retrofit {
        val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .create()
        return Retrofit.Builder()
            .baseUrl("https://www.tefas.gov.tr/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideBinanceApi(@Named("BinanceRetrofit") retrofit: Retrofit): BinanceApi {
        return retrofit.create(BinanceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideYahooFinanceApi(@Named("YahooRetrofit") retrofit: Retrofit): YahooFinanceApi {
        return retrofit.create(YahooFinanceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTefasApi(@Named("TefasRetrofit") retrofit: Retrofit): TefasApi {
        return retrofit.create(TefasApi::class.java)
    }
}
