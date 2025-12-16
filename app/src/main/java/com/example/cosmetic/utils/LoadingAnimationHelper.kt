package com.example.cosmetic.utils

import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.example.cosmetic.Constants.Animation.FADE_DURATION_MS
import com.example.cosmetic.Constants.Animation.LOADING_MESSAGE_INTERVAL_MS
import com.example.cosmetic.R

/**
 * 로딩 애니메이션 헬퍼 클래스
 * 
 * Fragment에서 사용하는 로딩 애니메이션 로직을 통합하여 중복 코드를 제거합니다.
 * 
 * 사용 예시:
 * ```kotlin
 * private val loadingHelper = LoadingAnimationHelper(this)
 * 
 * override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *     loadingHelper.setupLoadingViews(view)
 *     loadingHelper.startLoading()
 * }
 * 
 * override fun onDestroyView() {
 *     loadingHelper.stopLoading()
 *     super.onDestroyView()
 * }
 * ```
 */
class LoadingAnimationHelper(
    private val fragment: Fragment,
    private val lifecycleOwner: LifecycleOwner
) {
    
    private var loadingMessageHandler: Handler? = null
    private var loadingMessageRunnable: Runnable? = null
    private var currentMessageIndex = 0
    
    private var loadingMessageView: TextView? = null
    private var loadingSubMessageView: TextView? = null
    
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
     * 로딩 뷰를 설정합니다.
     * 
     * @param view Fragment의 루트 뷰
     */
    fun setupLoadingViews(view: View) {
        loadingMessageView = view.findViewById(R.id.loadingMessage)
        loadingSubMessageView = view.findViewById(R.id.loadingSubMessage)
    }
    
    /**
     * 로딩 애니메이션을 시작합니다.
     * 
     * 사용자에게 분석이 진행 중임을 알리기 위해 로딩 메시지를 주기적으로 변경합니다.
     * 메시지 변경 시 페이드 아웃 → 텍스트 변경 → 페이드 인 애니메이션을 적용합니다.
     */
    fun startLoading() {
        // 기존 Handler가 있으면 먼저 정리
        stopLoading()
        
        currentMessageIndex = 0
        
        // 초기 메시지 설정
        loadingMessageView?.text = loadingMessages[0]
        loadingSubMessageView?.text = loadingSubMessages[0]
        
        // 메시지 변경 핸들러 시작
        loadingMessageHandler = Handler(Looper.getMainLooper())
        loadingMessageRunnable = object : Runnable {
            override fun run() {
                // Fragment가 destroy되었는지 확인
                if (!fragment.isAdded || 
                    lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED)) {
                    return
                }
                
                currentMessageIndex = (currentMessageIndex + 1) % loadingMessages.size
                
                // 페이드 아웃 → 텍스트 변경 → 페이드 인 애니메이션
                loadingMessageView?.let { messageView ->
                    val fadeOut = ObjectAnimator.ofFloat(messageView, "alpha", 1f, 0f).apply {
                        duration = FADE_DURATION_MS
                    }
                    fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            // Fragment가 여전히 활성 상태인지 확인
                            if (!fragment.isAdded || 
                                lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED)) {
                                return
                            }
                            
                            messageView.text = loadingMessages[currentMessageIndex]
                            loadingSubMessageView?.text = loadingSubMessages[currentMessageIndex]
                            
                            ObjectAnimator.ofFloat(messageView, "alpha", 0f, 1f).apply {
                                duration = FADE_DURATION_MS
                            }.start()
                            
                            loadingSubMessageView?.let { subView ->
                                ObjectAnimator.ofFloat(subView, "alpha", 0f, 1f).apply {
                                    duration = FADE_DURATION_MS
                                }.start()
                            }
                        }
                    })
                    fadeOut.start()
                    
                    loadingSubMessageView?.let { subView ->
                        ObjectAnimator.ofFloat(subView, "alpha", 1f, 0f).apply {
                            duration = FADE_DURATION_MS
                        }.start()
                    }
                }
                
                // 로딩 메시지 변경 주기 (Fragment가 활성 상태일 때만)
                if (fragment.isAdded && 
                    !lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.DESTROYED)) {
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
     */
    fun stopLoading() {
        loadingMessageRunnable?.let { loadingMessageHandler?.removeCallbacks(it) }
        loadingMessageHandler?.removeCallbacksAndMessages(null)
        loadingMessageHandler = null
        loadingMessageRunnable = null
        currentMessageIndex = 0
    }
}

