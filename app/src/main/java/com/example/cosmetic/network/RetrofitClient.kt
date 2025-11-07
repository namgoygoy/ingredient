package com.example.cosmetic.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 클라이언트 싱글톤 객체
 */
object RetrofitClient {
    
    // 백엔드 서버 URL (로컬 개발 환경)
    // 실제 디바이스에서는 컴퓨터의 IP 주소로 변경 필요 (예: "http://192.168.0.100:5000")
    private const val BASE_URL = "http://10.0.2.2:5000/"  // Android 에뮬레이터용
    
    // 실제 디바이스 사용 시: 컴퓨터의 로컬 IP 주소 사용
    // 예: "http://192.168.0.100:5000/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY  // 요청/응답 로그 출력
    }
    
    private val okHttpClient = OkHttpClient.Builder()
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

