"""
FastAPI 라우터
API 엔드포인트 정의
"""

import logging
from fastapi import HTTPException

import sys
import os
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from api.models import (
    SearchRequest,
    SearchResponse,
    AnalyzeProductRequest,
    AnalyzeProductResponse,
    HealthResponse,
    GoodMatch,
    BadMatch,
    GeminiPurposeRequest,
    GeminiPurposeResponse,
    GeminiTranslateRequest,
    GeminiTranslateResponse,
    GeminiDescriptionRequest,
    GeminiDescriptionResponse,
    GeminiSuitabilityRequest,
    GeminiSuitabilityResponse,
    GeminiShortTextRequest,
    GeminiShortTextResponse,
    GeminiUserFriendlyRequest,
    GeminiUserFriendlyResponse,
    GeminiEnhanceRequest,
    GeminiEnhanceResponse,
)
from supabase_client import get_all_ingredients, test_supabase_connection
from llm.gemini_service import GeminiService

logger = logging.getLogger(__name__)


def setup_routes(app, rag_system):
    """
    FastAPI 앱에 라우트를 등록합니다.
    
    Args:
        app: FastAPI 앱 인스턴스
        rag_system: EnterpriseRAG 인스턴스
    """
    
    # Gemini 서비스 초기화
    gemini_service = GeminiService()
    
    @app.get("/", tags=["Root"])
    async def root():
        """
        루트 엔드포인트
        
        API 서버의 기본 정보를 반환합니다.
        
        Returns:
            서버 정보 딕셔너리
        """
        return {
            "message": "화장품 성분 RAG API 서버 (Supabase)",
            "version": "3.0.0",
            "database": rag_system.get_data_source(),
            "docs": "/docs"
        }
    
    @app.get("/health", response_model=HealthResponse, tags=["Health"])
    async def health_check():
        """
        서버 상태 확인 엔드포인트
        
        서버가 정상 작동 중인지 확인하고 현재 상태 정보를 반환합니다.
        
        Returns:
            HealthResponse: 서버 상태 정보
        """
        return HealthResponse(
            status="healthy",
            message="RAG 서버 정상 작동 중",
            ingredients_count=rag_system.get_ingredients_count(),
            database=rag_system.get_data_source(),
            features=[
                "Supabase PostgreSQL" if rag_system.use_supabase else "JSON Fallback",
                "ChromaDB Vector Store",
                "LangChain RAG Pipeline",
                "FastAPI Async"
            ]
        )
    
    @app.post("/search", response_model=SearchResponse, tags=["Search"])
    async def search_ingredients(request: SearchRequest):
        """
        성분 검색 엔드포인트
        
        성분명 또는 질문을 입력받아 관련 성분 정보를 검색합니다.
        
        Args:
            request: 검색 요청 (SearchRequest)
        
        Returns:
            SearchResponse: 검색 결과
        
        Raises:
            HTTPException: 검색어가 비어있을 경우 400 에러
        """
        if not request.query:
            raise HTTPException(status_code=400, detail="검색어를 입력해주세요")
        
        result = rag_system.search_ingredients(request.query, request.session_id)
        return SearchResponse(**result)
    
    @app.post("/analyze_product", response_model=AnalyzeProductResponse, tags=["Analysis"])
    async def analyze_product(request: AnalyzeProductRequest):
        """
        제품 성분 분석 엔드포인트
        
        화장품의 성분 리스트를 분석하여 사용자 피부 타입에 맞는 평가를 제공합니다.
        
        Args:
            request: 분석 요청 (AnalyzeProductRequest)
        
        Returns:
            AnalyzeProductResponse: 분석 결과
        
        Raises:
            HTTPException: 성분 리스트가 비어있을 경우 400 에러
        """
        if not request.ingredients:
            raise HTTPException(status_code=400, detail="성분 리스트가 필요합니다")
        
        result = rag_system.analyze_product_ingredients(request.ingredients, request.skin_type)
        
        good_matches = [GoodMatch(**m) for m in result["good_matches"]]
        bad_matches = [BadMatch(**m) for m in result["bad_matches"]]
        
        return AnalyzeProductResponse(
            analysis_report=result["analysis_report"],
            good_matches=good_matches,
            bad_matches=bad_matches,
            success=result["success"]
        )
    
    @app.get("/ingredients", tags=["Ingredients"])
    async def get_all_ingredients_api():
        """
        모든 성분 목록을 반환하는 엔드포인트
        
        데이터베이스에 저장된 모든 성분의 이름을 반환합니다.
        
        Returns:
            성분 목록 딕셔너리
        """
        if rag_system.use_supabase:
            ingredients = get_all_ingredients()
        else:
            ingredients = rag_system.data_loader.ingredients_data
        
        result = []
        for item in ingredients:
            kor = item.get('kor_name', '')
            eng = item.get('eng_name', '')
            if kor and eng:
                result.append(f"{kor} ({eng})")
            elif kor:
                result.append(kor)
            elif eng:
                result.append(eng)
        
        return {
            'ingredients': result,
            'count': len(result),
            'database': rag_system.get_data_source(),
            'success': True
        }
    
    @app.get("/database/status", tags=["Database"])
    async def database_status():
        """
        데이터베이스 상태 확인 엔드포인트
        
        Returns:
            데이터베이스 상태 정보
        """
        if rag_system.use_supabase:
            test_result = test_supabase_connection()
            return {
                "database": "supabase",
                "connected": test_result["success"],
                "message": test_result["message"],
                "ingredients_count": rag_system.get_ingredients_count()
            }
        else:
            return {
                "database": "json",
                "connected": True,
                "message": "JSON 파일 모드 (Supabase 연결 안됨)",
                "ingredients_count": len(rag_system.data_loader.ingredients_data)
            }

