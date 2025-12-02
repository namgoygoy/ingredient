package com.example.cosmetic.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 클라이언트 싱글톤 객체
 */ 
object RetrofitClient {
    
    // 백엔드 서버 URL (ngrok 터널)
    // Forwarding: https://prefearfully-bimanous-carmon.ngrok-free.dev -> http://localhost:5000
    private const val BASE_URL = "https://prefearfully-bimanous-carmon.ngrok-free.dev/"
    
    // ngrok 무료 버전 브라우저 경고 스킵 헤더
    private val ngrokHeaderInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("ngrok-skip-browser-warning", "true")
            .build()
        chain.proceed(newRequest)
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY  // 요청/응답 로그 출력
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(ngrokHeaderInterceptor)  // ngrok 헤더 추가
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: RAGApiService = retrofit.create(RAGApiService::class.java)
}

