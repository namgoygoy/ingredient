# Cosmetic - AI-Powered Cosmetic Ingredient Analyzer

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-005571?style=for-the-badge&logo=fastapi)
![Supabase](https://img.shields.io/badge/Supabase-181818?style=for-the-badge&logo=supabase&logoColor=white)
![ChromaDB](https://img.shields.io/badge/ChromaDB-FF6B6B?style=for-the-badge)
![Gemini AI](https://img.shields.io/badge/Gemini-AI-4285F4?style=for-the-badge&logo=google&logoColor=white)

## One-line Summary

스마트폰 카메라로 화장품 성분표를 촬영하면, RAG 기반 AI가 11,000개 이상의 검증된 성분 데이터를 분석하여 사용자 피부 타입에 맞는 맞춤형 정보를 제공하는 Android 애플리케이션입니다.

---

## 🐤 Demo

[여기에 데모 영상 첨부]

---

## 📖 Description

**Cosmetic**은 화장품 성분표를 카메라로 촬영하여 AI가 분석하는 모바일 애플리케이션입니다. ML Kit의 한글 OCR 기술을 활용하여 성분표를 자동으로 인식하고, RAG(Retrieval-Augmented Generation) 기반 백엔드 시스템이 11,000개 이상의 검증된 성분 데이터베이스를 검색하여 정확한 분석 결과를 제공합니다.

사용자는 자신의 피부 타입(건성, 지성, 민감성, 여드름성 등)을 설정하면, 각 성분이 해당 피부 타입에 좋은 성분인지 주의가 필요한 성분인지 자동으로 분류하여 표시합니다. 또한 개별 성분을 클릭하면 상세한 기능(purpose), 피부 타입 적합성, 과학적 근거가 담긴 설명을 확인할 수 있습니다.

### Why This Project?

**Key Differentiators**

기존 화장품 성분 분석 서비스들은 대부분 수동으로 성분을 입력해야 하거나, 제한적인 데이터베이스로 인해 정확도가 떨어지는 경우가 많습니다. **Cosmetic**은 다음과 같은 차별점을 제공합니다:

1. **원스톱 편의성**: 스마트폰 카메라로 성분표를 촬영하기만 하면 자동으로 분석이 완료됩니다. 복잡한 입력 과정 없이 즉시 결과를 확인할 수 있습니다.

2. **RAG 기반 정확성**: 일반적인 LLM 기반 서비스와 달리, RAG(Retrieval-Augmented Generation) 아키텍처를 채택하여 검증된 11,000개 이상의 성분 데이터베이스를 직접 검색합니다. 이를 통해 할루시네이션 없이 정확하고 신뢰할 수 있는 정보를 제공합니다.

3. **하이브리드 검색 시스템**: Supabase PostgreSQL과 ChromaDB 벡터 스토어를 결합한 하이브리드 검색으로, 정확한 키워드 매칭과 의미 기반 유사도 검색을 동시에 지원합니다. Supabase 연결 실패 시 JSON 파일로 자동 폴백하여 안정성을 보장합니다.

4. **지능형 보완 시스템**: RAG 서버가 주요 분석 엔진으로 동작하며, 정보가 부족한 경우에만 Gemini AI를 보완 엔진으로 활용합니다. 이를 통해 API 비용을 최소화하면서도 완전한 정보 제공이 가능합니다.

5. **피부 타입별 맞춤 분석**: 단순히 성분을 나열하는 것이 아니라, 사용자의 피부 타입을 기반으로 각 성분의 `good_for`와 `bad_for` 속성을 분석하여 개인화된 평가를 제공합니다.

---

## 🔨 System Architecture

[여기에 시스템 아키텍처 다이어그램 첨부]

---

## Logic Flow (Sequence Diagram)

<div align="center">
  <img width="845" height="629" alt="image시퀸스" src="https://github.com/user-attachments/assets/04f2919a-2e44-45f5-8780-97f118d5c1dc" />
</div>

---

## API Endpoints

- **URL:** `/analyze_product`
- **Method:** `POST`
- **Content-Type:** `application/json`

### 1. Request (요청)

**JSON Body Example**

```json
{
  "ingredients": ["정제수", "글리세린", "히알루론산", "니아신아마이드"],
  "skin_type": "건성"
}
```

### 2. Response (응답)

**Status: 200 OK**

**JSON Body Example**

```json
{
  "analysis_report": "이 화장품은(는) '보습'에 중점을 둔 제품으로 보입니다...",
  "good_matches": [
    {
      "name": "히알루론산",
      "purpose": "보습제"
    }
  ],
  "bad_matches": [
    {
      "name": "알코올",
      "description": "건성 피부에 건조를 유발할 수 있습니다"
    }
  ],
  "success": true
}
```

### 상세 문서
[EndPoint.pdf](https://github.com/user-attachments/files/24192548/EndPoint.pdf)

---

## 🔧 Stack

| Category | Technology |
| :--- | :--- |
| **Android** | **Kotlin**, Android SDK, MVVM, Coroutines, CameraX, ML Kit (OCR) |
| **Backend** | **Python (FastAPI)**, LangChain, Supabase (PostgreSQL), ChromaDB |
| **AI / ML** | **Google Gemini 2.5 Flash**, Sentence Transformers, HuggingFace Embeddings |
| **Tools** | Swagger UI, Retrofit2, OkHttp3, ngrok |

---

## 📂 Project Structure

```
cosmetic/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/cosmetic/
│   │   │   │   ├── MainActivity.kt              # 메인 액티비티, 바텀 네비게이션
│   │   │   │   ├── ScanFragment.kt             # 카메라 촬영 및 OCR
│   │   │   │   ├── ResultsFragment.kt          # 분석 결과 표시
│   │   │   │   ├── DetailsFragment.kt          # 제품 상세 분석
│   │   │   │   ├── ProfileFragment.kt           # 프로필 화면
│   │   │   │   ├── SkinTypeActivity.kt          # 피부 타입 선택 (온보딩)
│   │   │   │   ├── GeminiService.kt            # Gemini AI 통합
│   │   │   │   ├── IngredientParser.kt         # 성분 파싱 로직
│   │   │   │   ├── SharedViewModel.kt           # Fragment 간 데이터 공유
│   │   │   │   ├── UserPreferences.kt          # 사용자 설정 관리
│   │   │   │   ├── Constants.kt                # 앱 전역 상수
│   │   │   │   ├── config/
│   │   │   │   │   └── AppConfig.kt             # 앱 설정 관리
│   │   │   │   ├── network/
│   │   │   │   │   ├── RAGApiService.kt        # Retrofit API 인터페이스
│   │   │   │   │   ├── RetrofitClient.kt       # Retrofit 클라이언트 설정
│   │   │   │   │   └── AnalyzeProductModels.kt  # API 요청/응답 모델
│   │   │   │   ├── repository/
│   │   │   │   │   └── ProductAnalysisRepository.kt  # 제품 분석 Repository
│   │   │   │   └── utils/
│   │   │   │       ├── IngredientCache.kt      # 성분 캐시 유틸리티
│   │   │   │       ├── LoadingAnimationHelper.kt  # 로딩 애니메이션 헬퍼
│   │   │   │       └── SkinTypeExtractor.kt     # 피부 타입 추출 유틸리티
│   │   │   ├── res/                            # 리소스 파일 (레이아웃, 이미지 등)
│   │   │   └── assets/
│   │   │       └── ingredients.json           # 성분 데이터베이스 (11,000+ 항목)
│   │   ├── test/                               # 단위 테스트
│   │   └── androidTest/                        # Android 통합 테스트
│   ├── build.gradle.kts                        # 앱 빌드 설정
│   └── proguard-rules.pro                      # ProGuard 규칙
│
├── backend/
│   ├── api/                                    # API 라우터 및 모델
│   │   ├── __init__.py
│   │   ├── models.py                           # Pydantic 모델 정의
│   │   └── routes.py                           # FastAPI 라우터
│   ├── rag/                                    # RAG 시스템 핵심 로직
│   │   ├── __init__.py
│   │   ├── data_loader.py                      # 데이터 로더 (Supabase/JSON)
│   │   ├── enterprise_rag.py                   # Enterprise RAG 클래스
│   │   ├── ingredient_search.py                 # 성분 검색 로직
│   │   ├── memory.py                           # 대화 메모리 관리
│   │   └── vector_store.py                     # ChromaDB 벡터 스토어
│   ├── llm/                                    # LLM 관련 클래스
│   │   ├── __init__.py
│   │   └── mock_llm.py                         # Mock LLM 구현
│   ├── rag_server_supabase.py                  # FastAPI 메인 서버
│   ├── supabase_client.py                      # Supabase 클라이언트
│   ├── migrate_to_supabase.py                  # Supabase 마이그레이션 스크립트
│   ├── SUPABASE_SETUP.sql                      # 데이터베이스 스키마
│   ├── requirements.txt                        # Python 의존성
│   ├── start_server.sh                         # 개발 서버 실행 스크립트
│   ├── start_server_supabase.sh                # Supabase 연동 서버 실행
│   ├── start_server_prod.sh                   # 프로덕션 서버 실행
│   ├── chroma_db_ingredients/                  # ChromaDB 벡터 스토어 디렉토리
│   └── venv/                                   # Python 가상 환경
│
├── gradle/                                     # Gradle Wrapper
│   ├── libs.versions.toml                      # 의존성 버전 관리
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts                            # 프로젝트 빌드 설정
├── settings.gradle.kts                         # 프로젝트 설정
├── gradle.properties                           # Gradle 속성
├── local.properties                            # 로컬 설정 (API 키 등, .gitignore)
├── ARCHITECTURE.md                             # 시스템 아키텍처 문서
├── SETUP_GUIDE.md                              # 설정 가이드 문서
└── README.md                                   # 프로젝트 README
```

---
# 실행 및 설정 가이드 (Setup & Run)

### 1. 백엔드 서버 실행 (Backend)
가상 환경 활성화 후 Supabase 연동 서버를 실행합니다. (기본 포트: `5000`)

```bash
cd backend
source venv/bin/activate
python rag_server_supabase.py  # 또는 ./start_server_supabase.sh
```

### 2. 안드로이드 연동 설정 (Configuration)
모바일 기기에서 로컬 서버에 접근하기 위해 ngrok을 사용하거나 로컬 주소를 설정합니다.

Ngrok 실행 (옵션):

```bash
ngrok http 5000
local.properties 설정: 프로젝트 루트의 local.properties에 아래 내용을 추가합니다.
```

```properties
GEMINI_API_KEY=사용자_GEMINI_API_KEY
API_BASE_URL=[https://생성된-ngrok-url.io/](https://생성된-ngrok-url.io/)  # 로컬 환경: http://localhost:5000/
```
### 3. 앱 실행 (Run App)
Android Studio 또는 터미널을 통해 앱을 빌드하고 실행합니다.

```bash
./gradlew installDebug
4. 설치 검증 (Verification)
```

* 서버 상태 확인: curl http://localhost:5000/health 명령어로 status: healthy 응답 확인.

* 앱 기능 테스트: 피부 타입 선택 → 카메라 권한 허용 → 성분표 촬영 → 분석 결과 출력 확인.

Tech Stack: Google ML Kit (OCR), Google Gemini AI, Supabase (PostgreSQL)
