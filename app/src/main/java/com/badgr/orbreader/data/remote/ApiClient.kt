package com.badgr.orbreader.data.remote

import com.badgr.orbreader.BuildConfig
import com.badgr.orbreader.config.ApiConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // BODY crashes release builds on large uploads (OOM materialising the body string).
            // BASIC keeps status/URL visible in logcat without copying body bytes.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // Render cold-start (~50s) + processing
            .writeTimeout(300, TimeUnit.SECONDS)  // 100 MB on 4G (~10 Mbps) takes ~80s — 300s covers slow connections
            .build()
    }

    private val retrofit: Retrofit by lazy {
        val baseUrl = ApiConfig.BASE_URL.trimEnd('/') + "/"
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val convertApi: ConvertApi by lazy { retrofit.create(ConvertApi::class.java) }

    val summarizeApi: SummarizeApi by lazy { retrofit.create(SummarizeApi::class.java) }
}
