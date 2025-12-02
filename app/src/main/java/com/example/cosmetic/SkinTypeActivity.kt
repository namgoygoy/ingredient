package com.example.cosmetic

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class SkinTypeActivity : AppCompatActivity() {
    
    private lateinit var userPreferences: UserPreferences
    private val selectedSkinTypes = mutableSetOf<String>()
    
    // 피부 타입 카드들
    private lateinit var skinTypeDry: CardView
    private lateinit var skinTypeOily: CardView
    private lateinit var skinTypeCombination: CardView
    private lateinit var skinTypeSensitive: CardView
    private lateinit var skinTypeNormal: CardView
    
    // 체크박스들
    private lateinit var checkBoxDry: CheckBox
    private lateinit var checkBoxOily: CheckBox
    private lateinit var checkBoxCombination: CheckBox
    private lateinit var checkBoxSensitive: CheckBox
    private lateinit var checkBoxNormal: CheckBox
    
    private lateinit var confirmButton: Button
    private lateinit var skipButton: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_type)
        
        userPreferences = UserPreferences(this)
        
        // 뷰 초기화
        skinTypeDry = findViewById(R.id.skinTypeDry)
        skinTypeOily = findViewById(R.id.skinTypeOily)
        skinTypeCombination = findViewById(R.id.skinTypeCombination)
        skinTypeSensitive = findViewById(R.id.skinTypeSensitive)
        skinTypeNormal = findViewById(R.id.skinTypeNormal)
        
        checkBoxDry = findViewById(R.id.checkBoxDry)
        checkBoxOily = findViewById(R.id.checkBoxOily)
        checkBoxCombination = findViewById(R.id.checkBoxCombination)
        checkBoxSensitive = findViewById(R.id.checkBoxSensitive)
        checkBoxNormal = findViewById(R.id.checkBoxNormal)
        
        confirmButton = findViewById(R.id.confirmButton)
        skipButton = findViewById(R.id.skipButton)
        
        // 피부 타입 선택 리스너 (다중 선택)
        skinTypeDry.setOnClickListener {
            toggleSkinType(UserPreferences.SKIN_TYPE_DRY, checkBoxDry)
        }
        
        skinTypeOily.setOnClickListener {
            toggleSkinType(UserPreferences.SKIN_TYPE_OILY, checkBoxOily)
        }
        
        skinTypeCombination.setOnClickListener {
            toggleSkinType(UserPreferences.SKIN_TYPE_COMBINATION, checkBoxCombination)
        }
        
        skinTypeSensitive.setOnClickListener {
            toggleSkinType(UserPreferences.SKIN_TYPE_SENSITIVE, checkBoxSensitive)
        }
        
        skinTypeNormal.setOnClickListener {
            toggleSkinType(UserPreferences.SKIN_TYPE_NORMAL, checkBoxNormal)
        }
        
        // 완료 버튼
        confirmButton.setOnClickListener {
            if (selectedSkinTypes.isNotEmpty()) {
                // 선택된 피부 타입들을 콤마로 연결하여 저장
                val skinTypeString = selectedSkinTypes.joinToString(", ")
                userPreferences.setSkinType(skinTypeString)
                userPreferences.setOnboardingCompleted(true)
                navigateToMain()
            }
        }
        
        // 나중에 하기 버튼
        skipButton.setOnClickListener {
            userPreferences.setOnboardingCompleted(true)
            // 피부 타입은 "미설정" 상태로 유지
            navigateToMain()
        }
    }
    
    /**
     * 피부 타입 토글 (다중 선택)
     */
    private fun toggleSkinType(skinType: String, checkBox: CheckBox) {
        if (selectedSkinTypes.contains(skinType)) {
            // 이미 선택된 경우 제거
            selectedSkinTypes.remove(skinType)
            checkBox.isChecked = false
        } else {
            // 선택되지 않은 경우 추가
            selectedSkinTypes.add(skinType)
            checkBox.isChecked = true
        }
        
        // 완료 버튼 활성화 여부
        if (selectedSkinTypes.isNotEmpty()) {
            confirmButton.isEnabled = true
            confirmButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary_green)
        } else {
            confirmButton.isEnabled = false
            confirmButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_muted)
        }
    }
    
    /**
     * 메인 화면으로 이동
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onBackPressed() {
        // 온보딩 화면에서는 뒤로가기 비활성화
        // 사용자가 "나중에 하기"를 선택하도록 유도
    }
}

