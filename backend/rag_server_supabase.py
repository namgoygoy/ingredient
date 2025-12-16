#!/usr/bin/env python3
"""
화장품 성분 RAG 챗봇 서버 - Supabase PostgreSQL 버전
기존 rag_server_fastapi.py를 Supabase와 연동하도록 수정
Supabase 연결 실패 시 JSON 파일로 폴백
"""

import json
import logging
import os
import uuid
from typing import List, Dict, Optional, Any
from datetime import datetime

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# FastAPI 관련 imports
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# LangChain 관련 imports
from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import Chroma
from langchain_community.embeddings import SentenceTransformerEmbeddings
from langchain_core.prompts import FewShotPromptTemplate, PromptTemplate
from langchain_core.language_models.llms import LLM
from langchain_core.callbacks.manager import CallbackManagerForLLMRun

# Supabase 클라이언트
from supabase_client import (
    is_supabase_available,
    get_ingredients_by_names,
    get_all_ingredients,
    get_ingredients_count,
    search_ingredients as supabase_search_ingredients,
    test_supabase_connection
)


# ============================================================================
# Pydantic 모델 정의
# ============================================================================

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


# ============================================================================
# 메모리 및 LLM 클래스
# ============================================================================

class SimpleConversationMemory:
    """
    간단한 대화 메모리 클래스
    
    채팅 세션의 대화 히스토리를 메모리에 저장합니다.
    각 메시지는 입력, 출력, 타임스탬프를 포함합니다.
    
    Attributes:
        messages: 대화 메시지 리스트
    """
    def __init__(self):
        """대화 메모리 초기화"""
        self.messages = []
    
    def save_context(self, inputs: Dict, outputs: Dict):
        """
        대화 컨텍스트를 저장합니다.
        
        Args:
            inputs: 입력 딕셔너리 (예: {'input': '질문'})
            outputs: 출력 딕셔너리 (예: {'output': '답변'})
        """
        self.messages.append({
            'input': inputs.get('input', ''),
            'output': outputs.get('output', ''),
            'timestamp': datetime.now().isoformat()
        })
    
    def clear(self):
        """대화 히스토리를 모두 삭제합니다."""
        self.messages.clear()
    
    @property
    def chat_memory(self):
        """
        채팅 메모리 객체를 반환합니다.
        
        Returns:
            자기 자신 (LangChain 호환성을 위해)
        """
        return self


class MockLLM(LLM):
    """
    Mock LLM 클래스 - 제품 분석 리포트 생성
    
    실제 LLM 대신 규칙 기반으로 제품 분석 리포트를 생성합니다.
    LangChain의 LLM 인터페이스를 구현하여 RAG 파이프라인에서 사용됩니다.
    
    생성 로직:
    - 프롬프트에서 피부 타입, 좋은 성분, 주의 성분을 추출
    - 규칙 기반으로 자연스러운 한국어 리포트 생성
    """
    
    @property
    def _llm_type(self) -> str:
        """
        LLM 타입을 반환합니다.
        
        Returns:
            "mock" (Mock LLM임을 나타냄)
        """
        return "mock"
    
    def _call(
        self,
        prompt: str,
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> str:
        """
        프롬프트를 처리하여 응답을 생성합니다.
        
        현재는 "종합 분석 리포트" 생성만 지원합니다.
        다른 프롬프트는 기본 메시지를 반환합니다.
        
        Args:
            prompt: 입력 프롬프트
            stop: 중지 토큰 리스트 (사용 안 함)
            run_manager: 콜백 매니저 (사용 안 함)
            **kwargs: 추가 인자
        
        Returns:
            생성된 리포트 문자열
        """
        import re
        
        if "종합 분석 리포트" in prompt or "종합 분석" in prompt:
            return self._generate_product_analysis(prompt)
        
        return "해당 성분에 대한 정보를 찾을 수 없습니다."
    
    def _generate_product_analysis(self, prompt: str) -> str:
        """
        제품 종합 분석 리포트를 생성합니다.
        
        프롬프트에서 다음 정보를 추출하여 리포트를 생성합니다:
        - 사용자 피부 타입
        - 좋은 성분 목록
        - 주의 성분 목록
        - 주요 성분 목적
        
        리포트 구조:
        1. 제품 타입 추론 (보습, 항산화, 각질 제거 등)
        2. 긍정적 분석 (좋은 성분 언급)
        3. 주의 성분 분석
        4. 종합 평가
        
        Args:
            prompt: 분석 리포트 생성 프롬프트
        
        Returns:
            생성된 분석 리포트 (한국어)
        """
        import re
        
        # 피부 타입 추출
        skin_type_match = re.search(r'사용자 피부 타입:\s*([^\n]+)', prompt)
        skin_type = skin_type_match.group(1).strip() if skin_type_match else "알 수 없는"
        
        # 좋은 성분 목록 추출
        good_match_str = re.search(r'\[.*?\]에 좋은 성분 목록:\s*([^\n]+)', prompt)
        if not good_match_str:
            good_match_str = re.search(r'좋은 성분 목록:\s*([^\n]+)', prompt)
        good_names = good_match_str.group(1).strip() if good_match_str else ""
        if good_names == "없음":
            good_names = ""
        
        # 주의 성분 목록 추출
        bad_match_str = re.search(r'주의 성분 목록 \(일반적 포함\):\s*([^\n]+)', prompt)
        if not bad_match_str:
            bad_match_str = re.search(r'주의 성분 목록:\s*([^\n]+)', prompt)
        bad_names = bad_match_str.group(1).strip() if bad_match_str else ""
        if bad_names == "없음":
            bad_names = ""
        
        # 리포트 생성
        report_parts = []
        
        # 제품 타입 추론
        purpose_match = re.search(r'주요 성분 목적\):\s*([^\n]+)', prompt)
        main_purpose = "복합적인"
        
        if purpose_match:
            purposes = purpose_match.group(1).strip()
            purpose_map = {
                "moisturizer": "보습", "antioxidant": "항산화",
                "exfoliant": "각질 제거", "fragrance": "향료",
                "preservative": "보존", "emulsifier": "유화"
            }
            first_purpose_match = re.search(r'([a-zA-Z가-힣\s]+)\s*\(\d+회\)', purposes)
            if first_purpose_match:
                purpose_name = first_purpose_match.group(1).strip().lower()
                main_purpose = purpose_map.get(purpose_name, purpose_name)
        
        report_parts.append(f"이 화장품은(는) '{main_purpose}'에 중점을 둔 제품으로 보입니다.")
        
        # 긍정적 분석
        if good_names:
            good_names_list = [n.strip() for n in good_names.split(',') if n.strip()][:3]
            good_names_short = ", ".join(good_names_list)
            if len([n.strip() for n in good_names.split(',') if n.strip()]) > 3:
                good_names_short += " 등"
            report_parts.append(f"특히 {skin_type} 피부에 좋은 {good_names_short} 성분이 포함되어 있습니다.")
        
        # 주의 성분 분석
        if bad_names:
            bad_names_list = [n.strip() for n in bad_names.split(',') if n.strip()][:2]
            bad_names_short = ", ".join(bad_names_list)
            report_parts.append(f"다만, {bad_names_short} 성분은 일부 피부에 자극을 줄 수 있으니 참고하세요.")
        
        # 종합 평가
        if good_names and not bad_names:
            report_parts.append(f"전반적으로 {skin_type} 피부에 좋은 제품으로 평가됩니다.")
        elif good_names and bad_names:
            report_parts.append(f"사용 시 피부 반응을 주의 깊게 관찰하시기 바랍니다.")
        else:
            report_parts.append(f"개인적인 피부 반응을 확인하며 사용하시기 바랍니다.")
        
        return " ".join(report_parts)


# ============================================================================
# EnterpriseRAG 클래스 (Supabase 통합)
# ============================================================================

class EnterpriseRAG:
    """
    엔터프라이즈급 RAG 시스템 클래스
    
    Supabase PostgreSQL과 ChromaDB를 결합한 하이브리드 RAG 시스템입니다.
    Supabase 연결 실패 시 JSON 파일로 폴백합니다.
    
    주요 기능:
    - 성분 검색 (Supabase 직접 검색 또는 벡터 검색)
    - 제품 성분 분석 (피부 타입 기반)
    - 채팅 세션 관리
    
    데이터 소스:
    - 우선순위 1: Supabase PostgreSQL
    - 우선순위 2: JSON 파일 (폴백)
    
    벡터 스토어:
    - ChromaDB를 사용하여 성분 정보를 임베딩하여 저장
    - 다국어 지원 (paraphrase-multilingual-MiniLM-L12-v2)
    
    Args:
        data_file: JSON 폴백 파일 경로
        persist_directory: ChromaDB 벡터 스토어 저장 디렉토리
    """
    
    def __init__(self, data_file: str, persist_directory: str = "./chroma_db_ingredients"):
        self.data_file = data_file
        self.persist_directory = persist_directory
        self.ingredients_data = []
        self.use_supabase = False
        
        # LangChain 컴포넌트
        self.text_splitter = None
        self.embeddings = None
        self.vectorstore = None
        self.llm = None
        self.retriever = None
        
        # Chat History 관리
        self.chat_sessions = {}
        
        # 초기화
        self._initialize()
    
    def _initialize(self):
        """
        RAG 시스템을 초기화합니다.
        
        초기화 과정:
        1. Supabase 연결 확인
        2. 데이터 로드 (Supabase 또는 JSON)
        3. LangChain 컴포넌트 초기화
        4. ChromaDB 벡터 스토어 생성
        
        Raises:
            Exception: 초기화 실패 시
        """
        logger.info("🚀 RAG 시스템 초기화 중...")
        
        # Supabase 연결 확인
        if is_supabase_available():
            logger.info("✅ Supabase 연결 성공!")
            self.use_supabase = True
            self.ingredients_data = get_all_ingredients()
            logger.info(f"📊 Supabase에서 {len(self.ingredients_data)}개 성분 로드")
        else:
            logger.warning("⚠️ Supabase 연결 실패, JSON 파일 사용")
            self.use_supabase = False
            self._load_json_data()
        
        # LangChain 컴포넌트 초기화
        self._initialize_langchain()
        
        # 벡터 스토어 생성
        self._create_vectorstore()
    
    def _load_json_data(self):
        """
        JSON 파일에서 성분 데이터를 로드합니다.
        
        Supabase 연결 실패 시 폴백으로 사용됩니다.
        JSON 형식을 Supabase 형식으로 변환하여 저장합니다.
        
        처리 과정:
        1. JSON 파일 읽기
        2. 각 항목을 Supabase 형식으로 변환
        3. ingredients_data 리스트에 저장
        
        변환 규칙:
        - INGR_KOR_NAME → kor_name
        - INGR_ENG_NAME → eng_name
        - description → description
        - purpose → purpose (리스트로 변환)
        - good_for → good_for (리스트로 변환)
        - bad_for → bad_for (리스트로 변환)
        
        Raises:
            Exception: JSON 파일 읽기 실패 시
        """
        logger.info("📚 JSON 파일에서 데이터 로드 중...")
        try:
            with open(self.data_file, 'r', encoding='utf-8') as f:
                raw_data = json.load(f)
            
            # Supabase 형식으로 변환
            self.ingredients_data = []
            for item in raw_data:
                self.ingredients_data.append({
                    "kor_name": item.get("INGR_KOR_NAME", ""),
                    "eng_name": item.get("INGR_ENG_NAME", ""),
                    "description": item.get("description", ""),
                    "purpose": item.get("purpose") or [],
                    "good_for": item.get("good_for") or [],
                    "bad_for": item.get("bad_for") or []
                })
            
            logger.info(f"✅ {len(self.ingredients_data)}개 성분 로드 완료")
        except Exception as e:
            logger.error(f"❌ JSON 로드 실패: {e}", exc_info=True)
            self.ingredients_data = []
    
    def _initialize_langchain(self):
        """
        LangChain 컴포넌트를 초기화합니다.
        
        초기화하는 컴포넌트:
        - RecursiveCharacterTextSplitter: 텍스트를 청크로 분할
        - SentenceTransformerEmbeddings: 다국어 임베딩 모델
        - MockLLM: 리포트 생성용 LLM
        
        설정:
        - chunk_size: 1000자
        - chunk_overlap: 200자 (청크 간 겹침)
        - embedding 모델: paraphrase-multilingual-MiniLM-L12-v2 (한국어 지원)
        """
        logger.info("🔧 LangChain 컴포넌트 초기화 중...")
        
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000, chunk_overlap=200
        )
        
        self.embeddings = SentenceTransformerEmbeddings(
            model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
        )
        
        self.llm = MockLLM()
        logger.info("✅ LangChain 컴포넌트 초기화 완료")
    
    def _create_vectorstore(self):
        """
        ChromaDB 벡터 스토어를 생성합니다.
        
        각 성분 정보를 Document로 변환하여 벡터 스토어에 저장합니다.
        
        처리 과정:
        1. 각 성분 정보를 Document로 변환
           - page_content: 성분 정보 텍스트 (한국어명, 영어명, 설명, 목적 등)
           - metadata: 구조화된 메타데이터 (성분명, 설명, 목적, 피부 타입 등)
        2. 텍스트를 청크로 분할 (RecursiveCharacterTextSplitter 사용)
        3. ChromaDB에 저장 및 임베딩 생성
        4. Retriever 생성 (top_k=3)
        
        벡터 검색:
        - 유사도 기반 검색 (코사인 유사도)
        - 상위 3개 결과 반환
        
        Raises:
            Exception: 벡터 스토어 생성 실패 시
        """
        logger.info("🗄️ ChromaDB 벡터 스토어 생성 중...")
        
        documents = []
        for item in self.ingredients_data:
            kor_name = item.get('kor_name', '')
            eng_name = item.get('eng_name', '')
            description = item.get('description', '')
            purpose = item.get('purpose', [])
            good_for = item.get('good_for', [])
            bad_for = item.get('bad_for', [])
            
            content_parts = []
            if kor_name:
                content_parts.append(f"한국어 성분명: {kor_name}")
            if eng_name:
                content_parts.append(f"영어 성분명: {eng_name}")
            if description:
                content_parts.append(f"설명: {description[:500]}")
            if purpose:
                content_parts.append(f"목적: {', '.join(purpose) if isinstance(purpose, list) else purpose}")
            if good_for:
                content_parts.append(f"권장 피부 타입: {', '.join(good_for) if isinstance(good_for, list) else good_for}")
            if bad_for:
                content_parts.append(f"주의 피부 타입: {', '.join(bad_for) if isinstance(bad_for, list) else bad_for}")
            
            content = "\n".join(content_parts)
            
            doc = Document(
                page_content=content,
                metadata={
                    "ingredient_kor": kor_name,
                    "ingredient_eng": eng_name,
                    "description": (description[:200] + "..." if description and len(description) > 200 else description) or '',
                    "purpose": ', '.join(purpose) if isinstance(purpose, list) else (purpose or ''),
                    "good_for": ', '.join(good_for) if isinstance(good_for, list) else (good_for or ''),
                    "bad_for": ', '.join(bad_for) if isinstance(bad_for, list) else (bad_for or '')
                }
            )
            documents.append(doc)
        
        split_docs = self.text_splitter.split_documents(documents)
        logger.info(f"📄 {len(documents)}개 문서를 {len(split_docs)}개 청크로 분할")
        
        self.vectorstore = Chroma.from_documents(
            documents=split_docs,
            embedding=self.embeddings,
            persist_directory=self.persist_directory
        )
        
        self.retriever = self.vectorstore.as_retriever(search_kwargs={"k": 3})
        logger.info("✅ ChromaDB 벡터 스토어 생성 완료")
    
    def get_data_source(self) -> str:
        """
        현재 사용 중인 데이터 소스를 반환합니다.
        
        Returns:
            "supabase" 또는 "json"
        """
        return "supabase" if self.use_supabase else "json"
    
    def get_ingredients_count(self) -> int:
        """
        저장된 성분 개수를 반환합니다.
        
        Returns:
            성분 개수
        """
        if self.use_supabase:
            return get_ingredients_count()
        return len(self.ingredients_data)
    
    def get_or_create_session(self, session_id: str = None) -> str:
        """
        채팅 세션을 가져오거나 새로 생성합니다.
        
        Args:
            session_id: 세션 ID (없으면 새로 생성)
        
        Returns:
            세션 ID
        """
        if session_id is None:
            session_id = str(uuid.uuid4())
        if session_id not in self.chat_sessions:
            self.chat_sessions[session_id] = SimpleConversationMemory()
        return session_id
    
    def search_ingredients(self, query: str, session_id: str = None, top_k: int = 3) -> Dict:
        """
        성분을 검색합니다.
        
        검색 방식:
        1. Supabase 사용 시: 직접 SQL 쿼리로 검색 (빠름)
        2. Supabase 미사용 시: ChromaDB 벡터 검색 (폴백)
        
        반환 정보:
        - 검색 결과 답변
        - 유사한 성분 리스트 (최대 top_k개)
        - 채팅 히스토리 (최근 4개)
        
        Args:
            query: 검색 쿼리 (성분명 또는 질문)
            session_id: 채팅 세션 ID (선택적)
            top_k: 반환할 최대 결과 개수 (기본값: 3)
        
        Returns:
            검색 결과 딕셔너리:
            - query: 검색 쿼리
            - answer: 검색 결과 답변
            - similar_ingredients: 유사한 성분 리스트
            - session_id: 세션 ID
            - chat_history: 채팅 히스토리
            - success: 성공 여부
        
        Raises:
            Exception: 검색 중 오류 발생 시 (딕셔너리로 감싸서 반환)
        """
        session_id = self.get_or_create_session(session_id)
        memory = self.chat_sessions[session_id]
        
        try:
            # Supabase 직접 검색
            if self.use_supabase:
                db_results = supabase_search_ingredients(query, limit=top_k)
                if db_results:
                    first_result = db_results[0]
                    answer = f"{first_result.get('kor_name', '')}에 대한 정보: {first_result.get('description', '')[:300]}"
                    
                    similar_ingredients = [{
                        "ingredient_kor": r.get('kor_name', ''),
                        "ingredient_eng": r.get('eng_name', ''),
                        "description": r.get('description', '')[:200],
                        "purpose": ', '.join(r.get('purpose', [])),
                        "good_for": ', '.join(r.get('good_for', [])),
                        "bad_for": ', '.join(r.get('bad_for', []))
                    } for r in db_results]
                    
                    memory.save_context({"input": query}, {"output": answer})
                    
                    return {
                        "query": query,
                        "answer": answer,
                        "similar_ingredients": similar_ingredients,
                        "session_id": session_id,
                        "chat_history": memory.messages[-4:],
                        "success": True
                    }
            
            # 벡터 검색 폴백
            docs = self.retriever.invoke(query)
            if docs:
                first_doc = docs[0]
                answer = f"{first_doc.metadata.get('ingredient_kor', '')}에 대한 정보: {first_doc.metadata.get('description', '')}"
                
                similar_ingredients = [{
                    "ingredient_kor": d.metadata.get('ingredient_kor', ''),
                    "ingredient_eng": d.metadata.get('ingredient_eng', ''),
                    "description": d.metadata.get('description', ''),
                    "purpose": d.metadata.get('purpose', ''),
                    "good_for": d.metadata.get('good_for', ''),
                    "bad_for": d.metadata.get('bad_for', '')
                } for d in docs]
                
                memory.save_context({"input": query}, {"output": answer})
                
                return {
                    "query": query,
                    "answer": answer,
                    "similar_ingredients": similar_ingredients,
                    "session_id": session_id,
                    "chat_history": memory.messages[-4:],
                    "success": True
                }
            
            return {
                "query": query,
                "answer": "해당 성분에 대한 정보를 찾을 수 없습니다.",
                "similar_ingredients": [],
                "session_id": session_id,
                "chat_history": [],
                "success": False
            }
            
        except Exception as e:
            return {
                "query": query,
                "answer": f"검색 중 오류: {str(e)}",
                "similar_ingredients": [],
                "session_id": session_id,
                "chat_history": [],
                "success": False
            }
    
    def analyze_product_ingredients(self, ingredients: List[str], skin_type: str) -> Dict:
        """
        제품의 성분을 분석합니다.
        
        사용자의 피부 타입을 기반으로 각 성분이 좋은 성분인지 주의 성분인지 판단합니다.
        
        분석 과정:
        1. 성분 정보 조회 (Supabase 또는 로컬)
        2. 피부 타입 정규화 (한국어 → 영문)
        3. 각 성분에 대해 good_for/bad_for 확인
        4. 성분 목적 집계 (가장 많이 나타나는 목적 추출)
        5. MockLLM으로 종합 분석 리포트 생성
        
        판단 기준:
        - good_for에 사용자 피부 타입이 포함 → 좋은 성분
        - bad_for에 사용자 피부 타입이 포함 → 주의 성분
        - bad_for에 "sensitive" 또는 "민감성" 포함 → 일반적으로 주의 성분
        
        Args:
            ingredients: 분석할 성분명 리스트
            skin_type: 사용자 피부 타입 (예: "건성, 민감성")
        
        Returns:
            분석 결과 딕셔너리:
            - analysis_report: 종합 분석 리포트 (한국어)
            - good_matches: 좋은 성분 리스트
            - bad_matches: 주의 성분 리스트
            - success: 분석 성공 여부
        
        Raises:
            Exception: 분석 중 오류 발생 시 (딕셔너리로 감싸서 반환)
        """
        try:
            # 성분 정보 조회
            if self.use_supabase:
                ingredient_info_map = get_ingredients_by_names(ingredients)
            else:
                ingredient_info_map = self._get_ingredients_from_local(ingredients)
            
            # 피부 타입 매핑
            skin_type_map = {
                "건성": "dry", "지성": "oily", "민감성": "sensitive",
                "여드름성": "acne", "복합성": "combination", "중성": "normal"
            }
            normalized_skin_type = skin_type_map.get(skin_type, skin_type.lower())
            
            good_matches = []
            bad_matches = []
            good_names = []
            bad_names = []
            
            for ingredient_name, info in ingredient_info_map.items():
                good_for = info.get('good_for', []) or []
                bad_for = info.get('bad_for', []) or []
                purpose = info.get('purpose', []) or []
                description = info.get('description', '')
                display_name = info.get('kor_name') or info.get('eng_name') or ingredient_name
                
                # 리스트로 변환
                if isinstance(good_for, str):
                    good_for = [g.strip() for g in good_for.split(',') if g.strip()]
                if isinstance(bad_for, str):
                    bad_for = [b.strip() for b in bad_for.split(',') if b.strip()]
                if isinstance(purpose, str):
                    purpose = [p.strip() for p in purpose.split(',') if p.strip()]
                
                # good_for 분석
                if normalized_skin_type in good_for or skin_type in good_for:
                    good_matches.append({
                        "name": display_name,
                        "purpose": ', '.join(purpose) if purpose else "기능 정보 없음"
                    })
                    good_names.append(display_name)
                
                # bad_for 분석
                if normalized_skin_type in bad_for or skin_type in bad_for:
                    short_desc = description[:100] + "..." if len(description) > 100 else description
                    bad_matches.append({
                        "name": display_name,
                        "description": short_desc if short_desc else f"{skin_type} 피부에 주의가 필요합니다."
                    })
                    bad_names.append(display_name)
                elif any(kw in bad_for for kw in ["sensitive", "민감성", "acne", "여드름"]):
                    if display_name not in bad_names:
                        short_desc = description[:100] + "..." if len(description) > 100 else description
                        bad_matches.append({
                            "name": display_name,
                            "description": short_desc if short_desc else "일부 피부에 자극을 줄 수 있습니다."
                        })
                        bad_names.append(display_name)
            
            # 성분 목적 집계
            from collections import Counter
            all_purposes = []
            for info in ingredient_info_map.values():
                purpose = info.get('purpose', []) or []
                if isinstance(purpose, list):
                    all_purposes.extend(purpose)
                elif isinstance(purpose, str):
                    all_purposes.extend([p.strip() for p in purpose.split(',') if p.strip()])
            
            purpose_counts = Counter(all_purposes)
            common_purposes_str = ", ".join([f"{p} ({c}회)" for p, c in purpose_counts.most_common(3)])
            
            # 분석 리포트 생성
            analysis_prompt = f"""종합 분석 리포트 생성
사용자 피부 타입: {skin_type}
좋은 성분 목록: {', '.join(good_names) if good_names else '없음'}
주의 성분 목록 (일반적 포함): {', '.join(bad_names) if bad_names else '없음'}
참고용 (주요 성분 목적): {common_purposes_str}
"""
            
            analysis_report = self.llm.invoke(analysis_prompt)
            
            return {
                "analysis_report": analysis_report,
                "good_matches": good_matches,
                "bad_matches": bad_matches,
                "success": True
            }
            
        except Exception as e:
            return {
                "analysis_report": f"분석 중 오류: {str(e)}",
                "good_matches": [],
                "bad_matches": [],
                "success": False
            }
    
    def _get_ingredients_from_local(self, names: List[str]) -> Dict[str, Dict]:
        """
        로컬 데이터(JSON)에서 성분을 검색합니다.
        
        Supabase를 사용하지 않을 때 폴백으로 사용됩니다.
        
        검색 방식:
        1. 정확 매칭: 한국어 이름 또는 영어 이름으로 정확히 일치
        2. 부분 매칭: 정확 매칭이 없으면 부분 문자열로 검색
        
        정규화:
        - 공백 제거
        - 소문자 변환
        
        Args:
            names: 검색할 성분명 리스트
        
        Returns:
            성분명 → 성분 정보 딕셔너리 매핑
        """
        result_map = {}
        
        kor_index = {item['kor_name'].lower().replace(" ", ""): item 
                     for item in self.ingredients_data if item.get('kor_name')}
        eng_index = {item['eng_name'].lower().replace(" ", ""): item 
                     for item in self.ingredients_data if item.get('eng_name')}
        
        for name in names:
            normalized = name.strip().lower().replace(" ", "")
            
            if normalized in kor_index:
                result_map[name] = kor_index[normalized]
            elif normalized in eng_index:
                result_map[name] = eng_index[normalized]
            else:
                # 부분 매칭
                for kor_name, item in kor_index.items():
                    if normalized in kor_name or kor_name in normalized:
                        result_map[name] = item
                        break
        
        return result_map


# ============================================================================
# FastAPI 앱
# ============================================================================

app = FastAPI(
    title="화장품 성분 RAG API (Supabase)",
    description="PostgreSQL + ChromaDB 하이브리드 RAG 시스템",
    version="3.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# RAG 시스템 초기화
logger.info("🚀 RAG 시스템 초기화 시작...")
script_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(script_dir)
ingredients_file = os.path.join(project_root, 'app', 'src', 'main', 'assets', 'ingredients.json')
rag_system = EnterpriseRAG(ingredients_file)


@app.get("/", tags=["Root"])
async def root():
    """
    루트 엔드포인트
    
    API 서버의 기본 정보를 반환합니다.
    
    Returns:
        서버 정보 딕셔너리:
        - message: 서버 메시지
        - version: API 버전
        - database: 사용 중인 데이터베이스
        - docs: API 문서 URL
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
            - status: "healthy"
            - message: 상태 메시지
            - ingredients_count: 저장된 성분 개수
            - database: 사용 중인 데이터베이스
            - features: 지원하는 기능 리스트
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
            - query: 검색할 성분명 또는 질문
            - session_id: 채팅 세션 ID (선택적)
    
    Returns:
        SearchResponse: 검색 결과
            - query: 검색 쿼리
            - answer: 검색 결과 답변
            - similar_ingredients: 유사한 성분 리스트
            - session_id: 세션 ID
            - chat_history: 채팅 히스토리
            - success: 성공 여부
    
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
    
    처리 과정:
    1. 성분 정보 조회 (Supabase 또는 로컬)
    2. 각 성분의 good_for/bad_for 확인
    3. 좋은 성분/주의 성분 분류
    4. 종합 분석 리포트 생성 (MockLLM)
    
    Args:
        request: 분석 요청 (AnalyzeProductRequest)
            - ingredients: 분석할 성분명 리스트
            - skin_type: 사용자 피부 타입 (예: "건성, 민감성")
    
    Returns:
        AnalyzeProductResponse: 분석 결과
            - analysis_report: 종합 분석 리포트 (한국어)
            - good_matches: 좋은 성분 리스트
            - bad_matches: 주의 성분 리스트
            - success: 분석 성공 여부
    
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
    한국어 이름과 영어 이름이 모두 있으면 "한국어명 (영어명)" 형식으로 반환합니다.
    
    Returns:
        성분 목록 딕셔너리:
        - ingredients: 성분명 리스트
        - count: 성분 개수
        - database: 사용 중인 데이터베이스
        - success: 성공 여부
    """
    if rag_system.use_supabase:
        ingredients = get_all_ingredients()
    else:
        ingredients = rag_system.ingredients_data
    
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
    """데이터베이스 상태 확인"""
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
            "ingredients_count": len(rag_system.ingredients_data)
        }


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

