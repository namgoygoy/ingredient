package com.example.cosmetic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

class ResultsFragment : Fragment() {
    
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val apiService = RetrofitClient.apiService
    
    // 기본 피부 타입 (나중에 사용자 선택으로 변경 가능)
    private val defaultSkinType = "건성"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 선택된 성분 정보 표시
        val selectedIngredient = arguments?.getString("selectedIngredient") ?: ""
        
        if (selectedIngredient.isNotEmpty()) {
            // 선택된 성분에 대한 상세 정보 표시
            displayIngredientDetails(view, selectedIngredient)
        } else {
            // 선택된 성분이 없으면 전체 제품 분석 표시 (이전 로직)
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
        
        // 상세보기 버튼은 이제 필요 없음 (DetailsFragment에서 이미 본 상태)
        view.findViewById<Button>(R.id.viewDetailsButton)?.visibility = View.GONE
    }
    
    /**
     * 선택된 성분의 상세 정보 표시
     */
    private fun displayIngredientDetails(view: View, ingredientName: String) {
        // 선택된 성분명 표시
        view.findViewById<TextView>(R.id.productName)?.text = "성분 상세 정보"
        view.findViewById<TextView>(R.id.productIngredients)?.text = ingredientName
        
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
                val request = AnalyzeProductRequest(
                    ingredients = ingredients,
                    skinType = defaultSkinType
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
                sharedViewModel.errorMessage.value = "네트워크 오류: ${e.message}"
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
}


