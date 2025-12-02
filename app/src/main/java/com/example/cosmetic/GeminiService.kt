package com.example.cosmetic

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI 서비스
 * 화장품 성분 정보 생성 및 분석
 */
class GeminiService(private val apiKey: String) {
    
    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 2048  // 토큰 제한 증가
        },
        // 화장품 성분 정보 생성 시 안전성 필터 완화
        // HIGH 위험도만 차단하여 화장품 성분 정보가 정상적으로 생성되도록 설정
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH),
        )
    )
    
    private fun isApiKeyAvailable(): Boolean = apiKey.isNotBlank()
    
    /**
     * 성분의 기능(목적) 정보 생성
     */
    suspend fun generateIngredientPurpose(ingredientName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w("GeminiService", "API key is missing. Falling back.")
                    return@withContext "정보를 불러올 수 없습니다."
                }
                val prompt = """
                    화장품 성분 "${ingredientName}"의 주요 기능과 목적을 한 문장으로 간단히 설명해주세요.
                    
                    예시 형식:
                    - "보습": 피부에 수분을 공급하고 유지하는 기능
                    - "항산화": 피부 노화를 방지하고 자유 라디칼을 중화하는 기능
                    
                    "${ingredientName}"의 기능:
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "정보 생성 실패"
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                Log.w("GeminiService", "Content blocked by safety filter for: $ingredientName")
                "정보를 불러올 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                Log.w("GeminiService", "Response stopped: ${e.message}")
                "정보를 불러올 수 없습니다."
            } catch (e: Exception) {
                Log.e("GeminiService", "generateIngredientPurpose failed", e)
                e.printStackTrace()
                "정보를 불러올 수 없습니다."
            }
        }
    }
    
    /**
     * 영문 성분 설명을 한국어로 번역 및 요약
     * ingredients.json의 영문 description을 한국어로 변환
     */
    suspend fun translateIngredientDescription(ingredientName: String, englishDescription: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w("GeminiService", "API key is missing. Falling back.")
                    return@withContext "설명을 불러올 수 없습니다."
                }
                val prompt = """
                    다음은 화장품 성분 "${ingredientName}"에 대한 영문 설명입니다.
                    이 내용을 한국어로 번역하여 일반 사용자가 이해하기 쉽도록 2-3 문장으로 요약해주세요.
                    
                    주요 효과, 피부 타입 적합성, 주의사항을 포함하되 전문적이면서도 쉬운 한국어로 작성해주세요.
                    
                    영문 설명:
                    ${englishDescription.take(1000)}
                    
                    한국어 요약:
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "설명을 생성할 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                Log.w("GeminiService", "Content blocked by safety filter for: $ingredientName")
                "설명을 불러올 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                Log.w("GeminiService", "Response stopped: ${e.message}")
                "설명을 불러올 수 없습니다."
            } catch (e: Exception) {
                Log.e("GeminiService", "translateIngredientDescription failed", e)
                e.printStackTrace()
                "설명을 불러올 수 없습니다."
            }
        }
    }
    
    /**
     * 성분의 상세 설명 생성
     */
    suspend fun generateIngredientDescription(ingredientName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w("GeminiService", "API key is missing. Falling back.")
                    return@withContext "설명을 불러올 수 없습니다."
                }
                val prompt = """
                    화장품 성분 "${ingredientName}"에 대한 상세 설명을 2-3 문장으로 작성해주세요.
                    다음 내용을 포함해주세요:
                    1. 성분의 주요 효과와 작용 메커니즘
                    2. 어떤 피부 타입에 적합한지
                    3. 주의사항이 있다면 간단히 언급
                    
                    전문적이면서도 일반 사용자가 이해하기 쉽게 한국어로 작성해주세요.
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "상세 설명을 생성할 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                Log.w("GeminiService", "Content blocked by safety filter for: $ingredientName")
                "설명을 불러올 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                Log.w("GeminiService", "Response stopped: ${e.message}")
                "설명을 불러올 수 없습니다."
            } catch (e: Exception) {
                Log.e("GeminiService", "generateIngredientDescription failed", e)
                e.printStackTrace()
                "설명을 불러올 수 없습니다."
            }
        }
    }
    
    /**
     * 성분의 피부 타입 적합성 생성
     */
    suspend fun generateSkinTypeSuitability(ingredientName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w("GeminiService", "API key is missing. Falling back.")
                    return@withContext "모든 피부 타입"
                }
                val prompt = """
                    화장품 성분 "${ingredientName}"이 어떤 피부 타입에 적합하고 피해야 할 피부 타입이 무엇인지 알려주세요.
                    
                    다음 형식으로 답변해주세요:
                    "권장: [피부 타입들], 주의: [피부 타입들]"
                    
                    피부 타입: 지성, 건성, 민감성, 여드름성, 중성
                    모든 피부에 적합하면 "모든 피부 타입"이라고 답변해주세요.
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "모든 피부 타입"
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                Log.w("GeminiService", "Content blocked by safety filter for: $ingredientName")
                "모든 피부 타입"
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                Log.w("GeminiService", "Response stopped: ${e.message}")
                "모든 피부 타입"
            } catch (e: Exception) {
                Log.e("GeminiService", "generateSkinTypeSuitability failed", e)
                e.printStackTrace()
                "모든 피부 타입"
            }
        }
    }
    
    /**
     * RAG 서버 분석 리포트 개선 (서버 리포트가 부족할 경우)
     * 서버에서 이미 좋은 리포트를 생성하므로, 서버 리포트가 너무 짧거나 일반적일 때만 사용
     */
    suspend fun enhanceProductAnalysisSummary(
        serverReport: String,
        ingredients: List<String>,
        goodMatches: List<String>,
        badMatches: List<String>
    ): String {
        // 서버 리포트가 충분히 상세하면 그대로 사용
        if (serverReport.length > 100 && !serverReport.contains("분석 중") && !serverReport.contains("오류")) {
            return serverReport
        }
        
        // 서버 리포트가 부족할 경우에만 Gemini로 생성
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w("GeminiService", "API key is missing. Using server report.")
                    return@withContext serverReport
                }
                val ingredientsList = ingredients.take(10).joinToString(", ")
                val goodIngredients = goodMatches.take(5).joinToString(", ")
                val badIngredients = badMatches.take(3).joinToString(", ")
                
                val prompt = """
                    다음 화장품의 성분을 분석하여 종합 평가를 3-4 문장으로 작성해주세요.
                    
                    전체 성분: $ingredientsList
                    좋은 성분: ${goodIngredients.ifEmpty { "없음" }}
                    주의 성분: ${badIngredients.ifEmpty { "없음" }}
                    
                    다음 내용을 포함해주세요:
                    1. 제품의 주요 효능 (보습, 진정, 미백 등)
                    2. 어떤 피부 타입에 적합한지
                    3. 주의해야 할 성분이 있다면 간단히 언급
                    4. 전반적인 제품 평가
                    
                    전문적이면서도 이해하기 쉽게 작성해주세요.
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                response.text?.trim().takeUnless { it.isNullOrBlank() } ?: serverReport
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                Log.w("GeminiService", "Content blocked by safety filter")
                serverReport
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                Log.w("GeminiService", "Response stopped: ${e.message}")
                serverReport
            } catch (e: Exception) {
                Log.e("GeminiService", "enhanceProductAnalysisSummary failed", e)
                e.printStackTrace()
                serverReport // 에러 시 서버 리포트 사용
            }
        }
    }
    
}

