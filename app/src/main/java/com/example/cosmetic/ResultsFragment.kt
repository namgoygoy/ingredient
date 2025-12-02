package com.example.cosmetic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.cosmetic.network.AnalyzeProductRequest
import com.example.cosmetic.network.AnalyzeProductResponse
import com.example.cosmetic.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ResultsFragment : Fragment() {
    
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val apiService = RetrofitClient.apiService
    private lateinit var userPreferences: UserPreferences
    
    // ingredients.json 데이터 캐시
    private var ingredientsData: JSONArray? = null
    
    // Gemini AI Service
    private val geminiService by lazy {
        GeminiService(BuildConfig.GEMINI_API_KEY)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 뒤로가기 버튼
        view.findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // 선택된 성분 정보 표시
        val selectedIngredient = arguments?.getString("selectedIngredient") ?: ""
        
        if (selectedIngredient.isNotEmpty()) {
            // 선택된 성분에 대한 상세 정보 표시
            displayIngredientDetails(view, selectedIngredient)
        } else {
            // 선택된 성분이 없으면 전체 제품 분석 표시 (이전 로직)
            showProductAnalysisMode(view)
            
            sharedViewModel.recognizedText.observe(viewLifecycleOwner) { recognizedText ->
                if (recognizedText.isNotEmpty()) {
                    val ingredients = parseIngredients(recognizedText)
                    if (ingredients.isNotEmpty()) {
                        sharedViewModel.parsedIngredients.value = ingredients
                        val ingredientsText = ingredients.joinToString(", ")
                        view.findViewById<TextView>(R.id.productIngredients)?.text = ingredientsText
                        analyzeProduct(ingredients)
                    } else {
                        val ingredientSection = extractIngredientSection(recognizedText)
                        view.findViewById<TextView>(R.id.productIngredients)?.text = 
                            ingredientSection.ifEmpty { "성분을 인식할 수 없습니다." }
                        
                        Toast.makeText(
                            requireContext(),
                            "성분을 추출할 수 없습니다. 다시 스캔해주세요.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        
        // 분석 결과 표시
        sharedViewModel.analysisResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                displayAnalysisResult(view, it)
            }
        }
        
        // 로딩 상태 표시
        sharedViewModel.isLoading.observe(viewLifecycleOwner) { _ ->
            // TODO: 로딩 인디케이터 표시/숨김
        }
        
        // 에러 메시지 표시
        sharedViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 전체 제품 분석 모드로 UI 전환
     */
    private fun showProductAnalysisMode(view: View) {
        // 성분 상세 정보 카드 숨김
        view.findViewById<CardView>(R.id.ingredientDetailCard)?.visibility = View.GONE
        
        // AI 분석 리포트 섹션 표시
        view.findViewById<TextView>(R.id.aiAnalysisTitle)?.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.aiAnalysisReport)?.visibility = View.VISIBLE
        
        // 좋은 성분 섹션 표시
        view.findViewById<TextView>(R.id.goodMatchesTitle)?.visibility = View.VISIBLE
        
        // 주의 성분 섹션 표시
        view.findViewById<TextView>(R.id.badMatchesTitle)?.visibility = View.VISIBLE
        
        // 상세보기 버튼 표시
        view.findViewById<Button>(R.id.viewDetailsButton)?.visibility = View.VISIBLE
    }
    
    /**
     * 성분 상세 정보 모드로 UI 전환
     */
    private fun showIngredientDetailMode(view: View) {
        // 성분 상세 정보 카드 표시
        view.findViewById<CardView>(R.id.ingredientDetailCard)?.visibility = View.VISIBLE
        
        // AI 분석 리포트 섹션 숨김
        view.findViewById<TextView>(R.id.aiAnalysisTitle)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.aiAnalysisReport)?.visibility = View.GONE
        
        // 좋은 성분 섹션 숨김
        view.findViewById<TextView>(R.id.goodMatchesTitle)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.goodMatches)?.visibility = View.GONE
        
        // 주의 성분 섹션 숨김
        view.findViewById<TextView>(R.id.badMatchesTitle)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.badMatches)?.visibility = View.GONE
        
        // 상세보기 버튼 숨김
        view.findViewById<Button>(R.id.viewDetailsButton)?.visibility = View.GONE
    }
    
    /**
     * 선택된 성분의 상세 정보 표시
     */
    private fun displayIngredientDetails(view: View, ingredientName: String) {
        // UI 모드 전환
        showIngredientDetailMode(view)
        
        // 선택된 성분명 표시
        view.findViewById<TextView>(R.id.productName)?.text = "성분 상세 정보"
        view.findViewById<TextView>(R.id.productIngredients)?.text = ingredientName
        view.findViewById<TextView>(R.id.ingredientName)?.text = ingredientName
        
        // 선택된 성분 하나만으로 분석 수행
        analyzeProduct(listOf(ingredientName))
    }
    
    /**
     * 인식된 텍스트에서 성분 섹션만 추출 (UI 표시용)
     */
    private fun extractIngredientSection(text: String): String {
        // 성분 섹션 찾기 (예: "[성분]", "성분:", "전성분" 등)
        // 성분 섹션은 다음 중 하나로 끝남:
        // - 다른 섹션 시작 (예: [제조번호], [사용기한], [제조원] 등)
        // - 제품 코드 (예: A1801290, RC023A03 등)
        // - 제품 정보 (예: Tel, Made, ml 등)
        val ingredientSectionRegex = Regex(
            "(?:\\[성분\\]|성분\\s*:|전성분\\s*:?)\\s*([\\s\\S]*?)(?:(?:\\[|제조|제품|Tel|본\\s*제품|Made|고객|보관|사용|안구|재활용|유리|뚜경|PP|A\\d+[\\s/]?[A-Z]?\\d+|RC\\d+[A-Z]?\\d*|\\d+\\s*ml)[\\s\\S]*)?\$",
            RegexOption.IGNORE_CASE
        )
        
        val sectionMatch = ingredientSectionRegex.find(text)
        if (sectionMatch != null) {
            var ingredientText = sectionMatch.groupValues[1].trim()
            
            // 제품 코드 패턴 제거 (예: A1801290, RC023A03 등)
            ingredientText = ingredientText.replace(Regex("\\s*[A-Z]\\d+[A-Z]?\\d*[\\s/]?[A-Z]?\\d*\\s*\$"), "").trim()
            
            // 숫자만 있는 라인 제거 (예: "40 ml" 같은 것)
            ingredientText = ingredientText.replace(Regex("^\\d+\\s*(ml|g|kg|%)\\s*\$", RegexOption.MULTILINE), "").trim()
            
            return ingredientText
        }
        
        return ""
    }
    
    /**
     * 인식된 텍스트에서 성분 리스트 추출
     * 성분 섹션을 찾아서 성분명만 정확히 추출
     */
    private fun parseIngredients(text: String): List<String> {
        // 성분 섹션 추출
        val ingredientText = extractIngredientSection(text)
        if (ingredientText.isEmpty()) {
            return emptyList()
        }
        
        // 성분명 추출: 쉼표, 점, 공백, 줄바꿈으로 구분
        val ingredients = mutableListOf<String>()
        
        // 알파벳/숫자 코드 패턴 제거 (예: A1801290, RC023A03 등)
        val cleanedText = ingredientText.replace(Regex("\\s*[A-Z]\\d+[A-Z]?\\d*[\\s/]?[A-Z]?\\d*\\s*"), " ")
        
        // 1단계: 쉼표, 점, 줄바꿈으로 분리
        val parts = cleanedText.split(Regex("[,，.。\\n\\r]+"))
        
        for (part in parts) {
            var processedPart = part.trim()
            
            // 2단계: 공백으로 분리
            val words = processedPart.split(Regex("\\s+"))
            
            for (word in words) {
                var cleaned = word.trim()
                    .replace(Regex("[\\[\\]()]"), "") // 대괄호, 괄호 제거
                    .trim()
                
                // 숫자만 있는 경우 제외
                if (cleaned.matches(Regex("^[0-9\\-]+$"))) continue
                
                // 3단계: 알려진 성분명 패턴으로 분리 (OCR 오류 대응)
                // 예: "리날률시트로넬올" -> "리날룰", "시트로넬올"로 분리 시도
                val knownIngredientPatterns = listOf(
                    "시트로넬올", "리날룰", "리모넨", "제라니올", "시트랄",
                    "하이드록시시트로넬알", "알파아이소메틸아이오논",
                    "글리세린", "프로판다이올", "디메치콘", "정제수"
                )
                
                var foundPatterns = false
                for (pattern in knownIngredientPatterns) {
                    if (cleaned.contains(pattern, ignoreCase = true)) {
                        // 패턴이 포함되어 있으면 해당 부분 추출
                        val patternIndex = cleaned.indexOf(pattern, ignoreCase = true)
                        if (patternIndex > 0) {
                            // 패턴 앞부분도 성분일 수 있음 (예: "리날룰"이 "리날률"로 오인식)
                            val beforePattern = cleaned.substring(0, patternIndex).trim()
                            if (beforePattern.length >= 2 && isValidIngredientName(beforePattern)) {
                                ingredients.add(beforePattern)
                            }
                        }
                        ingredients.add(pattern)
                        foundPatterns = true
                        // 패턴 뒤부분도 확인
                        val afterPattern = cleaned.substring(patternIndex + pattern.length).trim()
                        if (afterPattern.length >= 2 && isValidIngredientName(afterPattern)) {
                            ingredients.add(afterPattern)
                        }
                        break
                    }
                }
                
                // 패턴이 없으면 전체 단어를 그대로 추가
                if (!foundPatterns && isValidIngredientName(cleaned)) {
                    ingredients.add(cleaned)
                }
            }
        }
        
        // 중복 제거 및 필터링
        return ingredients
            .distinct()
            .filter { it.length >= 2 && it.length <= 50 } // 너무 짧거나 긴 것 제외
            .filter { !isNonIngredientText(it) } // 제품명, 주의사항 등 제외
            .take(50)  // 최대 50개까지 제한
    }
    
    /**
     * 유효한 성분명인지 확인
     */
    private fun isValidIngredientName(text: String): Boolean {
        if (text.isEmpty() || text.length < 2) return false
        
        // 숫자만 있는 경우 제외
        if (text.matches(Regex("^[0-9\\-]+$"))) return false
        
        // 일반적인 제품명/안내문 패턴 제외
        val excludePatterns = listOf(
            Regex("^[A-Z][a-z]+.*[A-Z]"), // 제품명 패턴 (예: "Girl Rochas")
            Regex(".*제품.*"),
            Regex(".*주의.*"),
            Regex(".*Tel.*"),
            Regex(".*Made.*"),
            Regex(".*ml$"),
            Regex(".*%$"),
            Regex("^[0-9]+$")
        )
        
        return !excludePatterns.any { it.matches(text) }
    }
    
    /**
     * 성분이 아닌 텍스트인지 확인 (제품명, 주의사항 등)
     */
    private fun isNonIngredientText(text: String): Boolean {
        val nonIngredientKeywords = listOf(
            "제품", "주의", "사용", "보관", "제조", "Tel", "Made", "본", "교환", "보상",
            "공정", "거래", "고시", "소비자", "분쟁", "해결", "기준", "의거",
            "알코올", "함유", "화기", "직사광선", "안구", "점막", "씻어", "의사",
            "상담", "패치", "테스트", "민감성", "피부", "보유자", "반점", "부어오름",
            "가려움", "이상", "증상", "부작용", "상처", "자제", "어린이", "손",
            "닿지", "않는", "곳", "재활용", "어려움", "유리", "뚜껑", "PP",
            "제조번호", "사용기한", "알레르기", "유발성분표시", "베이퍼라이져",
            "내추럴", "스프레이", "굿", "필", "오드", "뚜왈렛", "패리스",
            "로샤스", "걸", "스프레이", "에스디알코올", "40-B", "향료",
            "ml", "A1801290", "RC023A03"
        )
        
        return nonIngredientKeywords.any { text.contains(it, ignoreCase = true) }
    }
    
    /**
     * 제품 분석 API 호출
     */
    private fun analyzeProduct(ingredients: List<String>) {
        lifecycleScope.launch {
            sharedViewModel.isLoading.value = true
            sharedViewModel.errorMessage.value = null
            
            try {
                // 사용자 피부 타입 가져오기
                if (!::userPreferences.isInitialized) {
                    userPreferences = UserPreferences(requireContext())
                }
                val skinType = userPreferences.getSkinType()
                
                val request = AnalyzeProductRequest(
                    ingredients = ingredients,
                    skinType = skinType
                )
                
                val response = withContext(Dispatchers.IO) {
                    apiService.analyzeProduct(request).execute()
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    sharedViewModel.analysisResult.value = result
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "알 수 없는 오류가 발생했습니다."
                    sharedViewModel.errorMessage.value = "분석 실패: $errorMsg"
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("UnknownHostException") == true || 
                    e.message?.contains("Unable to resolve host") == true -> 
                        "서버에 연결할 수 없습니다.\n\n확인 사항:\n1. 인터넷 연결 확인\n2. ngrok 터널이 실행 중인지 확인\n3. ngrok 주소가 변경되지 않았는지 확인"
                    e.message?.contains("ConnectException") == true || 
                    e.message?.contains("ECONNREFUSED") == true -> 
                        "서버에 연결할 수 없습니다. ngrok 터널이 실행 중인지 확인해주세요."
                    else -> "네트워크 오류: ${e.message}"
                }
                sharedViewModel.errorMessage.value = errorMsg
                e.printStackTrace()
            } finally {
                sharedViewModel.isLoading.value = false
            }
        }
    }
    
    /**
     * 분석 결과를 UI에 표시
     */
    private fun displayAnalysisResult(view: View, result: AnalyzeProductResponse) {
        // 선택된 성분 정보 확인
        val selectedIngredient = arguments?.getString("selectedIngredient") ?: ""
        
        if (selectedIngredient.isNotEmpty()) {
            // 성분 상세 정보 모드: 개별 성분 정보 표시
            displayIngredientDetailInfo(view, result, selectedIngredient)
        } else {
            // 제품 분석 모드: 전체 제품 분석 정보 표시
            displayProductAnalysisInfo(view, result)
        }
    }
    
     /**
      * 성분 상세 정보 표시 (개별 성분 모드)
      */
     private fun displayIngredientDetailInfo(view: View, result: AnalyzeProductResponse, ingredientName: String) {
         lifecycleScope.launch {
             // 좋은 성분 목록에서 해당 성분 찾기
             val goodMatch = result.goodMatches.firstOrNull { 
                 it.name.contains(ingredientName, ignoreCase = true) || 
                 ingredientName.contains(it.name, ignoreCase = true) 
             }
             
             // 주의 성분 목록에서 해당 성분 찾기
             val badMatch = result.badMatches.firstOrNull { 
                 it.name.contains(ingredientName, ignoreCase = true) || 
                 ingredientName.contains(it.name, ignoreCase = true) 
             }
             
             // 성분명 표시
             view.findViewById<TextView>(R.id.ingredientName)?.text = ingredientName
             
             // 기능(목적) 표시 - ingredients.json의 purpose 배열 활용
             val purpose = loadIngredientPurpose(ingredientName)
             
             if (purpose.isNotEmpty()) {
                 view.findViewById<TextView>(R.id.ingredientPurpose)?.text = purpose
             } else {
                 // ingredients.json에 없으면 Gemini로 생성
                 view.findViewById<TextView>(R.id.ingredientPurpose)?.text = "정보를 생성하는 중..."
                 val generatedPurpose = try {
                     geminiService.generateIngredientPurpose(ingredientName)
                 } catch (e: Exception) {
                     e.printStackTrace()
                     "정보를 불러올 수 없습니다."
                 }
                 view.findViewById<TextView>(R.id.ingredientPurpose)?.text = generatedPurpose
             }
             
             // 피부 타입 적합성 표시
             var suitability = when {
                 goodMatch != null && badMatch != null -> {
                     // 좋은 점과 주의할 점이 모두 있는 경우
                     val goodSkinTypes = extractSkinTypesFromPurpose(goodMatch.purpose)
                     val badSkinTypes = extractSkinTypesFromDescription(badMatch.description)
                     "권장: $goodSkinTypes, 주의: $badSkinTypes"
                 }
                 goodMatch != null -> {
                     // 좋은 점만 있는 경우
                     val goodSkinTypes = extractSkinTypesFromPurpose(goodMatch.purpose)
                     "권장: $goodSkinTypes"
                 }
                 badMatch != null -> {
                     // 주의할 점만 있는 경우
                     val badSkinTypes = extractSkinTypesFromDescription(badMatch.description)
                     "주의: $badSkinTypes"
                 }
                 else -> null
             }
             
             if (suitability == null) {
                 // RAG 서버에 정보가 없으면 Gemini로 생성
                 view.findViewById<TextView>(R.id.ingredientSuitability)?.text = "정보를 생성하는 중..."
                 suitability = try {
                     geminiService.generateSkinTypeSuitability(ingredientName)
                 } catch (e: Exception) {
                     e.printStackTrace()
                     "모든 피부 타입"
                 }
             }
             view.findViewById<TextView>(R.id.ingredientSuitability)?.text = suitability
             
             // ingredients.json에서 description 로드하여 표시 (없으면 Gemini로 생성)
             loadIngredientDescription(view, ingredientName)
         }
     }
    
     /**
      * ingredients.json에서 purpose 배열을 로드하여 한국어로 변환
      */
     private suspend fun loadIngredientPurpose(ingredientName: String): String {
         return withContext(Dispatchers.IO) {
             try {
                 // ingredients.json 로드 (캐시가 있으면 재사용)
                 if (ingredientsData == null) {
                     ingredientsData = loadIngredientsJson()
                 }
                 
                 // 성분 정보 찾기
                 val ingredientInfo = findIngredientByName(ingredientName)
                 
                 if (ingredientInfo != null) {
                     val purposeArray = ingredientInfo.optJSONArray("purpose")
                     if (purposeArray != null && purposeArray.length() > 0) {
                         // purpose 배열을 한국어로 변환
                         val purposes = mutableListOf<String>()
                         for (i in 0 until purposeArray.length()) {
                             val englishPurpose = purposeArray.getString(i)
                             val koreanPurpose = translatePurposeToKorean(englishPurpose)
                             purposes.add(koreanPurpose)
                         }
                         return@withContext purposes.joinToString(", ")
                     }
                 }
                 
                 return@withContext ""
             } catch (e: Exception) {
                 e.printStackTrace()
                 return@withContext ""
             }
         }
     }
     
     /**
      * 영문 purpose를 한국어로 변환
      */
     private fun translatePurposeToKorean(englishPurpose: String): String {
         return when (englishPurpose.lowercase()) {
             "moisturizer", "humectant" -> "보습제"
             "exfoliant" -> "각질제거제"
             "solvent" -> "용매"
             "fragrance", "perfuming" -> "향료"
             "antioxidant" -> "항산화제"
             "emulsifier" -> "유화제"
             "thickener" -> "증점제"
             "surfactant" -> "계면활성제"
             "preservative" -> "방부제"
             "emollient" -> "연화제"
             "sunscreen", "uv filter" -> "자외선차단제"
             "colorant" -> "착색제"
             "buffering" -> "완충제"
             "chelating" -> "킬레이트제"
             "antimicrobial" -> "항균제"
             "skin conditioning" -> "피부컨디셔닝"
             "viscosity controlling" -> "점도조절제"
             "absorbent" -> "흡수제"
             "astringent" -> "수렴제"
             "soothing" -> "진정제"
             "whitening" -> "미백제"
             "anti-acne" -> "여드름케어"
             else -> englishPurpose // 매핑되지 않은 경우 원문 표시
         }
     }
     
     /**
      * ingredients.json을 로드하여 성분의 description 표시
      * 없으면 Gemini AI로 생성
      */
     private fun loadIngredientDescription(view: View, ingredientName: String) {
         lifecycleScope.launch {
             try {
                 // ingredients.json 로드 (캐시가 있으면 재사용)
                 if (ingredientsData == null) {
                     ingredientsData = withContext(Dispatchers.IO) {
                         loadIngredientsJson()
                     }
                 }
                 
                 // 성분 정보 찾기
                 val ingredientInfo = findIngredientByName(ingredientName)
                 
                 if (ingredientInfo != null) {
                     // ingredients.json에 정보가 있는 경우
                     val description = ingredientInfo.optString("description", "")
                     
                     if (description.isNotEmpty()) {
                         // 영문 description을 한국어로 번역
                         view.findViewById<TextView>(R.id.aiExplanation)?.text = "설명을 생성하는 중..."
                         
                         val koreanDescription = try {
                             geminiService.translateIngredientDescription(ingredientName, description)
                         } catch (e: Exception) {
                             e.printStackTrace()
                             // 번역 실패 시 Gemini로 새로 생성
                             geminiService.generateIngredientDescription(ingredientName)
                         }
                         
                         view.findViewById<TextView>(R.id.aiExplanation)?.text = koreanDescription
                     } else {
                         // description이 비어있으면 Gemini로 생성
                         generateDescriptionWithGemini(view, ingredientName)
                     }
                 } else {
                     // ingredients.json에 없는 성분 -> Gemini로 생성
                     generateDescriptionWithGemini(view, ingredientName)
                 }
                 
             } catch (e: Exception) {
                 // 에러 발생 시 Gemini로 fallback
                 e.printStackTrace()
                 generateDescriptionWithGemini(view, ingredientName)
             }
         }
     }
     
     /**
      * Gemini AI로 성분 설명 생성
      */
     private fun generateDescriptionWithGemini(view: View, ingredientName: String) {
         lifecycleScope.launch {
             try {
                 view.findViewById<TextView>(R.id.aiExplanation)?.text = "AI가 정보를 생성하는 중..."
                 
                 val description = geminiService.generateIngredientDescription(ingredientName)
                 view.findViewById<TextView>(R.id.aiExplanation)?.text = description
                 
             } catch (e: Exception) {
                 view.findViewById<TextView>(R.id.aiExplanation)?.text = 
                     "해당 성분에 대한 정보를 생성할 수 없습니다."
                 e.printStackTrace()
             }
         }
     }
    
    /**
     * ingredients.json 파일을 assets에서 로드
     */
    private fun loadIngredientsJson(): JSONArray {
        val jsonString = try {
            requireContext().assets.open("ingredients.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            e.printStackTrace()
            "[]"
        }
        return JSONArray(jsonString)
    }
    
    /**
     * 성분명으로 ingredients.json에서 성분 정보 찾기
     */
    private fun findIngredientByName(ingredientName: String): JSONObject? {
        val data = ingredientsData ?: return null
        
        // 성분명 정규화 (공백 제거, 소문자 변환)
        val normalizedSearchName = ingredientName.trim().lowercase().replace(" ", "")
        
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val korName = item.optString("INGR_KOR_NAME", "")
            val engName = item.optString("INGR_ENG_NAME", "")
            
            // 한국어 또는 영어 이름으로 정확 매칭
            val normalizedKorName = korName.lowercase().replace(" ", "")
            val normalizedEngName = engName.lowercase().replace(" ", "")
            
            if (normalizedKorName == normalizedSearchName || normalizedEngName == normalizedSearchName) {
                return item
            }
        }
        
        // 정확한 매칭이 없으면 부분 매칭 시도
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val korName = item.optString("INGR_KOR_NAME", "")
            val engName = item.optString("INGR_ENG_NAME", "")
            
            val normalizedKorName = korName.lowercase().replace(" ", "")
            val normalizedEngName = engName.lowercase().replace(" ", "")
            
            if (normalizedKorName.contains(normalizedSearchName) || 
                normalizedSearchName.contains(normalizedKorName) ||
                normalizedEngName.contains(normalizedSearchName) || 
                normalizedSearchName.contains(normalizedEngName)) {
                return item
            }
        }
        
        return null
    }
    
    /**
     * 전체 제품 분석 정보 표시 (제품 분석 모드)
     */
    private fun displayProductAnalysisInfo(view: View, result: AnalyzeProductResponse) {
        // 분석 리포트 표시
        view.findViewById<TextView>(R.id.aiAnalysisReport)?.let {
            it.text = result.analysisReport
        }
        
        // 좋은 성분 표시 (중복 제거)
        view.findViewById<TextView>(R.id.goodMatches)?.let { goodMatchesView ->
            if (result.goodMatches.isNotEmpty()) {
                // 성분명 기준으로 중복 제거 (이름이 같으면 하나만 표시)
                val uniqueGoodMatches = result.goodMatches.distinctBy { it.name }
                val goodMatchesText = uniqueGoodMatches.joinToString("\n") { 
                    "✅ ${it.name}: ${it.purpose}" 
                }
                goodMatchesView.text = goodMatchesText
                goodMatchesView.visibility = View.VISIBLE
            } else {
                goodMatchesView.visibility = View.GONE
            }
        }
        
        // 주의 성분 표시 (중복 제거)
        view.findViewById<TextView>(R.id.badMatches)?.let { badMatchesView ->
            if (result.badMatches.isNotEmpty()) {
                // 성분명 기준으로 중복 제거 (이름이 같으면 하나만 표시)
                val uniqueBadMatches = result.badMatches.distinctBy { it.name }
                val badMatchesText = uniqueBadMatches.joinToString("\n") { 
                    "⚠️ ${it.name}: ${it.description}" 
                }
                badMatchesView.text = badMatchesText
                badMatchesView.visibility = View.VISIBLE
            } else {
                badMatchesView.visibility = View.GONE
            }
        }
    }
    
    /**
     * Purpose에서 피부 타입 추출
     */
    private fun extractSkinTypesFromPurpose(purpose: String): String {
        val skinTypes = mutableListOf<String>()
        
        if (purpose.contains("지성", ignoreCase = true) || purpose.contains("oily", ignoreCase = true)) {
            skinTypes.add("지성")
        }
        if (purpose.contains("건성", ignoreCase = true) || purpose.contains("dry", ignoreCase = true)) {
            skinTypes.add("건성")
        }
        if (purpose.contains("민감성", ignoreCase = true) || purpose.contains("sensitive", ignoreCase = true)) {
            skinTypes.add("민감성")
        }
        if (purpose.contains("여드름", ignoreCase = true) || purpose.contains("acne", ignoreCase = true)) {
            skinTypes.add("여드름성")
        }
        
        return if (skinTypes.isNotEmpty()) skinTypes.joinToString(", ") else "모든 피부"
    }
    
    /**
     * Description에서 피부 타입 추출
     */
    private fun extractSkinTypesFromDescription(description: String): String {
        val skinTypes = mutableListOf<String>()
        
        if (description.contains("지성", ignoreCase = true) || description.contains("oily", ignoreCase = true)) {
            skinTypes.add("지성")
        }
        if (description.contains("건성", ignoreCase = true) || description.contains("dry", ignoreCase = true)) {
            skinTypes.add("건성")
        }
        if (description.contains("민감성", ignoreCase = true) || description.contains("sensitive", ignoreCase = true)) {
            skinTypes.add("민감성")
        }
        if (description.contains("여드름", ignoreCase = true) || description.contains("acne", ignoreCase = true)) {
            skinTypes.add("여드름성")
        }
        
        return if (skinTypes.isNotEmpty()) skinTypes.joinToString(", ") else "일부 피부"
    }
}


