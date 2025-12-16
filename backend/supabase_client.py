"""
Supabase 클라이언트 모듈
PostgreSQL 데이터베이스 연결 및 쿼리 함수
"""

import logging
import os
from typing import List, Dict, Optional, Any
from supabase import create_client, Client
from dotenv import load_dotenv

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# .env 파일 로드
load_dotenv()

# Supabase 설정
SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

# 전역 클라이언트 (싱글톤)
_supabase_client: Optional[Client] = None


def get_supabase_client() -> Optional[Client]:
    """Supabase 클라이언트 싱글톤 반환"""
    global _supabase_client
    
    if _supabase_client is None:
        if SUPABASE_URL and SUPABASE_KEY:
            try:
                _supabase_client = create_client(SUPABASE_URL, SUPABASE_KEY)
                logger.info("✅ Supabase 클라이언트 초기화 완료")
            except Exception as e:
                logger.error(f"❌ Supabase 클라이언트 초기화 실패: {e}", exc_info=True)
                return None
        else:
            logger.warning("⚠️ Supabase 환경변수가 설정되지 않았습니다. JSON 폴백 모드 사용")
            return None
    
    return _supabase_client


def is_supabase_available() -> bool:
    """Supabase 연결 가능 여부 확인"""
    return get_supabase_client() is not None


# ============================================================
# 성분 검색 함수들
# ============================================================

def get_ingredient_by_name(name: str) -> Optional[Dict]:
    """성분명으로 정확한 매칭 검색"""
    client = get_supabase_client()
    if not client:
        return None
    
    try:
        # 한국어 이름으로 검색
        result = client.table("ingredients") \
            .select("*") \
            .ilike("kor_name", name.strip()) \
            .limit(1) \
            .execute()
        
        if result.data:
            return result.data[0]
        
        # 영어 이름으로 검색
        result = client.table("ingredients") \
            .select("*") \
            .ilike("eng_name", name.strip()) \
            .limit(1) \
            .execute()
        
        if result.data:
            return result.data[0]
        
        return None
    except Exception as e:
        logger.error(f"❌ 성분 검색 오류: {e}", exc_info=True)
        return None


def search_ingredients(query: str, limit: int = 10) -> List[Dict]:
    """성분명 부분 매칭 검색"""
    client = get_supabase_client()
    if not client:
        return []
    
    try:
        result = client.table("ingredients") \
            .select("*") \
            .or_(f"kor_name.ilike.%{query}%,eng_name.ilike.%{query}%") \
            .limit(limit) \
            .execute()
        
        return result.data if result.data else []
    except Exception as e:
        logger.error(f"❌ 성분 검색 오류: {e}", exc_info=True)
        return []


def get_ingredients_by_names(names: List[str]) -> Dict[str, Dict]:
    """여러 성분명으로 일괄 검색 (성능 최적화)"""
    client = get_supabase_client()
    if not client:
        return {}
    
    result_map = {}
    
    try:
        # 모든 성분 한 번에 조회 (OR 조건)
        all_ingredients = client.table("ingredients") \
            .select("*") \
            .execute()
        
        if not all_ingredients.data:
            return {}
        
        # 이름으로 인덱싱
        kor_index = {item['kor_name'].lower().replace(" ", ""): item for item in all_ingredients.data if item.get('kor_name')}
        eng_index = {item['eng_name'].lower().replace(" ", ""): item for item in all_ingredients.data if item.get('eng_name')}
        
        # 각 이름에 대해 매칭
        for name in names:
            normalized = name.strip().lower().replace(" ", "")
            
            if normalized in kor_index:
                result_map[name] = kor_index[normalized]
            elif normalized in eng_index:
                result_map[name] = eng_index[normalized]
            else:
                # 부분 매칭 시도
                for kor_name, item in kor_index.items():
                    if normalized in kor_name or kor_name in normalized:
                        result_map[name] = item
                        break
                else:
                    for eng_name, item in eng_index.items():
                        if normalized in eng_name or eng_name in normalized:
                            result_map[name] = item
                            break
        
        return result_map
    except Exception as e:
        logger.error(f"❌ 일괄 검색 오류: {e}", exc_info=True)
        return {}


def get_all_ingredients() -> List[Dict]:
    """모든 성분 조회"""
    client = get_supabase_client()
    if not client:
        return []
    
    try:
        result = client.table("ingredients") \
            .select("*") \
            .execute()
        
        return result.data if result.data else []
    except Exception as e:
        logger.error(f"❌ 전체 성분 조회 오류: {e}", exc_info=True)
        return []


def get_ingredients_count() -> int:
    """성분 개수 조회"""
    client = get_supabase_client()
    if not client:
        return 0
    
    try:
        result = client.table("ingredients") \
            .select("id", count="exact") \
            .execute()
        
        return result.count if hasattr(result, 'count') else len(result.data)
    except Exception as e:
        logger.error(f"❌ 성분 개수 조회 오류: {e}", exc_info=True)
        return 0


def get_good_ingredients_for_skin_type(skin_type: str) -> List[Dict]:
    """특정 피부 타입에 좋은 성분 조회"""
    client = get_supabase_client()
    if not client:
        return []
    
    try:
        result = client.table("ingredients") \
            .select("*") \
            .contains("good_for", [skin_type]) \
            .execute()
        
        return result.data if result.data else []
    except Exception as e:
        logger.error(f"❌ 피부 타입별 좋은 성분 조회 오류: {e}", exc_info=True)
        return []


def get_bad_ingredients_for_skin_type(skin_type: str) -> List[Dict]:
    """특정 피부 타입에 나쁜 성분 조회"""
    client = get_supabase_client()
    if not client:
        return []
    
    try:
        result = client.table("ingredients") \
            .select("*") \
            .contains("bad_for", [skin_type]) \
            .execute()
        
        return result.data if result.data else []
    except Exception as e:
        logger.error(f"❌ 피부 타입별 나쁜 성분 조회 오류: {e}", exc_info=True)
        return []


# ============================================================
# 테스트 함수
# ============================================================

def test_supabase_connection():
    """Supabase 연결 테스트"""
    client = get_supabase_client()
    if not client:
        return {
            "success": False,
            "message": "Supabase 클라이언트 초기화 실패"
        }
    
    try:
        count = get_ingredients_count()
        sample = search_ingredients("글리세린", limit=1)
        
        return {
            "success": True,
            "message": f"Supabase 연결 성공! {count}개 성분 저장됨",
            "sample": sample[0] if sample else None
        }
    except Exception as e:
        return {
            "success": False,
            "message": f"Supabase 테스트 실패: {e}"
        }


if __name__ == "__main__":
    logger.info("=" * 60)
    logger.info("🧪 Supabase 연결 테스트")
    logger.info("=" * 60)
    
    result = test_supabase_connection()
    logger.info(f"\n결과: {result}")

