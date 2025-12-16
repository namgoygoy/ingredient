# Cosmetic - AI-Powered Cosmetic Ingredient Analyzer

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-005571?style=for-the-badge&logo=fastapi)
![Supabase](https://img.shields.io/badge/Supabase-181818?style=for-the-badge&logo=supabase&logoColor=white)
![ChromaDB](https://img.shields.io/badge/ChromaDB-FF6B6B?style=for-the-badge)
![Gemini AI](https://img.shields.io/badge/Gemini-AI-4285F4?style=for-the-badge&logo=google&logoColor=white)

<div align="center">
  <img src="https://github.com/user-attachments/assets/c51fbe04-6393-481a-bf2c-33fabb319ff7" alt="ingredient_promo_instagram" width="33%" />
</div>

## One-line Summary

복잡한 화장품 전성분을 OCR로 인식한 뒤 AI 알고리즘을 통해 분석하여, 사용자의 피부 고민과 타입에 맞는 성분인지 즉시 식별해 주는 개인 맞춤형 뷰티 헬스케어 앱입니다.

---
## 🐤 Demo
<div align="center">
  <video src="https://github.com/user-attachments/assets/292d6afb-a9ec-4ac4-bbbf-fb41c0a7804e" width="50%" />
</div>

## 📖 Description

**Cosmetic**은 **OCR(광학 문자 인식)**과 **RAG(검색 증강 생성)** 기술을 결합한 지능형 화장품 성분 분석기입니다.

단순히 성분 사전을 검색하는 것을 넘어, **11,000개 이상의 검증된 데이터**와 **사용자 피부 타입**을 대조하여 **"이 제품이 나에게 맞을까?"** 라는 질문에 즉각적인 답을 제공합니다.

###  Why This Project?

기존 서비스들이 가진 한계점을 기술적으로 어떻게 극복했는지 확인해보세요.

| 특징 (Feature) | 기존 서비스 (Others) | ⚡ Cosmetic (Our Solution) |
| :--- | :--- | :--- |
| **입력 방식** | 일일이 타이핑하거나 선택 | **카메라 원터치 스캔 (OCR)** |
| **분석 정확도** | LLM 환각 현상 발생 가능 | **RAG 기반 팩트 체크 (11k+ DB)** |
| **검색 로직** | 단순 문자열 일치 검색 | **키워드 + 벡터 하이브리드 검색** |
| **데이터 처리** | 정적인 데이터 나열 | **규칙 기반 분석 + AI 보완 (Gemini)** |
| **사용자 경험** | 일반적인 성분 정보 제공 | **피부 타입별 맞춤 적합도 판단** |

<br>

> **💡 Core Tech**: `Android CameraX` + `Google ML Kit` + `LangChain RAG` + `Gemini AI`

---

## System Architecture

<img width="2816" height="1536" alt="Gemini_Generated_Image_ldc961ldc961ldc9" src="https://github.com/user-attachments/assets/0a3e683b-1293-4588-b654-89ac6f2cf3b8" />

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
```

### 4. 설치 검증 (Verification)

* 서버 상태 확인: curl http://localhost:5000/health 명령어로 status: healthy 응답 확인.

* 앱 기능 테스트: 피부 타입 선택 → 카메라 권한 허용 → 성분표 촬영 → 분석 결과 출력 확인.

Tech Stack: Google ML Kit (OCR), Google Gemini AI, Supabase (PostgreSQL)
