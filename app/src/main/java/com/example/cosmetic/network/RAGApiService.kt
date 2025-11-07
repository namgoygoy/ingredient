package com.example.cosmetic.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * RAG 서버 API 인터페이스
 */
interface RAGApiService {
    
    /**
     * 제품 성분 분석 API
     * @param request 성분 리스트와 피부 타입을 포함한 요청 객체
     * @return 분석 결과 응답
     */
    @POST("analyze_product")
    fun analyzeProduct(@Body request: AnalyzeProductRequest): Call<AnalyzeProductResponse>
}

