package com.example.cuzdan.util

/**
 * Uygulama genelinde veri durumlarını (Yükleniyor, Başarı, Hata) sarmalamak için kullanılan sınıf.
 * MVVM mimarisinde ViewModel ve UI arasındaki iletişimi standartlaştırır.
 */
sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}
