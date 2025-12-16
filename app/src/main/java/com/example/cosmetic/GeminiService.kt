package com.example.cosmetic

import android.util.Log
import com.example.cosmetic.Constants.Analysis.MIN_DESCRIPTION_LENGTH
import com.example.cosmetic.Constants.Gemini.DESCRIPTION_MAX_LENGTH
import com.example.cosmetic.Constants.Gemini.DESCRIPTION_SUMMARY_MAX_LENGTH
import com.example.cosmetic.Constants.Gemini.LRU_CACHE_MAX_SIZE
import com.example.cosmetic.Constants.Gemini.USER_FRIENDLY_EXPLANATION_MAX_LENGTH
import com.example.cosmetic.Constants.Gemini.MAX_OUTPUT_TOKENS
import com.example.cosmetic.Constants.Gemini.MODEL_NAME
import com.example.cosmetic.Constants.Gemini.PURPOSE_MAX_LENGTH
import com.example.cosmetic.Constants.Gemini.SHORT_TEXT_LENGTH
import com.example.cosmetic.Constants.Gemini.SHORT_TRANSLATION_MAX_LENGTH
import com.example.cosmetic.Constants.Gemini.TEMPERATURE
import com.example.cosmetic.Constants.Gemini.TOP_K
import com.example.cosmetic.Constants.Gemini.TOP_P
import com.example.cosmetic.Constants.LogTag.GEMINI_SERVICE
import com.example.cosmetic.Constants.Parsing.KOREAN_TEXT_THRESHOLD
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI 서비스를 제공하는 클래스입니다.
 * 
 * Google의 Gemini AI 모델을 사용하여 화장품 성분 정보를 생성하고 분석합니다.
 * 
 * 주요 기능:
 * - 성분의 기능(purpose) 정보 생성
 * - 영문 성분 설명을 한국어로 번역
 * - 성분의 상세 설명 생성
 * - 성분의 피부 타입 적합성 생성
 * - 짧은 텍스트 번역 (뱃지 클릭용)
 * - 제품 분석 리포트 개선
 * 
 * 성능 최적화:
 * - 캐싱 메커니즘: 이미 생성된 정보를 메모리에 저장하여 재사용
 * - API 키 검증: API 키가 없으면 fallback 메시지 반환
 * - 예외 처리: 각 예외 타입에 맞는 적절한 처리
 * 
 * 사용 모델:
 * - gemini-2.5-flash: 빠른 응답 속도를 위한 경량 모델
 * 
 * @param apiKey Gemini API 키 (BuildConfig.GEMINI_API_KEY에서 주입)
 * 
 * @see DetailsFragment 뱃지 클릭 시 번역 기능 사용
 * @see ResultsFragment 성분 상세 정보 생성 시 사용
 */
class GeminiService(private val apiKey: String) {
    
    private val model = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = TEMPERATURE  // 더 일관된 응답을 위해 낮춤
            topK = TOP_K
            topP = TOP_P
            maxOutputTokens = MAX_OUTPUT_TOKENS  // 토큰 제한 증가
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
    
    // LRU 캐시: 최대 100개까지만 저장하여 메모리 누수 방지
    // 오래된 항목은 자동으로 삭제되어 OutOfMemoryError 위험 방지
    private val purposeCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > LRU_CACHE_MAX_SIZE // 최대 100개 캐시 항목 유지
        }
    }
    
    private val descriptionCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > LRU_CACHE_MAX_SIZE
        }
    }
    
    private val suitabilityCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > LRU_CACHE_MAX_SIZE
        }
    }
    
    private val translationCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > LRU_CACHE_MAX_SIZE
        }
    }
    
    // 사용자 친화적 설명 캐시 (뱃지 클릭 시 표시되는 설명)
    private val userFriendlyExplanationCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > LRU_CACHE_MAX_SIZE
        }
    }
    
    /**
     * API 키가 사용 가능한지 확인합니다.
     * 
     * @return API 키가 비어있지 않으면 true, 그렇지 않으면 false
     */
    private fun isApiKeyAvailable(): Boolean = apiKey.isNotBlank()
    
    /**
     * 성분의 기능(목적) 정보를 생성합니다.
     * 
     * Gemini AI를 사용하여 화장품 성분의 주요 기능을 간단히 설명합니다.
     * 
     * 처리 과정:
     * 1. 캐시 확인: 이미 생성된 정보가 있으면 즉시 반환
     * 2. API 키 검증: API 키가 없으면 fallback 메시지 반환
     * 3. Gemini AI 호출: 성분명을 기반으로 기능 정보 생성
     * 4. 캐시 저장: 생성된 정보를 메모리에 저장
     * 
     * 예외 처리:
     * - SerializationException: 안전성 필터에 의해 차단된 경우
     * - ResponseStoppedException: 응답이 중단된 경우
     * - 기타 예외: 로그 기록 후 fallback 메시지 반환
     * 
     * @param ingredientName 기능 정보를 생성할 성분명
     * @return 성분의 기능 설명 (20자 이내), 실패 시 "정보를 불러올 수 없습니다."
     * 
     * @throws Exception 네트워크 오류 또는 API 오류 발생 시
     */
    suspend fun generateIngredientPurpose(ingredientName: String): String {
        // 캐시 확인
        purposeCache[ingredientName]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w(GEMINI_SERVICE, "API key is missing. Falling back.")
                    return@withContext "정보를 불러올 수 없습니다."
                }
                val prompt = """
                    화장품 성분 "${ingredientName}"의 주요 기능을 ${PURPOSE_MAX_LENGTH}자 이내로 간단히 답해주세요.
                    예: "피부 보습 및 수분 유지"
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                val result = response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "정보 생성 실패"
                // 캐시 저장
                purposeCache[ingredientName] = result
                result
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                // 안전성 필터에 의한 차단 (예상된 예외)
                Log.w(GEMINI_SERVICE, "Content blocked by safety filter for: $ingredientName")
                "정보를 불러올 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                // 응답 중단 (예상된 예외)
                Log.w(GEMINI_SERVICE, "Response stopped: ${e.message}")
                "정보를 불러올 수 없습니다."
            } catch (e: java.io.IOException) {
                // 네트워크 오류 (재시도 가능)
                Log.e(GEMINI_SERVICE, "Network error in generateIngredientPurpose for: $ingredientName", e)
                "정보를 불러올 수 없습니다."
            } catch (e: Exception) {
                // CRITICAL: OutOfMemoryError, StackOverflowError 등 시스템 에러는 여기 들어오지 않음
                // Exception만 잡아서 앱 크래시 방지, 시스템 에러는 상위로 전파하여 앱 재시작
                Log.e(GEMINI_SERVICE, "Unexpected error in generateIngredientPurpose for: $ingredientName", e)
                "정보를 불러올 수 없습니다."
            }
        }
    }
    
    /**
     * 영문 성분 설명을 한국어로 번역 및 요약합니다.
     * 
     * ingredients.json에 저장된 영문 description을 자연스러운 한국어로 번역합니다.
     * 텍스트 길이에 따라 다른 프롬프트를 사용하여 최적의 번역을 제공합니다.
     * 
     * 처리 과정:
     * 1. 캐시 키 생성: 성분명 + 설명 해시값
     * 2. 캐시 확인: 이미 번역된 정보가 있으면 즉시 반환
     * 3. 텍스트 길이에 따른 프롬프트 선택:
     *    - 150자 미만: 간단 번역 (50자 이내)
     *    - 150자 이상: 요약 번역 (100자 이내, 2문장)
     * 4. Gemini AI 호출
     * 5. 캐시 저장
     * 
     * 캐싱 전략:
     * - 캐시 키: "성분명:해시값" 형식
     * - 동일한 설명은 재번역하지 않음
     * 
     * @param ingredientName 번역할 성분명 (프롬프트에 포함)
     * @param englishDescription 영문 설명 텍스트
     * @return 한국어로 번역된 설명, 실패 시 "설명을 불러올 수 없습니다."
     * 
     * @throws Exception 네트워크 오류 또는 API 오류 발생 시
     */
    suspend fun translateIngredientDescription(ingredientName: String, englishDescription: String): String {
        // 캐시 키 생성 (성분명 + 설명 해시)
        val cacheKey = "$ingredientName:${englishDescription.hashCode()}"
        translationCache[cacheKey]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w(GEMINI_SERVICE, "API key is missing. Falling back.")
                    return@withContext "설명을 불러올 수 없습니다."
                }
                
                // 텍스트 길이에 따라 다른 프롬프트 사용
                val prompt = if (englishDescription.length < SHORT_TEXT_LENGTH) {
                    // 짧은 텍스트 (purpose 등): 간단 번역
                    """
                        다음 화장품 성분 설명을 자연스러운 한국어로 번역하세요:
                        "${englishDescription}"
                        
                        (${SHORT_TRANSLATION_MAX_LENGTH}자 이내로 핵심만 간결하게 번역)
                    """.trimIndent()
                } else {
                    // 긴 텍스트 (description): 요약 번역
                    """
                    화장품 성분 "${ingredientName}" 영문 설명을 한국어로 2문장 이내 요약:
                    ${englishDescription.take(500)}
                    
                    (${DESCRIPTION_SUMMARY_MAX_LENGTH}자 이내로 핵심만 답변)
                """.trimIndent()
                }
                
                val response = model.generateContent(prompt)
                val result = response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "설명을 생성할 수 없습니다."
                // 캐시 저장
                translationCache[cacheKey] = result
                result
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                // 안전성 필터에 의한 차단
                Log.w(GEMINI_SERVICE, "Content blocked by safety filter for: $ingredientName")
                "설명을 불러올 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                // 응답 중단
                Log.w(GEMINI_SERVICE, "Response stopped: ${e.message}")
                "설명을 불러올 수 없습니다."
            } catch (e: java.io.IOException) {
                // 네트워크 오류
                Log.e(GEMINI_SERVICE, "Network error in translateIngredientDescription for: $ingredientName", e)
                "설명을 불러올 수 없습니다."
            } catch (e: Exception) {
                // CRITICAL: 시스템 에러(OutOfMemoryError 등)는 잡지 않고 상위로 전파
                Log.e(GEMINI_SERVICE, "Unexpected error in translateIngredientDescription for: $ingredientName", e)
                "설명을 불러올 수 없습니다."
            }
        }
    }
    
    /**
     * 성분의 상세 설명을 생성합니다.
     * 
     * ingredients.json에 정보가 없거나 description이 비어있는 경우 호출됩니다.
     * Gemini AI를 사용하여 성분의 효과와 적합한 피부 타입을 설명합니다.
     * 
     * 처리 과정:
     * 1. 캐시 확인: 이미 생성된 정보가 있으면 즉시 반환
     * 2. API 키 검증
     * 3. Gemini AI 호출: 효과와 피부 타입 적합성을 2문장으로 설명
     * 4. 캐시 저장
     * 
     * 생성되는 설명 형식:
     * - 80자 이내로 간결하게
     * - 효과와 적합 피부 타입 포함
     * 
     * @param ingredientName 설명을 생성할 성분명
     * @return 성분의 상세 설명, 실패 시 "설명을 불러올 수 없습니다."
     * 
     * @throws Exception 네트워크 오류 또는 API 오류 발생 시
     */
    suspend fun generateIngredientDescription(ingredientName: String): String {
        // 캐시 확인
        descriptionCache[ingredientName]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w(GEMINI_SERVICE, "API key is missing. Falling back.")
                    return@withContext "설명을 불러올 수 없습니다."
                }
                val prompt = """
                    화장품 성분 "${ingredientName}"의 효과와 적합 피부타입을 2문장으로 설명해주세요.
                    (${DESCRIPTION_MAX_LENGTH}자 이내로 간결하게)
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                val result = response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "상세 설명을 생성할 수 없습니다."
                // 캐시 저장
                descriptionCache[ingredientName] = result
                result
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                // 안전성 필터에 의한 차단
                Log.w(GEMINI_SERVICE, "Content blocked by safety filter for: $ingredientName")
                "설명을 불러올 수 없습니다."
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                // 응답 중단
                Log.w(GEMINI_SERVICE, "Response stopped: ${e.message}")
                "설명을 불러올 수 없습니다."
            } catch (e: java.io.IOException) {
                // 네트워크 오류
                Log.e(GEMINI_SERVICE, "Network error in generateIngredientDescription for: $ingredientName", e)
                "설명을 불러올 수 없습니다."
            } catch (e: Exception) {
                // CRITICAL: 시스템 에러는 잡지 않고 상위로 전파
                Log.e(GEMINI_SERVICE, "Unexpected error in generateIngredientDescription for: $ingredientName", e)
                "설명을 불러올 수 없습니다."
            }
        }
    }
    
    /**
     * 성분의 피부 타입 적합성을 생성합니다.
     * 
     * RAG 서버에 정보가 없을 때 호출되어 Gemini AI로 피부 타입 적합성을 생성합니다.
     * 
     * 생성 형식:
     * - "권장: OO, 주의: OO" 형식
     * - 15자 이내로 간결하게
     * - 모두 적합한 경우 "모든 피부" 반환
     * 
     * 지원하는 피부 타입:
     * - 지성, 건성, 민감성, 여드름성, 중성
     * 
     * 처리 과정:
     * 1. 캐시 확인
     * 2. API 키 검증
     * 3. Gemini AI 호출
     * 4. 캐시 저장
     * 
     * @param ingredientName 적합성을 생성할 성분명
     * @return 피부 타입 적합성 문자열, 실패 시 "모든 피부 타입"
     * 
     * @throws Exception 네트워크 오류 또는 API 오류 발생 시
     */
    suspend fun generateSkinTypeSuitability(ingredientName: String): String {
        // 캐시 확인
        suitabilityCache[ingredientName]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w(GEMINI_SERVICE, "API key is missing. Falling back.")
                    return@withContext "모든 피부 타입"
                }
                val prompt = """
                    "${ingredientName}" 적합 피부타입을 "권장: OO, 주의: OO" 형식으로 15자 이내 답변.
                    (지성/건성/민감성/여드름성/중성 중 선택, 모두 적합시 "모든 피부")
                """.trimIndent()
                
                val response = model.generateContent(prompt)
                val result = response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "모든 피부 타입"
                // 캐시 저장
                suitabilityCache[ingredientName] = result
                result
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                // 안전성 필터에 의한 차단
                Log.w(GEMINI_SERVICE, "Content blocked by safety filter for: $ingredientName")
                "모든 피부 타입"
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                // 응답 중단
                Log.w(GEMINI_SERVICE, "Response stopped: ${e.message}")
                "모든 피부 타입"
            } catch (e: java.io.IOException) {
                // 네트워크 오류
                Log.e(GEMINI_SERVICE, "Network error in generateSkinTypeSuitability for: $ingredientName", e)
                "모든 피부 타입"
            } catch (e: Exception) {
                // CRITICAL: 시스템 에러는 잡지 않고 상위로 전파
                Log.e(GEMINI_SERVICE, "Unexpected error in generateSkinTypeSuitability for: $ingredientName", e)
                "모든 피부 타입"
            }
        }
    }
    
    /**
     * 짧은 텍스트를 한국어로 번역합니다.
     * 
     * DetailsFragment에서 뱃지를 클릭했을 때 표시되는 간단한 이유 설명을 번역합니다.
     * 매우 짧은 텍스트(20자 이내)로 번역하므로 빠른 응답이 가능합니다.
     * 
     * 사용 사례:
     * - 성분 뱃지 클릭 시 이유 설명 번역
     * - purpose 등 짧은 텍스트 번역
     * 
     * 처리 과정:
     * 1. 캐시 키 생성: "short:해시값"
     * 2. 캐시 확인
     * 3. API 키 검증 (없으면 원문 반환)
     * 4. Gemini AI 호출
     * 5. 캐시 저장
     * 
     * @param text 번역할 텍스트 (영어 또는 기타 언어)
     * @return 한국어로 번역된 텍스트 (20자 이내), 실패 시 원문 반환
     * 
     * @throws Exception 네트워크 오류 또는 API 오류 발생 시
     * 
     * @see DetailsFragment.showReasonBottomSheet 뱃지 클릭 시 이 메서드 호출
     */
    suspend fun translateShortText(text: String): String {
        // 캐시 키 생성
        val cacheKey = "short:${text.hashCode()}"
        translationCache[cacheKey]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w(GEMINI_SERVICE, "API key is missing. Falling back.")
                    return@withContext text // API 키 없으면 원문 반환
                }
                val prompt = """
                    다음 텍스트를 한국어로 ${PURPOSE_MAX_LENGTH}자 이내로 간결하게 번역해주세요.
                    텍스트: "$text"
                    번역:
                """.trimIndent()

                val response = model.generateContent(prompt)
                val result = response.text?.trim().takeUnless { it.isNullOrBlank() } ?: text
                // 캐시 저장
                translationCache[cacheKey] = result
                result
            } catch (e: java.io.IOException) {
                // 네트워크 오류
                Log.e(GEMINI_SERVICE, "Network error in translateShortText", e)
                text // 에러 시 원문 반환
            } catch (e: Exception) {
                // CRITICAL: 시스템 에러는 잡지 않고 상위로 전파
                Log.e(GEMINI_SERVICE, "Unexpected error in translateShortText", e)
                text // 에러 시 원문 반환
            }
        }
    }
    
    /**
     * 성분 뱃지 클릭 시 사용자가 이해하기 쉬운 설명을 생성합니다.
     * 
     * 주의 성분과 좋은 성분에 대해 일반 사용자가 쉽게 이해할 수 있는
     * 자연스러운 한국어 설명을 생성합니다.
     * 
     * 주요 특징:
     * - 전문 용어 없이 쉬운 한국어로 설명
     * - "왜" 주의해야 하는지 또는 "왜" 좋은 성분인지 명확히 설명
     * - 실질적인 조언 포함 (패치 테스트, 사용 방법 등)
     * 
     * 처리 과정:
     * 1. 캐시 키 생성: "성분명:타입"
     * 2. 캐시 확인
     * 3. API 키 검증 (없으면 기본 메시지 반환)
     * 4. 타입별 맞춤 프롬프트로 Gemini AI 호출
     * 5. 캐시 저장
     * 
     * @param ingredientName 성분명
     * @param ingredientType 성분 타입 ("good" 또는 "bad")
     * @param originalReason 원본 이유 설명 (영어 또는 한국어, 백엔드에서 전달받은 값)
     * @return 사용자 친화적인 한국어 설명 (2-3문장), 실패 시 기본 메시지 반환
     * 
     * @throws Exception 네트워크 오류 또는 API 오류 발생 시
     * 
     * @see DetailsFragment.showReasonBottomSheet 뱃지 클릭 시 이 메서드 호출
     */
    suspend fun generateUserFriendlyExplanation(
        ingredientName: String,
        ingredientType: String,
        originalReason: String
    ): String {
        // 캐시 키 생성
        val cacheKey = "$ingredientName:$ingredientType"
        userFriendlyExplanationCache[cacheKey]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                if (!isApiKeyAvailable()) {
                    Log.w(GEMINI_SERVICE, "API key is missing. Falling back to default message.")
                    return@withContext getDefaultExplanation(ingredientType)
                }
                
                val prompt = when (ingredientType) {
                    "bad" -> """
                        화장품 성분 "$ingredientName"이(가) 왜 주의가 필요한 성분인지 일반인이 쉽게 이해할 수 있도록 설명해주세요.
                        
                        참고 정보: $originalReason
                        
                        다음 조건을 따라주세요:
                        1. 전문 용어 없이 쉬운 한국어로 2-3문장으로 설명
                        2. 어떤 피부 타입이나 상황에서 주의해야 하는지 구체적으로 언급
                        3. 실질적인 조언 포함 (예: 패치 테스트 권장, 소량 사용 권장 등)
                        4. 너무 무섭게 쓰지 말고, 객관적이고 중립적인 톤 유지
                        5. 총 ${USER_FRIENDLY_EXPLANATION_MAX_LENGTH}자 이내로 작성
                        
                        예시: "이 성분은 민감한 피부에 자극을 줄 수 있어요. 특히 피부가 예민하거나 알레르기가 있다면 처음 사용 전 팔 안쪽에 먼저 테스트해보세요."
                    """.trimIndent()
                    
                    "good" -> """
                        화장품 성분 "$ingredientName"이(가) 왜 좋은 성분인지 일반인이 쉽게 이해할 수 있도록 설명해주세요.
                        
                        참고 정보: $originalReason
                        
                        다음 조건을 따라주세요:
                        1. 전문 용어 없이 쉬운 한국어로 2-3문장으로 설명
                        2. 이 성분이 피부에 어떤 좋은 효과를 주는지 구체적으로 언급
                        3. 어떤 피부 고민에 도움이 되는지 포함
                        4. 긍정적이지만 과장하지 않는 톤 유지
                        5. 총 ${USER_FRIENDLY_EXPLANATION_MAX_LENGTH}자 이내로 작성
                        
                        예시: "피부 깊숙이 수분을 채워주는 보습 성분이에요. 건조하거나 당기는 피부에 촉촉함을 오래 유지시켜줍니다."
                    """.trimIndent()
                    
                    else -> """
                        화장품 성분 "$ingredientName"에 대해 일반인이 쉽게 이해할 수 있도록 2-3문장으로 설명해주세요.
                        참고 정보: $originalReason
                        총 ${USER_FRIENDLY_EXPLANATION_MAX_LENGTH}자 이내로 작성해주세요.
                    """.trimIndent()
                }
                
                val response = model.generateContent(prompt)
                val result = response.text?.trim().takeUnless { it.isNullOrBlank() } 
                    ?: getDefaultExplanation(ingredientType)
                
                // 캐시 저장
                userFriendlyExplanationCache[cacheKey] = result
                result
                
            } catch (e: com.google.ai.client.generativeai.type.SerializationException) {
                Log.w(GEMINI_SERVICE, "Content blocked by safety filter for: $ingredientName")
                getDefaultExplanation(ingredientType)
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                Log.w(GEMINI_SERVICE, "Response stopped: ${e.message}")
                getDefaultExplanation(ingredientType)
            } catch (e: java.io.IOException) {
                Log.e(GEMINI_SERVICE, "Network error in generateUserFriendlyExplanation for: $ingredientName", e)
                getDefaultExplanation(ingredientType)
            } catch (e: Exception) {
                Log.e(GEMINI_SERVICE, "Unexpected error in generateUserFriendlyExplanation for: $ingredientName", e)
                getDefaultExplanation(ingredientType)
            }
        }
    }
    
    /**
     * 사용자 친화적 설명 생성 실패 시 반환할 기본 메시지를 생성합니다.
     * 
     * @param ingredientType 성분 타입 ("good" 또는 "bad")
     * @return 기본 설명 메시지
     */
    private fun getDefaultExplanation(ingredientType: String): String {
        return when (ingredientType) {
            "bad" -> "이 성분은 일부 피부 타입에 자극을 줄 수 있어요. 민감한 피부라면 먼저 소량으로 테스트해보시는 것을 권장합니다."
            "good" -> "피부에 좋은 효과를 주는 성분이에요. 꾸준히 사용하면 피부 개선에 도움이 됩니다."
            else -> "이 성분에 대한 정보입니다."
        }
    }
    
    /**
     * RAG 서버 분석 리포트를 개선합니다.
     * 
     * 서버 리포트가 충분히 상세하면 그대로 사용하고,
     * 부족한 경우에만 Gemini AI로 보완합니다.
     * 
     * 서버 리포트가 충분한 조건:
     * - 길이가 100자 이상
     * - "분석 중" 또는 "오류" 문자열 미포함
     * 
     * 개선 시 포함되는 내용:
     * 1. 제품의 주요 효능 (보습, 진정, 미백 등)
     * 2. 적합한 피부 타입
     * 3. 주의해야 할 성분 (있는 경우)
     * 4. 전반적인 제품 평가
     * 
     * 성능 최적화:
     * - 서버 리포트가 충분하면 Gemini 호출 생략
     * - 성분 리스트는 최대 5개만 사용 (프롬프트 길이 제한)
     * - 좋은 성분은 최대 3개, 주의 성분은 최대 2개만 사용
     * 
     * @param serverReport RAG 서버로부터 받은 분석 리포트
     * @param ingredients 전체 성분 리스트
     * @param goodMatches 좋은 성분명 리스트
     * @param badMatches 주의 성분명 리스트
     * @return 개선된 분석 리포트, 실패 시 서버 리포트 반환
     * 
     * @throws Exception 네트워크 오류 또는 API 오류 발생 시
     * 
     * @see DetailsFragment.generateEnhancedAnalysisSummary 이 메서드를 호출하는 Fragment
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
                val ingredientsList = ingredients.take(5).joinToString(", ")
                val goodIngredients = goodMatches.take(3).joinToString(", ")
                val badIngredients = badMatches.take(2).joinToString(", ")
                
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
            } catch (e: java.io.IOException) {
                // 네트워크 오류
                Log.e(GEMINI_SERVICE, "Network error in enhanceProductAnalysisSummary", e)
                serverReport // 에러 시 서버 리포트 사용
            } catch (e: Exception) {
                // CRITICAL: 시스템 에러는 잡지 않고 상위로 전파
                Log.e(GEMINI_SERVICE, "Unexpected error in enhanceProductAnalysisSummary", e)
                serverReport // 에러 시 서버 리포트 사용
            }
        }
    }
    
}

