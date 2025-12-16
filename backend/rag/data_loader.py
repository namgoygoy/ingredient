"""
데이터 로더 모듈
Supabase 및 JSON 파일에서 성분 데이터 로드
"""

import json
import logging
from typing import List, Dict

from supabase_client import (
    is_supabase_available,
    get_all_ingredients,
    get_ingredients_by_names
)

logger = logging.getLogger(__name__)


class DataLoader:
    """
    성분 데이터 로더 클래스
    
    Supabase 또는 JSON 파일에서 성분 데이터를 로드합니다.
    Supabase 연결 실패 시 JSON 파일로 폴백합니다.
    """
    
    def __init__(self, data_file: str):
        """
        데이터 로더 초기화
        
        Args:
            data_file: JSON 폴백 파일 경로
        """
        self.data_file = data_file
        self.use_supabase = False
        self.ingredients_data = []
        # 인덱스 캐시 (효율성 개선: O(1) 검색을 위해)
        self._kor_index = None
        self._eng_index = None
        self._load_data()
    
    def _load_data(self):
        """
        데이터를 로드합니다.
        
        우선순위:
        1. Supabase PostgreSQL
        2. JSON 파일 (폴백)
        """
        if is_supabase_available():
            logger.info("✅ Supabase 연결 성공!")
            self.use_supabase = True
            self.ingredients_data = get_all_ingredients()
            logger.info(f"📊 Supabase에서 {len(self.ingredients_data)}개 성분 로드")
        else:
            logger.warning("⚠️ Supabase 연결 실패, JSON 파일 사용")
            self.use_supabase = False
            self._load_json_data()
    
    def _load_json_data(self):
        """
        JSON 파일에서 성분 데이터를 로드합니다.
        
        Supabase 연결 실패 시 폴백으로 사용됩니다.
        JSON 형식을 Supabase 형식으로 변환하여 저장합니다.
        
        변환 규칙:
        - INGR_KOR_NAME → kor_name
        - INGR_ENG_NAME → eng_name
        - description → description
        - purpose → purpose (리스트로 변환)
        - good_for → good_for (리스트로 변환)
        - bad_for → bad_for (리스트로 변환)
        
        Raises:
            FileNotFoundError: JSON 파일이 없을 경우
            json.JSONDecodeError: JSON 파싱 오류
            IOError: 파일 읽기 오류
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
            # 인덱스 생성 (효율성 개선: O(1) 검색)
            self._build_indexes()
        except FileNotFoundError as e:
            logger.error(f"❌ JSON 파일을 찾을 수 없습니다: {self.data_file}", exc_info=True)
            self.ingredients_data = []
        except json.JSONDecodeError as e:
            logger.error(f"❌ JSON 파싱 오류: {e}", exc_info=True)
            self.ingredients_data = []
        except IOError as e:
            logger.error(f"❌ 파일 읽기 오류: {e}", exc_info=True)
            self.ingredients_data = []
        except Exception as e:
            logger.error(f"❌ JSON 로드 실패 (예상치 못한 오류): {e}", exc_info=True)
            self.ingredients_data = []
    
    def _build_indexes(self):
        """
        검색 인덱스를 생성합니다.
        
        효율성 개선: O(n) 선형 검색 대신 O(1) 해시 테이블 검색 사용
        인덱스는 한 번만 생성하고 재사용합니다.
        """
        if self._kor_index is not None and self._eng_index is not None:
            return  # 이미 인덱스가 생성되어 있음
        
        self._kor_index = {}
        self._eng_index = {}
        
        for item in self.ingredients_data:
            kor_name = item.get('kor_name', '')
            eng_name = item.get('eng_name', '')
            
            if kor_name:
                normalized_kor = kor_name.lower().replace(" ", "")
                self._kor_index[normalized_kor] = item
            
            if eng_name:
                normalized_eng = eng_name.lower().replace(" ", "")
                self._eng_index[normalized_eng] = item
        
        logger.debug(f"인덱스 생성 완료: 한국어 {len(self._kor_index)}개, 영어 {len(self._eng_index)}개")
    
    def get_ingredients_by_names(self, names: List[str]) -> Dict[str, Dict]:
        """
        여러 성분명으로 일괄 검색
        
        Args:
            names: 검색할 성분명 리스트
        
        Returns:
            성분명 → 성분 정보 딕셔너리 매핑
        
        Raises:
            Exception: 검색 중 오류 발생 시 (빈 딕셔너리 반환)
        """
        try:
            if self.use_supabase:
                return get_ingredients_by_names(names)
            else:
                return self._get_ingredients_from_local(names)
        except Exception as e:
            logger.error(f"성분 검색 오류 (names: {names}): {e}", exc_info=True)
            return {}  # 빈 딕셔너리 반환하여 상위에서 처리 가능하도록
    
    def _get_ingredients_from_local(self, names: List[str]) -> Dict[str, Dict]:
        """
        로컬 데이터(JSON)에서 성분을 검색합니다.
        
        효율성 개선:
        - 인덱스를 한 번만 생성하고 재사용 (O(1) 검색)
        - 이전: 매번 O(n) 선형 검색으로 인덱스 재생성
        - 개선: 초기화 시 한 번만 인덱스 생성, 이후 O(1) 검색
        
        검색 방식:
        1. 정확 매칭: 한국어 이름 또는 영어 이름으로 정확히 일치 (O(1))
        2. 부분 매칭: 정확 매칭이 없으면 부분 문자열로 검색 (O(n), 최후의 수단)
        
        Args:
            names: 검색할 성분명 리스트
        
        Returns:
            성분명 → 성분 정보 딕셔너리 매핑
        """
        # 인덱스가 없으면 생성
        if self._kor_index is None or self._eng_index is None:
            self._build_indexes()
        
        result_map = {}
        
        for name in names:
            normalized = name.strip().lower().replace(" ", "")
            
            # 정확 매칭 (O(1))
            if normalized in self._kor_index:
                result_map[name] = self._kor_index[normalized]
                continue
            elif normalized in self._eng_index:
                result_map[name] = self._eng_index[normalized]
                continue
            
            # 부분 매칭 (O(n), 최후의 수단)
            # 정확 매칭이 없을 때만 부분 매칭 시도
            found = False
            for kor_name, item in self._kor_index.items():
                if normalized in kor_name or kor_name in normalized:
                    result_map[name] = item
                    found = True
                    break
            
            if not found:
                for eng_name, item in self._eng_index.items():
                    if normalized in eng_name or eng_name in normalized:
                        result_map[name] = item
                        break
        
        return result_map
    
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
            from supabase_client import get_ingredients_count
            return get_ingredients_count()
        return len(self.ingredients_data)

