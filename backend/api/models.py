"""
Pydantic 모델 정의
API 요청/응답 모델
"""

from typing import List, Dict, Optional, Any
from pydantic import BaseModel, Field


class SearchRequest(BaseModel):
    """
    성분 검색 요청 모델
    
    Attributes:
        query: 검색할 성분명 또는 질문
        session_id: 채팅 세션 ID (선택적, 없으면 자동 생성)
    """
    query: str = Field(..., description="검색할 성분명 또는 질문")
    session_id: Optional[str] = Field(None, description="채팅 세션 ID")


class AnalyzeProductRequest(BaseModel):
    """
    제품 분석 요청 모델
    
    Attributes:
        ingredients: 분석할 성분명 리스트
        skin_type: 사용자 피부 타입 (예: "건성, 민감성")
    """
    ingredients: List[str] = Field(..., description="성분명 리스트")
    skin_type: str = Field(..., description="사용자 피부 타입")


class GoodMatch(BaseModel):
    """
    좋은 성분 매칭 결과 모델
    
    Attributes:
        name: 성분명
        purpose: 성분의 목적/기능 (영문 또는 한국어)
    """
    name: str
    purpose: str


class BadMatch(BaseModel):
    """
    주의 성분 매칭 결과 모델
    
    Attributes:
        name: 성분명
        description: 성분에 대한 설명 (영문, 클라이언트에서 번역됨)
    """
    name: str
    description: str


class AnalyzeProductResponse(BaseModel):
    """
    제품 분석 응답 모델
    
    Attributes:
        analysis_report: AI가 생성한 종합 분석 리포트 (한국어)
        good_matches: 사용자 피부 타입에 좋은 성분 리스트
        bad_matches: 사용자 피부 타입에 주의가 필요한 성분 리스트
        success: 분석 성공 여부
    """
    analysis_report: str
    good_matches: List[GoodMatch]
    bad_matches: List[BadMatch]
    success: bool


class SearchResponse(BaseModel):
    """
    성분 검색 응답 모델
    
    Attributes:
        query: 검색한 쿼리
        answer: 검색 결과 답변
        similar_ingredients: 유사한 성분 리스트
        session_id: 채팅 세션 ID
        chat_history: 최근 채팅 히스토리 (최대 4개)
        success: 검색 성공 여부
    """
    query: str
    answer: str
    similar_ingredients: List[Dict[str, Any]]
    session_id: str
    chat_history: List[Dict[str, str]]
    success: bool


class HealthResponse(BaseModel):
    """
    서버 상태 확인 응답 모델
    
    Attributes:
        status: 서버 상태 ("healthy")
        message: 상태 메시지
        ingredients_count: 저장된 성분 개수
        features: 지원하는 기능 리스트
        database: 사용 중인 데이터베이스 ("supabase" or "json")
    """
    status: str
    message: str
    ingredients_count: int
    features: List[str]
    database: str  # "supabase" or "json"


# Gemini API 요청/응답 모델
class GeminiPurposeRequest(BaseModel):
    """성분 기능 생성 요청 모델"""
    ingredient_name: str = Field(..., description="성분명")


class GeminiPurposeResponse(BaseModel):
    """성분 기능 생성 응답 모델"""
    purpose: str
    success: bool


class GeminiTranslateRequest(BaseModel):
    """성분 설명 번역 요청 모델"""
    ingredient_name: str = Field(..., description="성분명")
    english_description: str = Field(..., description="영문 설명")


class GeminiTranslateResponse(BaseModel):
    """성분 설명 번역 응답 모델"""
    translated_description: str
    success: bool


class GeminiDescriptionRequest(BaseModel):
    """성분 설명 생성 요청 모델"""
    ingredient_name: str = Field(..., description="성분명")


class GeminiDescriptionResponse(BaseModel):
    """성분 설명 생성 응답 모델"""
    description: str
    success: bool


class GeminiSuitabilityRequest(BaseModel):
    """피부 타입 적합성 생성 요청 모델"""
    ingredient_name: str = Field(..., description="성분명")


class GeminiSuitabilityResponse(BaseModel):
    """피부 타입 적합성 생성 응답 모델"""
    suitability: str
    success: bool


class GeminiShortTextRequest(BaseModel):
    """짧은 텍스트 번역 요청 모델"""
    text: str = Field(..., description="번역할 텍스트")


class GeminiShortTextResponse(BaseModel):
    """짧은 텍스트 번역 응답 모델"""
    translated_text: str
    success: bool


class GeminiUserFriendlyRequest(BaseModel):
    """사용자 친화적 설명 생성 요청 모델"""
    ingredient_name: str = Field(..., description="성분명")
    ingredient_type: str = Field(..., description="성분 타입 (good/bad)")
    original_reason: str = Field(..., description="원본 이유 설명")


class GeminiUserFriendlyResponse(BaseModel):
    """사용자 친화적 설명 생성 응답 모델"""
    explanation: str
    success: bool


class GeminiEnhanceRequest(BaseModel):
    """제품 분석 리포트 개선 요청 모델"""
    server_report: str = Field(..., description="서버 리포트")
    ingredients: List[str] = Field(..., description="성분 리스트")
    good_matches: List[str] = Field(default=[], description="좋은 성분 리스트")
    bad_matches: List[str] = Field(default=[], description="주의 성분 리스트")


class GeminiEnhanceResponse(BaseModel):
    """제품 분석 리포트 개선 응답 모델"""
    enhanced_report: str
    success: bool

