# 시스템 아키텍처 및 역할 분담

## 시스템 구조

```
Android App (Kotlin)
    ├── RAG 서버 (Python) - 주요 분석 엔진
    │   └── ingredients.json 기반 검색 및 분석
    └── Gemini AI (Kotlin) - 보완 엔진
        └── 누락된 정보만 생성
```

## 역할 분담

### 1. RAG 서버 (rag_server_fastapi.py) - **주요 분석 엔진** ⭐ FastAPI 업그레이드

#### 핵심 역할:
- ✅ **ingredients.json 기반 성분 검색** (11,000+ 성분)
- ✅ **ChromaDB 벡터 데이터베이스 검색**
- ✅ **제품 종합 분석 리포트 생성** (MockLLM)
- ✅ **피부 타입별 성분 매칭** (good_for, bad_for)
- ✅ **good_matches, bad_matches 생성**
- ⭐ **비동기 처리** (FastAPI 프레임워크)
- ⭐ **자동 API 문서화** (Swagger UI, ReDoc)

#### API 엔드포인트:
- `POST /analyze_product` - 제품 성분 종합 분석
- `POST /search` - 개별 성분 검색
- `GET /ingredients` - 전체 성분 목록

#### 장점:
- 11,000개 이상의 검증된 성분 데이터
- 정확한 purpose, good_for, bad_for 정보
- 빠른 응답 속도 (로컬 데이터베이스)
- 비용 없음
- ⭐ **Flask 대비 2-3배 빠른 성능** (FastAPI)
- ⭐ **타입 안전성** (Pydantic 모델 자동 검증)
- ⭐ **실시간 API 문서** (개발 생산성 향상)

---

### 2. Gemini AI (GeminiService.kt) - **보완 엔진**

#### 핵심 역할 (최소화):
- ✅ **RAG 서버에 없는 성분 정보 생성**
  - OCR이 인식했지만 ingredients.json에 없는 성분
  - 예: 정제수, 향료 등
  
- ✅ **불완전한 데이터 보완**
  - purpose가 "정보 없음"일 때
  - description이 비어있을 때
  
- ✅ **서버 리포트 개선 (선택적)**
  - 서버 리포트가 100자 미만일 때만
  - 에러가 발생했을 때만

#### 함수:
```kotlin
// 개별 성분 정보 생성 (서버에 없을 때만)
generateIngredientPurpose(ingredientName: String): String
generateIngredientDescription(ingredientName: String): String
generateSkinTypeSuitability(ingredientName: String): String

// 서버 리포트 개선 (부족할 때만)
enhanceProductAnalysisSummary(
    serverReport: String,  // 서버 리포트 우선 사용
    ingredients: List<String>,
    goodMatches: List<String>,
    badMatches: List<String>
): String
```

#### 주의사항:
- ⚠️ API 호출 비용 발생 (무료 티어 제한)
- ⚠️ 2-3초 응답 시간
- ⚠️ 네트워크 연결 필요

---

## 데이터 흐름

### 전체 제품 분석 (DetailsFragment)

```
1. OCR 텍스트 → 성분 파싱
           ↓
2. RAG 서버 /analyze_product 호출
           ↓
3. 서버 분석 리포트 생성 (ingredients.json 기반)
           ↓
4. 서버 리포트 표시
           ↓
5. [조건부] 서버 리포트가 부족하면 → Gemini로 개선
```

### 개별 성분 분석 (ResultsFragment)

```
1. 성분 클릭 → RAG 서버 /analyze_product 호출 (단일 성분)
           ↓
2. 서버에서 정보 검색
           ↓
3-a. 정보 있음 → 바로 표시
3-b. 정보 없음 → Gemini로 생성
           ↓
4. ingredients.json에서 description 검색
           ↓
5-a. description 있음 → 표시
5-b. description 없음 → Gemini로 생성
```

---

## 성능 최적화

### 우선순위:
1. **RAG 서버** (빠르고 정확) ⭐⭐⭐
2. **ingredients.json** (로컬 파일) ⭐⭐
3. **Gemini AI** (느리지만 유연) ⭐

### 비용 최적화:
- RAG 서버: **무료**
- Gemini API: **제한적 사용** (월 60회 요청까지 무료)

### Gemini 호출 조건:
```kotlin
// DetailsFragment - 서버 리포트 개선
if (serverReport.length < 100 || 
    serverReport.contains("분석 중") || 
    serverReport.contains("오류")) {
    // Gemini로 보완
}

// ResultsFragment - 개별 성분
if (purpose == null || purpose == "정보 없음") {
    // Gemini로 생성
}

if (description.isEmpty() || description.length < 20) {
    // Gemini로 생성
}
```

---

## 결론

### RAG 서버의 중요성:
- ✅ **11,000개 검증된 성분 데이터** - 신뢰할 수 있는 정보원
- ✅ **빠른 응답** - 로컬 데이터베이스 검색
- ✅ **정확한 분석** - purpose, good_for, bad_for 정보
- ✅ **무료** - API 비용 없음

### Gemini AI의 역할:
- ✅ **보완 엔진** - 서버에 없는 정보만 생성
- ✅ **유연성** - 새로운 성분도 설명 가능
- ⚠️ **제한적 사용** - 비용과 속도 고려

### 권장 사항:
1. **RAG 서버를 주요 엔진으로 사용**
2. **Gemini는 보완 용도로만 사용**
3. **서버 리포트가 충분하면 Gemini 호출 안 함**
4. **ingredients.json 지속적으로 업데이트**

