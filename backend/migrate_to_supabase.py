#!/usr/bin/env python3
"""
ingredients.json → Supabase PostgreSQL 마이그레이션 스크립트
사용법: python migrate_to_supabase.py
"""

import json
import logging
import os
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

def load_ingredients_json():
    """ingredients.json 파일 로드"""
    # 프로젝트 루트 기준 경로
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    json_path = os.path.join(project_root, 'app', 'src', 'main', 'assets', 'ingredients.json')
    
    logger.info(f"📂 JSON 파일 로드: {json_path}")
    
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    logger.info(f"✅ {len(data)}개 성분 로드 완료")
    return data

def transform_ingredient(item):
    """JSON 항목을 Supabase 테이블 형식으로 변환"""
    kor_name = item.get("INGR_KOR_NAME", "")
    eng_name = item.get("INGR_ENG_NAME", "")
    
    # kor_name이 없으면 eng_name을 사용 (NOT NULL 제약 충족)
    if not kor_name or kor_name.strip() == "":
        kor_name = eng_name if eng_name else "Unknown"
    
    return {
        "kor_name": kor_name,
        "eng_name": eng_name if eng_name else "",
        "description": item.get("description", "") or "",
        "purpose": item.get("purpose") if item.get("purpose") else [],
        "good_for": item.get("good_for") if item.get("good_for") else [],
        "bad_for": item.get("bad_for") if item.get("bad_for") else []
    }

def migrate_to_supabase():
    """Supabase로 데이터 마이그레이션"""
    
    # Supabase 클라이언트 생성
    if not SUPABASE_URL or not SUPABASE_KEY:
        logger.error("❌ 환경변수가 설정되지 않았습니다!")
        logger.error("   .env 파일에 SUPABASE_URL과 SUPABASE_KEY를 설정하세요.")
        return False
    
    logger.info(f"🔗 Supabase 연결: {SUPABASE_URL[:30]}...")
    supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)
    
    # JSON 데이터 로드
    ingredients = load_ingredients_json()
    
    # 기존 데이터 삭제 (선택사항)
    logger.info("🗑️ 기존 데이터 삭제 중...")
    try:
        supabase.table("ingredients").delete().neq("id", 0).execute()
        logger.info("✅ 기존 데이터 삭제 완료")
    except Exception as e:
        logger.warning(f"⚠️ 기존 데이터 삭제 실패 (테이블이 비어있을 수 있음): {e}")
    
    # 배치 삽입 (100개씩)
    batch_size = 100
    total = len(ingredients)
    
    logger.info(f"📤 {total}개 성분 업로드 시작...")
    
    for i in range(0, total, batch_size):
        batch = ingredients[i:i+batch_size]
        transformed_batch = [transform_ingredient(item) for item in batch]
        
        try:
            supabase.table("ingredients").insert(transformed_batch).execute()
            logger.info(f"   ✅ {min(i+batch_size, total)}/{total} 완료")
        except Exception as e:
            logger.error(f"   ❌ 배치 {i//batch_size + 1} 실패: {e}")
            # 개별 삽입 시도
            for j, item in enumerate(transformed_batch):
                try:
                    supabase.table("ingredients").insert(item).execute()
                except Exception as e2:
                    logger.error(f"      ❌ 항목 실패: {item.get('kor_name', 'unknown')} - {e2}")
    
    # 결과 확인
    result = supabase.table("ingredients").select("id", count="exact").execute()
    count = result.count if hasattr(result, 'count') else len(result.data)
    logger.info(f"\n🎉 마이그레이션 완료! 총 {count}개 성분이 Supabase에 저장되었습니다.")
    
    return True

def test_connection():
    """Supabase 연결 테스트"""
    if not SUPABASE_URL or not SUPABASE_KEY:
        logger.error("❌ 환경변수가 설정되지 않았습니다!")
        return False
    
    try:
        supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)
        result = supabase.table("ingredients").select("*").limit(1).execute()
        logger.info(f"✅ Supabase 연결 성공! 샘플 데이터: {result.data}")
        return True
    except Exception as e:
        logger.error(f"❌ Supabase 연결 실패: {e}", exc_info=True)
        return False

if __name__ == "__main__":
    logger.info("=" * 60)
    logger.info("🚀 Supabase 마이그레이션 스크립트")
    logger.info("=" * 60)
    
    # 연결 테스트
    if test_connection():
        logger.info("\n연결 테스트 성공, 마이그레이션을 시작합니다...\n")
        migrate_to_supabase()
    else:
        logger.warning("\n⚠️ 먼저 Supabase 설정을 완료하세요:")
        logger.warning("   1. https://supabase.com 에서 프로젝트 생성")
        logger.warning("   2. SQL Editor에서 SUPABASE_SETUP.sql 실행")
        logger.warning("   3. .env 파일에 SUPABASE_URL, SUPABASE_KEY 설정")

