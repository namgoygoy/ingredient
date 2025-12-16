package com.example.cosmetic.utils

/**
 * 피부 타입 추출 유틸리티
 * 
 * 성분의 purpose나 description에서 피부 타입을 추출하는 로직을 통합합니다.
 * DetailsFragment와 ResultsFragment에서 중복되던 로직을 제거합니다.
 */
object SkinTypeExtractor {
    
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
     * - 매칭되는 키워드가 없으면 → 기본값 반환
     * 
     * @param purpose 성분의 목적(purpose) 문자열
     * @param default 기본값 (매칭되는 키워드가 없을 때 반환할 값)
     * @return 추출된 피부 타입 문자열 (여러 개인 경우 쉼표로 구분)
     */
    fun extractFromPurpose(purpose: String, default: String = "중성"): String {
        return when {
            purpose.contains("지성", ignoreCase = true) || purpose.contains("oily", ignoreCase = true) -> "지성"
            purpose.contains("건성", ignoreCase = true) || purpose.contains("dry", ignoreCase = true) -> "건성"
            purpose.contains("민감성", ignoreCase = true) || purpose.contains("sensitive", ignoreCase = true) -> "민감성"
            purpose.contains("여드름", ignoreCase = true) || purpose.contains("acne", ignoreCase = true) -> "여드름성"
            else -> default
        }
    }
    
    /**
     * 성분의 purpose 문자열에서 여러 피부 타입을 추출합니다.
     * 
     * 여러 피부 타입이 포함된 경우 모두 추출하여 쉼표로 구분된 문자열로 반환합니다.
     * 
     * @param purpose 성분의 목적(purpose) 문자열
     * @param default 기본값 (매칭되는 키워드가 없을 때 반환할 값)
     * @return 추출된 피부 타입 문자열 (여러 개인 경우 쉼표로 구분)
     */
    fun extractMultipleFromPurpose(purpose: String, default: String = "모든 피부"): String {
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
        
        return if (skinTypes.isNotEmpty()) skinTypes.joinToString(", ") else default
    }
    
    /**
     * 성분의 description 문자열에서 피부 타입을 추출합니다.
     * 
     * description 문자열에 포함된 피부 타입 키워드를 찾아 한국어로 반환합니다.
     * 주의 성분의 경우 기본값으로 "민감성"을 반환합니다.
     * 
     * @param description 성분의 설명(description) 문자열
     * @param default 기본값 (매칭되는 키워드가 없을 때 반환할 값, 주의 성분의 경우 "민감성")
     * @return 추출된 피부 타입 문자열
     */
    fun extractFromDescription(description: String, default: String = "민감성"): String {
        return when {
            description.contains("지성", ignoreCase = true) || description.contains("oily", ignoreCase = true) -> "지성"
            description.contains("건성", ignoreCase = true) || description.contains("dry", ignoreCase = true) -> "건성"
            description.contains("민감성", ignoreCase = true) || description.contains("sensitive", ignoreCase = true) -> "민감성"
            description.contains("여드름", ignoreCase = true) || description.contains("acne", ignoreCase = true) -> "여드름성"
            else -> default
        }
    }
    
    /**
     * 성분의 description 문자열에서 여러 피부 타입을 추출합니다.
     * 
     * 여러 피부 타입이 포함된 경우 모두 추출하여 쉼표로 구분된 문자열로 반환합니다.
     * 
     * @param description 성분의 설명(description) 문자열
     * @param default 기본값 (매칭되는 키워드가 없을 때 반환할 값, 주의 성분의 경우 "일부 피부")
     * @return 추출된 피부 타입 문자열 (여러 개인 경우 쉼표로 구분)
     */
    fun extractMultipleFromDescription(description: String, default: String = "일부 피부"): String {
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
        
        return if (skinTypes.isNotEmpty()) skinTypes.joinToString(", ") else default
    }
}

