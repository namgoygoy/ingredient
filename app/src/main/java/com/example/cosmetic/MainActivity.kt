package com.example.cosmetic

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 온보딩 체크
        userPreferences = UserPreferences(this)
        if (!userPreferences.isOnboardingCompleted()) {
            // 온보딩 미완료 시 SkinTypeActivity로 이동
            startActivity(Intent(this, SkinTypeActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val bottomNavView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavView.setupWithNavController(navController)
        
        // 바텀 네비게이션 배경을 화이트로 강제 설정
        bottomNavView.setBackgroundColor(getColor(R.color.bg_card))
        bottomNavView.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.bg_card))
        
        // 그림자 효과 완전 제거
        removeAllShadows(bottomNavView)
        
        // 선택 표시기 배경 완전 제거 및 바운스 애니메이션 적용
        setupBottomNavigation(bottomNavView, navController)
    }
    
    /**
     * 바텀 네비게이션 설정 (배경 제거 + 바운스 애니메이션)
     */
    private fun setupBottomNavigation(bottomNavView: BottomNavigationView, navController: androidx.navigation.NavController) {
        // 뷰 트리가 완전히 그려진 후 실행
        bottomNavView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                bottomNavView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // 초기 배경 제거
                removeActiveIndicatorBackground(bottomNavView)
                
                // 아이템 선택 리스너 설정 (배경 제거 + 바운스 애니메이션 + 네비게이션)
                bottomNavView.setOnItemSelectedListener { menuItem ->
                    // 배경 제거 (선택 변경 시마다)
                    bottomNavView.post {
                        removeActiveIndicatorBackground(bottomNavView)
                    }
                    
                    // 바운스 애니메이션 실행
                    val itemView = bottomNavView.findViewById<View>(menuItem.itemId)
                    itemView?.let { playBounceAnimation(it) }
                    
                    // Navigation Controller로 이동
                    try {
                        navController.navigate(menuItem.itemId)
                        true
                    } catch (e: Exception) {
                        // 이미 현재 화면이거나 Navigation 실패 시
                        false
                    }
                }
            }
        })
    }
    
    /**
     * 선택 표시기 배경 완전 제거
     * Material3의 BottomNavigationView 내부 선택 표시기 뷰 찾아서 제거
     */
    private fun removeActiveIndicatorBackground(bottomNavView: BottomNavigationView) {
        try {
            // 리플렉션을 사용하여 내부 뷰 접근
            val menuViewField = bottomNavView.javaClass.getDeclaredField("menuView")
            menuViewField.isAccessible = true
            val menuView = menuViewField.get(bottomNavView) as? android.view.ViewGroup
            
            menuView?.let {
                // 모든 자식 뷰 순회
                for (i in 0 until it.childCount) {
                    val itemView = it.getChildAt(i)
                    
                    // BottomNavigationItemView인 경우
                    if (itemView.javaClass.simpleName == "BottomNavigationItemView") {
                        // activeIndicator 뷰 찾기
                        try {
                            val activeIndicatorField = itemView.javaClass.getDeclaredField("activeIndicatorView")
                            activeIndicatorField.isAccessible = true
                            val activeIndicator = activeIndicatorField.get(itemView) as? View
                            activeIndicator?.visibility = View.GONE
                            activeIndicator?.background = null
                        } catch (e: Exception) {
                            // 필드가 없으면 무시
                        }
                        
                        // 배경 제거
                        itemView.background = null
                        
                        // 모든 자식 뷰의 배경 제거
                        removeBackgroundRecursive(itemView)
                    }
                }
            }
        } catch (e: Exception) {
            // 리플렉션 실패 시 일반적인 방법으로 제거
            removeBackgroundRecursive(bottomNavView)
        }
    }
    
    /**
     * 모든 그림자 효과 제거
     * BottomNavigationView와 모든 자식 뷰의 elevation, outlineProvider, StateListAnimator 제거
     */
    private fun removeAllShadows(view: View) {
        // 뷰 트리가 완전히 그려진 후 실행
        view.post {
            // 자기 자신의 그림자 제거
            view.elevation = 0f
            view.outlineProvider = null
            view.stateListAnimator = null
            
            // 모든 자식 뷰의 그림자 제거
            removeShadowsRecursive(view)
        }
    }
    
    /**
     * 재귀적으로 모든 자식 뷰의 그림자 제거
     */
    private fun removeShadowsRecursive(view: View) {
        view.elevation = 0f
        view.outlineProvider = null
        view.stateListAnimator = null
        
        // CardView의 경우 추가 설정
        if (view is androidx.cardview.widget.CardView) {
            view.cardElevation = 0f
            view.maxCardElevation = 0f
        }
        
        // 자식 뷰가 있으면 재귀적으로 처리
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                removeShadowsRecursive(view.getChildAt(i))
            }
        }
    }
    
    /**
     * 재귀적으로 모든 자식 뷰의 배경 제거
     * Material3의 선택 표시기 배경(연보라색) 완전 제거
     */
    private fun removeBackgroundRecursive(view: View) {
        // 배경을 투명하게 설정
        if (view.background != null) {
            val drawable = view.background
            // Material3의 선택 표시기 배경 제거
            if (drawable is android.graphics.drawable.ColorDrawable) {
                val color = drawable.color
                // 연보라색 계열 색상이면 제거 (투명하게)
                if (color != 0 && color != android.graphics.Color.TRANSPARENT) {
                    view.background = null
                }
            } else if (drawable is android.graphics.drawable.GradientDrawable) {
                // GradientDrawable도 제거 (연보라색 배경)
                view.background = null
            } else if (drawable is android.graphics.drawable.RippleDrawable) {
                // RippleDrawable의 배경 레이어 제거
                view.background = null
            } else if (drawable != null) {
                // 기타 배경도 제거
                view.background = null
            }
        }
        
        // 자식 뷰가 있으면 재귀적으로 처리
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                removeBackgroundRecursive(child)
            }
        }
    }
    
    /**
     * 바운스 애니메이션 실행
     * 0.8배 축소 → 1.2배 확대 → 1.0배 복귀 (스프링 효과)
     */
    private fun playBounceAnimation(view: View) {
        // 1단계: 0.8배로 축소
        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.8f).apply {
            duration = 100
        }
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.8f).apply {
            duration = 100
        }
        
        // 2단계: 1.2배로 확대 (OvershootInterpolator로 탄성 효과)
        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1.2f).apply {
            duration = 150
            interpolator = OvershootInterpolator(2.0f) // 탄성 계수
        }
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1.2f).apply {
            duration = 150
            interpolator = OvershootInterpolator(2.0f)
        }
        
        // 3단계: 1.0배로 복귀
        val scaleNormalX = ObjectAnimator.ofFloat(view, "scaleX", 1.2f, 1.0f).apply {
            duration = 100
        }
        val scaleNormalY = ObjectAnimator.ofFloat(view, "scaleY", 1.2f, 1.0f).apply {
            duration = 100
        }
        
        // 애니메이션 순서대로 실행
        AnimatorSet().apply {
            play(scaleDownX).with(scaleDownY)
            play(scaleUpX).with(scaleUpY).after(scaleDownX)
            play(scaleNormalX).with(scaleNormalY).after(scaleUpX)
            start()
        }
    }
}