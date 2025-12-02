#!/usr/bin/env python3
"""
화장품 성분 RAG 챗봇 서버 - FastAPI 버전
LangChain, ChromaDB, Chat History, Few-shot 프롬프팅을 통합한 고급 구현
비동기 처리로 성능 최적화
"""

import json
import os
import uuid
from typing import List, Dict, Optional, Any
from datetime import datetime

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
from langchain_core.runnables import RunnablePassthrough
from langchain_core.output_parsers import StrOutputParser

import chromadb
from sentence_transformers import SentenceTransformer


# ============================================================================
# Pydantic 모델 정의 (타입 안전성 및 자동 검증)
# ============================================================================

class SearchRequest(BaseModel):
    """성분 검색 요청"""
    query: str = Field(..., description="검색할 성분명 또는 질문")
    session_id: Optional[str] = Field(None, description="채팅 세션 ID (선택)")


class AnalyzeProductRequest(BaseModel):
    """제품 분석 요청"""
    ingredients: List[str] = Field(..., description="성분명 리스트")
    skin_type: str = Field(..., description="사용자 피부 타입")


class GoodMatch(BaseModel):
    """좋은 성분 매칭 결과"""
    name: str
    purpose: str


class BadMatch(BaseModel):
    """주의 성분 매칭 결과"""
    name: str
    description: str


class AnalyzeProductResponse(BaseModel):
    """제품 분석 응답"""
    analysis_report: str
    good_matches: List[GoodMatch]
    bad_matches: List[BadMatch]
    success: bool


class SearchResponse(BaseModel):
    """검색 응답"""
    query: str
    answer: str
    similar_ingredients: List[Dict[str, Any]]
    session_id: str
    chat_history: List[Dict[str, str]]
    success: bool


class ChatHistoryResponse(BaseModel):
    """채팅 히스토리 응답"""
    session_id: str
    chat_history: List[Dict[str, str]]
    success: bool


class ClearHistoryRequest(BaseModel):
    """채팅 히스토리 초기화 요청"""
    session_id: str


class HealthResponse(BaseModel):
    """헬스체크 응답"""
    status: str
    message: str
    ingredients_count: int
    features: List[str]


# ============================================================================
# 메모리 및 LLM 클래스 (기존과 동일)
# ============================================================================

class SimpleConversationMemory:
    """간단한 대화 메모리 구현"""
    
    def __init__(self):
        self.messages = []
    
    def save_context(self, inputs: Dict, outputs: Dict):
        """대화 컨텍스트 저장"""
        self.messages.append({
            'input': inputs.get('input', ''),
            'output': outputs.get('output', ''),
            'timestamp': datetime.now().isoformat()
        })
    
    def clear(self):
        """메모리 초기화"""
        self.messages.clear()
    
    @property
    def chat_memory(self):
        """채팅 메모리 객체 반환"""
        return self


class MockLLM(LLM):
    """Mock LLM for demonstration purposes - 컨텍스트 기반 답변 생성"""
    
    @property
    def _llm_type(self) -> str:
        return "mock"
    
    def _call(
        self,
        prompt: str,
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> str:
        """Mock LLM call - 프롬프트에서 컨텍스트를 추출하여 답변 생성"""
        import re
        
        # 종합 분석 프롬프트 감지 (제품 분석 요청)
        if "종합 분석 리포트" in prompt or "종합 분석" in prompt or "이 제품은 다음 성분들로 구성되어 있습니다" in prompt:
            return self._generate_product_analysis(prompt)
        
        # 프롬프트에서 질문과 컨텍스트 추출 시도
        lines = prompt.split('\n')
        
        # 질문 추출
        question = ""
        context_start = False
        context_parts = []
        in_context_section = False
        
        for line in lines:
            line_stripped = line.strip()
            line_lower = line_stripped.lower()
            
            # 컨텍스트 섹션 시작 확인
            if "컨텍스트" in line_stripped or "context" in line_lower or "==========" in line_stripped:
                if "컨텍스트" in line_stripped or "context" in line_lower:
                    in_context_section = True
                continue
            
            # 질문 섹션 확인 (답변 시작 전까지)
            if "질문:" in line_stripped or "question:" in line_lower:
                question = line_stripped.split(":", 1)[-1].strip() if ":" in line_stripped else line_stripped
                in_context_section = False
                continue
            
            if "답변:" in line_stripped or "answer:" in line_lower or "========" in line_stripped:
                in_context_section = False
                continue
            
            # 컨텍스트 섹션 내의 내용 수집
            if in_context_section and line_stripped and not line_stripped.startswith("=="):
                context_parts.append(line_stripped)
            elif not in_context_section and any(keyword in line_stripped for keyword in ["한국어 성분명:", "영어 성분명:", "설명:", "목적:", "권장 피부 타입:", "주의 피부 타입:", "Korean", "English", "Description"]):
                context_parts.append(line_stripped)
        
        # 컨텍스트가 있으면 컨텍스트 기반으로 답변 생성
        if context_parts:
            context_text = "\n".join(context_parts)
            
            # 질문에서 주요 키워드 추출
            question_lower = question.lower() if question else prompt.lower()
            
            # 성분명 추출 시도 (한국어 또는 영어)
            ingredient_name = ""
            description = ""
            purpose = ""
            good_for = ""
            
            # 모든 컨텍스트를 하나의 문자열로 합치기
            full_context = "\n".join(context_parts)
            
            # 정규표현식이나 간단한 파싱으로 정보 추출
            
            # 한국어 성분명 추출
            kor_match = re.search(r'한국어 성분명:\s*([^\n]+)', full_context, re.IGNORECASE)
            if kor_match:
                ingredient_name = kor_match.group(1).strip()
            
            # 영어 성분명 추출 (한국어가 없으면 영어 사용)
            eng_match = re.search(r'영어 성분명:\s*([^\n]+)', full_context, re.IGNORECASE)
            if eng_match:
                eng_name = eng_match.group(1).strip()
                if not ingredient_name:
                    ingredient_name = eng_name
            
            # 설명 추출 (더 넓은 범위로)
            desc_match = re.search(r'설명:\s*([^\n]+(?:\n(?!한국어|영어|목적|권장|주의)[^\n]+)*)', full_context, re.IGNORECASE | re.MULTILINE)
            if desc_match:
                description = desc_match.group(1).strip()[:500]
            
            # 목적 추출
            purpose_match = re.search(r'목적:\s*([^\n]+)', full_context, re.IGNORECASE)
            if purpose_match:
                purpose = purpose_match.group(1).strip()
            
            # 권장 피부 타입 추출
            good_match = re.search(r'권장 피부 타입:\s*([^\n]+)', full_context, re.IGNORECASE)
            if good_match:
                good_for = good_match.group(1).strip()
            
            # 컨텍스트 기반 답변 생성
            if ingredient_name and description:
                # 질문 유형에 따라 답변 형식 변경
                if any(word in question_lower for word in ["what is", "무엇", "뭐야", "소개", "이유"]):
                    answer = f"{ingredient_name}에 대해 설명드리면, {description[:300]}"
                elif any(word in question_lower for word in ["효과", "effect", "도움", "help"]):
                    # 목적 정보 추출
                    purpose = ""
                    for part in context_parts:
                        if "목적:" in part:
                            purpose = part.split(":", 1)[-1].strip()
                    if purpose:
                        answer = f"{ingredient_name}은(는) {purpose} 등의 목적으로 사용되며, {description[:200]}"
                    else:
                        answer = f"{ingredient_name}의 효과는 다음과 같습니다: {description[:300]}"
                else:
                    answer = f"{ingredient_name}에 대한 정보: {description[:300]}"
                
                # 답변이 너무 짧으면 설명을 더 추가
                if len(answer) < 100 and description:
                    answer += f"\n\n더 자세히 말씀드리면, {description[300:600]}"
                
                return answer
            elif description:
                return f"검색된 정보에 따르면: {description[:400]}"
        
        # 컨텍스트가 없으면 기본 답변
        if "니아신아마이드" in prompt or "niacinamide" in prompt.lower():
            return "니아신아마이드는 비타민 B3의 한 형태로, 피부 진정과 수분 공급에 효과적이며 모공 축소와 색소침착 개선에도 도움을 줍니다."
        elif "히알루론산" in prompt or "hyaluronic" in prompt.lower():
            return "히알루론산은 천연 보습 성분으로 피부에 수분을 공급하고 탄력을 개선하며 주름 완화에 효과적입니다."
        else:
            return "죄송합니다. 해당 성분에 대한 정보를 찾을 수 없습니다. 다른 질문을 시도해보시거나 더 구체적으로 질문해주세요."
    
    def _generate_product_analysis(self, prompt: str) -> str:
        """제품 종합 분석 리포트 생성 (자연스러운 서술형)"""
        import re
        
        # 피부 타입 추출
        skin_type_match = re.search(r'사용자 피부 타입:\s*([^\n]+)', prompt)
        skin_type = skin_type_match.group(1).strip() if skin_type_match else "알 수 없는"
        
        # 좋은 성분 목록 추출 (이름만)
        good_match_str = re.search(r'\[.*?\]에 좋은 성분 목록:\s*([^\n]+)', prompt)
        if not good_match_str:
            good_match_str = re.search(r'좋은 성분 목록:\s*([^\n]+)', prompt)
        good_names = good_match_str.group(1).strip() if good_match_str else ""
        if good_names == "없음":
            good_names = ""
        
        # 주의 성분 목록 추출 (이름만) - 일반적 포함 형식도 지원
        bad_match_str = re.search(r'주의 성분 목록 \(일반적 포함\):\s*([^\n]+)', prompt)
        if not bad_match_str:
            bad_match_str = re.search(r'\[.*?\]에 주의 성분 목록:\s*([^\n]+)', prompt)
        if not bad_match_str:
            bad_match_str = re.search(r'주의 성분 목록:\s*([^\n]+)', prompt)
        bad_names = bad_match_str.group(1).strip() if bad_match_str else ""
        if bad_names == "없음":
            bad_names = ""
        
        # 좋은 성분 세부정보 추출
        good_details_match = re.search(r'좋은 성분 세부정보:\s*([^\n]+(?:\n- [^\n]+)*)', prompt, re.MULTILINE)
        good_details = good_details_match.group(1).strip() if good_details_match else ""
        if good_details == "없음":
            good_details = ""
        
        # 주의 성분 세부정보 추출
        bad_details_match = re.search(r'주의 성분 세부정보:\s*([^\n]+(?:\n- [^\n]+)*)', prompt, re.MULTILINE)
        bad_details = bad_details_match.group(1).strip() if bad_details_match else ""
        if bad_details == "없음":
            bad_details = ""
        
        # 목적 추론 (가장 빈번한 목적 1개)
        purpose_match = re.search(r'주요 성분 목적\):\s*([^\n]+)', prompt)
        main_purpose = "복합적인"  # 기본값
        
        if purpose_match:
            purposes = purpose_match.group(1).strip()
            if purposes:
                # "moisturizer (3회)" 같은 형식에서 "moisturizer"만 추출
                first_purpose_match = re.search(r'([a-zA-Z가-힣\s]+)\s*\(\d+회\)', purposes)
                if first_purpose_match:
                    purpose_name = first_purpose_match.group(1).strip()
                    
                    # purpose 한글화 매핑
                    purpose_map = {
                        "anti-acne": "여드름 완화",
                        "antioxidant": "항산화",
                        "cleansing": "세정",
                        "colorant": "착색",
                        "emulsifier": "유화",
                        "exfoliant": "각질 제거",
                        "fragrance": "향료",
                        "moisturizer": "보습",
                        "ph adjuster": "pH 조절",
                        "preservative": "보존",
                        "skin-brightening": "미백",
                        "solvent": "용해",
                        "soothing": "진정",
                        "sunscreen": "자외선 차단",
                        "thickener": "점증",
                        # 동의어/변형어
                        "emollient": "보습 및 유연화",
                        "humectant": "수분 공급",
                        "anti-aging": "항노화",
                        "whitening": "미백",
                        "anti-inflammatory": "항염",
                        "antimicrobial": "항균",
                        "moisturizing": "보습",
                        "conditioning": "컨디셔닝"
                    }
                    
                    # 영어 또는 한글 매칭
                    purpose_lower = purpose_name.lower()
                    main_purpose = purpose_map.get(purpose_lower, purpose_name)
                    
                    # 한글이 이미 포함되어 있으면 그대로 사용
                    if any(ord(c) >= 0xAC00 and ord(c) <= 0xD7A3 for c in purpose_name):
                        main_purpose = purpose_name
        
        # --- 서술형 리포트 생성 ---
        report_parts = []
        
        # 1. 제품 타입/목적 추론
        product_type = None
        if main_purpose == "향료" or "fragrance" in bad_details.lower() or "fragrance" in good_details.lower():
            product_type = "향수"
            report_parts.append(f"이 화장품은(는) 향수 제품으로 보입니다.")
        else:
            report_parts.append(f"이 화장품은(는) '{main_purpose}'에 중점을 둔 제품으로 보입니다.")
        
        # 2. 긍정적 분석
        if good_names:
            # 성분명 리스트 정리 (첫 2-3개만 언급)
            good_names_list = [name.strip() for name in good_names.split(',') if name.strip()][:3]
            good_names_short = ", ".join(good_names_list)
            if len([name.strip() for name in good_names.split(',') if name.strip()]) > 3:
                good_names_short += " 등"
            
            # 좋은 성분의 주요 목적 추출
            main_benefit = "피부 개선"
            if good_details:
                # purpose에서 주요 효능 추출 시도
                if "보습" in good_details or "moisturizer" in good_details.lower():
                    main_benefit = "보습 및 컨디셔닝"
                elif "진정" in good_details or "soothing" in good_details.lower():
                    main_benefit = "진정 및 케어"
                elif "항산화" in good_details or "antioxidant" in good_details.lower():
                    main_benefit = "항산화 및 보호"
            
            # 피부 타입별 정보가 있으면 언급
            if skin_type != "알 수 없는":
                report_parts.append(f"특히 {skin_type} 피부에 좋은 {good_names_short} 성분이 포함되어 있어, {main_benefit}에 도움을 줄 수 있습니다.")
            else:
                report_parts.append(f"이 제품에는 {good_names_short} 성분이 포함되어 있어, {main_benefit}에 도움을 줄 수 있습니다.")
        else:
            if product_type == "향수":
                report_parts.append(f"이 제품은 향료 성분에 중점을 둔 향수 제품입니다.")
            else:
                report_parts.append(f"특별히 {skin_type} 피부에 유익한 성분은 확인되지 않았습니다.")
        
        # 3. 부정적 분석 (일반적인 주의사항 포함)
        if bad_names:
            # 성분명 리스트 정리 (첫 1-2개만 언급)
            bad_names_list = [name.strip() for name in bad_names.split(',') if name.strip()][:2]
            bad_names_short = ", ".join(bad_names_list)
            if len([name.strip() for name in bad_names.split(',') if name.strip()]) > 2:
                bad_names_short += " 등"
            
            # 주의 이유 추출 (bad_details에서 민감성 등 추출)
            caution_reason = "자극"
            caution_target = None
            
            if bad_details:
                bad_details_lower = bad_details.lower()
                # 한글 키워드 확인
                if "민감성" in bad_details or "sensitive" in bad_details_lower:
                    caution_reason = "알레르기 반응"
                    caution_target = "민감성 피부를 가진 분들"
                elif "여드름" in bad_details or "acne" in bad_details_lower:
                    caution_reason = "여드름 악화"
                    caution_target = "여드름성 피부를 가진 분들"
                elif "여드름성" in bad_details or "acne-prone" in bad_details_lower:
                    caution_reason = "여드름 악화"
                    caution_target = "여드름성 피부를 가진 분들"
                elif "알레르기" in bad_details or "allergy" in bad_details_lower:
                    caution_reason = "알레르기 반응"
                    caution_target = "알레르기 체질인 분들"
                elif "건조" in bad_details or "dry" in bad_details_lower:
                    caution_reason = "건조 유발"
                    caution_target = f"{skin_type} 피부"
                elif "자극" in bad_details or "irritation" in bad_details_lower:
                    caution_reason = "피부 자극"
                    caution_target = "일부 사용자"
            
            # 제품 타입에 따라 적절한 표현
            if product_type == "향수":
                if caution_target:
                    report_parts.append(f"다만, {bad_names_short} 등의 향료 성분은 {caution_target}에게 {caution_reason}을 유발할 수 있으니, 향료 알레르기나 민감성 피부가 있으신 분들은 사용 전 패치 테스트를 권장합니다.")
                else:
                    report_parts.append(f"다만, {bad_names_short} 등의 향료 성분은 일부 사용자에게 알레르기 반응을 유발할 수 있으니, 향료 알레르기나 민감성 피부가 있으신 분들은 사용 전 패치 테스트를 권장합니다.")
            else:
                if caution_target:
                    report_parts.append(f"다만, {bad_names_short} 성분은 {caution_target}에게 {caution_reason}을 유발할 가능성이 있으니 참고하시기 바랍니다.")
                else:
                    report_parts.append(f"다만, {bad_names_short} 성분은 {skin_type} 피부에 {caution_reason}을 유발할 가능성이 있으니 참고하시기 바랍니다.")
        else:
            if product_type == "향수":
                report_parts.append(f"다만, 향수 제품의 특성상 향료 성분이 포함되어 있어, 향료 알레르기나 민감성 피부가 있으신 분들은 사용 전 패치 테스트를 권장합니다.")
            else:
                report_parts.append(f"다행히 {skin_type} 피부에 특별히 주의가 필요한 성분은 보이지 않습니다.")
        
        # 4. 최종 권장사항
        if product_type == "향수":
            if bad_names:
                report_parts.append(f"따라서 이 향수 제품은 일반적인 사용에는 무난하지만, 향료 알레르기나 민감성 피부를 가진 분들은 사용 전 반드시 패치 테스트를 수행하시고, 피부 반응이 좋지 않으면 사용을 중단하시기 바랍니다.")
            else:
                report_parts.append(f"따라서 이 향수 제품은 일반적인 사용에는 무난하지만, 향료 성분이 포함되어 있어 사용 전 개인적인 피부 반응을 확인하시기 바랍니다.")
        else:
            if good_names and not bad_names:
                report_parts.append(f"따라서 이 제품은 {skin_type} 피부에 대해 전반적으로 긍정적인 평가를 받을 수 있습니다. 좋은 성분들이 포함되어 있어 사용을 권장합니다.")
            elif good_names and bad_names:
                report_parts.append(f"따라서 이 제품은 {skin_type} 피부에 도움이 되는 성분과 주의가 필요한 성분이 혼재되어 있습니다. 사용 시 피부 반응을 주의 깊게 관찰하시고, 처음 사용 시 소량으로 테스트해보시기 바랍니다.")
            elif bad_names and not good_names:
                report_parts.append(f"따라서 이 제품은 {skin_type} 피부 타입에게 주의가 필요합니다. 사용 전 반드시 패치 테스트를 수행하시고, 피부 반응이 좋지 않으면 사용을 중단하시기 바랍니다.")
            else:
                report_parts.append(f"따라서 이 제품은 {skin_type} 피부에 대해 중립적인 평가입니다. 개인적인 피부 반응을 확인하며 사용하시기 바랍니다.")
        
        return " ".join(report_parts)


# ============================================================================
# EnterpriseRAG 클래스 (기존과 동일하나 비동기 메서드 추가)
# ============================================================================

class EnterpriseRAG:
    """엔터프라이즈급 RAG 시스템 - LangChain, ChromaDB, Chat History 통합"""
    
    def __init__(self, data_file: str, persist_directory: str = "./chroma_db_ingredients"):
        """엔터프라이즈급 RAG 시스템 초기화"""
        self.data_file = data_file
        self.persist_directory = persist_directory
        self.ingredients_data = []
        
        # LangChain 컴포넌트들
        self.text_splitter = None
        self.embeddings = None
        self.vectorstore = None
        self.llm = None
        self.qa_runnable = None
        self.few_shot_prompt = None
        
        # Chat History 관리
        self.chat_sessions = {}  # session_id -> SimpleConversationMemory
        
        # 초기화 실행
        self.load_data()
        self.initialize_components()
        self.create_vectorstore()
        self.create_few_shot_prompt()
        self.create_qa_runnable()
    
    def load_data(self):
        """JSON 데이터 로드"""
        print("📚 화장품 성분 데이터 로딩 중...")
        with open(self.data_file, 'r', encoding='utf-8') as f:
            self.ingredients_data = json.load(f)
        print(f"✅ {len(self.ingredients_data)}개 성분 데이터 로드 완료")
    
    def initialize_components(self):
        """LangChain 컴포넌트들 초기화"""
        print("🔧 LangChain 컴포넌트 초기화 중...")
        
        # 텍스트 분할기 초기화
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200,
            length_function=len,
            separators=["\n\n", "\n", ". ", " ", ""]
        )
        
        # 임베딩 모델 초기화 (한국어 지원)
        self.embeddings = SentenceTransformerEmbeddings(
            model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
        )
        
        # Mock LLM 초기화
        self.llm = MockLLM()
        
        print("✅ LangChain 컴포넌트 초기화 완료")
    
    def create_vectorstore(self):
        """ChromaDB 벡터 스토어 생성"""
        print("🗄️ ChromaDB 벡터 스토어 생성 중...")
        
        # 문서 생성
        documents = []
        for item in self.ingredients_data:
            kor_name = item.get('INGR_KOR_NAME', '')
            eng_name = item.get('INGR_ENG_NAME', '')
            description = item.get('description', '')
            purpose = item.get('purpose', [])
            good_for = item.get('good_for', [])
            bad_for = item.get('bad_for', [])
            
            # 풍부한 컨텍스트 정보 생성
            content_parts = []
            if kor_name:
                content_parts.append(f"한국어 성분명: {kor_name}")
            if eng_name:
                content_parts.append(f"영어 성분명: {eng_name}")
            if description:
                content_parts.append(f"설명: {description}")
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
        
        # 텍스트 분할
        split_docs = self.text_splitter.split_documents(documents)
        print(f"📄 {len(documents)}개 문서를 {len(split_docs)}개 청크로 분할")
        
        # ChromaDB 벡터 스토어 생성
        self.vectorstore = Chroma.from_documents(
            documents=split_docs,
            embedding=self.embeddings,
            persist_directory=self.persist_directory
        )
        
        print("✅ ChromaDB 벡터 스토어 생성 완료")
    
    def create_few_shot_prompt(self):
        """Few-shot 프롬프팅 템플릿 생성"""
        print("🎯 Few-shot 프롬프팅 템플릿 생성 중...")
        
        # Few-shot 예시들
        examples = [
            {
                "question": "니아신아마이드는 어떤 효과가 있나요?",
                "answer": "니아신아마이드는 비타민 B3의 한 형태로, 피부 진정과 수분 공급에 효과적입니다. 또한 모공 축소와 색소침착 개선에도 도움을 주는 성분입니다."
            },
            {
                "question": "히알루론산은 무엇인가요?",
                "answer": "히알루론산은 천연 보습 성분으로, 피부에 수분을 공급하고 탄력을 개선하는 역할을 합니다. 주름 완화와 피부 보습에 매우 효과적인 성분입니다."
            },
            {
                "question": "레티놀의 주의사항은 무엇인가요?",
                "answer": "레티놀은 강력한 항노화 성분이지만, 자외선에 민감하므로 주간 사용 시 반드시 자외선 차단제를 사용해야 합니다. 또한 처음 사용 시 피부 자극이 있을 수 있으므로 점진적으로 사용량을 늘려가는 것이 좋습니다."
            }
        ]
        
        # 프롬프트 템플릿 생성
        example_prompt = PromptTemplate(
            input_variables=["question", "answer"],
            template="질문: {question}\n답변: {answer}"
        )
        
        # Few-shot 프롬프트 템플릿 생성
        self.few_shot_prompt = FewShotPromptTemplate(
            examples=examples,
            example_prompt=example_prompt,
            prefix="당신은 화장품 성분 전문가입니다. 다음 예시들을 참고하여 사용자의 질문에 정확하고 도움이 되는 답변을 제공해주세요.\n\n예시:",
            suffix="\n\n질문: {question}\n답변:",
            input_variables=["question"]
        )
        
        print("✅ Few-shot 프롬프팅 템플릿 생성 완료")
    
    def create_qa_runnable(self):
        """Runnable 기반 QA 파이프라인 생성"""
        print("🔗 Runnable QA 파이프라인 생성 중...")
        
        # Retriever 설정
        self.retriever = self.vectorstore.as_retriever(search_kwargs={"k": 3})
        
        # 커스텀 Runnable 체인 생성
        def custom_qa_chain(input_dict):
            """커스텀 QA 체인: 검색 -> 문서 직접 사용 -> 답변 생성"""
            question = input_dict["question"]
            # 검색 수행
            retriever = self.retriever
            docs = retriever.invoke(question)
            
            # 검색된 문서가 없으면 기본 답변
            if not docs:
                answer = "죄송합니다. 해당 성분에 대한 정보를 찾을 수 없습니다."
                return {"answer": answer, "docs": []}
            
            # 첫 번째 문서에서 정보 추출
            first_doc = docs[0]
            doc_content = first_doc.page_content
            metadata = first_doc.metadata
            
            # 메타데이터에서 정보 추출
            ingredient_kor = metadata.get("ingredient_kor", "")
            ingredient_eng = metadata.get("ingredient_eng", "")
            description_meta = metadata.get("description", "")
            purpose = metadata.get("purpose", "")
            
            # 문서 내용에서 직접 추출 시도
            ingredient_name = ingredient_kor or ingredient_eng or ""
            
            # 설명 추출
            description = description_meta
            if not description or len(description) < 50:
                # 문서 내용에서 설명 추출
                import re
                desc_match = re.search(r'설명:\s*([^\n]+(?:\n[^\n]+)*)', doc_content, re.IGNORECASE | re.MULTILINE)
                if desc_match:
                    description = desc_match.group(1).strip()[:500]
                elif doc_content:
                    description = doc_content[:500]
            
            # 답변 생성
            if ingredient_name and description:
                question_lower = question.lower()
                if any(word in question_lower for word in ["what is", "무엇", "뭐야", "소개"]):
                    answer = f"{ingredient_name}에 대해 설명드리면, {description[:400]}"
                elif any(word in question_lower for word in ["효과", "effect", "도움", "help"]):
                    if purpose:
                        answer = f"{ingredient_name}은(는) {purpose} 등의 목적으로 사용되며, {description[:300]}"
                    else:
                        answer = f"{ingredient_name}의 효과는 다음과 같습니다: {description[:400]}"
                else:
                    answer = f"{ingredient_name}에 대한 정보: {description[:400]}"
            elif description:
                answer = f"검색된 정보에 따르면: {description[:400]}"
            else:
                answer = "죄송합니다. 해당 성분에 대한 상세 정보를 찾을 수 없습니다."
            
            return {"answer": answer, "docs": docs}
        
        self.qa_runnable = lambda input_dict: custom_qa_chain(input_dict)
        
        print("✅ Runnable QA 파이프라인 생성 완료")
    
    def get_or_create_session(self, session_id: str = None) -> str:
        """채팅 세션 생성 또는 조회"""
        if session_id is None:
            session_id = str(uuid.uuid4())
        
        if session_id not in self.chat_sessions:
            self.chat_sessions[session_id] = SimpleConversationMemory()
        
        return session_id
    
    def search_ingredients(self, query: str, session_id: str = None, top_k: int = 3) -> Dict:
        """LangChain 기반 성분 검색 및 답변 생성"""
        # 세션 관리
        session_id = self.get_or_create_session(session_id)
        memory = self.chat_sessions[session_id]
        
        try:
            # Runnable 파이프라인 실행
            result = self.qa_runnable({"question": query})
            answer = result["answer"]
            docs = result["docs"]
            
            similar_ingredients = []
            for doc in docs:
                ingredient_info = {
                    "ingredient_kor": doc.metadata.get("ingredient_kor", ""),
                    "ingredient_eng": doc.metadata.get("ingredient_eng", ""),
                    "description": doc.metadata.get("description", ""),
                    "purpose": doc.metadata.get("purpose", ""),
                    "good_for": doc.metadata.get("good_for", ""),
                    "bad_for": doc.metadata.get("bad_for", ""),
                    "similarity": 0.8
                }
                similar_ingredients.append(ingredient_info)
            
            # 채팅 히스토리에 추가
            memory.save_context(
                {"input": query},
                {"output": answer}
            )
            
            return {
                "query": query,
                "answer": answer,
                "similar_ingredients": similar_ingredients,
                "session_id": session_id,
                "chat_history": memory.messages[-4:] if len(memory.messages) > 4 else memory.messages,
                "success": True
            }
            
        except Exception as e:
            return {
                "query": query,
                "answer": f"죄송합니다. 처리 중 오류가 발생했습니다: {str(e)}",
                "similar_ingredients": [],
                "session_id": session_id,
                "chat_history": [],
                "success": False
            }
    
    def get_chat_history(self, session_id: str) -> Dict:
        """채팅 히스토리 조회"""
        if session_id not in self.chat_sessions:
            return {"error": "세션을 찾을 수 없습니다.", "chat_history": []}
        
        memory = self.chat_sessions[session_id]
        return {
            "session_id": session_id,
            "chat_history": memory.messages,
            "success": True
        }
    
    def clear_chat_history(self, session_id: str) -> Dict:
        """채팅 히스토리 초기화"""
        if session_id in self.chat_sessions:
            self.chat_sessions[session_id].clear()
            return {"message": "채팅 히스토리가 초기화되었습니다.", "success": True}
        else:
            return {"error": "세션을 찾을 수 없습니다.", "success": False}
    
    def analyze_product_ingredients(self, ingredients: List[str], skin_type: str) -> Dict:
        """
        제품 성분 리스트와 피부 타입을 기반으로 종합 분석 리포트 생성
        """
        try:
            # 성분명 정규화 함수
            def normalize_ingredient_name(name: str) -> str:
                return name.strip().lower().replace(" ", "").replace("-", "").replace("_", "")
            
            # 1. 컨텍스트 수집
            ingredient_info_map = {}
            
            for ingredient_name in ingredients:
                normalized_name = normalize_ingredient_name(ingredient_name)
                
                # 정확한 매칭 시도
                matched_ingredient = None
                for item in self.ingredients_data:
                    kor_name = item.get('INGR_KOR_NAME', '')
                    eng_name = item.get('INGR_ENG_NAME', '')
                    
                    if (kor_name and normalize_ingredient_name(kor_name) == normalized_name) or \
                       (eng_name and normalize_ingredient_name(eng_name) == normalized_name):
                        matched_ingredient = item
                        break
                
                # 부분 매칭 시도
                if not matched_ingredient and len(normalized_name) >= 3:
                    for item in self.ingredients_data:
                        kor_name = item.get('INGR_KOR_NAME', '')
                        eng_name = item.get('INGR_ENG_NAME', '')
                        
                        kor_normalized = normalize_ingredient_name(kor_name) if kor_name else ""
                        eng_normalized = normalize_ingredient_name(eng_name) if eng_name else ""
                        
                        if (kor_normalized and (normalized_name in kor_normalized or kor_normalized in normalized_name)) or \
                           (eng_normalized and (normalized_name in eng_normalized or eng_normalized in normalized_name)):
                            matched_ingredient = item
                            break
                
                # 매칭된 성분 정보 저장
                if matched_ingredient:
                    kor_name = matched_ingredient.get('INGR_KOR_NAME', '')
                    eng_name = matched_ingredient.get('INGR_ENG_NAME', '')
                    description = matched_ingredient.get('description', '')
                    purpose = matched_ingredient.get('purpose', [])
                    good_for = matched_ingredient.get('good_for', [])
                    bad_for = matched_ingredient.get('bad_for', [])
                    
                    ingredient_info_map[ingredient_name] = {
                        "name": kor_name or eng_name,
                        "eng_name": eng_name,
                        "description": description,
                        "purpose": ', '.join(purpose) if isinstance(purpose, list) else (purpose or ''),
                        "good_for": ', '.join(good_for) if isinstance(good_for, list) else (good_for or ''),
                        "bad_for": ', '.join(bad_for) if isinstance(bad_for, list) else (bad_for or ''),
                        "page_content": f"한국어 성분명: {kor_name}\n영어 성분명: {eng_name}\n설명: {description}"
                    }
                else:
                    # 벡터 검색
                    docs = self.retriever.invoke(ingredient_name)
                    if docs:
                        best_doc = docs[0]
                        metadata = best_doc.metadata
                        ingredient_info_map[ingredient_name] = {
                            "name": metadata.get("ingredient_kor", "") or metadata.get("ingredient_eng", ""),
                            "eng_name": metadata.get("ingredient_eng", ""),
                            "description": metadata.get("description", ""),
                            "purpose": metadata.get("purpose", ""),
                            "good_for": metadata.get("good_for", ""),
                            "bad_for": metadata.get("bad_for", ""),
                            "page_content": best_doc.page_content
                        }
            
            # 2. 데이터 집계
            good_matches = []
            bad_matches = []
            good_ingredient_names = []
            bad_ingredient_names = []
            
            # 피부 타입 매핑
            skin_type_map = {
                "acne": "여드름",
                "acne-prone": "여드름성",
                "damaged": "손상된",
                "dry": "건성",
                "irritated": "자극받은",
                "oily": "지성",
                "sensitive": "민감성"
            }
            
            good_for_map = {
                "acne": "여드름",
                "acne-prone": "여드름성",
                "damaged": "손상된",
                "dry": "건성",
                "irritated": "자극받은",
                "oily": "지성",
                "sensitive": "민감성"
            }
            
            bad_for_map = {
                "acne": "여드름",
                "acne-prone": "여드름성",
                "sensitive": "민감성"
            }
            
            normalized_skin_type = skin_type_map.get(skin_type.lower(), skin_type)
            
            general_caution_keywords = ["sensitive", "allergy", "irritation", "acne", "acne-prone"]
            general_caution_keywords_kr = ["민감성", "알레르기", "자극", "여드름", "여드름성"]
            
            for ingredient_name, info in ingredient_info_map.items():
                good_for = info.get("good_for", "")
                bad_for = info.get("bad_for", "")
                purpose = info.get("purpose", "")
                description = info.get("description", "")
                
                # good_for 분석
                if good_for:
                    good_for_list = [x.strip() for x in good_for.split(',') if x.strip()]
                    good_for_list_normalized = [
                        good_for_map.get(item.lower(), item) 
                        for item in good_for_list
                    ]
                    if normalized_skin_type in good_for_list or normalized_skin_type in good_for_list_normalized:
                        good_matches.append({
                            "name": info.get("name", ingredient_name),
                            "purpose": purpose if purpose else "기능 정보 없음"
                        })
                        good_ingredient_names.append(info.get("name", ingredient_name))
                
                # bad_for 분석
                if bad_for:
                    bad_for_list = [x.strip() for x in bad_for.split(',') if x.strip()]
                    bad_for_list_normalized = [
                        bad_for_map.get(item.lower(), item) 
                        for item in bad_for_list
                    ]
                    
                    # 해당 피부 타입에 주의가 필요한 경우
                    if normalized_skin_type in bad_for_list or normalized_skin_type in bad_for_list_normalized:
                        short_desc = description[:100] + "..." if len(description) > 100 else description
                        bad_matches.append({
                            "name": info.get("name", ingredient_name),
                            "description": short_desc if short_desc else f"{skin_type} 피부에 주의가 필요한 성분입니다."
                        })
                        bad_ingredient_names.append(info.get("name", ingredient_name))
                    # 일반적인 주의사항
                    elif any(keyword in bad_for_list for keyword in general_caution_keywords) or \
                         any(keyword in bad_for_list for keyword in general_caution_keywords_kr):
                        if info.get("name", ingredient_name) not in bad_ingredient_names:
                            caution_reason = None
                            bad_for_lower = [x.lower() for x in bad_for_list]
                            
                            if "sensitive" in bad_for_lower or "민감성" in bad_for_list:
                                caution_reason = "민감성 피부에 주의가 필요합니다"
                            elif "acne" in bad_for_lower or "여드름" in bad_for_list:
                                caution_reason = "여드름성 피부에 주의가 필요합니다"
                            elif "acne-prone" in bad_for_lower or "여드름성" in bad_for_list:
                                caution_reason = "여드름성 피부에 주의가 필요합니다"
                            elif "allergy" in bad_for_lower or "알레르기" in bad_for_list:
                                caution_reason = "알레르기 반응을 유발할 수 있습니다"
                            elif "irritation" in bad_for_lower or "자극" in bad_for_list:
                                caution_reason = "피부 자극을 유발할 수 있습니다"
                            
                            short_desc = description[:100] + "..." if len(description) > 100 else description
                            if short_desc:
                                bad_matches.append({
                                    "name": info.get("name", ingredient_name),
                                    "description": f"{short_desc} ({caution_reason or '일반적인 주의가 필요합니다'})"
                                })
                            else:
                                bad_matches.append({
                                    "name": info.get("name", ingredient_name),
                                    "description": caution_reason or "일반적인 주의가 필요한 성분입니다."
                                })
                            bad_ingredient_names.append(info.get("name", ingredient_name))
            
            # 성분 목적 집계
            from collections import Counter
            all_purposes = []
            for info in ingredient_info_map.values():
                purpose = info.get("purpose", "")
                if purpose:
                    if isinstance(purpose, list):
                        all_purposes.extend(purpose)
                    elif isinstance(purpose, str):
                        all_purposes.extend([p.strip() for p in purpose.split(',') if p.strip()])
            
            purpose_counts = Counter(all_purposes)
            common_purposes_str = ", ".join([f"{p} ({c}회)" for p, c in purpose_counts.most_common(3)])
            
            # 3. 프롬프트 생성
            ingredients_str = ", ".join(ingredients)
            good_ingredients_str = ", ".join(good_ingredient_names) if good_ingredient_names else "없음"
            bad_ingredients_str = ", ".join(bad_ingredient_names) if bad_ingredient_names else "없음"
            
            good_matches_str = ""
            if good_matches:
                good_matches_parts = []
                for match in good_matches:
                    good_matches_parts.append(f"- {match['name']}: {match['purpose']}")
                good_matches_str = "\n".join(good_matches_parts)
            else:
                good_matches_str = "없음"
            
            bad_matches_str = ""
            if bad_matches:
                bad_matches_parts = []
                for match in bad_matches:
                    bad_matches_parts.append(f"- {match['name']}: {match['description']}")
                bad_matches_str = "\n".join(bad_matches_parts)
            else:
                bad_matches_str = "없음"
            
            # 종합 분석 프롬프트
            analysis_prompt = f"""당신은 친절하고 전문적인 화장품 성분 분석가입니다.
제공된 데이터를 바탕으로, 사용자를 위한 'AI 종합 분석 리포트'를 자연스러운 서술형 문장으로 작성해주세요.

[지시 사항]
1. [주요 성분 목적]을 보고 이 제품의 핵심 목적과 제품 타입을 추론하여 "이 화장품은(는) [제품 타입/목적]으로 보입니다." 또는 "이 화장품은(는) [핵심 목적]에 중점을 둔 제품으로 보입니다."로 문장을 시작하세요.
2. [좋은 성분 목록]과 [좋은 성분 세부정보]가 있으면, "이 제품에는 [성분명 1], [성분명 2] 등이 포함되어 있어 [주요 효능]에 도움을 줄 수 있습니다." 형식으로 긍정적인 부분을 요약하세요.
3. [주의 성분 목록]과 [주의 성분 세부정보]가 있으면, "다만, [성분명 1] 등은 [주의 이유]을 유발할 수 있으니 [적용 대상] 참고하시기 바랍니다." 형식으로 주의점을 요약하세요.
4. 제품 타입에 따라 적절한 설명을 제공하세요.
5. 긍정/주의 성분이 없다면, 해당 부분을 자연스럽게 생략하거나 "특별히 긍정적인/주의가 필요한 성분은 보이지 않습니다."라고 언급하세요.

[데이터]
- 사용자 피부 타입: {skin_type}
- 전체 성분 리스트: {ingredients_str}
- [{skin_type}]에 좋은 성분 목록: {good_ingredients_str}
- [{skin_type}]에 좋은 성분 세부정보:
{good_matches_str}
- 주의 성분 목록 (일반적 포함): {bad_ingredients_str}
- 주의 성분 세부정보:
{bad_matches_str}
- 참고용 (주요 성분 목적): {common_purposes_str}

[리포트 작성 시작]
"""
            
            # 4. 답변 생성
            analysis_report = self.llm.invoke(analysis_prompt)
            
            # 5. 최종 응답
            return {
                "analysis_report": analysis_report,
                "good_matches": good_matches,
                "bad_matches": bad_matches,
                "success": True
            }
            
        except Exception as e:
            return {
                "analysis_report": f"분석 중 오류가 발생했습니다: {str(e)}",
                "good_matches": [],
                "bad_matches": [],
                "success": False
            }


# ============================================================================
# FastAPI 앱 초기화 및 라우트 정의
# ============================================================================

# FastAPI 앱 생성
app = FastAPI(
    title="화장품 성분 RAG API",
    description="LangChain, ChromaDB, Few-shot 프롬프팅을 활용한 엔터프라이즈급 RAG 시스템",
    version="2.0.0"
)

# CORS 설정 (Android 앱에서 접근 가능하도록)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 프로덕션에서는 구체적인 도메인으로 제한
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 엔터프라이즈급 RAG 시스템 초기화
print("🚀 엔터프라이즈급 RAG 시스템 초기화 시작...")
script_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(script_dir)
ingredients_file = os.path.join(project_root, 'app', 'src', 'main', 'assets', 'ingredients.json')
rag_system = EnterpriseRAG(ingredients_file)


@app.get("/", tags=["Root"])
async def root():
    """루트 엔드포인트"""
    return {
        "message": "화장품 성분 RAG API 서버",
        "version": "2.0.0 (FastAPI)",
        "docs": "/docs",
        "health": "/health"
    }


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    """서버 상태 확인"""
    return HealthResponse(
        status="healthy",
        message="엔터프라이즈급 RAG 서버가 정상 작동 중입니다 (FastAPI)",
        ingredients_count=len(rag_system.ingredients_data),
        features=[
            "FastAPI Framework",
            "LangChain Integration",
            "ChromaDB Vector Store",
            "Chat History Management",
            "Few-shot Prompting",
            "Enterprise-grade RAG Pipeline",
            "Async Processing"
        ]
    )


@app.post("/search", response_model=SearchResponse, tags=["Search"])
async def search_ingredients(request: SearchRequest):
    """LangChain 기반 성분 검색 API (채팅 히스토리 지원)"""
    if not request.query:
        raise HTTPException(status_code=400, detail="검색어를 입력해주세요")
    
    try:
        result = rag_system.search_ingredients(request.query, request.session_id)
        return SearchResponse(**result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")


@app.post("/analyze_product", response_model=AnalyzeProductResponse, tags=["Analysis"])
async def analyze_product(request: AnalyzeProductRequest):
    """
    제품 성분 리스트와 피부 타입을 기반으로 종합 분석 리포트 생성 API
    """
    if not request.ingredients or not isinstance(request.ingredients, list):
        raise HTTPException(status_code=400, detail="ingredients 필드는 성분명 리스트여야 합니다.")
    
    if not request.skin_type or not isinstance(request.skin_type, str):
        raise HTTPException(status_code=400, detail="skin_type 필드는 문자열이어야 합니다.")
    
    try:
        result = rag_system.analyze_product_ingredients(request.ingredients, request.skin_type)
        
        # GoodMatch, BadMatch 객체로 변환
        good_matches_obj = [GoodMatch(**match) for match in result["good_matches"]]
        bad_matches_obj = [BadMatch(**match) for match in result["bad_matches"]]
        
        return AnalyzeProductResponse(
            analysis_report=result["analysis_report"],
            good_matches=good_matches_obj,
            bad_matches=bad_matches_obj,
            success=result["success"]
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")


@app.get("/chat/history", response_model=ChatHistoryResponse, tags=["Chat"])
async def get_chat_history(session_id: str = Query(..., description="세션 ID")):
    """채팅 히스토리 조회 API"""
    if not session_id:
        raise HTTPException(status_code=400, detail="session_id가 필요합니다")
    
    try:
        result = rag_system.get_chat_history(session_id)
        if "error" in result:
            raise HTTPException(status_code=404, detail=result["error"])
        return ChatHistoryResponse(**result)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")


@app.post("/chat/clear", tags=["Chat"])
async def clear_chat_history(request: ClearHistoryRequest):
    """채팅 히스토리 초기화 API"""
    if not request.session_id:
        raise HTTPException(status_code=400, detail="session_id가 필요합니다")
    
    try:
        result = rag_system.clear_chat_history(request.session_id)
        if "error" in result:
            raise HTTPException(status_code=404, detail=result["error"])
        return result
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")


@app.get("/ingredients", tags=["Ingredients"])
async def get_all_ingredients():
    """모든 성분 목록 조회"""
    try:
        ingredients = []
        for item in rag_system.ingredients_data:
            kor_name = item.get('INGR_KOR_NAME', '')
            eng_name = item.get('INGR_ENG_NAME', '')
            if kor_name and eng_name:
                ingredients.append(f"{kor_name} ({eng_name})")
            elif kor_name:
                ingredients.append(kor_name)
            elif eng_name:
                ingredients.append(eng_name)
        return {
            'ingredients': ingredients,
            'count': len(ingredients),
            'success': True
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")


@app.get("/sessions", tags=["Sessions"])
async def get_active_sessions():
    """활성 세션 목록 조회"""
    try:
        sessions = []
        for session_id, memory in rag_system.chat_sessions.items():
            sessions.append({
                'session_id': session_id,
                'message_count': len(memory.messages),
                'last_activity': datetime.now().isoformat()
            })
        
        return {
            'sessions': sessions,
            'total_sessions': len(sessions),
            'success': True
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")


@app.get("/rag/info", tags=["RAG System"])
async def get_rag_info():
    """RAG 시스템 정보 조회"""
    try:
        return {
            'system_type': 'Enterprise RAG Pipeline',
            'framework': 'FastAPI',
            'components': {
                'vector_store': 'ChromaDB',
                'embedding_model': 'sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2',
                'llm': 'MockLLM (Demo)',
                'framework': 'LangChain',
                'memory': 'ConversationBufferMemory',
                'prompting': 'Few-shot Prompting'
            },
            'features': [
                'Async Processing',
                'Vector Database Integration',
                'Chat History Management',
                'Few-shot Learning',
                'Context-aware Responses',
                'Session Management',
                'Auto API Documentation'
            ],
            'success': True
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"서버 오류: {str(e)}")


if __name__ == '__main__':
    import uvicorn
    
    print("🚀 엔터프라이즈급 화장품 성분 RAG 챗봇 서버 시작 (FastAPI)")
    print("📱 Android 앱에서 http://localhost:5000 으로 접속하세요")
    print("📚 API 문서: http://localhost:5000/docs (Swagger UI)")
    print("📊 대체 문서: http://localhost:5000/redoc (ReDoc)")
    print("🔧 주요 기능:")
    print("  - FastAPI 프레임워크 (비동기 처리)")
    print("  - LangChain 기반 RAG 파이프라인")
    print("  - ChromaDB 벡터 데이터베이스")
    print("  - 채팅 히스토리 관리")
    print("  - Few-shot 프롬프팅")
    print("  - 세션 관리")
    print("  - 제품 종합 분석 기능")
    print("  - 자동 API 문서화")
    print("📊 API 엔드포인트:")
    print("  - GET  / - 루트")
    print("  - GET  /health - 서버 상태 확인")
    print("  - POST /search - 성분 검색 (채팅 히스토리 지원)")
    print("  - POST /analyze_product - 제품 성분 종합 분석")
    print("  - GET  /chat/history?session_id=<id> - 채팅 히스토리 조회")
    print("  - POST /chat/clear - 채팅 히스토리 초기화")
    print("  - GET  /sessions - 활성 세션 목록")
    print("  - GET  /ingredients - 전체 성분 목록")
    print("  - GET  /rag/info - RAG 시스템 정보")
    
    uvicorn.run(app, host="0.0.0.0", port=5000, log_level="info")

