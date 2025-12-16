package com.example.cosmetic

import android.animation.ObjectAnimator
import android.util.Log
import android.animation.ValueAnimator
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmetic.Constants.Analysis.MIN_REPORT_LENGTH
import com.example.cosmetic.Constants.Animation.FADE_DURATION_MS
import com.example.cosmetic.Constants.Animation.LOADING_MESSAGE_INTERVAL_MS
import com.example.cosmetic.Constants.ErrorMessage.ANALYSIS_FAILED
import com.example.cosmetic.Constants.ErrorMessage.SERVER_CONNECTION_FAILED
import com.example.cosmetic.Constants.LogTag.DETAILS_FRAGMENT
import com.example.cosmetic.network.AnalyzeProductRequest
import com.example.cosmetic.network.AnalyzeProductResponse
import com.example.cosmetic.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 제품 분석 결과 상세 화면 Fragment
 * 
 * 이 Fragment는 OCR로 인식된 화장품 성분을 분석한 결과를 사용자에게 표시합니다.
 * 주요 기능:
 * - 전체 제품 분석 요약 표시 (AI 분석 리포트, 추천/주의 피부 타입, 보습/장벽 성분)
 * - 전성분 목록 표시 및 접기/펼치기 기능
 * - 성분명 클릭 시 개별 성분 상세 정보 화면으로 이동
 * - 성분 뱃지(좋음/주의) 클릭 시 간단한 이유 설명 Bottom Sheet 표시
 * 
 * 데이터 흐름:
 * 1. SharedViewModel의 recognizedText를 관찰하여 성분 파싱
 * 2. 파싱된 성분으로 RAG 서버에 분석 요청
 * 3. 분석 결과를 UI에 표시 (서버 리포트 우선, 부족 시 Gemini AI로 보완)
 * 
 * @see ResultsFragment 개별 성분 상세 정보를 표시하는 Fragment
 * @see SharedViewModel Fragment 간 데이터 공유를 위한 ViewModel
 */
class DetailsFragment : Fragment() {
    
    // 뷰 모델 가져오기 아마 OCR 로 받은 값을 해당 파일에서도 사용하기 위해서
    private val sharedViewModel: SharedViewModel by activityViewModels()
    // 초기 값 
    private var isIngredientListExpanded = false
    private lateinit var ingredientsAdapter: IngredientsAdapter
    
    // 현재 분석 결과 저장 (Bottom Sheet에서 사용)
    private var currentAnalysisResult: AnalyzeProductResponse? = null
    
    // Gemini AI Service
    private val geminiService by lazy {
        GeminiService(BuildConfig.GEMINI_API_KEY)
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
        return inflater.inflate(R.layout.fragment_details, container, false)
    }

    /**
     * 뷰가 생성된 후 초기화 작업을 수행합니다.
     * 
     * 이 메서드에서 다음 작업을 수행합니다:
     * - 뒤로가기 버튼 설정
     * - 전성분 목록 접기/펼치기 토글 설정
     * - RecyclerView 어댑터 초기화
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
        
        // 전성분 목록 접기/펼치기 토글
        val toggleHeader = view.findViewById<View>(R.id.ingredientToggleHeader)
        val toggleIcon = view.findViewById<TextView>(R.id.ingredientToggleIcon)
        val ingredientsRecyclerView = view.findViewById<RecyclerView>(R.id.ingredientsRecyclerView)
        
        // RecyclerView 설정
        ingredientsAdapter = IngredientsAdapter(
            goodMatches = emptySet(),
            badMatches = emptySet(),
            goodMatchesData = emptyMap(),
            badMatchesData = emptyMap(),
            onIngredientNameClick = { ingredient ->
                // 성분명 클릭 시 ResultsFragment로 이동하여 상세 정보 표시
                navigateToIngredientDetail(ingredient)
            },
            onBadgeClick = { ingredient, ingredientType, reason ->
                // 뱃지 클릭 시 주의/좋음 이유 Bottom Sheet 표시
                showReasonBottomSheet(ingredient, ingredientType, reason)
            }
        )
        
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
                // 성분 색상 업데이트
                updateIngredientColors(it)
            }
        }
        
        // 인식된 텍스트에서 성분 파싱 및 분석 수행
        // parsedIngredients가 이미 있으면 재사용 (ResultsFragment에서 파싱된 것)
        sharedViewModel.parsedIngredients.observe(viewLifecycleOwner) { ingredients ->
            if (ingredients.isNotEmpty()) {
                ingredientsAdapter.submitList(ingredients)
            }
        }
        
        // 인식된 텍스트가 있고 parsedIngredients가 없으면 파싱 수행
        sharedViewModel.recognizedText.observe(viewLifecycleOwner) { recognizedText ->
            if (recognizedText.isNotEmpty() && sharedViewModel.parsedIngredients.value.isNullOrEmpty()) {
                // IngredientParser를 사용하여 성분 파싱
                val ingredients = ingredientParser.parseIngredients(recognizedText)
                if (ingredients.isNotEmpty()) {
                    sharedViewModel.parsedIngredients.value = ingredients
                    ingredientsAdapter.submitList(ingredients)
                    
                    // 전체 제품 분석 수행
                    analyzeProduct(ingredients)
                }
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
     * @param view Fragment의 루트 뷰
     * 
     * @see hideLoadingAnimation 로딩 애니메이션을 중지하는 메서드
     */
    private fun showLoadingAnimation(view: View) {
        currentMessageIndex = 0
        
        val loadingMessage = view.findViewById<TextView>(R.id.loadingMessage)
        val loadingSubMessage = view.findViewById<TextView>(R.id.loadingSubMessage)
        
        // 초기 메시지 설정
        loadingMessage?.text = loadingMessages[0]
        loadingSubMessage?.text = loadingSubMessages[0]
        
        // 메시지 변경 핸들러 시작
        loadingMessageHandler = Handler(Looper.getMainLooper())
        loadingMessageRunnable = object : Runnable {
            override fun run() {
                currentMessageIndex = (currentMessageIndex + 1) % loadingMessages.size
                
                // 페이드 아웃 → 텍스트 변경 → 페이드 인 애니메이션
                loadingMessage?.let { messageView ->
                    val fadeOut = ObjectAnimator.ofFloat(messageView, "alpha", 1f, 0f).apply {
                        duration = FADE_DURATION_MS
                    }
                    fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
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
                
                // 로딩 메시지 변경 주기
                loadingMessageHandler?.postDelayed(this, LOADING_MESSAGE_INTERVAL_MS)
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
     * 제품 성분 분석을 위해 RAG 서버에 API 요청을 보냅니다.
     * 
     * 사용자의 피부 타입과 함께 성분 리스트를 서버로 전송하여 분석을 요청합니다.
     * 분석 결과는 SharedViewModel의 analysisResult에 저장되며, 이를 관찰하는 UI가 자동으로 업데이트됩니다.
     * 
     * 처리 흐름:
     * 1. 로딩 상태를 true로 설정
     * 2. 사용자 피부 타입 조회
     * 3. RAG 서버에 분석 요청 (IO 스레드에서 실행)
     * 4. 성공 시 결과를 SharedViewModel에 저장
     * 5. 실패 시 에러 메시지를 SharedViewModel에 저장
     * 6. finally 블록에서 로딩 상태를 false로 설정
     * 
     * 에러 처리:
     * - UnknownHostException: 서버 연결 불가 (ngrok 터널 확인 필요)
     * - ConnectException: 서버 연결 거부 (서버 실행 여부 확인)
     * - 기타 예외: 네트워크 오류 메시지 표시
     * 
     * @param ingredients 분석할 성분명 리스트
     * 
     * @throws Exception 네트워크 오류 또는 서버 오류 발생 시
     * 
     * @see SharedViewModel.analysisResult 분석 결과를 저장하는 LiveData
     * @see displayAnalysisDetails 분석 결과를 UI에 표시하는 메서드
     */
    private fun analyzeProduct(ingredients: List<String>) {
        // ResultsFragment와 동일한 로직으로 전체 제품 분석 수행
        lifecycleScope.launch {
            sharedViewModel.isLoading.value = true
            sharedViewModel.errorMessage.value = null
            
            try {
                // 사용자 피부 타입 가져오기
                val userPreferences = UserPreferences(requireContext())
                val skinType = userPreferences.getSkinType()
                
                val request = com.example.cosmetic.network.AnalyzeProductRequest(
                    ingredients = ingredients,
                    skinType = skinType // 사용자 피부 타입 사용
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
            } catch (e: java.net.UnknownHostException) {
                // CRITICAL: DNS 해석 실패 (네트워크 또는 서버 주소 문제)
                sharedViewModel.errorMessage.value = 
                    "서버에 연결할 수 없습니다.\n\n확인 사항:\n1. 인터넷 연결 확인\n2. ngrok 터널이 실행 중인지 확인\n3. ngrok 주소가 변경되지 않았는지 확인"
                Log.e(DETAILS_FRAGMENT, "DNS resolution failed", e)
            } catch (e: java.net.ConnectException) {
                // CRITICAL: 서버 연결 거부 (서버 미실행)
                sharedViewModel.errorMessage.value = 
                    "서버에 연결할 수 없습니다. ngrok 터널이 실행 중인지 확인해주세요."
                Log.e(DETAILS_FRAGMENT, "Connection refused", e)
            } catch (e: java.net.SocketTimeoutException) {
                // CRITICAL: 타임아웃 (서버 응답 지연)
                sharedViewModel.errorMessage.value = "서버 응답 시간이 초과되었습니다. 다시 시도해주세요."
                Log.e(DETAILS_FRAGMENT, "Socket timeout", e)
            } catch (e: java.io.IOException) {
                // CRITICAL: 기타 네트워크 오류
                sharedViewModel.errorMessage.value = "네트워크 오류: ${e.message}"
                Log.e(DETAILS_FRAGMENT, "Network I/O error", e)
            } catch (e: org.json.JSONException) {
                // CRITICAL: JSON 파싱 오류 (서버 응답 형식 문제)
                sharedViewModel.errorMessage.value = "서버 응답 형식이 올바르지 않습니다."
                Log.e(DETAILS_FRAGMENT, "JSON parsing error", e)
            } catch (e: Exception) {
                // CRITICAL: 예상치 못한 예외 (OutOfMemoryError 등 시스템 에러는 제외)
                // 시스템 에러는 이 블록에 들어오지 않고 상위로 전파되어 앱 재시작
                sharedViewModel.errorMessage.value = "예상치 못한 오류: ${e.message}"
                Log.e(DETAILS_FRAGMENT, "Unexpected error in analyzeProduct", e)
            } finally {
                sharedViewModel.isLoading.value = false
            }
        }
    }
    
    /**
     * 분석 결과의 상세 정보를 UI에 표시합니다.
     * 
     * RAG 서버로부터 받은 분석 결과를 파싱하여 각 UI 컴포넌트에 표시합니다.
     * 
     * 표시되는 정보:
     * 1. AI 분석 요약 (Gemini로 보완 가능)
     * 2. 추천 피부 타입 (goodMatches에서 추출, 중복 제거)
     * 3. 주의 피부 타입 (badMatches에서 추출, 중복 제거)
     * 4. 보습 성분 (purpose에 "보습", "moisturizer", "humectant" 포함)
     * 5. 장벽 지원 성분 (세라마이드 등)
     * 
     * @param view Fragment의 루트 뷰
     * @param result RAG 서버로부터 받은 분석 결과
     * 
     * @see generateEnhancedAnalysisSummary AI 분석 요약을 생성하는 메서드
     * @see updateCardStyle 카드 스타일을 업데이트하는 메서드
     * @see extractSkinTypeFromPurpose purpose에서 피부 타입을 추출하는 메서드
     * @see extractSkinTypeFromDescription description에서 피부 타입을 추출하는 메서드
     */
    private fun displayAnalysisDetails(view: View, result: com.example.cosmetic.network.AnalyzeProductResponse) {
        // AI 분석 요약 - Gemini로 더 풍부한 정보 생성
        generateEnhancedAnalysisSummary(view, result)
        
        // 추천 피부 타입 (중복 제거)
        val recommendedText: String
        val hasRecommended: Boolean
        if (result.goodMatches.isNotEmpty()) {
            val uniqueGoodMatches = result.goodMatches.distinctBy { it.name }
            val skinTypes = uniqueGoodMatches
                .map { match -> extractSkinTypeFromPurpose(match.purpose) }
                .distinct()
                .joinToString(", ")
            recommendedText = skinTypes.ifEmpty { "없음" }
            hasRecommended = skinTypes.isNotEmpty()
        } else {
            recommendedText = "없음"
            hasRecommended = false
        }
        view.findViewById<TextView>(R.id.recommendedSkinTypes)?.text = recommendedText
        updateCardStyle(
            view,
            iconId = R.id.iconRecommendedSkin,
            labelId = R.id.labelRecommendedSkin,
            valueId = R.id.recommendedSkinTypes,
            hasData = hasRecommended,
            activeEmoji = "🌿",
            inactiveEmoji = "🌿"
        )
        
        // 주의 피부 타입 (중복 제거)
        val cautionText: String
        val hasCaution: Boolean
        if (result.badMatches.isNotEmpty()) {
            val uniqueBadMatches = result.badMatches.distinctBy { it.name }
            val skinTypes = uniqueBadMatches
                .map { match -> extractSkinTypeFromDescription(match.description) }
                .distinct()
                .joinToString(", ")
            cautionText = skinTypes.ifEmpty { "없음" }
            hasCaution = skinTypes.isNotEmpty()
        } else {
            cautionText = "없음"
            hasCaution = false
        }
        view.findViewById<TextView>(R.id.cautionSkinTypes)?.text = cautionText
        updateCardStyle(
            view,
            iconId = R.id.iconCautionSkin,
            labelId = R.id.labelCautionSkin,
            valueId = R.id.cautionSkinTypes,
            hasData = hasCaution,
            activeEmoji = "⚠️",
            inactiveEmoji = "⚠️"
        )
        
        // 보습 성분 (중복 제거)
        val uniqueGoodMatches = result.goodMatches.distinctBy { it.name }
        val hydratingIngredients = uniqueGoodMatches.filter { match ->
            match.purpose.contains("보습", ignoreCase = true) ||
            match.purpose.contains("moisturizer", ignoreCase = true) ||
            match.purpose.contains("humectant", ignoreCase = true)
        }
        val hydratingText: String
        val hasHydrating: Boolean
        if (hydratingIngredients.isNotEmpty()) {
            hydratingText = "${hydratingIngredients.first().name} 함유"
            hasHydrating = true
        } else {
            hydratingText = "없음"
            hasHydrating = false
        }
        view.findViewById<TextView>(R.id.hydratingIngredients)?.text = hydratingText
        updateCardStyle(
            view,
            iconId = R.id.iconHydrating,
            labelId = R.id.labelHydrating,
            valueId = R.id.hydratingIngredients,
            hasData = hasHydrating,
            activeEmoji = "💧",
            inactiveEmoji = "💧"
        )
        
        // 장벽 지원 성분 (중복 제거)
        val barrierIngredients = uniqueGoodMatches.filter { match ->
            match.name.contains("세라마이드", ignoreCase = true) ||
            match.name.contains("ceramide", ignoreCase = true) ||
            match.purpose.contains("장벽", ignoreCase = true) ||
            match.purpose.contains("barrier", ignoreCase = true)
        }
        val barrierText: String
        val hasBarrier: Boolean
        if (barrierIngredients.isNotEmpty()) {
            barrierText = barrierIngredients.first().name
            hasBarrier = true
        } else {
            barrierText = "없음"
            hasBarrier = false
        }
        view.findViewById<TextView>(R.id.barrierIngredients)?.text = barrierText
        updateCardStyle(
            view,
            iconId = R.id.iconBarrier,
            labelId = R.id.labelBarrier,
            valueId = R.id.barrierIngredients,
            hasData = hasBarrier,
            activeEmoji = "🛡️",
            inactiveEmoji = "🛡️"
        )
    }
    
    /**
     * 카드의 활성화/비활성화 스타일을 업데이트합니다.
     * 
     * 데이터 존재 여부에 따라 카드의 시각적 스타일을 변경합니다.
     * 
     * 활성화 상태 (hasData = true):
     * - 아이콘: 컬러 이모지, 투명도 100%
     * - 라벨: 진한 텍스트 색상, 볼드체
     * - 값: 기본 텍스트 색상
     * 
     * 비활성화 상태 (hasData = false):
     * - 아이콘: 동일한 이모지, 투명도 30% (회색처럼 보임)
     * - 라벨: 비활성화 텍스트 색상, 일반체
     * - 값: 비활성화 텍스트 색상
     * 
     * @param view Fragment의 루트 뷰
     * @param iconId 아이콘 TextView의 리소스 ID
     * @param labelId 라벨 TextView의 리소스 ID
     * @param valueId 값 TextView의 리소스 ID
     * @param hasData 데이터 존재 여부
     * @param activeEmoji 활성화 상태일 때 표시할 이모지
     * @param inactiveEmoji 비활성화 상태일 때 표시할 이모지 (현재는 activeEmoji와 동일하게 사용)
     */
    private fun updateCardStyle(
        view: View,
        iconId: Int,
        labelId: Int,
        valueId: Int,
        hasData: Boolean,
        activeEmoji: String,
        inactiveEmoji: String
    ) {
        val iconView = view.findViewById<TextView>(iconId)
        val labelView = view.findViewById<TextView>(labelId)
        val valueView = view.findViewById<TextView>(valueId)
        
        if (hasData) {
            // 활성화 상태: 컬러 아이콘 + 진한 글씨
            iconView?.apply {
                text = activeEmoji
                alpha = 1.0f
            }
            labelView?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
                setTypeface(typeface, Typeface.BOLD)
            }
            valueView?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
            }
        } else {
            // 비활성화 상태: 회색 아이콘 + 연한 글씨
            iconView?.apply {
                text = inactiveEmoji
                alpha = 0.3f  // 회색처럼 보이게 투명도 적용
            }
            labelView?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_disabled))
                setTypeface(typeface, Typeface.NORMAL)
            }
            valueView?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_disabled))
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
     * - 매칭되는 키워드가 없으면 → "중성"
     * 
     * @param purpose 성분의 목적(purpose) 문자열
     * @return 추출된 피부 타입 문자열 (여러 개인 경우 쉼표로 구분), 없으면 "중성"
     * 
     * @see extractSkinTypeFromDescription description에서 피부 타입을 추출하는 메서드
     */
    private fun extractSkinTypeFromPurpose(purpose: String): String {
        return when {
            purpose.contains("지성", ignoreCase = true) || purpose.contains("oily", ignoreCase = true) -> "지성"
            purpose.contains("건성", ignoreCase = true) || purpose.contains("dry", ignoreCase = true) -> "건성"
            purpose.contains("민감성", ignoreCase = true) || purpose.contains("sensitive", ignoreCase = true) -> "민감성"
            purpose.contains("여드름", ignoreCase = true) || purpose.contains("acne", ignoreCase = true) -> "여드름성"
            else -> "중성"
        }
    }
    
    /**
     * 성분의 description 문자열에서 피부 타입을 추출합니다.
     * 
     * description 문자열에 포함된 피부 타입 키워드를 찾아 한국어로 반환합니다.
     * 주의 성분의 경우 기본값으로 "민감성"을 반환합니다.
     * 
     * 인식하는 피부 타입:
     * - "지성" 또는 "oily" → "지성"
     * - "건성" 또는 "dry" → "건성"
     * - "민감성" 또는 "sensitive" → "민감성"
     * - "여드름" 또는 "acne" → "여드름성"
     * - 매칭되는 키워드가 없으면 → "민감성" (주의 성분이므로)
     * 
     * @param description 성분의 설명(description) 문자열
     * @return 추출된 피부 타입 문자열 (여러 개인 경우 쉼표로 구분), 없으면 "민감성"
     * 
     * @see extractSkinTypeFromPurpose purpose에서 피부 타입을 추출하는 메서드
     */
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
     * RAG 서버 리포트를 우선 사용하고, 부족할 경우에만 Gemini AI로 보완합니다.
     * 
     * 성능 최적화를 위해 즉시 표시 + 점진적 업데이트 패턴을 사용합니다.
     * 
     * 처리 흐름:
     * 1. 서버 리포트를 즉시 표시 (볼드 처리 적용)
     * 2. 서버 리포트가 충분히 상세하면 (100자 이상, "분석 중"/"오류" 미포함) Gemini 호출 생략
     * 3. 서버 리포트가 부족하면 백그라운드에서 Gemini로 개선
     * 4. Gemini 결과가 준비되면 UI 업데이트
     * 
     * 볼드 처리:
     * - 성분명 (goodMatches, badMatches)
     * - 핵심 키워드 (보습, 진정, 미백, 피부 타입 등)
     * 
     * @param view Fragment의 루트 뷰
     * @param result RAG 서버로부터 받은 분석 결과
     * 
     * @see applyBoldToKeywords 키워드에 볼드 스타일을 적용하는 메서드
     * @see GeminiService.enhanceProductAnalysisSummary Gemini AI로 리포트를 개선하는 메서드
     */
    private fun generateEnhancedAnalysisSummary(view: View, result: com.example.cosmetic.network.AnalyzeProductResponse) {
        val aiSummaryTextView = view.findViewById<TextView>(R.id.aiSummaryText) ?: return
        
        // 분석에서 추출한 성분명들을 키워드로 사용
        val goodMatchNames = result.goodMatches.map { it.name }
        val badMatchNames = result.badMatches.map { it.name }
        
        // ==== 즉시 표시: 서버 리포트를 먼저 표시 ====
        val serverReportText = if (result.analysisReport.isNotEmpty()) {
            result.analysisReport
        } else {
            "성분 분석 결과를 불러오는 중..."
        }
        val styledText = applyBoldToKeywords(serverReportText, goodMatchNames, badMatchNames)
        aiSummaryTextView.text = styledText
        
        // 서버 리포트가 충분히 상세하면 Gemini 호출 생략
        if (result.analysisReport.length > MIN_REPORT_LENGTH && 
            !result.analysisReport.contains("분석 중") && 
            !result.analysisReport.contains("오류")) {
            return
        }
        
        // ==== 점진적 업데이트: 서버 리포트가 부족할 경우에만 Gemini로 개선 ====
        lifecycleScope.launch {
            try {
                val ingredients = sharedViewModel.parsedIngredients.value ?: emptyList()
                val goodMatches = result.goodMatches.distinctBy { it.name }.map { it.name }
                val badMatches = result.badMatches.distinctBy { it.name }.map { it.name }
                
                val enhancedSummary = geminiService.enhanceProductAnalysisSummary(
                    serverReport = result.analysisReport,
                    ingredients = ingredients,
                    goodMatches = goodMatches,
                    badMatches = badMatches
                )
                
                // Gemini 결과가 서버 리포트와 다르면 업데이트
                if (enhancedSummary != result.analysisReport) {
                    val enhancedStyledText = applyBoldToKeywords(enhancedSummary, goodMatchNames, badMatchNames)
                    aiSummaryTextView.text = enhancedStyledText
                }
                
            } catch (e: Exception) {
                Log.e(DETAILS_FRAGMENT, "Gemini 리포트 개선 실패: ${e.message}", e)
                // 에러 발생 시에도 이미 서버 리포트가 표시되어 있으므로 추가 작업 불필요
            }
        }
    }
    
    /**
     * 텍스트 내의 핵심 키워드에 볼드 스타일을 적용합니다.
     * 
     * 사용자가 중요한 정보를 빠르게 파악할 수 있도록 성분명과 핵심 키워드를 강조합니다.
     * 
     * 볼드 처리되는 키워드:
     * 1. 기본 키워드: 보습, 진정, 미백, 항산화, 각질제거, 자외선차단, 항노화, 피부 타입, 주의 관련 키워드
     * 2. 성분명: goodIngredients와 badIngredients에 포함된 모든 성분명
     * 
     * 처리 방식:
     * - 대소문자 구분 없이 검색 (ignoreCase = true)
     * - 텍스트 내 모든 매칭 위치에 볼드 적용
     * - SpannableString을 사용하여 TextView에 직접 적용 가능
     * 
     * @param text 원본 텍스트
     * @param goodIngredients 좋은 성분명 리스트
     * @param badIngredients 주의 성분명 리스트
     * @return 볼드 스타일이 적용된 SpannableString
     * 
     * @see StyleSpan 볼드 스타일을 적용하는 Span 클래스
     */
    private fun applyBoldToKeywords(
        text: String,
        goodIngredients: List<String>,
        badIngredients: List<String>
    ): SpannableString {
        val spannable = SpannableString(text)
        
        // 기본 핵심 키워드 (항상 볼드 처리)
        val baseKeywords = listOf(
            // 제품 목적/효과
            "보습", "진정", "미백", "항산화", "각질제거", "자외선차단", "항노화",
            // 피부 타입
            "지성", "건성", "민감성", "여드름성", "복합성", "중성",
            // 주의 관련
            "주의", "자극", "알레르기", "패치 테스트",
            // 영문 키워드
            "moisturizer", "soothing", "brightening", "antioxidant"
        )
        
        // 모든 키워드 합치기 (성분명 + 기본 키워드)
        val allKeywords = (baseKeywords + goodIngredients + badIngredients).distinct()
        
        for (keyword in allKeywords) {
            if (keyword.isBlank()) continue
            
            var startIndex = 0
            while (true) {
                val index = text.indexOf(keyword, startIndex, ignoreCase = true)
                if (index == -1) break
                
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    index,
                    index + keyword.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                startIndex = index + keyword.length
            }
        }
        
        return spannable
    }
    
    /**
     * 성분 리스트의 색상과 뱃지를 업데이트합니다.
     * 
     * 분석 결과를 바탕으로 각 성분이 좋은 성분인지 주의 성분인지 판단하여
     * RecyclerView 어댑터에 정보를 전달합니다.
     * 
     * 처리 과정:
     * 1. 분석 결과를 내부 변수에 저장 (Bottom Sheet에서 사용)
     * 2. goodMatches와 badMatches를 Set으로 변환 (소문자로 정규화)
     * 3. 성분명 → 상세 정보 매핑 생성 (purpose, description)
     * 4. 어댑터에 업데이트 요청
     * 
     * @param result RAG 서버로부터 받은 분석 결과
     * 
     * @see IngredientsAdapter.updateMatches 어댑터의 매칭 정보 업데이트 메서드
     */
    private fun updateIngredientColors(result: AnalyzeProductResponse) {
        // 분석 결과 저장 (Bottom Sheet에서 사용)
        currentAnalysisResult = result
        
        val goodMatches = result.goodMatches.map { it.name.lowercase() }.toSet()
        val badMatches = result.badMatches.map { it.name.lowercase() }.toSet()
        
        // 성분명 -> 상세 정보 매핑
        val goodMatchesData = result.goodMatches.associate { it.name.lowercase() to it.purpose }
        val badMatchesData = result.badMatches.associate { it.name.lowercase() to it.description }
        
        ingredientsAdapter.updateMatches(goodMatches, badMatches, goodMatchesData, badMatchesData)
    }
    
    /**
     * 성분명을 클릭했을 때 ResultsFragment로 이동하여 해당 성분의 상세 정보를 표시합니다.
     * 
     * Navigation Component를 사용하여 화면 전환을 수행합니다.
     * 선택된 성분명은 Bundle을 통해 전달됩니다.
     * 
     * @param ingredient 클릭된 성분명
     * 
     * @see ResultsFragment 선택된 성분의 상세 정보를 표시하는 Fragment
     */
    private fun navigateToIngredientDetail(ingredient: String) {
        // ResultsFragment로 이동 (selectedIngredient 전달)
        val bundle = Bundle().apply {
            putString("selectedIngredient", ingredient)
        }
        findNavController().navigate(R.id.action_nav_results_to_nav_details, bundle)
    }
    
    /**
     * 성분 뱃지를 클릭했을 때 주의/좋음 이유를 사용자 친화적인 설명으로 Bottom Sheet에 표시합니다.
     * 
     * 사용자가 성분의 뱃지(좋음/주의)를 클릭하면 해당 성분이 왜 좋은 성분인지 또는
     * 주의 성분인지에 대해 일반인이 쉽게 이해할 수 있는 설명을 Bottom Sheet로 표시합니다.
     * 
     * 처리 과정:
     * 1. Bottom Sheet Dialog 생성 및 레이아웃 인플레이트
     * 2. 성분명과 뱃지 타입에 따라 UI 설정
     * 3. Gemini AI를 사용하여 사용자 친화적인 설명 생성
     *    - 전문 용어 없이 쉬운 한국어로 2-3문장 설명
     *    - "왜" 주의/좋은 성분인지 명확히 설명
     *    - 실질적인 조언 포함
     * 4. 생성된 설명을 Bottom Sheet에 표시
     * 5. "상세 정보 보기" 버튼 클릭 시 ResultsFragment로 이동
     * 
     * UI 구성:
     * - 성분명 표시
     * - 뱃지 (좋은 성분/주의 성분)
     * - 사용자 친화적인 설명 (AI 생성)
     * - 상세 정보 보기 버튼
     * 
     * @param ingredient 성분명
     * @param ingredientType 성분 타입 ("good" 또는 "bad")
     * @param reason 원본 이유 설명 텍스트 (백엔드에서 전달받은 값)
     * 
     * @see GeminiService.generateUserFriendlyExplanation 사용자 친화적 설명 생성 메서드
     * @see navigateToIngredientDetail 상세 정보 화면으로 이동하는 메서드
     */
    private fun showReasonBottomSheet(ingredient: String, ingredientType: String, reason: String) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_ingredient, null)
        
        // 성분명 표시
        sheetView.findViewById<TextView>(R.id.sheetIngredientName)?.text = ingredient
        
        // 뱃지 설정
        val badgeView = sheetView.findViewById<TextView>(R.id.sheetIngredientBadge)
        val reasonContainer = sheetView.findViewById<LinearLayout>(R.id.sheetReasonContainer)
        val reasonTitle = sheetView.findViewById<TextView>(R.id.sheetReasonTitle)
        val reasonDescription = sheetView.findViewById<TextView>(R.id.sheetReasonDescription)
        
        // 기능/목적, 피부타입 섹션 숨김 (간단한 이유만 표시)
        sheetView.findViewById<View>(R.id.sheetPurposeContainer)?.visibility = View.GONE
        sheetView.findViewById<View>(R.id.sheetSkinTypeContainer)?.visibility = View.GONE
        
        when (ingredientType) {
            "good" -> {
                badgeView?.apply {
                    text = "좋은 성분"
                    setBackgroundResource(R.drawable.badge_good)
                    visibility = View.VISIBLE
                }
                reasonContainer?.visibility = View.VISIBLE
                reasonTitle?.text = "✅ 왜 좋은 성분인가요?"
                // 로딩 메시지 표시
                reasonDescription?.text = "AI가 설명을 준비하고 있어요..."
            }
            "bad" -> {
                badgeView?.apply {
                    text = "주의 성분"
                    setBackgroundResource(R.drawable.badge_bad)
                    visibility = View.VISIBLE
                }
                reasonContainer?.visibility = View.VISIBLE
                reasonTitle?.text = "⚠️ 왜 주의해야 하나요?"
                // 로딩 메시지 표시
                reasonDescription?.text = "AI가 설명을 준비하고 있어요..."
            }
        }
        
        // AI로 사용자 친화적인 설명 생성
        lifecycleScope.launch {
            try {
                val userFriendlyExplanation = geminiService.generateUserFriendlyExplanation(
                    ingredientName = ingredient,
                    ingredientType = ingredientType,
                    originalReason = reason
                )
                
                reasonDescription?.text = userFriendlyExplanation
            } catch (e: Exception) {
                Log.e(DETAILS_FRAGMENT, "사용자 친화적 설명 생성 실패: ${e.message}", e)
                // 실패 시 기본 메시지 표시
                val fallbackMessage = when (ingredientType) {
                    "bad" -> "이 성분은 일부 피부 타입에 자극을 줄 수 있어요. 민감한 피부라면 먼저 소량으로 테스트해보시는 것을 권장합니다."
                    "good" -> "피부에 좋은 효과를 주는 성분이에요. 꾸준히 사용하면 피부 개선에 도움이 됩니다."
                    else -> reason
                }
                reasonDescription?.text = fallbackMessage
            }
        }
        
        // "상세 정보 보기" 버튼
        sheetView.findViewById<TextView>(R.id.sheetCloseButton)?.apply {
            text = "상세 정보 보기"
            setOnClickListener {
            bottomSheetDialog.dismiss()
                // ResultsFragment로 이동
                navigateToIngredientDetail(ingredient)
            }
        }
        
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }
    
    /**
     * 전성분 목록을 표시하는 RecyclerView 어댑터입니다.
     * 
     * 각 성분에 대해 다음 정보를 표시합니다:
     * - 성분명 (클릭 가능, 클릭 시 상세 정보 화면으로 이동)
     * - 뱃지 (좋음/주의, 클릭 가능, 클릭 시 이유 설명 Bottom Sheet 표시)
     * 
     * 데이터 구조:
     * - goodMatches: 좋은 성분명 Set (소문자로 정규화)
     * - badMatches: 주의 성분명 Set (소문자로 정규화)
     * - goodMatchesData: 성분명 → purpose 매핑
     * - badMatchesData: 성분명 → description 매핑
     * 
     * @property goodMatches 좋은 성분명 Set
     * @property badMatches 주의 성분명 Set
     * @property goodMatchesData 성분명 → purpose 매핑
     * @property badMatchesData 성분명 → description 매핑
     * @property onIngredientNameClick 성분명 클릭 시 호출되는 콜백
     * @property onBadgeClick 뱃지 클릭 시 호출되는 콜백 (성분명, 타입, 이유)
     */
    private class IngredientsAdapter(
        private var goodMatches: Set<String>,
        private var badMatches: Set<String>,
        private var goodMatchesData: Map<String, String>,
        private var badMatchesData: Map<String, String>,
        private val onIngredientNameClick: (String) -> Unit,  // 성분명 클릭 → 상세 페이지 이동
        private val onBadgeClick: (String, String, String) -> Unit  // (성분명, 타입, 이유) → Bottom Sheet
    ) : RecyclerView.Adapter<IngredientsAdapter.IngredientViewHolder>() {
        
        private var ingredients: List<String> = emptyList()
        
        /**
         * 표시할 성분 리스트를 업데이트합니다.
         * 
         * @param newIngredients 새로운 성분 리스트
         */
        fun submitList(newIngredients: List<String>) {
            ingredients = newIngredients
            notifyDataSetChanged()
        }
        
        /**
         * 성분 매칭 정보를 업데이트합니다.
         * 
         * 분석 결과가 업데이트될 때 호출되어 각 성분의 좋음/주의 여부와
         * 관련 정보를 갱신합니다.
         * 
         * @param newGoodMatches 새로운 좋은 성분명 Set
         * @param newBadMatches 새로운 주의 성분명 Set
         * @param newGoodMatchesData 새로운 성분명 → purpose 매핑
         * @param newBadMatchesData 새로운 성분명 → description 매핑
         */
        fun updateMatches(
            newGoodMatches: Set<String>, 
            newBadMatches: Set<String>,
            newGoodMatchesData: Map<String, String>,
            newBadMatchesData: Map<String, String>
        ) {
            goodMatches = newGoodMatches
            badMatches = newBadMatches
            goodMatchesData = newGoodMatchesData
            badMatchesData = newBadMatchesData
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ingredient, parent, false)
            return IngredientViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
            val ingredient = ingredients[position]
            holder.bind(ingredient, goodMatches, badMatches)
        }
        
        override fun getItemCount(): Int = ingredients.size
        
        /**
         * 성분 아이템의 ViewHolder입니다.
         * 
         * 각 성분 아이템의 뷰를 관리하고 데이터를 바인딩합니다.
         */
        inner class IngredientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameView: TextView = itemView.findViewById(R.id.ingredientName)
            private val badgeView: TextView = itemView.findViewById(R.id.ingredientBadge)
            
            /**
             * 성분 데이터를 뷰에 바인딩합니다.
             * 
             * 성분명을 표시하고, goodMatches/badMatches에 따라 뱃지를 표시합니다.
             * 성분명과 뱃지에 각각 클릭 리스너를 설정합니다.
             * 
             * 뱃지 표시 규칙:
             * - goodMatches에 포함: 파란색 "좋음" 뱃지
             * - badMatches에 포함: 빨간색 "주의" 뱃지
             * - 둘 다 없음: 뱃지 숨김
             * 
             * @param ingredient 성분명
             * @param goodMatches 좋은 성분명 Set
             * @param badMatches 주의 성분명 Set
             */
            fun bind(ingredient: String, goodMatches: Set<String>, badMatches: Set<String>) {
                // 성분명 (검정색 통일)
                nameView.text = ingredient
                nameView.setTextColor(itemView.context.getColor(R.color.text_dark))
                
                // 성분 타입 결정 및 뱃지 표시
                val ingredientLower = ingredient.lowercase()
                var ingredientType = "neutral"
                var reason = ""
                
                when {
                    goodMatches.contains(ingredientLower) -> {
                        // 좋은 성분: 파란색 뱃지
                        ingredientType = "good"
                        reason = goodMatchesData[ingredientLower] ?: "피부에 좋은 성분입니다."
                        badgeView.visibility = View.VISIBLE
                        badgeView.text = "좋음"
                        badgeView.setBackgroundResource(R.drawable.badge_good)
                    }
                    badMatches.contains(ingredientLower) -> {
                        // 주의 성분: 빨간색 뱃지
                        ingredientType = "bad"
                        reason = badMatchesData[ingredientLower] ?: "일부 피부 타입에 자극을 줄 수 있습니다."
                        badgeView.visibility = View.VISIBLE
                        badgeView.text = "주의"
                        badgeView.setBackgroundResource(R.drawable.badge_bad)
                    }
                    else -> {
                        // 중립 성분: 뱃지 숨김
                        ingredientType = "neutral"
                        badgeView.visibility = View.GONE
                    }
                }
                
                // 성분명 클릭 → ResultsFragment로 이동하여 상세 정보 표시
                nameView.setOnClickListener {
                    onIngredientNameClick(ingredient)
                }
                
                // 뱃지 클릭 → 주의/좋음 이유 Bottom Sheet 표시
                if (ingredientType != "neutral") {
                    val finalType = ingredientType
                    val finalReason = reason
                    badgeView.setOnClickListener {
                        onBadgeClick(ingredient, finalType, finalReason)
                    }
                }
            }
        }
    }
}
