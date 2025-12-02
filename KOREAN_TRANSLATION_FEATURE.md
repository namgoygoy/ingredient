# 성분 설명 한국어 번역 기능

## 📋 개요

`ingredients.json`에 저장된 영문 성분 설명을 Gemini AI를 사용하여 한국어로 번역 및 요약하는 기능을 추가했습니다.

## 🎯 문제 상황

### 이전
- `ingredients.json`의 `description`이 영어로 저장되어 있음
- 성분 상세 화면에서 영문 설명이 그대로 표시됨
- 사용자가 이해하기 어려움

### 예시 (리모넨)
```
A super common and cheap fragrance ingredient. 
It's in many plants, e.g. rosemary, eucalyptus, 
lavender, lemongrass, peppermint...
```

## ✅ 해결 방법

### 1. Gemini AI 번역 함수 추가
`GeminiService.kt`에 `translateIngredientDescription()` 함수 추가:

```kotlin
suspend fun translateIngredientDescription(
    ingredientName: String, 
    englishDescription: String
): String
```

**기능:**
- 영문 설명을 한국어로 번역
- 2-3 문장으로 요약
- 주요 효과, 피부 타입 적합성, 주의사항 포함
- 일반 사용자가 이해하기 쉬운 표현 사용

### 2. ResultsFragment 수정
`loadIngredientDescription()` 함수 업데이트:

**동작 플로우:**
```
1. ingredients.json에서 성분 정보 검색
   ↓
2. description 필드 확인
   ↓
3-A. description 있음
   → Gemini로 한국어 번역
   ↓
3-B. description 없음
   → Gemini로 새로 생성
   ↓
4. 한국어 설명 표시
```

## 🔄 상세 동작 과정

### Case 1: ingredients.json에 성분 정보 있음
1. 영문 `description` 추출
2. `geminiService.translateIngredientDescription()` 호출
3. 한국어로 번역된 설명 표시

### Case 2: ingredients.json에 성분 정보 없음
1. `geminiService.generateIngredientDescription()` 호출
2. 새로 생성된 한국어 설명 표시

### Case 3: 번역 실패 시
1. Fallback으로 `generateIngredientDescription()` 호출
2. 데이터베이스 정보 없이 새로 생성

## 📝 Gemini 프롬프트

### 번역 프롬프트
```
다음은 화장품 성분 "{성분명}"에 대한 영문 설명입니다.
이 내용을 한국어로 번역하여 일반 사용자가 이해하기 쉽도록 
2-3 문장으로 요약해주세요.

주요 효과, 피부 타입 적합성, 주의사항을 포함하되 
전문적이면서도 쉬운 한국어로 작성해주세요.

영문 설명:
{영문 description}

한국어 요약:
```

### 생성 프롬프트 (fallback)
```
화장품 성분 "{성분명}"에 대한 상세 설명을 2-3 문장으로 작성해주세요.
1. 성분의 주요 효과와 작용 메커니즘
2. 어떤 피부 타입에 적합한지
3. 주의사항이 있다면 간단히 언급

전문적이면서도 일반 사용자가 이해하기 쉽게 한국어로 작성해주세요.
```

## 🎨 사용자 경험

### 이전
```
성분: 리모넨

AI 간편 설명:
A super common and cheap fragrance ingredient. 
It's in many plants, e.g. rosemary, eucalyptus...
(영어 설명 계속...)
```

### 현재
```
성분: 리모넨

AI 간편 설명:
리모넨은 감귤류 껍질에서 주로 추출되는 향료 성분입니다. 
자연스러운 향을 제공하지만, 공기 중 산화로 인해 
민감성 피부에는 알레르기 반응을 일으킬 수 있어 주의가 필요합니다.
```

## 📊 처리 우선순위

1. **ingredients.json 데이터** (최우선)
   - 영문 description → Gemini 번역 → 한국어 표시

2. **Gemini 생성** (fallback)
   - ingredients.json에 없는 성분
   - 번역 실패 시

3. **에러 처리**
   - "설명을 불러올 수 없습니다." 표시

## 🔧 수정된 파일

1. **GeminiService.kt**
   - `translateIngredientDescription()` 함수 추가
   - 영문 → 한국어 번역 기능

2. **ResultsFragment.kt**
   - `loadIngredientDescription()` 함수 수정
   - 번역 로직 추가

## ✅ 장점

1. **데이터 활용**: ingredients.json의 풍부한 정보 활용
2. **정확성**: 검증된 데이터베이스 기반 번역
3. **사용자 친화**: 한국어로 이해하기 쉬운 설명
4. **Fallback**: 데이터 없을 시 자동 생성

## 🚀 향후 개선 사항

1. 번역 결과 캐싱으로 API 호출 최소화
2. 오프라인 한국어 설명 데이터베이스 구축
3. 사용자 피부 타입별 맞춤 설명 강화

