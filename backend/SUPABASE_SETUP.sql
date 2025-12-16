-- ============================================================
-- Supabase 테이블 스키마 (복사하여 Supabase SQL Editor에 붙여넣기)
-- ============================================================

-- 1. 성분 테이블 생성
CREATE TABLE IF NOT EXISTS ingredients (
    id SERIAL PRIMARY KEY,
    kor_name VARCHAR(255) NOT NULL,
    eng_name VARCHAR(255),
    description TEXT,
    purpose TEXT[],           -- PostgreSQL 배열: ['moisturizer', 'exfoliant']
    good_for TEXT[],          -- ['dry', 'sensitive', 'acne']
    bad_for TEXT[],           -- ['oily', 'acne-prone']
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. 인덱스 생성 (검색 성능 향상)
CREATE INDEX IF NOT EXISTS idx_ingredients_kor_name ON ingredients(kor_name);
CREATE INDEX IF NOT EXISTS idx_ingredients_eng_name ON ingredients(eng_name);
CREATE INDEX IF NOT EXISTS idx_ingredients_good_for ON ingredients USING GIN(good_for);
CREATE INDEX IF NOT EXISTS idx_ingredients_bad_for ON ingredients USING GIN(bad_for);

-- 3. 업데이트 시간 자동 갱신 함수
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 4. 트리거 생성
DROP TRIGGER IF EXISTS update_ingredients_updated_at ON ingredients;
CREATE TRIGGER update_ingredients_updated_at
    BEFORE UPDATE ON ingredients
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 5. Row Level Security (RLS) 비활성화 (개발용)
-- 프로덕션에서는 활성화 권장
ALTER TABLE ingredients DISABLE ROW LEVEL SECURITY;

-- 6. 성분명으로 검색하는 함수
CREATE OR REPLACE FUNCTION search_ingredients(search_term TEXT)
RETURNS SETOF ingredients AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM ingredients
    WHERE 
        kor_name ILIKE '%' || search_term || '%'
        OR eng_name ILIKE '%' || search_term || '%'
    ORDER BY 
        CASE 
            WHEN kor_name ILIKE search_term THEN 1
            WHEN eng_name ILIKE search_term THEN 2
            WHEN kor_name ILIKE search_term || '%' THEN 3
            WHEN eng_name ILIKE search_term || '%' THEN 4
            ELSE 5
        END
    LIMIT 10;
END;
$$ LANGUAGE plpgsql;

-- 7. 피부 타입별 좋은 성분 검색 함수
CREATE OR REPLACE FUNCTION get_good_ingredients_for_skin_type(skin_type TEXT)
RETURNS SETOF ingredients AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM ingredients
    WHERE skin_type = ANY(good_for);
END;
$$ LANGUAGE plpgsql;

-- 8. 피부 타입별 주의 성분 검색 함수
CREATE OR REPLACE FUNCTION get_bad_ingredients_for_skin_type(skin_type TEXT)
RETURNS SETOF ingredients AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM ingredients
    WHERE skin_type = ANY(bad_for);
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 설정 완료 확인 쿼리
-- ============================================================
-- SELECT COUNT(*) FROM ingredients;

