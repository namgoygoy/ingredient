package com.example.cosmetic.network

import com.example.cosmetic.config.AppConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 클라이언트 싱글톤 객체
 * 
 * Base URL은 AppConfig에서 읽어옵니다.
 * local.properties 파일에 API_BASE_URL을 설정하세요:
 * API_BASE_URL=https://your-server-url.com/
 */ 
object RetrofitClient {
    
    // 백엔드 서버 URL (AppConfig에서 읽기)
    // local.properties에 API_BASE_URL 설정 필요
    private val BASE_URL = AppConfig.apiBaseUrl
    
    // ngrok 무료 버전 브라우저 경고 스킵 헤더
    private val ngrokHeaderInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("ngrok-skip-browser-warning", "true")
            .build()
        chain.proceed(newRequest)
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // 프로덕션에서는 로깅 비활성화 (보안 및 성능)
        level = if (com.example.cosmetic.config.AppConfig.isDebug) {
            HttpLoggingInterceptor.Level.BODY  // 디버그 모드: 요청/응답 로그 출력
        } else {
            HttpLoggingInterceptor.Level.NONE  // 프로덕션: 로깅 비활성화
        }
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

