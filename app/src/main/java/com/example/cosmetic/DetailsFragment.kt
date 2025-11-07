package com.example.cosmetic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmetic.network.AnalyzeProductRequest
import com.example.cosmetic.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailsFragment : Fragment() {
    
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var isIngredientListExpanded = false
    private lateinit var ingredientsAdapter: IngredientsAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 뒤로가기 버튼
        view.findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // 전성분 목록 접기/펼치기 토글
        val toggleHeader = view.findViewById<View>(R.id.ingredientToggleHeader)
        val toggleIcon = view.findViewById<TextView>(R.id.ingredientToggleIcon)
        val ingredientsRecyclerView = view.findViewById<RecyclerView>(R.id.ingredientsRecyclerView)
        
        // RecyclerView 설정
        ingredientsAdapter = IngredientsAdapter { ingredient ->
            // 성분 클릭 시 ResultsFragment로 이동하면서 선택된 성분 전달
            val bundle = Bundle().apply {
                putString("selectedIngredient", ingredient)
            }
            findNavController().navigate(R.id.action_nav_details_to_nav_results, bundle)
        }
        
        ingredientsRecyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ingredientsAdapter
        }
        
        toggleHeader?.setOnClickListener {
            isIngredientListExpanded = !isIngredientListExpanded
            
            if (isIngredientListExpanded) {
                ingredientsRecyclerView?.visibility = View.VISIBLE
                toggleIcon?.text = "▲"
            } else {
                ingredientsRecyclerView?.visibility = View.GONE
                toggleIcon?.text = "▼"
            }
        }
        
        // 분석 결과 관찰 및 표시
        sharedViewModel.analysisResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                displayAnalysisDetails(view, it)
            }
        }
        
        // 인식된 텍스트에서 성분 파싱 및 분석 수행
        sharedViewModel.recognizedText.observe(viewLifecycleOwner) { recognizedText ->
            if (recognizedText.isNotEmpty()) {
                // ResultsFragment의 parseIngredients 함수를 사용하여 성분 파싱
                val ingredients = parseIngredients(recognizedText)
                if (ingredients.isNotEmpty()) {
                    sharedViewModel.parsedIngredients.value = ingredients
                    ingredientsAdapter.submitList(ingredients)
                    
                    // 전체 제품 분석 수행
                    analyzeProduct(ingredients)
                }
            }
        }
    }
    
    /**
     * 인식된 텍스트에서 성분 섹션만 추출 (UI 표시용)
     */
    private fun extractIngredientSection(text: String): String {
        val ingredientSectionRegex = Regex(
            "(?:\\[성분\\]|성분\\s*:|전성분\\s*:?)\\s*([\\s\\S]*?)(?:(?:\\[|제조|제품|Tel|본\\s*제품|Made|고객|보관|사용|안구|재활용|유리|뚜경|PP|A\\d+[\\s/]?[A-Z]?\\d+|RC\\d+[A-Z]?\\d*|\\d+\\s*ml)[\\s\\S]*)?\$",
            RegexOption.IGNORE_CASE
        )
        
        val sectionMatch = ingredientSectionRegex.find(text)
        if (sectionMatch != null) {
            var ingredientText = sectionMatch.groupValues[1].trim()
            ingredientText = ingredientText.replace(Regex("\\s*[A-Z]\\d+[A-Z]?\\d*[\\s/]?[A-Z]?\\d*\\s*\$"), "").trim()
            ingredientText = ingredientText.replace(Regex("^\\d+\\s*(ml|g|kg|%)\\s*\$", RegexOption.MULTILINE), "").trim()
            return ingredientText
        }
        
        return ""
    }
    
    /**
     * 인식된 텍스트에서 성분 리스트 추출
     */
    private fun parseIngredients(text: String): List<String> {
        val ingredientText = extractIngredientSection(text)
        if (ingredientText.isEmpty()) {
            return emptyList()
        }
        
        val ingredients = mutableListOf<String>()
        val cleanedText = ingredientText.replace(Regex("\\s*[A-Z]\\d+[A-Z]?\\d*[\\s/]?[A-Z]?\\d*\\s*"), " ")
        val parts = cleanedText.split(Regex("[,，.。\\n\\r]+"))
        
        for (part in parts) {
            var processedPart = part.trim()
            val words = processedPart.split(Regex("\\s+"))
            
            for (word in words) {
                var cleaned = word.trim()
                    .replace(Regex("[\\[\\]()]"), "")
                    .trim()
                
                if (cleaned.matches(Regex("^[0-9\\-]+$"))) continue
                
                val knownIngredientPatterns = listOf(
                    "시트로넬올", "리날룰", "리모넨", "제라니올", "시트랄",
                    "하이드록시시트로넬알", "알파아이소메틸아이오논",
                    "글리세린", "프로판다이올", "디메치콘", "정제수"
                )
                
                var foundPatterns = false
                for (pattern in knownIngredientPatterns) {
                    if (cleaned.contains(pattern, ignoreCase = true)) {
                        val patternIndex = cleaned.indexOf(pattern, ignoreCase = true)
                        if (patternIndex > 0) {
                            val beforePattern = cleaned.substring(0, patternIndex).trim()
                            if (beforePattern.length >= 2 && isValidIngredientName(beforePattern)) {
                                ingredients.add(beforePattern)
                            }
                        }
                        ingredients.add(pattern)
                        foundPatterns = true
                        val afterPattern = cleaned.substring(patternIndex + pattern.length).trim()
                        if (afterPattern.length >= 2 && isValidIngredientName(afterPattern)) {
                            ingredients.add(afterPattern)
                        }
                        break
                    }
                }
                
                if (!foundPatterns && isValidIngredientName(cleaned)) {
                    ingredients.add(cleaned)
                }
            }
        }
        
        return ingredients
            .distinct()
            .filter { it.length >= 2 && it.length <= 50 }
            .filter { !isNonIngredientText(it) }
            .take(50)
    }
    
    private fun isValidIngredientName(text: String): Boolean {
        if (text.isEmpty() || text.length < 2) return false
        if (text.matches(Regex("^[0-9\\-]+$"))) return false
        
        val excludePatterns = listOf(
            Regex("^[A-Z][a-z]+.*[A-Z]"),
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
        // ResultsFragment와 동일한 로직으로 전체 제품 분석 수행
        lifecycleScope.launch {
            sharedViewModel.isLoading.value = true
            sharedViewModel.errorMessage.value = null
            
            try {
                val request = com.example.cosmetic.network.AnalyzeProductRequest(
                    ingredients = ingredients,
                    skinType = "건성" // 기본 피부 타입
                )
                
                val response = withContext(Dispatchers.IO) {
                    com.example.cosmetic.network.RetrofitClient.apiService.analyzeProduct(request).execute()
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
     * 분석 결과 상세 정보를 UI에 표시
     */
    private fun displayAnalysisDetails(view: View, result: com.example.cosmetic.network.AnalyzeProductResponse) {
        // AI 분석 요약
        view.findViewById<TextView>(R.id.aiSummaryText)?.let {
            it.text = result.analysisReport
        }
        
        // 추천 피부 타입 (중복 제거)
        view.findViewById<TextView>(R.id.recommendedSkinTypes)?.let {
            if (result.goodMatches.isNotEmpty()) {
                val uniqueGoodMatches = result.goodMatches.distinctBy { it.name }
                val skinTypes = uniqueGoodMatches
                    .map { match -> extractSkinTypeFromPurpose(match.purpose) }
                    .distinct()
                    .joinToString(", ")
                it.text = skinTypes.ifEmpty { "없음" }
            } else {
                it.text = "없음"
            }
        }
        
        // 주의 피부 타입 (중복 제거)
        view.findViewById<TextView>(R.id.cautionSkinTypes)?.let {
            if (result.badMatches.isNotEmpty()) {
                val uniqueBadMatches = result.badMatches.distinctBy { it.name }
                val skinTypes = uniqueBadMatches
                    .map { match -> extractSkinTypeFromDescription(match.description) }
                    .distinct()
                    .joinToString(", ")
                it.text = skinTypes.ifEmpty { "없음" }
            } else {
                it.text = "없음"
            }
        }
        
        // 보습 성분 (중복 제거)
        view.findViewById<TextView>(R.id.hydratingIngredients)?.let {
            val uniqueGoodMatches = result.goodMatches.distinctBy { it.name }
            val hydratingIngredients = uniqueGoodMatches.filter { match ->
                match.purpose.contains("보습", ignoreCase = true) ||
                match.purpose.contains("moisturizer", ignoreCase = true) ||
                match.purpose.contains("humectant", ignoreCase = true)
            }
            
            if (hydratingIngredients.isNotEmpty()) {
                it.text = "${hydratingIngredients.first().name} 함유"
            } else {
                it.text = "없음"
            }
        }
        
        // 장벽 지원 성분 (중복 제거)
        view.findViewById<TextView>(R.id.barrierIngredients)?.let {
            val uniqueGoodMatches = result.goodMatches.distinctBy { it.name }
            val barrierIngredients = uniqueGoodMatches.filter { match ->
                match.name.contains("세라마이드", ignoreCase = true) ||
                match.name.contains("ceramide", ignoreCase = true) ||
                match.purpose.contains("장벽", ignoreCase = true) ||
                match.purpose.contains("barrier", ignoreCase = true)
            }
            
            if (barrierIngredients.isNotEmpty()) {
                it.text = barrierIngredients.first().name
            } else {
                it.text = "없음"
            }
        }
    }
    
    private fun extractSkinTypeFromPurpose(purpose: String): String {
        return when {
            purpose.contains("지성", ignoreCase = true) || purpose.contains("oily", ignoreCase = true) -> "지성"
            purpose.contains("건성", ignoreCase = true) || purpose.contains("dry", ignoreCase = true) -> "건성"
            purpose.contains("민감성", ignoreCase = true) || purpose.contains("sensitive", ignoreCase = true) -> "민감성"
            purpose.contains("여드름", ignoreCase = true) || purpose.contains("acne", ignoreCase = true) -> "여드름성"
            else -> "중성"
        }
    }
    
    private fun extractSkinTypeFromDescription(description: String): String {
        return when {
            description.contains("지성", ignoreCase = true) || description.contains("oily", ignoreCase = true) -> "지성"
            description.contains("건성", ignoreCase = true) || description.contains("dry", ignoreCase = true) -> "건성"
            description.contains("민감성", ignoreCase = true) || description.contains("sensitive", ignoreCase = true) -> "민감성"
            description.contains("여드름", ignoreCase = true) || description.contains("acne", ignoreCase = true) -> "여드름성"
            else -> "민감성"
        }
    }
    
    /**
     * 성분 리스트 어댑터
     */
    private class IngredientsAdapter(
        private val onIngredientClick: (String) -> Unit
    ) : RecyclerView.Adapter<IngredientsAdapter.IngredientViewHolder>() {
        
        private var ingredients: List<String> = emptyList()
        
        fun submitList(newIngredients: List<String>) {
            ingredients = newIngredients
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return IngredientViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
            val ingredient = ingredients[position]
            holder.bind(ingredient)
        }
        
        override fun getItemCount(): Int = ingredients.size
        
        inner class IngredientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(android.R.id.text1)
            
            fun bind(ingredient: String) {
                textView.text = ingredient
                textView.textSize = 12f
                textView.setTextColor(itemView.context.getColor(R.color.text_muted))
                
                // 패딩 설정 (dp를 픽셀로 변환)
                val paddingDp = 8
                val paddingPx = (paddingDp * itemView.context.resources.displayMetrics.density).toInt()
                textView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                
                itemView.setOnClickListener {
                    onIngredientClick(ingredient)
                }
                
                // 클릭 가능한 스타일 적용
                itemView.isClickable = true
                itemView.isFocusable = true
                itemView.background = itemView.context.getDrawable(android.R.drawable.list_selector_background)
            }
        }
    }
}
