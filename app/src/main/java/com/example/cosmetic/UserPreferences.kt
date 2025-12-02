package com.example.cosmetic

import android.content.Context
import android.content.SharedPreferences

/**
 * 사용자 설정 관리 클래스
 * SharedPreferences를 사용하여 피부 타입 등의 사용자 설정 저장
 */
class UserPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "cosmetic_user_prefs"
        private const val KEY_SKIN_TYPE = "skin_type"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        
        // 피부 타입 상수
        const val SKIN_TYPE_DRY = "건성"
        const val SKIN_TYPE_OILY = "지성"
        const val SKIN_TYPE_COMBINATION = "복합성"
        const val SKIN_TYPE_SENSITIVE = "민감성"
        const val SKIN_TYPE_NORMAL = "중성"
        const val SKIN_TYPE_UNKNOWN = "미설정"
    }
    
    /**
     * 피부 타입 저장
     */
    fun setSkinType(skinType: String) {
        prefs.edit().putString(KEY_SKIN_TYPE, skinType).apply()
    }
    
    /**
     * 피부 타입 가져오기
     */
    fun getSkinType(): String {
        return prefs.getString(KEY_SKIN_TYPE, SKIN_TYPE_UNKNOWN) ?: SKIN_TYPE_UNKNOWN
    }
    
    /**
     * 온보딩 완료 여부 저장
     */
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
    
    /**
     * 온보딩 완료 여부 확인
     */
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }
    
    /**
     * 피부 타입 설정 여부 확인
     */
    fun isSkinTypeSet(): Boolean {
        val skinType = getSkinType()
        return skinType != SKIN_TYPE_UNKNOWN
    }
}

