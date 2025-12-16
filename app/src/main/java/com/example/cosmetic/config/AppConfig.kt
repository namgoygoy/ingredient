package com.example.cosmetic.config

import com.example.cosmetic.BuildConfig

/**
 * 앱 전역 설정 관리 객체
 * 
 * API 키, Base URL 등 앱 전역 설정을 중앙화하여 관리합니다.
 * BuildConfig에서 값을 읽어와 환경별(개발/스테이징/프로덕션) 설정을 지원합니다.
 * 
 * 사용 예시:
 * ```kotlin
 * val apiUrl = AppConfig.apiBaseUrl
 * val geminiKey = AppConfig.geminiApiKey
 * ```
 */
object AppConfig {
    /**
     * API Base URL
     * local.properties의 API_BASE_URL에서 읽어옵니다.
     * 기본값: "http://localhost:5000/"
     */
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    
    /**
     * Gemini API Key
     * local.properties의 GEMINI_API_KEY에서 읽어옵니다.
     */
    val geminiApiKey: String = BuildConfig.GEMINI_API_KEY
    
    /**
     * 디버그 모드 여부
     * BuildConfig.DEBUG에서 읽어옵니다.
     */
    val isDebug: Boolean = BuildConfig.DEBUG
    
    /**
     * API Base URL이 유효한지 확인합니다.
     * 
     * @return URL이 비어있지 않고 유효한 형식이면 true
     */
    fun isApiBaseUrlValid(): Boolean {
        return apiBaseUrl.isNotBlank() && 
               (apiBaseUrl.startsWith("http://") || apiBaseUrl.startsWith("https://"))
    }
    
    /**
     * Gemini API Key가 설정되어 있는지 확인합니다.
     * 
     * @return API Key가 비어있지 않으면 true
     */
    fun isGeminiApiKeySet(): Boolean {
        return geminiApiKey.isNotBlank()
    }
}

