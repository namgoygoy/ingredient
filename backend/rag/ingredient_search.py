"""
성분 검색 모듈
Supabase 및 벡터 검색을 통한 성분 검색
"""

import logging
from typing import Dict, List

import sys
import os
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from supabase_client import search_ingredients as supabase_search_ingredients
from rag.vector_store import VectorStore
from rag.memory import ConversationManager

logger = logging.getLogger(__name__)


class IngredientSearch:
    """
    성분 검색 클래스
    
    Supabase 직접 검색 또는 벡터 검색을 통해 성분을 검색합니다.
    """
    
    def __init__(self, use_supabase: bool, vector_store: VectorStore = None, conversation_manager: ConversationManager = None):
        """
        성분 검색 초기화
        
        Args:
            use_supabase: Supabase 사용 여부
            vector_store: 벡터 스토어 (폴백용)
            conversation_manager: 대화 관리자
        """
        self.use_supabase = use_supabase
        self.vector_store = vector_store
        self.conversation_manager = conversation_manager or ConversationManager()
    
    def search(self, query: str, session_id: str = None, top_k: int = 3) -> Dict:
        """
        성분을 검색합니다.
        
        검색 방식:
        1. Supabase 사용 시: 직접 SQL 쿼리로 검색 (빠름)
        2. Supabase 미사용 시: ChromaDB 벡터 검색 (폴백)
        
        Args:
            query: 검색 쿼리 (성분명 또는 질문)
            session_id: 채팅 세션 ID (선택적)
            top_k: 반환할 최대 결과 개수 (기본값: 3)
        
        Returns:
            검색 결과 딕셔너리
        """
        session_id = self.conversation_manager.get_or_create_session(session_id)
        memory = self.conversation_manager.get_session(session_id)
        
        try:
            # Supabase 직접 검색
            if self.use_supabase:
                return self._search_supabase(query, memory, session_id, top_k)
            
            # 벡터 검색 폴백
            if self.vector_store:
                return self._search_vector(query, memory, session_id, top_k)
            
            return {
                "query": query,
                "answer": "해당 성분에 대한 정보를 찾을 수 없습니다.",
                "similar_ingredients": [],
                "session_id": session_id,
                "chat_history": [],
                "success": False
            }
            
        except Exception as e:
            logger.error(f"검색 중 오류: {e}", exc_info=True)
            return {
                "query": query,
                "answer": f"검색 중 오류: {str(e)}",
                "similar_ingredients": [],
                "session_id": session_id,
                "chat_history": [],
                "success": False
            }
    
    def _search_supabase(self, query: str, memory, session_id: str, top_k: int) -> Dict:
        """
        Supabase 직접 검색
        
        Args:
            query: 검색 쿼리
            memory: 대화 메모리
            session_id: 세션 ID
            top_k: 반환할 최대 결과 개수
        
        Returns:
            검색 결과 딕셔너리
        """
        try:
            db_results = supabase_search_ingredients(query, limit=top_k)
        except Exception as e:
            logger.error(f"Supabase 검색 오류 (query: {query}): {e}", exc_info=True)
            return {
                "query": query,
                "answer": "데이터베이스 검색 중 오류가 발생했습니다.",
                "similar_ingredients": [],
                "session_id": session_id,
                "chat_history": [],
                "success": False
            }
        
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
        
        return {
            "query": query,
            "answer": "해당 성분에 대한 정보를 찾을 수 없습니다.",
            "similar_ingredients": [],
            "session_id": session_id,
            "chat_history": [],
            "success": False
        }
    
    def _search_vector(self, query: str, memory, session_id: str, top_k: int) -> Dict:
        """
        벡터 검색
        
        Args:
            query: 검색 쿼리
            memory: 대화 메모리
            session_id: 세션 ID
            top_k: 반환할 최대 결과 개수
        
        Returns:
            검색 결과 딕셔너리
        """
        try:
            docs = self.vector_store.search(query, top_k)
        except Exception as e:
            logger.error(f"벡터 검색 오류 (query: {query}): {e}", exc_info=True)
            return {
                "query": query,
                "answer": "벡터 검색 중 오류가 발생했습니다.",
                "similar_ingredients": [],
                "session_id": session_id,
                "chat_history": [],
                "success": False
            }
        
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

