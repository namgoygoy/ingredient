# 🧴 Cosmetic - AI-Powered Cosmetic Ingredient Analyzer

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.13-3776AB?style=flat&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.104.0-009688?style=flat&logo=fastapi&logoColor=white)
![Supabase](https://img.shields.io/badge/Supabase-PostgreSQL-3ECF8E?style=flat&logo=supabase&logoColor=white)
![ChromaDB](https://img.shields.io/badge/ChromaDB-0.4.0-FF6B6B?style=flat)
![Gemini AI](https://img.shields.io/badge/Gemini-2.5--Flash-4285F4?style=flat&logo=google&logoColor=white)
![Android](https://img.shields.io/badge/Android-24+-3DDC84?style=flat&logo=android&logoColor=white)

> **스마트폰 카메라로 화장품 성분표를 촬영하면, AI가 11,000개 이상의 검증된 성분 데이터를 기반으로 사용자의 피부 타입에 맞는 맞춤형 분석을 제공하는 Android 애플리케이션**

---

## 📖 Description

**Cosmetic**은 화장품 성분표를 카메라로 촬영하여 즉시 성분을 분석하고, 사용자의 피부 타입에 맞는 맞춤형 정보를 제공하는 AI 기반 모바일 애플리케이션입니다.

### 해결하려는 문제

화장품을 구매할 때 성분표를 확인하고 각 성분의 효과와 주의사항을 파악하는 것은 일반 소비자에게 매우 어려운 일입니다. 기존의 성분 분석 서비스들은 다음과 같은 한계가 있습니다:

- **수동 입력의 불편함**: 성분을 하나씩 직접 검색해야 함
- **할루시네이션 문제**: AI가 잘못된 정보를 생성할 위험
- **피부 타입별 맞춤 정보 부족**: 개인의 피부 특성을 고려하지 않은 일반적인 정보 제공
- **영문 성분명의 언어 장벽**: 한국어 사용자가 이해하기 어려운 영문 설명

### 작동 방식

1. **OCR 기반 자동 인식**: ML Kit의 한글 텍스트 인식을 활용하여 성분표를 자동으로 인식
2. **RAG 기반 정확한 분석**: 11,000개 이상의 검증된 성분 데이터베이스를 기반으로 한 RAG(Retrieval-Augmented Generation) 시스템으로 할루시네이션 없는 정확한 정보 제공
3. **피부 타입별 맞춤 분석**: 사용자가 선택한 피부 타입(건성, 지성, 민감성 등)에 따라 좋은 성분과 주의 성분을 자동으로 분류
4. **하이브리드 AI 시스템**: RAG 서버를 주요 엔진으로 사용하고, Gemini AI는 보완 엔진으로만 활용하여 비용과 속도를 최적화

### Why This Project?

**Key Differentiators**

기존의 성분 분석 애플리케이션들과 달리, **Cosmetic**은 다음과 같은 차별화된 특징을 제공합니다:

- **🎯 스마트폰으로 한 번에 완결**: 성분표를 촬영하기만 하면 자동으로 성분을 인식하고 분석하여 즉시 결과를 제공합니다. 복잡한 수동 입력 과정이 필요 없습니다.

- **🔒 RAG 기반 할루시네이션 없는 정보**: 순수 LLM 기반 서비스와 달리, 11,000개 이상의 검증된 성분 데이터베이스를 기반으로 한 RAG(Retrieval-Augmented Generation) 시스템을 통해 정확하고 신뢰할 수 있는 정보만을 제공합니다. AI가 임의로 생성한 잘못된 정보의 위험을 최소화합니다.

- **⚡ 하이브리드 아키텍처로 최적화**: RAG 서버를 주요 분석 엔진으로 사용하고, Gemini AI는 보완 엔진으로만 활용하여 API 비용을 최소화하면서도 빠른 응답 속도를 보장합니다.

- **🌐 다국어 지원**: 한글 성분명과 영문 성분명을 모두 지원하며, 영문 설명을 자연스러운 한국어로 번역하여 제공합니다.

- **📊 피부 타입별 맞춤 분석**: 단순한 성분 나열이 아닌, 사용자의 피부 타입에 맞는 좋은 성분과 주의 성분을 자동으로 분류하여 실질적인 구매 결정에 도움을 줍니다.

---

## ⭐ Main Features

### 📷 스마트 OCR 인식
- **ML Kit 한글 텍스트 인식**: 카메라로 촬영한 성분표를 자동으로 인식
- **자동 성분 파싱**: OCR 결과에서 성분명을 자동으로 추출하여 리스트화

### 🔍 RAG 기반 정확한 분석
- **11,000+ 검증된 성분 데이터**: Supabase PostgreSQL과 ChromaDB 벡터 검색을 통한 정확한 성분 정보 제공
- **피부 타입별 매칭**: `good_for`, `bad_for` 필드를 기반으로 사용자 피부 타입에 맞는 성분 자동 분류
- **종합 분석 리포트**: 제품의 주요 효능, 적합한 피부 타입, 주의 성분을 포함한 종합 분석 제공

### 🤖 하이브리드 AI 시스템
- **RAG 서버 우선**: 빠르고 정확한 로컬 데이터베이스 검색을 주요 엔진으로 사용
- **Gemini AI 보완**: RAG 서버에 없는 성분 정보만 Gemini AI로 생성하여 비용 최적화
- **스마트 캐싱**: LRU 캐시를 통한 중복 API 호출 방지 및 응답 속도 향상

### 📱 사용자 친화적 UI/UX
- **직관적인 네비게이션**: 바텀 네비게이션을 통한 쉬운 화면 전환
- **실시간 로딩 피드백**: 분석 진행 상황을 시각적으로 표시
- **성분 상세 정보**: 개별 성분 클릭 시 상세 정보(기능, 피부 타입 적합성, 설명) 제공
- **뱃지 시스템**: 좋은 성분과 주의 성분을 색상으로 구분하여 한눈에 파악 가능

### 🎨 온보딩 및 프로필 관리
- **피부 타입 선택**: 첫 실행 시 사용자의 피부 타입을 선택하여 맞춤 분석 제공
- **프로필 관리**: 피부 타입 변경 및 앱 설정 관리

---

## 🔨 System Architecture

[여기에 시스템 아키텍처 다이어그램 이미지 첨부]

---

## 📊 Logic Flow (Sequence Diagram)

[여기에 시퀀스 다이어그램 이미지 첨부]

---

## 🔌 API Endpoints

[여기에 API 명세서 첨부]

### 주요 엔드포인트

- `POST /analyze_product`: 제품 성분 종합 분석
- `POST /search`: 개별 성분 검색
- `GET /ingredients`: 전체 성분 목록 조회
- `GET /health`: 서버 상태 확인
- `GET /database/status`: 데이터베이스 연결 상태 확인

---

## 🔧 Stack

### **Language**
- **Kotlin** (Android App)
- **Python 3.13** (Backend Server)

### **Framework & Library**
- **Android**
  - CameraX: 카메라 촬영 및 이미지 캡처
  - ML Kit: 한글 텍스트 인식 (OCR)
  - Retrofit: RESTful API 통신
  - Navigation Component: 화면 전환 관리
  - LiveData & ViewModel: 반응형 데이터 관리
  - Coroutines: 비동기 처리

- **Backend**
  - FastAPI: 비동기 웹 프레임워크
  - LangChain: RAG 파이프라인 구축
  - Sentence Transformers: 다국어 임베딩 모델
  - Uvicorn: ASGI 서버

### **Database**
- **Supabase PostgreSQL**: 성분 데이터 저장 (11,000+ 레코드)
- **ChromaDB**: 벡터 임베딩 저장 및 유사도 검색
- **JSON Fallback**: Supabase 연결 실패 시 로컬 JSON 파일 사용

### **AI & ML**
- **Google Gemini 2.5 Flash**: 성분 정보 생성 및 번역 (보완 엔진)
- **paraphrase-multilingual-MiniLM-L12-v2**: 다국어 임베딩 모델

### **Infrastructure**
- **ngrok**: 로컬 개발 서버 터널링
- **Git**: 버전 관리

---

## 📂 Project Structure

```
cosmetic/
├── app/                          # Android 애플리케이션
│   ├── src/main/
│   │   ├── java/com/example/cosmetic/
│   │   │   ├── MainActivity.kt           # 메인 액티비티
│   │   │   ├── ScanFragment.kt           # 카메라 촬영 및 OCR
│   │   │   ├── ResultsFragment.kt        # 분석 결과 표시
│   │   │   ├── DetailsFragment.kt         # 제품 분석 상세
│   │   │   ├── GeminiService.kt           # Gemini AI 서비스
│   │   │   ├── IngredientParser.kt       # 성분 파싱 로직
│   │   │   ├── SkinTypeActivity.kt        # 피부 타입 선택
│   │   │   ├── SharedViewModel.kt         # 데이터 공유 ViewModel
│   │   │   ├── UserPreferences.kt         # 사용자 설정 관리
│   │   │   ├── Constants.kt               # 앱 전역 상수
│   │   │   └── network/                   # 네트워크 레이어
│   │   │       ├── RAGApiService.kt      # API 인터페이스
│   │   │       ├── RetrofitClient.kt    # Retrofit 클라이언트
│   │   │       └── AnalyzeProductModels.kt # 데이터 모델
│   │   ├── res/                           # 리소스 파일
│   │   └── assets/
│   │       └── ingredients.json           # 성분 데이터 (폴백)
│   └── build.gradle.kts                   # Android 빌드 설정
│
├── backend/                      # Python 백엔드 서버
│   ├── rag_server_supabase.py    # FastAPI 메인 서버
│   ├── supabase_client.py        # Supabase 클라이언트
│   ├── SUPABASE_SETUP.sql        # 데이터베이스 스키마
│   ├── requirements.txt          # Python 의존성
│   ├── start_server.sh           # 개발 서버 실행 스크립트
│   ├── start_server_supabase.sh   # Supabase 연동 서버 실행
│   └── chroma_db_ingredients/     # ChromaDB 벡터 스토어
│
├── gradle/                        # Gradle 설정
├── build.gradle.kts               # 프로젝트 빌드 설정
├── settings.gradle.kts            # 프로젝트 설정
├── ARCHITECTURE.md                # 아키텍처 문서
└── README.md                      # 프로젝트 문서
```

---

## 💻 Getting Started

### Prerequisites

- **Android Studio** (Hedgehog | 2023.1.1 이상)
- **JDK 11** 이상
- **Python 3.13** 이상
- **Supabase 계정** (선택사항, JSON 폴백 모드 지원)

### Installation

#### 1. Android App 설정

```bash
# 저장소 클론
git clone <repository-url>
cd cosmetic

# Android Studio에서 프로젝트 열기
# File > Open > cosmetic 폴더 선택
```

**환경 변수 설정:**

`local.properties` 파일에 Gemini API 키를 추가합니다:

```properties
GEMINI_API_KEY=your_gemini_api_key_here
```

#### 2. Backend Server 설정

```bash
# 백엔드 디렉토리로 이동
cd backend

# 가상 환경 생성 및 활성화
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 의존성 설치
pip install -r requirements.txt
```

**환경 변수 설정:**

`.env` 파일을 생성하고 Supabase 연결 정보를 추가합니다 (선택사항):

```env
SUPABASE_URL=your_supabase_url
SUPABASE_KEY=your_supabase_key
```

**데이터베이스 설정:**

Supabase를 사용하는 경우, `SUPABASE_SETUP.sql` 파일의 내용을 Supabase SQL Editor에서 실행합니다.

#### 3. 서버 실행

**개발 모드 (JSON 폴백):**
```bash
cd backend
./start_server.sh
```

**Supabase 연동 모드:**
```bash
cd backend
./start_server_supabase.sh
```

서버는 기본적으로 `http://localhost:5000`에서 실행됩니다.

**ngrok 터널링 (Android 앱에서 접근하기 위해):**
```bash
ngrok http 5000
```

생성된 ngrok URL을 `RetrofitClient.kt`의 `BASE_URL`에 설정합니다.

#### 4. Android App 실행

1. Android Studio에서 프로젝트를 열고 Gradle 동기화를 완료합니다.
2. Android 기기 또는 에뮬레이터를 연결합니다.
3. `Run` 버튼을 클릭하여 앱을 실행합니다.

### Run

**Backend Server:**
```bash
cd backend
source venv/bin/activate
python rag_server_supabase.py
```

**Android App:**
- Android Studio에서 `Run` 버튼 클릭 또는 `Shift + F10`

---

## 📝 License

[라이선스 정보를 여기에 추가하세요]

---

## 🙏 Acknowledgments

- [참고한 오픈소스 프로젝트나 라이브러리 정보를 여기에 추가하세요]

