package com.example.cosmetic

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
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
import com.example.cosmetic.Constants.Analysis.MIN_DESCRIPTION_LENGTH
import com.example.cosmetic.Constants.Animation.FADE_DURATION_MS
import com.example.cosmetic.Constants.Animation.LOADING_MESSAGE_INTERVAL_MS
import com.example.cosmetic.Constants.ErrorMessage.DATA_LOAD_FAILED
import com.example.cosmetic.Constants.ErrorMessage.GEMINI_API_FAILED
import com.example.cosmetic.Constants.LogTag.RESULTS_FRAGMENT
import com.example.cosmetic.network.AnalyzeProductResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * 성분 분석 결과 화면 Fragment
 * 
 * 이 Fragment는 두 가지 모드로 동작합니다:
 * 1. 전체 제품 분석 모드: OCR로 인식된 모든 성분을 분석한 결과를 표시
 * 2. 개별 성분 상세 모드: 선택된 단일 성분의 상세 정보를 표시
 * 
 * 주요 기능:
 * - 전체 제품 분석 리포트 표시 (RAG 서버 결과)
 * - 좋은 성분/주의 성분 목록 표시
 * - 개별 성분 상세 정보 표시 (ingredients.json + Gemini AI)
 * - 성분의 기능(purpose), 피부 타입 적합성, 상세 설명 표시
 * 
 * 데이터 소스 우선순위:
 * 1. ingredients.json (로컬 assets)
 * 2. RAG 서버 분석 결과
 * 3. Gemini AI (정보가 없을 경우)
 * 
 * 성능 최적화:
 * - 병렬 처리: purpose, suitability, description을 동시에 로드
 * - 캐싱: ingredients.json 데이터를 메모리에 캐시
 * - 점진적 업데이트: 데이터가 준비되는 대로 UI 업데이트
 * 
 * @see DetailsFragment 전체 제품 분석 요약을 표시하는 Fragment
 * @see SharedViewModel Fragment 간 데이터 공유를 위한 ViewModel
 */
class ResultsFragment : Fragment() {
    
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var userPreferences: UserPreferences
    
    // 효율성 개선: IngredientCache 사용 (인덱싱 및 싱글톤)
    private val ingredientCache by lazy {
        com.example.cosmetic.utils.IngredientCache.getInstance(requireContext())
    }
    
    // Gemini AI Service (AppConfig에서 API 키 자동 로드)
    private val geminiService by lazy {
        GeminiService()
    }
    
    // 성분 파싱 유틸리티
    private val ingredientParser = IngredientParser.instance
    
    // 로딩 애니메이션 관련
    private var loadingMessageHandler: Handler? = null
    private var loadingMessageRunnable: Runnable? = null
    private var currentMessageIndex = 0
    
    // 로딩 메시지 목록
    private val loadingMessages = listOf(
        "🔬 성분을 꼼꼼히 분석 중입니다...",
        "🧪 피부 타입별 적합성을 확인 중...",
        "💡 좋은 성분과 주의 성분을 분류 중...",
        "📊 AI가 종합 리포트를 작성 중...",
        "✨ 거의 다 됐어요!"
    )
    
    private val loadingSubMessages = listOf(
        "잠시만 기다려 주세요",
        "1,000개 이상의 성분 데이터를 검색 중",
        "당신의 피부에 맞는 정보를 찾고 있어요",
        "분석 결과를 정리하고 있어요",
        "곧 결과를 보여드릴게요"
    )
    
    /**
     * Fragment의 뷰를 생성합니다.
     * 
     * @param inflater 레이아웃 인플레이터
     * @param container 부모 뷰 그룹 (null 가능)
     * @param savedInstanceState 저장된 인스턴스 상태
     * @return 생성된 뷰 또는 null
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    // 현재 분석 중인 성분명을 추적하여 중복 UI 업데이트 방지
    // SharedViewModel에 이전 결과가 남아있을 때 observer가 2번 호출되는 문제 해결
    private var currentAnalyzingIngredient: String? = null
    
    /**
     * 뷰가 생성된 후 초기화 작업을 수행합니다.
     * 
     * 이 메서드에서 다음 작업을 수행합니다:
     * - 뒤로가기 버튼 설정
     * - 선택된 성분 여부 확인 (arguments에서 "selectedIngredient" 확인)
     * - 선택된 성분이 있으면 개별 성분 상세 모드, 없으면 전체 제품 분석 모드
     * - SharedViewModel의 LiveData 관찰 설정
     * 
     * @param view 생성된 뷰
     * @param savedInstanceState 저장된 인스턴스 상태
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 뒤로가기 버튼
        view.findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // 선택된 성분 정보 표시
        val selectedIngredient = arguments?.getString("selectedIngredient") ?: ""
        
        if (selectedIngredient.isNotEmpty()) {
            // 개별 성분 모드: 이전 결과를 초기화하여 observer 중복 호출 방지
            // SharedViewModel에 남아있는 이전 제품 분석 결과로 인해 observer가 2번 호출되는 문제 해결
            sharedViewModel.setAnalysisResult(null)
            currentAnalyzingIngredient = selectedIngredient
            
            // 선택된 성분에 대한 상세 정보 표시
            displayIngredientDetails(view, selectedIngredient)
        } else {
            // 선택된 성분이 없으면 전체 제품 분석 표시 (이전 로직)
            currentAnalyzingIngredient = null
            showProductAnalysisMode(view)
            
            sharedViewModel.recognizedText.observe(viewLifecycleOwner) { recognizedText ->
                if (recognizedText.isNotEmpty()) {
                    val ingredients = ingredientParser.parseIngredients(recognizedText)
                    if (ingredients.isNotEmpty()) {
                        sharedViewModel.setParsedIngredients(ingredients)
                        val ingredientsText = ingredients.joinToString(", ")
                        view.findViewById<TextView>(R.id.productIngredients)?.text = ingredientsText
                        analyzeProduct(ingredients)
                    } else {
                        val ingredientSection = ingredientParser.extractIngredientSection(recognizedText)
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
        
        // 로딩 상태 관찰 및 로딩 오버레이 표시
        sharedViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val loadingOverlay = view.findViewById<View>(R.id.loadingOverlay)
            val mainContent = view.findViewById<View>(R.id.mainContent)
            
            if (isLoading) {
                showLoadingAnimation(view)
                loadingOverlay?.visibility = View.VISIBLE
                mainContent?.alpha = 0.3f
            } else {
                hideLoadingAnimation()
                loadingOverlay?.visibility = View.GONE
                mainContent?.alpha = 1.0f
            }
        }
        
        // 에러 메시지 표시
        sharedViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * 로딩 애니메이션을 시작합니다.
     * 
     * 사용자에게 분석이 진행 중임을 알리기 위해 로딩 메시지를 주기적으로 변경합니다.
     * 메시지 변경 시 페이드 아웃 → 텍스트 변경 → 페이드 인 애니메이션을 적용합니다.
     * 
     * 동작 방식:
     * 1. 초기 메시지를 표시
     * 2. 2.5초마다 다음 메시지로 변경 (페이드 애니메이션 적용)
     * 3. 메시지 목록을 순환하며 반복
     * 
     * 메모리 누수 방지:
     * - Handler는 viewLifecycleOwner와 연결되어 Fragment 생명주기에 따라 자동 정리됩니다.
     * - onDestroyView에서 명시적으로 모든 콜백을 제거합니다.
     * 
     * @param view Fragment의 루트 뷰
     * 
     * @see hideLoadingAnimation 로딩 애니메이션을 중지하는 메서드
     */
    private fun showLoadingAnimation(view: View) {
        // 기존 Handler가 있으면 먼저 정리
        hideLoadingAnimation()
        
        currentMessageIndex = 0
        
        val loadingMessage = view.findViewById<TextView>(R.id.loadingMessage)
        val loadingSubMessage = view.findViewById<TextView>(R.id.loadingSubMessage)
        
        // 초기 메시지 설정
        loadingMessage?.text = loadingMessages[0]
        loadingSubMessage?.text = loadingSubMessages[0]
        
        // 메시지 변경 핸들러 시작 (viewLifecycleOwner와 연결하여 생명주기 관리)
        loadingMessageHandler = Handler(Looper.getMainLooper())
        loadingMessageRunnable = object : Runnable {
            override fun run() {
                // Fragment가 destroy되었는지 확인
                if (!isAdded || viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED)) {
                    return
                }
                
                currentMessageIndex = (currentMessageIndex + 1) % loadingMessages.size
                
                // 페이드 아웃 → 텍스트 변경 → 페이드 인 애니메이션
                loadingMessage?.let { messageView ->
                    val fadeOut = ObjectAnimator.ofFloat(messageView, "alpha", 1f, 0f).apply {
                        duration = FADE_DURATION_MS
                    }
                    fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            // Fragment가 여전히 활성 상태인지 확인
                            if (!isAdded || viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED)) {
                                return
                            }
                            
                            messageView.text = loadingMessages[currentMessageIndex]
                            loadingSubMessage?.text = loadingSubMessages[currentMessageIndex]
                            
                            ObjectAnimator.ofFloat(messageView, "alpha", 0f, 1f).apply {
                                duration = FADE_DURATION_MS
                            }.start()
                            
                            loadingSubMessage?.let { subView ->
                                ObjectAnimator.ofFloat(subView, "alpha", 0f, 1f).apply {
                                    duration = FADE_DURATION_MS
                                }.start()
                            }
                        }
                    })
                    fadeOut.start()
                    
                    loadingSubMessage?.let { subView ->
                        ObjectAnimator.ofFloat(subView, "alpha", 1f, 0f).apply {
                            duration = FADE_DURATION_MS
                        }.start()
                    }
                }
                
                // 로딩 메시지 변경 주기 (Fragment가 활성 상태일 때만)
                if (isAdded && !viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED)) {
                    loadingMessageHandler?.postDelayed(this, LOADING_MESSAGE_INTERVAL_MS)
                }
            }
        }
        
        // 첫 메시지 변경
        loadingMessageHandler?.postDelayed(loadingMessageRunnable!!, LOADING_MESSAGE_INTERVAL_MS)
    }
    
    /**
     * 로딩 애니메이션을 중지합니다.
     * 
     * Handler의 모든 콜백을 제거하고 리소스를 정리합니다.
     * Fragment가 destroy될 때 자동으로 호출됩니다.
     * 
     * @see onDestroyView Fragment 생명주기 메서드
     */
    private fun hideLoadingAnimation() {
        loadingMessageRunnable?.let { loadingMessageHandler?.removeCallbacks(it) }
        loadingMessageHandler = null
        loadingMessageRunnable = null
        currentMessageIndex = 0
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // CRITICAL: Handler 메모리 누수 방지
        // 뷰가 파괴될 때 모든 pending 메시지와 콜백을 제거하여
        // Fragment가 destroy된 후에도 Handler가 살아있어 메모리 누수 발생하는 것을 방지
        loadingMessageHandler?.removeCallbacksAndMessages(null)
        loadingMessageHandler = null
        loadingMessageRunnable = null
        currentMessageIndex = 0
    }
    
    /**
     * 전체 제품 분석 모드로 UI를 전환합니다.
     * 
     * 전체 제품 분석 결과를 표시하기 위해 필요한 UI 요소만 표시하고
     * 개별 성분 상세 정보 관련 UI는 숨깁니다.
     * 
     * 표시되는 요소:
     * - AI 분석 리포트
     * - 좋은 성분 목록
     * - 주의 성분 목록
     * - 상세보기 버튼
     * 
     * 숨겨지는 요소:
     * - 성분 상세 정보 카드
     * 
     * @param view Fragment의 루트 뷰
     * 
     * @see showIngredientDetailMode 개별 성분 상세 모드로 전환하는 메서드
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
     * 개별 성분 상세 정보 모드로 UI를 전환합니다.
     * 
     * 선택된 단일 성분의 상세 정보를 표시하기 위해 필요한 UI 요소만 표시하고
     * 전체 제품 분석 관련 UI는 숨깁니다.
     * 
     * 표시되는 요소:
     * - 성분 상세 정보 카드 (성분명, 기능, 피부 타입 적합성, AI 설명)
     * 
     * 숨겨지는 요소:
     * - AI 분석 리포트
     * - 좋은 성분/주의 성분 목록
     * - 상세보기 버튼
     * 
     * @param view Fragment의 루트 뷰
     * 
     * @see showProductAnalysisMode 전체 제품 분석 모드로 전환하는 메서드
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
     * 선택된 성분의 상세 정보를 표시합니다.
     * 
     * 사용자가 성분명을 클릭했을 때 호출되어 해당 성분의 상세 정보를 표시합니다.
     * 
     * 처리 과정:
     * 1. UI를 개별 성분 상세 모드로 전환
     * 2. 성분명을 UI에 표시
     * 3. 선택된 성분 하나만으로 RAG 서버에 분석 요청
     * 4. 분석 결과를 바탕으로 상세 정보 표시
     * 
     * @param view Fragment의 루트 뷰
     * @param ingredientName 선택된 성분명
     * 
     * @see analyzeProduct RAG 서버에 분석 요청을 보내는 메서드
     * @see displayIngredientDetailInfo 개별 성분 상세 정보를 표시하는 메서드
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
     * 제품 성분 분석을 위해 Repository를 통해 API 요청을 보냅니다.
     * 
     * Repository 패턴을 사용하여 네트워크 호출과 에러 처리를 중앙화했습니다.
     * 분석 결과는 SharedViewModel의 analysisResult에 저장되며, 이를 관찰하는 UI가 자동으로 업데이트됩니다.
     * 
     * @param ingredients 분석할 성분명 리스트
     * 
     * @see ProductAnalysisRepository 네트워크 호출을 담당하는 Repository
     * @see SharedViewModel.analysisResult 분석 결과를 저장하는 LiveData
     * @see displayAnalysisResult 분석 결과를 UI에 표시하는 메서드
     */
    private fun analyzeProduct(ingredients: List<String>) {
        lifecycleScope.launch {
            sharedViewModel.setLoading(true)
            sharedViewModel.setErrorMessage(null)
            
            // Repository를 통한 분석 수행
            if (!::userPreferences.isInitialized) {
                userPreferences = UserPreferences(requireContext())
            }
            
            val repository = com.example.cosmetic.repository.ProductAnalysisRepository(
                apiService = com.example.cosmetic.network.RetrofitClient.apiService,
                userPreferences = userPreferences
            )
            
            when (val result = repository.analyzeProduct(ingredients)) {
                is kotlin.Result.Success -> {
                    sharedViewModel.setAnalysisResult(result.getOrNull())
                }
                is kotlin.Result.Failure -> {
                    val error = result.exceptionOrNull()
                    when (error) {
                        is com.example.cosmetic.repository.NetworkError -> {
                            sharedViewModel.setErrorMessage(error.getUserMessage())
                        }
                        else -> {
                            sharedViewModel.setErrorMessage("예상치 못한 오류가 발생했습니다.")
                            Log.e(RESULTS_FRAGMENT, "Unexpected error in analyzeProduct", error)
                        }
                    }
                }
            }
            
            sharedViewModel.setLoading(false)
        }
    }
    
    /**
     * 분석 결과를 UI에 표시합니다.
     * 
     * 선택된 성분 여부에 따라 두 가지 모드로 동작합니다:
     * - 선택된 성분이 있으면: 개별 성분 상세 정보 표시
     * - 선택된 성분이 없으면: 전체 제품 분석 정보 표시
     * 
     * @param view Fragment의 루트 뷰
     * @param result RAG 서버로부터 받은 분석 결과
     * 
     * @see displayIngredientDetailInfo 개별 성분 상세 정보를 표시하는 메서드
     * @see displayProductAnalysisInfo 전체 제품 분석 정보를 표시하는 메서드
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
      * 개별 성분의 상세 정보를 표시합니다.
      * 
      * 성능 최적화를 위해 병렬 처리와 점진적 업데이트 패턴을 사용합니다.
      * 
      * 처리 과정:
      * 1. RAG 서버 결과에서 해당 성분 찾기 (goodMatches, badMatches)
      * 2. 성분명 즉시 표시
      * 3. 피부 타입 적합성 즉시 결정 (RAG 서버 데이터 우선)
      * 4. 병렬 처리로 다음 정보 동시 로드:
      *    - Purpose (기능): ingredients.json → 없으면 Gemini AI
      *    - Suitability (피부 타입 적합성): RAG 서버 → 없으면 Gemini AI
      *    - Description (상세 설명): ingredients.json → 번역 → 없으면 Gemini AI
      * 5. 각 정보가 준비되는 대로 UI 업데이트
      * 
      * 데이터 소스 우선순위:
      * - Purpose: ingredients.json > Gemini AI
      * - Suitability: RAG 서버 > Gemini AI
      * - Description: ingredients.json (번역) > Gemini AI
      * 
      * @param view Fragment의 루트 뷰
      * @param result RAG 서버로부터 받은 분석 결과
      * @param ingredientName 표시할 성분명
      * 
      * @see loadIngredientPurpose ingredients.json에서 purpose를 로드하는 메서드
      * @see loadIngredientDescriptionValue ingredients.json에서 description을 로드하는 메서드
      * @see GeminiService 성분 정보를 생성하는 AI 서비스
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
             
             // 성분명 즉시 표시
             view.findViewById<TextView>(R.id.ingredientName)?.text = ingredientName
             
             // 피부 타입 적합성 즉시 결정 (RAG 서버 데이터 우선)
             val suitability = when {
                 goodMatch != null && badMatch != null -> {
                     val goodSkinTypes = extractSkinTypesFromPurpose(goodMatch.purpose)
                     val badSkinTypes = extractSkinTypesFromDescription(badMatch.description)
                     "권장: $goodSkinTypes, 주의: $badSkinTypes"
                 }
                 goodMatch != null -> {
                     val goodSkinTypes = extractSkinTypesFromPurpose(goodMatch.purpose)
                     "권장: $goodSkinTypes"
                 }
                 badMatch != null -> {
                     val badSkinTypes = extractSkinTypesFromDescription(badMatch.description)
                     "주의: $badSkinTypes"
                 }
                 else -> null
             }
             
             // ==== 병렬 처리: 독립적인 정보 동시 로드 ====
             val purposeDeferred = async {
                 val localPurpose = loadIngredientPurpose(ingredientName)
                 if (localPurpose.isNotEmpty()) {
                     localPurpose
                 } else {
                     try {
                        geminiService.generateIngredientPurpose(ingredientName)
                     } catch (e: Exception) {
                         Log.e(RESULTS_FRAGMENT, "$GEMINI_API_FAILED (purpose): ${e.message}", e)
                         "정보를 불러올 수 없습니다."
                     }
                 }
             }
             
             val suitabilityDeferred = async {
                 suitability ?: try {
                     geminiService.generateSkinTypeSuitability(ingredientName)
                 } catch (e: Exception) {
                     Log.e(RESULTS_FRAGMENT, "$GEMINI_API_FAILED (suitability): ${e.message}", e)
                     "모든 피부 타입"
                 }
             }
             
             val descriptionDeferred = async {
                 loadIngredientDescriptionValue(ingredientName)
             }
             
             // ==== 점진적 업데이트: 결과가 준비되는 대로 UI 업데이트 ====
             // Purpose 업데이트
             launch {
                 val purposeValue = purposeDeferred.await()
                 view.findViewById<TextView>(R.id.ingredientPurpose)?.text = purposeValue
             }
             
             // Suitability 업데이트
             launch {
                 val suitabilityValue = suitabilityDeferred.await()
                 view.findViewById<TextView>(R.id.ingredientSuitability)?.text = suitabilityValue
             }
             
             // Description 업데이트
             launch {
                 val descriptionValue = descriptionDeferred.await()
                 view.findViewById<TextView>(R.id.aiExplanation)?.text = descriptionValue
             }
         }
     }
     
     /**
      * ingredients.json에서 성분의 description을 로드하여 반환합니다.
      * 
      * 병렬 처리 최적화를 위해 반환값으로 description을 제공합니다.
      * 
      * 처리 과정:
      * 1. ingredients.json 로드 (캐시가 있으면 재사용)
      * 2. 성분명으로 성분 정보 찾기 (정확 매칭 → 부분 매칭)
      * 3. description이 있으면 Gemini AI로 한국어 번역
      * 4. description이 없거나 번역 실패 시 Gemini AI로 새로 생성
      * 
      * @param ingredientName 찾을 성분명
      * @return 한국어로 번역된 description 또는 Gemini AI로 생성한 설명
      * 
      * @see findIngredientByName 성분명으로 ingredients.json에서 찾는 메서드
      * @see GeminiService.translateIngredientDescription 영문 description을 번역하는 메서드
      * @see GeminiService.generateIngredientDescription description을 생성하는 메서드
      */
     private suspend fun loadIngredientDescriptionValue(ingredientName: String): String {
         return withContext(Dispatchers.IO) {
             try {
                 // 효율성 개선: IngredientCache 사용 (인덱싱)
                 ingredientCache.loadData()
                 val ingredientInfo = ingredientCache.findByName(ingredientName)
                 
                 if (ingredientInfo != null) {
                     val description = ingredientInfo.optString("description", "")
                     
                     if (description.isNotEmpty()) {
                         // 영문 description을 한국어로 번역
                        return@withContext try {
                            geminiService.translateIngredientDescription(ingredientName, description)
                        } catch (e: Exception) {
                            Log.e(RESULTS_FRAGMENT, "$GEMINI_API_FAILED (translate): ${e.message}", e)
                            geminiService.generateIngredientDescription(ingredientName)
                        }
                     }
                 }
                 
                 // ingredients.json에 없으면 Gemini로 생성
                return@withContext try {
                    geminiService.generateIngredientDescription(ingredientName)
                } catch (e: Exception) {
                    Log.e(RESULTS_FRAGMENT, "$GEMINI_API_FAILED (generate description): ${e.message}", e)
                    "해당 성분에 대한 정보를 생성할 수 없습니다."
                }
            } catch (e: Exception) {
                Log.e(RESULTS_FRAGMENT, "$DATA_LOAD_FAILED (description): ${e.message}", e)
                return@withContext "해당 성분에 대한 정보를 생성할 수 없습니다."
            }
         }
     }
    
     /**
      * ingredients.json에서 성분의 purpose 배열을 로드하여 한국어로 변환합니다.
      * 
      * ingredients.json의 purpose는 영문으로 저장되어 있으므로 한국어로 변환합니다.
      * 
      * 처리 과정:
      * 1. ingredients.json 로드 (캐시가 있으면 재사용)
      * 2. 성분명으로 성분 정보 찾기
      * 3. purpose 배열을 순회하며 각 purpose를 한국어로 변환
      * 4. 변환된 purpose들을 쉼표로 구분하여 반환
      * 
      * @param ingredientName 찾을 성분명
      * @return 한국어로 변환된 purpose 문자열 (쉼표로 구분), 없으면 빈 문자열
      * 
      * @see findIngredientByName 성분명으로 ingredients.json에서 찾는 메서드
      * @see translatePurposeToKorean 영문 purpose를 한국어로 변환하는 메서드
      */
     private suspend fun loadIngredientPurpose(ingredientName: String): String {
         return withContext(Dispatchers.IO) {
             try {
                 // 효율성 개선: IngredientCache 사용 (인덱싱)
                 ingredientCache.loadData()
                 val ingredientInfo = ingredientCache.findByName(ingredientName)
                 
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
                Log.e(RESULTS_FRAGMENT, "$DATA_LOAD_FAILED (purpose): ${e.message}", e)
                return@withContext ""
            }
         }
     }
     
     /**
      * 영문 purpose를 한국어로 변환합니다.
     * 
     * ingredients.json에 저장된 영문 purpose를 한국어로 매핑합니다.
     * 매핑되지 않은 경우 원문을 그대로 반환합니다.
     * 
     * 지원하는 purpose 매핑:
     * - moisturizer, humectant → 보습제
     * - exfoliant → 각질제거제
     * - solvent → 용매
     * - fragrance, perfuming → 향료
     * - antioxidant → 항산화제
     * - emulsifier → 유화제
     * - thickener → 증점제
     * - surfactant → 계면활성제
     * - preservative → 방부제
     * - emollient → 연화제
     * - sunscreen, uv filter → 자외선차단제
     * - colorant → 착색제
     * - buffering → 완충제
     * - chelating → 킬레이트제
     * - antimicrobial → 항균제
     * - skin conditioning → 피부컨디셔닝
     * - viscosity controlling → 점도조절제
     * - absorbent → 흡수제
     * - astringent → 수렴제
     * - soothing → 진정제
     * - whitening → 미백제
     * - anti-acne → 여드름케어
     * 
     * @param englishPurpose 영문 purpose 문자열
     * @return 한국어로 변환된 purpose, 매핑되지 않은 경우 원문 반환
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
      * ingredients.json을 로드하여 성분의 description을 표시합니다.
      * 
      * description이 없거나 로드에 실패하면 Gemini AI로 새로 생성합니다.
      * 
      * 처리 과정:
      * 1. ingredients.json 로드 (캐시가 있으면 재사용)
      * 2. 성분명으로 성분 정보 찾기
      * 3. description이 있으면 Gemini AI로 한국어 번역
      * 4. description이 없거나 번역 실패 시 Gemini AI로 새로 생성
      * 
     * @param view Fragment의 루트 뷰
     * @param ingredientName 찾을 성분명
     * 
     * @see findIngredientByName 성분명으로 ingredients.json에서 찾는 메서드
     * @see GeminiService.translateIngredientDescription 영문 description을 번역하는 메서드
     * @see generateDescriptionWithGemini Gemini AI로 description을 생성하는 메서드
     */
     private fun loadIngredientDescription(view: View, ingredientName: String) {
         lifecycleScope.launch {
             try {
                 // 효율성 개선: IngredientCache 사용 (인덱싱)
                 withContext(Dispatchers.IO) {
                     ingredientCache.loadData()
                 }
                 val ingredientInfo = ingredientCache.findByName(ingredientName)
                 
                 if (ingredientInfo != null) {
                     // ingredients.json에 정보가 있는 경우
                     val description = ingredientInfo.optString("description", "")
                     
                     if (description.isNotEmpty()) {
                         // 영문 description을 한국어로 번역
                         view.findViewById<TextView>(R.id.aiExplanation)?.text = "설명을 생성하는 중..."
                         
                        val koreanDescription = try {
                            geminiService.translateIngredientDescription(ingredientName, description)
                        } catch (e: Exception) {
                            Log.e(RESULTS_FRAGMENT, "$GEMINI_API_FAILED (translate in load): ${e.message}", e)
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
                Log.e(RESULTS_FRAGMENT, "$DATA_LOAD_FAILED (ingredient info): ${e.message}", e)
                generateDescriptionWithGemini(view, ingredientName)
            }
         }
     }
     
     /**
      * Gemini AI를 사용하여 성분 설명을 생성합니다.
      * 
      * ingredients.json에 정보가 없거나 로드에 실패한 경우 호출됩니다.
      * 
     * @param view Fragment의 루트 뷰
     * @param ingredientName 설명을 생성할 성분명
     * 
     * @see GeminiService.generateIngredientDescription Gemini AI로 description을 생성하는 메서드
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
                Log.e(RESULTS_FRAGMENT, "$GEMINI_API_FAILED (generate with gemini): ${e.message}", e)
            }
         }
     }
    
    // 효율성 개선: loadIngredientsJson()과 findIngredientByName() 메서드 제거
    // IngredientCache가 이 기능을 대체합니다.
    
    /**
     * 전체 제품 분석 정보를 표시합니다.
     * 
     * RAG 서버로부터 받은 전체 제품 분석 결과를 UI에 표시합니다.
     * 
     * 표시되는 정보:
     * 1. AI 분석 리포트 (analysisReport)
     * 2. 좋은 성분 목록 (goodMatches, 중복 제거)
     * 3. 주의 성분 목록 (badMatches, 중복 제거)
     * 
     * 중복 제거:
     * - 성분명 기준으로 distinctBy를 사용하여 동일한 이름의 성분은 하나만 표시
     * - 이렇게 하면 RAG 서버에서 중복으로 반환된 성분을 필터링
     * 
     * @param view Fragment의 루트 뷰
     * @param result RAG 서버로부터 받은 분석 결과
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
     * 성분의 purpose 문자열에서 피부 타입을 추출합니다.
     * 
     * purpose 문자열에 포함된 피부 타입 키워드를 찾아 한국어로 반환합니다.
     * 여러 피부 타입이 포함된 경우 모두 추출하여 쉼표로 구분된 문자열로 반환합니다.
     * 
     * 인식하는 피부 타입:
     * - "지성" 또는 "oily" → "지성"
     * - "건성" 또는 "dry" → "건성"
     * - "민감성" 또는 "sensitive" → "민감성"
     * - "여드름" 또는 "acne" → "여드름성"
     * - 매칭되는 키워드가 없으면 → "모든 피부"
     * 
     * @param purpose 성분의 목적(purpose) 문자열
     * @return 추출된 피부 타입 문자열 (여러 개인 경우 쉼표로 구분), 없으면 "모든 피부"
     * 
     * @see extractSkinTypesFromDescription description에서 피부 타입을 추출하는 메서드
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
     * 성분의 description 문자열에서 피부 타입을 추출합니다.
     * 
     * description 문자열에 포함된 피부 타입 키워드를 찾아 한국어로 반환합니다.
     * 주의 성분의 경우 기본값으로 "일부 피부"를 반환합니다.
     * 
     * 인식하는 피부 타입:
     * - "지성" 또는 "oily" → "지성"
     * - "건성" 또는 "dry" → "건성"
     * - "민감성" 또는 "sensitive" → "민감성"
     * - "여드름" 또는 "acne" → "여드름성"
     * - 매칭되는 키워드가 없으면 → "일부 피부" (주의 성분이므로)
     * 
     * @param description 성분의 설명(description) 문자열
     * @return 추출된 피부 타입 문자열 (여러 개인 경우 쉼표로 구분), 없으면 "일부 피부"
     * 
     * @see extractSkinTypesFromPurpose purpose에서 피부 타입을 추출하는 메서드
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


