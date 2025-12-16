#!/usr/bin/env python3
"""
화장품 성분 RAG 챗봇 서버 - Supabase PostgreSQL 버전
모듈화된 구조로 리팩토링됨

모듈 구조:
- api/: API 라우터 및 Pydantic 모델
- rag/: RAG 시스템 핵심 로직
- llm/: LLM 관련 클래스
"""

import os
import logging

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# FastAPI 관련 imports
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# RAG 시스템
from rag.enterprise_rag import EnterpriseRAG

# API 라우터
from api.routes import setup_routes

# FastAPI 앱 생성
app = FastAPI(
    title="화장품 성분 RAG API (Supabase)",
    description="PostgreSQL + ChromaDB 하이브리드 RAG 시스템",
    version="3.0.0"
)

# CORS 설정
# 환경변수에서 허용할 origin 목록 읽기
ALLOWED_ORIGINS = os.getenv("ALLOWED_ORIGINS", "").split(",")
# 빈 문자열 제거 및 필터링
ALLOWED_ORIGINS = [origin.strip() for origin in ALLOWED_ORIGINS if origin.strip()]

# 개발 환경 기본값 (환경변수가 없을 때)
if not ALLOWED_ORIGINS:
    ALLOWED_ORIGINS = ["http://localhost:5000", "http://127.0.0.1:5000"]

logger.info(f"🔒 CORS 허용 Origin: {ALLOWED_ORIGINS}")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,  # 특정 origin만 허용
    allow_credentials=True,
    allow_methods=["GET", "POST"],  # 필요한 메서드만 허용
    allow_headers=["Content-Type", "Authorization"],  # 필요한 헤더만 허용
)

# RAG 시스템 초기화
logger.info("🚀 RAG 시스템 초기화 시작...")
script_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(script_dir)
ingredients_file = os.path.join(project_root, 'app', 'src', 'main', 'assets', 'ingredients.json')
rag_system = EnterpriseRAG(ingredients_file)

# 라우트 등록
setup_routes(app, rag_system)

if __name__ == '__main__':
    import uvicorn
    
    logger.info("=" * 60)
    logger.info("🚀 화장품 성분 RAG 서버 (Supabase 버전)")
    logger.info("=" * 60)
    logger.info(f"📊 데이터 소스: {rag_system.get_data_source()}")
    logger.info(f"📦 성분 개수: {rag_system.get_ingredients_count()}")
    logger.info("📚 API 문서: http://localhost:5000/docs")
    logger.info("=" * 60)
    
    uvicorn.run(app, host="0.0.0.0", port=5000, log_level="info")
