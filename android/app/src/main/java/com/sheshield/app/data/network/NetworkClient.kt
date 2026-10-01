package com.sheshield.app.data.network

import com.sheshield.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BODY
        else
            HttpLoggingInterceptor.Level.NONE
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: SheShieldApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL + "/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SheShieldApi::class.java)
    }
}
