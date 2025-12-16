package com.example.cosmetic.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 성분 데이터 캐시 및 인덱스 관리 클래스
 * 
 * 효율성 개선:
 * - ingredients.json을 한 번만 로드하고 인덱스 생성
 * - O(n) 선형 검색 대신 O(1) 해시 테이블 검색 사용
 * - 여러 Fragment에서 공유 가능한 싱글톤 패턴
 * 
 * 사용 예시:
 * ```kotlin
 * val cache = IngredientCache.getInstance(requireContext())
 * val ingredient = cache.findByName("글리세린")
 * ```
 */
class IngredientCache private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: IngredientCache? = null
        
        /**
         * 싱글톤 인스턴스를 가져옵니다.
         * 
         * @param context Application Context
         * @return IngredientCache 인스턴스
         */
        fun getInstance(context: Context): IngredientCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IngredientCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val context: Context = context.applicationContext
    
    // 원본 데이터 (필요시 사용)
    private var ingredientsData: JSONArray? = null
    
    // 인덱스 (효율성 개선: O(1) 검색)
    private val korNameIndex = mutableMapOf<String, JSONObject>()
    private val engNameIndex = mutableMapOf<String, JSONObject>()
    
    // 로드 상태
    @Volatile
    private var isLoaded = false
    
    @Volatile
    private var isLoading = false
    
    /**
     * ingredients.json을 로드하고 인덱스를 생성합니다.
     * 
     * 효율성 개선:
     * - 한 번만 로드하고 인덱스 생성
     * - 이후 모든 검색은 O(1) 시간 복잡도
     * 
     * @return 로드 성공 여부
     */
    fun loadData(): Boolean {
        if (isLoaded) {
            return true
        }
        
        if (isLoading) {
            // 이미 로딩 중이면 대기
            while (isLoading) {
                Thread.sleep(100)
            }
            return isLoaded
        }
        
        synchronized(this) {
            if (isLoaded) {
                return true
            }
            
            isLoading = true
            try {
                // JSON 파일 로드
                val jsonString = try {
                    context.assets.open("ingredients.json").bufferedReader().use { it.readText() }
                } catch (e: IOException) {
                    Log.e("IngredientCache", "Failed to read ingredients.json file", e)
                    return false
                }
                
                // JSON 파싱
                ingredientsData = try {
                    JSONArray(jsonString)
                } catch (e: org.json.JSONException) {
                    Log.e("IngredientCache", "Failed to parse ingredients.json", e)
                    return false
                }
                
                // 인덱스 생성 (O(n) 한 번만 수행)
                buildIndexes()
                
                isLoaded = true
                Log.d("IngredientCache", "✅ ${ingredientsData?.length() ?: 0}개 성분 로드 및 인덱스 생성 완료")
                return true
            } catch (e: Exception) {
                Log.e("IngredientCache", "Unexpected error loading ingredients.json", e)
                return false
            } finally {
                isLoading = false
            }
        }
    }
    
    /**
     * 인덱스를 생성합니다.
     * 
     * 효율성 개선:
     * - O(n) 시간에 한 번만 인덱스 생성
     * - 이후 모든 검색은 O(1) 시간 복잡도
     */
    private fun buildIndexes() {
        val data = ingredientsData ?: return
        
        korNameIndex.clear()
        engNameIndex.clear()
        
        for (i in 0 until data.length()) {
            try {
                val item = data.getJSONObject(i)
                val korName = item.optString("INGR_KOR_NAME", "")
                val engName = item.optString("INGR_ENG_NAME", "")
                
                // 정규화된 이름으로 인덱싱
                if (korName.isNotEmpty()) {
                    val normalized = korName.lowercase().replace(" ", "")
                    korNameIndex[normalized] = item
                }
                
                if (engName.isNotEmpty()) {
                    val normalized = engName.lowercase().replace(" ", "")
                    engNameIndex[normalized] = item
                }
            } catch (e: Exception) {
                Log.w("IngredientCache", "Failed to index ingredient at position $i", e)
            }
        }
        
        Log.d("IngredientCache", "인덱스 생성 완료: 한국어 ${korNameIndex.size}개, 영어 ${engNameIndex.size}개")
    }
    
    /**
     * 성분명으로 성분 정보를 찾습니다.
     * 
     * 효율성 개선:
     * - 이전: O(n) 선형 검색 (매번 전체 배열 순회)
     * - 개선: O(1) 해시 테이블 검색
     * 
     * 검색 방식:
     * 1. 정확 매칭: 한국어 이름 또는 영어 이름으로 정확히 일치 (O(1))
     * 2. 부분 매칭: 정확 매칭이 없으면 부분 문자열로 검색 (O(n), 최후의 수단)
     * 
     * @param ingredientName 찾을 성분명
     * @return 찾은 성분 정보 JSONObject, 없으면 null
     */
    fun findByName(ingredientName: String): JSONObject? {
        if (!isLoaded) {
            loadData()
        }
        
        if (!isLoaded) {
            return null
        }
        
        // 성분명 정규화
        val normalizedSearchName = ingredientName.trim().lowercase().replace(" ", "")
        
        // 정확 매칭 (O(1))
        korNameIndex[normalizedSearchName]?.let { return it }
        engNameIndex[normalizedSearchName]?.let { return it }
        
        // 부분 매칭 (O(n), 최후의 수단)
        for ((normalizedName, item) in korNameIndex) {
            if (normalizedSearchName in normalizedName || normalizedName in normalizedSearchName) {
                return item
            }
        }
        
        for ((normalizedName, item) in engNameIndex) {
            if (normalizedSearchName in normalizedName || normalizedName in normalizedSearchName) {
                return item
            }
        }
        
        return null
    }
    
    /**
     * 원본 데이터를 가져옵니다.
     * 
     * @return ingredients.json의 원본 JSONArray
     */
    fun getRawData(): JSONArray? {
        if (!isLoaded) {
            loadData()
        }
        return ingredientsData
    }
    
    /**
     * 캐시를 초기화합니다.
     * 
     * 테스트나 메모리 관리 목적으로 사용합니다.
     */
    fun clear() {
        synchronized(this) {
            ingredientsData = null
            korNameIndex.clear()
            engNameIndex.clear()
            isLoaded = false
            isLoading = false
        }
    }
}

