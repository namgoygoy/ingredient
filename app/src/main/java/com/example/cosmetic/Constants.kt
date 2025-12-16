package com.example.cosmetic

/**
 * 앱 전역 상수 정의
 * 
 * 매직 넘버와 하드코딩된 문자열을 한 곳에 모아 관리합니다.
 * 이를 통해 코드 가독성을 높이고 유지보수를 용이하게 합니다.
 */
object Constants {
    
    /**
     * 애니메이션 관련 상수
     */
    object Animation {
        /** 페이드 애니메이션 지속 시간 (밀리초) */
        const val FADE_DURATION_MS = 200L
        
        /** 로딩 메시지 변경 주기 (밀리초) */
        const val LOADING_MESSAGE_INTERVAL_MS = 2500L
        
        /** 바운스 애니메이션 축소 단계 지속 시간 (밀리초) */
        const val BOUNCE_SCALE_DOWN_MS = 100L
        
        /** 바운스 애니메이션 확대 단계 지속 시간 (밀리초) */
        const val BOUNCE_SCALE_UP_MS = 150L
        
        /** 바운스 애니메이션 복귀 단계 지속 시간 (밀리초) */
        const val BOUNCE_SCALE_NORMAL_MS = 100L
    }
    
    /**
     * 성분 파싱 관련 상수
     */
    object Parsing {
        /** 유효한 성분명 최소 길이 */
        const val MIN_INGREDIENT_NAME_LENGTH = 2
        
        /** 유효한 성분명 최대 길이 */
        const val MAX_INGREDIENT_NAME_LENGTH = 50
        
        /** 파싱된 성분 최대 개수 */
        const val MAX_INGREDIENT_COUNT = 50
        
        /** 한국어 판정 기준 비율 (한글 문자 비율) */
        const val KOREAN_TEXT_THRESHOLD = 0.3
    }
    
    /**
     * Gemini AI 관련 상수
     */
    object Gemini {
        /** AI 모델명 */
        const val MODEL_NAME = "gemini-2.5-flash"
        
        /** Temperature (응답의 랜덤성 조절, 낮을수록 일관적) */
        const val TEMPERATURE = 0.5f
        
        /** Top K (다음 토큰 선택 시 고려할 상위 K개 후보) */
        const val TOP_K = 40
        
        /** Top P (누적 확률 기준 토큰 선택) */
        const val TOP_P = 0.9f
        
        /** 최대 출력 토큰 수 */
        const val MAX_OUTPUT_TOKENS = 4096
        
        /** LRU 캐시 최대 크기 (메모리 누수 방지) */
        const val LRU_CACHE_MAX_SIZE = 100
        
        /** 짧은 텍스트 번역 기준 길이 */
        const val SHORT_TEXT_LENGTH = 150
        
        /** Purpose 응답 최대 길이 */
        const val PURPOSE_MAX_LENGTH = 20
        
        /** 짧은 번역 응답 최대 길이 */
        const val SHORT_TRANSLATION_MAX_LENGTH = 50
        
        /** 설명 요약 응답 최대 길이 */
        const val DESCRIPTION_SUMMARY_MAX_LENGTH = 100
        
        /** Description 응답 최대 길이 */
        const val DESCRIPTION_MAX_LENGTH = 80
        
        /** 사용자 친화적 설명 최대 길이 */
        const val USER_FRIENDLY_EXPLANATION_MAX_LENGTH = 100
    }
    
    /**
     * 분석 리포트 관련 상수
     */
    object Analysis {
        /** 서버 리포트가 충분하다고 판단하는 최소 길이 */
        const val MIN_REPORT_LENGTH = 100
        
        /** Description이 충분하다고 판단하는 최소 길이 */
        const val MIN_DESCRIPTION_LENGTH = 20
    }
    
    /**
     * 로그 태그
     */
    object LogTag {
        const val DETAILS_FRAGMENT = "DetailsFragment"
        const val RESULTS_FRAGMENT = "ResultsFragment"
        const val GEMINI_SERVICE = "GeminiService"
        const val INGREDIENT_PARSER = "IngredientParser"
        const val MAIN_ACTIVITY = "MainActivity"
        const val SCAN_FRAGMENT = "ScanFragment"
        const val NETWORK = "Network"
    }
    
    /**
     * 에러 메시지
     */
    object ErrorMessage {
        const val INGREDIENT_PARSE_FAILED = "성분 파싱 실패"
        const val SERVER_CONNECTION_FAILED = "서버 연결 실패"
        const val GEMINI_API_FAILED = "Gemini API 호출 실패"
        const val DATA_LOAD_FAILED = "데이터 로드 실패"
        const val TRANSLATION_FAILED = "번역 실패"
        const val ANALYSIS_FAILED = "분석 실패"
    }
}

