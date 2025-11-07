package com.example.cosmetic.network

import com.google.gson.annotations.SerializedName

/**
 * 제품 분석 요청 데이터
 */
data class AnalyzeProductRequest(
    val ingredients: List<String>,
    @SerializedName("skin_type")
    val skinType: String
)

/**
 * 좋은 성분 매칭 결과
 */
data class GoodMatch(
    val name: String,
    val purpose: String
)

/**
 * 주의 성분 매칭 결과
 */
data class BadMatch(
    val name: String,
    val description: String
)

/**
 * 제품 분석 응답 데이터
 */
data class AnalyzeProductResponse(
    @SerializedName("analysis_report")
    val analysisReport: String,
    @SerializedName("good_matches")
    val goodMatches: List<GoodMatch>,
    @SerializedName("bad_matches")
    val badMatches: List<BadMatch>,
    val success: Boolean
)

