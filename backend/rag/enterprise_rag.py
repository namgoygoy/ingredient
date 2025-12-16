"""
Enterprise RAG 클래스
엔터프라이즈급 RAG 시스템 구현
"""

import logging
from typing import List, Dict
from collections import Counter

import sys
import os
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from rag.data_loader import DataLoader
from rag.vector_store import VectorStore
from rag.ingredient_search import IngredientSearch
from llm.mock_llm import MockLLM

logger = logging.getLogger(__name__)


class EnterpriseRAG:
    """
    엔터프라이즈급 RAG 시스템 클래스
    
    Supabase PostgreSQL과 ChromaDB를 결합한 하이브리드 RAG 시스템입니다.
    Supabase 연결 실패 시 JSON 파일로 폴백합니다.
    
    주요 기능:
    - 성분 검색 (Supabase 직접 검색 또는 벡터 검색)
    - 제품 성분 분석 (피부 타입 기반)
    - 채팅 세션 관리
    
    Args:
        data_file: JSON 폴백 파일 경로
        persist_directory: ChromaDB 벡터 스토어 저장 디렉토리
    """
    
    def __init__(self, data_file: str, persist_directory: str = "./chroma_db_ingredients"):
        """
        RAG 시스템 초기화
        
        Args:
            data_file: JSON 폴백 파일 경로
            persist_directory: ChromaDB 벡터 스토어 저장 디렉토리
        """
        self.data_file = data_file
        self.persist_directory = persist_directory
        
        # 데이터 로더 초기화
        self.data_loader = DataLoader(data_file)
        self.use_supabase = self.data_loader.use_supabase
        
        # LangChain 컴포넌트
        self.llm = MockLLM()
        
        # 벡터 스토어 초기화
        self.vector_store = VectorStore(
            self.data_loader.ingredients_data,
            persist_directory
        )
        
        # 성분 검색 초기화
        from rag.memory import ConversationManager
        self.conversation_manager = ConversationManager()
        self.ingredient_search = IngredientSearch(
            self.use_supabase,
            self.vector_store,
            self.conversation_manager
        )
        
        logger.info("✅ RAG 시스템 초기화 완료")
    
    def get_data_source(self) -> str:
        """
        현재 사용 중인 데이터 소스를 반환합니다.
        
        Returns:
            "supabase" 또는 "json"
        """
        return self.data_loader.get_data_source()
    
    def get_ingredients_count(self) -> int:
        """
        저장된 성분 개수를 반환합니다.
        
        Returns:
            성분 개수
        """
        return self.data_loader.get_ingredients_count()
    
    def search_ingredients(self, query: str, session_id: str = None, top_k: int = 3) -> Dict:
        """
        성분을 검색합니다.
        
        Args:
            query: 검색 쿼리 (성분명 또는 질문)
            session_id: 채팅 세션 ID (선택적)
            top_k: 반환할 최대 결과 개수 (기본값: 3)
        
        Returns:
            검색 결과 딕셔너리
        """
        return self.ingredient_search.search(query, session_id, top_k)
    
    def analyze_product_ingredients(self, ingredients: List[str], skin_type: str) -> Dict:
        """
        제품의 성분을 분석합니다.
        
        사용자의 피부 타입을 기반으로 각 성분이 좋은 성분인지 주의 성분인지 판단합니다.
        
        Args:
            ingredients: 분석할 성분명 리스트
            skin_type: 사용자 피부 타입 (예: "건성, 민감성")
        
        Returns:
            분석 결과 딕셔너리
        """
        try:
            # 성분 정보 조회
            ingredient_info_map = self.data_loader.get_ingredients_by_names(ingredients)
            
            if not ingredient_info_map:
                logger.warning(f"성분 정보를 찾을 수 없습니다: {ingredients}")
                return {
                    "analysis_report": "성분 정보를 찾을 수 없어 분석할 수 없습니다.",
                    "good_matches": [],
                    "bad_matches": [],
                    "success": False
                }
            
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
            
        except KeyError as e:
            logger.error(f"분석 중 키 오류 (필수 필드 누락): {e}", exc_info=True)
            return {
                "analysis_report": "성분 데이터 형식 오류로 분석할 수 없습니다.",
                "good_matches": [],
                "bad_matches": [],
                "success": False
            }
        except ValueError as e:
            logger.error(f"분석 중 값 오류: {e}", exc_info=True)
            return {
                "analysis_report": "입력 데이터 형식 오류로 분석할 수 없습니다.",
                "good_matches": [],
                "bad_matches": [],
                "success": False
            }
        except Exception as e:
            logger.error(f"분석 중 예상치 못한 오류: {e}", exc_info=True)
            return {
                "analysis_report": f"분석 중 오류가 발생했습니다: {str(e)}",
                "good_matches": [],
                "bad_matches": [],
                "success": False
            }

