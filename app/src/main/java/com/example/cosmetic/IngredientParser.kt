package com.example.cosmetic

import android.util.Log
import com.example.cosmetic.Constants.Parsing.MAX_INGREDIENT_COUNT
import com.example.cosmetic.Constants.Parsing.MAX_INGREDIENT_NAME_LENGTH
import com.example.cosmetic.Constants.Parsing.MIN_INGREDIENT_NAME_LENGTH

/**
 * 성분 파싱 유틸리티 클래스
 * 
 * OCR로 인식된 텍스트에서 화장품 성분을 추출하고 검증하는 기능을 제공합니다.
 * DetailsFragment와 ResultsFragment에서 공통으로 사용하는 파싱 로직을 통합했습니다.
 * 
 * 주요 기능:
 * - OCR 텍스트에서 성분 섹션 추출
 * - 성분 섹션을 개별 성분명으로 파싱
 * - 성분명 유효성 검증
 * - 비성분 텍스트 필터링
 * 
 * 사용 예시:
 * ```kotlin
 * val parser = IngredientParser()
 * val ingredients = parser.parseIngredients(ocrText)
 * ```
 */
class IngredientParser {
    
    companion object {
        /**
         * 싱글톤 인스턴스를 제공합니다.
         * 
         * 파싱 로직에는 상태가 없으므로 싱글톤으로 사용해도 안전합니다.
         */
        val instance = IngredientParser()
    }
    
    /**
     * 인식된 텍스트에서 성분 리스트를 추출합니다.
     * 
     * 성분 섹션 텍스트를 파싱하여 개별 성분명 리스트로 변환합니다.
     * OCR 오류를 고려하여 다양한 구분자(쉼표, 점, 공백, 줄바꿈)를 처리합니다.
     * 
     * 파싱 단계:
     * 1. 제품 코드 패턴 제거 (예: A1801290, RC023A03)
     * 2. 쉼표, 점, 줄바꿈으로 분리
     * 3. 공백으로 추가 분리
     * 4. 알려진 성분명 패턴으로 분리 (OCR 오류 대응)
     *    예: "리날률시트로넬올" → "리날룰", "시트로넬올"
     * 5. 유효성 검증 및 필터링
     * 
     * @param text OCR로 인식된 전체 텍스트
     * @return 파싱된 성분명 리스트 (최대 50개, 중복 제거됨)
     * 
     * @see extractIngredientSection 성분 섹션만 추출하는 메서드
     * @see isValidIngredientName 성분명 유효성 검증 메서드
     * @see isNonIngredientText 성분이 아닌 텍스트 필터링 메서드
     */
    fun parseIngredients(text: String): List<String> {
        val ingredientText = extractIngredientSection(text)
        if (ingredientText.isEmpty()) {
            return emptyList()
        }
        
        val ingredients = mutableListOf<String>()
        
        // 알파벳/숫자 코드 패턴 제거 (예: A1801290, RC023A03 등)
        val cleanedText = ingredientText.replace(Regex("\\s*[A-Z]\\d+[A-Z]?\\d*[\\s/]?[A-Z]?\\d*\\s*"), " ")
        
        // 1단계: 쉼표, 점, 줄바꿈으로 분리
        val parts = cleanedText.split(Regex("[,，.。\\n\\r]+"))
        
        for (part in parts) {
            val processedPart = part.trim()
            
            // 빈 파트는 건너뛰기
            if (processedPart.isEmpty()) continue
            
            // 2단계: 공백으로 분리
            val words = processedPart.split(Regex("\\s+"))
            
            for (word in words) {
                val cleaned = word.trim()
                    .replace(Regex("[\\[\\]()]"), "") // 대괄호, 괄호 제거
                    .trim()
                
                // 숫자만 있는 경우 제외
                if (cleaned.matches(Regex("^[0-9\\-]+$"))) continue
                
                // 3단계: 알려진 성분명 패턴으로 분리 (OCR 오류 대응)
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
                            if (beforePattern.length >= MIN_INGREDIENT_NAME_LENGTH && isValidIngredientName(beforePattern)) {
                                ingredients.add(beforePattern)
                            }
                        }
                        ingredients.add(pattern)
                        foundPatterns = true
                        val afterPattern = cleaned.substring(patternIndex + pattern.length).trim()
                        if (afterPattern.length >= MIN_INGREDIENT_NAME_LENGTH && isValidIngredientName(afterPattern)) {
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
        
        return ingredients
            .distinct()
            .filter { it.length >= MIN_INGREDIENT_NAME_LENGTH && it.length <= MAX_INGREDIENT_NAME_LENGTH }
            .filter { !isNonIngredientText(it) }
            .take(MAX_INGREDIENT_COUNT)
    }
    
    /**
     * 인식된 텍스트에서 성분 섹션만 추출합니다.
     * 
     * OCR로 인식된 전체 텍스트에서 성분 목록 부분만 분리합니다.
     * 정규식을 사용하여 "[성분]", "성분:", "전성분" 등의 키워드로 시작하는 섹션을 찾습니다.
     * 
     * 처리 과정:
     * 1. 성분 섹션 시작 패턴 찾기 (예: "[성분]", "성분:", "전성분")
     * 2. 성분 섹션 종료 패턴 찾기 (예: "[제조번호]", "Tel", "Made", 제품 코드 등)
     * 3. 제품 코드 패턴 제거 (예: A1801290, RC023A03)
     * 4. 숫자만 있는 라인 제거 (예: "40 ml")
     * 
     * @param text OCR로 인식된 전체 텍스트
     * @return 추출된 성분 섹션 텍스트, 성분 섹션을 찾지 못한 경우 빈 문자열
     * 
     * @see parseIngredients 성분 섹션에서 개별 성분명을 파싱하는 메서드
     */
    fun extractIngredientSection(text: String): String {
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
     * 텍스트가 유효한 성분명인지 확인합니다.
     * 
     * 성분명으로 적합하지 않은 텍스트를 필터링합니다.
     * 
     * 검증 조건:
     * - 길이가 2자 이상이어야 함
     * - 숫자만 있는 경우 제외
     * - 제품명 패턴 제외 (예: "Girl Rochas")
     * - 주의사항, 연락처 등 비성분 텍스트 제외
     * 
     * @param text 검증할 텍스트
     * @return 유효한 성분명이면 true, 그렇지 않으면 false
     * 
     * @see isNonIngredientText 성분이 아닌 텍스트인지 확인하는 메서드
     */
    fun isValidIngredientName(text: String): Boolean {
        if (text.isEmpty() || text.length < MIN_INGREDIENT_NAME_LENGTH) return false
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
    
    /**
     * 텍스트가 성분이 아닌 텍스트인지 확인합니다.
     * 
     * 제품명, 주의사항, 안내문구 등 성분명이 아닌 텍스트를 필터링합니다.
     * 키워드 기반 검사를 수행하여 성분명으로 오인될 수 있는 텍스트를 제외합니다.
     * 
     * 필터링되는 키워드 예시:
     * - 제품 관련: "제품", "주의", "사용", "보관", "제조"
     * - 연락처: "Tel", "Made"
     * - 단위: "ml", "A1801290", "RC023A03"
     * - 안내문구: "알레르기", "유발성분표시", "재활용"
     * 
     * @param text 확인할 텍스트
     * @return 성분이 아닌 텍스트이면 true, 그렇지 않으면 false
     * 
     * @see isValidIngredientName 유효한 성분명인지 확인하는 메서드
     */
    fun isNonIngredientText(text: String): Boolean {
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
}

