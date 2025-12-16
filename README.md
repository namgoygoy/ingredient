# 🧴 Cosmetic - AI-Powered Cosmetic Ingredient Analyzer

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

1. **📸 원스톱 편의성**: 스마트폰 카메라로 성분표를 촬영하기만 하면 자동으로 분석이 완료됩니다. 복잡한 입력 과정 없이 즉시 결과를 확인할 수 있습니다.

2. **🎯 RAG 기반 정확성**: 일반적인 LLM 기반 서비스와 달리, RAG(Retrieval-Augmented Generation) 아키텍처를 채택하여 검증된 11,000개 이상의 성분 데이터베이스를 직접 검색합니다. 이를 통해 할루시네이션 없이 정확하고 신뢰할 수 있는 정보를 제공합니다.

3. **🔬 하이브리드 검색 시스템**: Supabase PostgreSQL과 ChromaDB 벡터 스토어를 결합한 하이브리드 검색으로, 정확한 키워드 매칭과 의미 기반 유사도 검색을 동시에 지원합니다. Supabase 연결 실패 시 JSON 파일로 자동 폴백하여 안정성을 보장합니다.

4. **🤖 지능형 보완 시스템**: RAG 서버가 주요 분석 엔진으로 동작하며, 정보가 부족한 경우에만 Gemini AI를 보완 엔진으로 활용합니다. 이를 통해 API 비용을 최소화하면서도 완전한 정보 제공이 가능합니다.

5. **📊 피부 타입별 맞춤 분석**: 단순히 성분을 나열하는 것이 아니라, 사용자의 피부 타입을 기반으로 각 성분의 `good_for`와 `bad_for` 속성을 분석하여 개인화된 평가를 제공합니다.

---

## ⭐ Main Features

- **📷 카메라 기반 OCR**: CameraX와 ML Kit을 활용한 실시간 성분표 촬영 및 한글 텍스트 인식
- **🔍 지능형 성분 파싱**: OCR로 인식된 텍스트에서 성분명을 자동으로 추출하고 정규화
- **📊 종합 제품 분석**: 전체 성분 리스트를 분석하여 제품의 주요 효능과 적합 피부 타입 평가
- **✅ 좋은 성분 / ⚠️ 주의 성분 분류**: 피부 타입별로 성분을 자동 분류하고 시각적 뱃지로 표시
- **📖 개별 성분 상세 정보**: 성분명 클릭 시 기능(purpose), 피부 타입 적합성, 과학적 근거 설명 제공
- **🌐 RAG 기반 검색**: 11,000개 이상의 검증된 성분 데이터베이스에서 정확한 정보 검색
- **🤖 AI 보완 시스템**: RAG 서버에 없는 정보는 Gemini AI로 자동 보완
- **💾 오프라인 지원**: ingredients.json 파일을 앱 내부에 포함하여 네트워크 없이도 기본 분석 가능
- **🎨 직관적인 UI/UX**: Material Design 기반의 깔끔한 인터페이스와 부드러운 애니메이션

---

## 🔨 System Architecture

[여기에 시스템 아키텍처 다이어그램 첨부]

---

## 📊 Logic Flow (Sequence Diagram)

[여기에 시퀀스 다이어그램 첨부]

---

## 🔌 API Endpoints

[여기에 API 명세서 첨부]

---

## 🔧 Stack

### **Frontend (Mobile)**
- **Language**: Kotlin
- **Framework**: Android SDK
- **UI/UX**: Material Design, ViewBinding
- **Architecture**: MVVM (ViewModel, LiveData)
- **Async Processing**: Kotlin Coroutines, Flow
- **Navigation**: Navigation Component
- **Camera**: CameraX
- **OCR**: ML Kit Text Recognition (Korean)
- **Network**: Retrofit2, OkHttp3
- **AI Integration**: Google Gemini AI SDK

### **Backend**
- **Language**: Python 3.13
- **Framework**: FastAPI
- **RAG Framework**: LangChain
- **Vector Database**: ChromaDB
- **Embeddings**: Sentence Transformers (paraphrase-multilingual-MiniLM-L12-v2)
- **Database**: Supabase PostgreSQL (JSON Fallback)
- **API Documentation**: Swagger UI, ReDoc

### **Database & Storage**
- **Primary**: Supabase PostgreSQL
- **Vector Store**: ChromaDB (Persistent)
- **Fallback**: JSON File (ingredients.json)

### **AI/ML**
- **LLM**: Google Gemini 2.5 Flash
- **Embedding Model**: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
- **Text Processing**: LangChain Text Splitters

### **Infrastructure**
- **Development**: Local Development Server (uvicorn)
- **Production**: TBD (AWS/GCP/Azure)
- **API Gateway**: ngrok (Development)

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
│   │   │   │   ├── SkinTypeActivity.kt          # 피부 타입 선택 (온보딩)
│   │   │   │   ├── GeminiService.kt            # Gemini AI 통합
│   │   │   │   ├── IngredientParser.kt         # 성분 파싱 로직
│   │   │   │   ├── SharedViewModel.kt           # Fragment 간 데이터 공유
│   │   │   │   ├── UserPreferences.kt          # 사용자 설정 관리
│   │   │   │   ├── Constants.kt                # 앱 전역 상수
│   │   │   │   └── network/
│   │   │   │       ├── RAGApiService.kt        # Retrofit API 인터페이스
│   │   │   │       ├── RetrofitClient.kt       # Retrofit 클라이언트 설정
│   │   │   │       └── AnalyzeProductModels.kt  # API 요청/응답 모델
│   │   │   ├── res/                            # 리소스 파일 (레이아웃, 이미지 등)
│   │   │   └── assets/
│   │   │       └── ingredients.json           # 성분 데이터베이스 (11,000+ 항목)
│   │   └── build.gradle.kts                    # 앱 빌드 설정
│   └── proguard-rules.pro                      # ProGuard 규칙
│
├── backend/
│   ├── rag_server_supabase.py                  # FastAPI 메인 서버
│   ├── supabase_client.py                       # Supabase 클라이언트
│   ├── SUPABASE_SETUP.sql                      # 데이터베이스 스키마
│   ├── requirements.txt                        # Python 의존성
│   ├── start_server.sh                         # 개발 서버 실행 스크립트
│   ├── start_server_supabase.sh                # Supabase 연동 서버 실행
│   ├── start_server_prod.sh                    # 프로덕션 서버 실행
│   ├── chroma_db_ingredients/                  # ChromaDB 벡터 스토어 디렉토리
│   └── venv/                                    # Python 가상 환경
│
├── gradle/                                      # Gradle Wrapper
├── build.gradle.kts                            # 프로젝트 빌드 설정
├── settings.gradle.kts                         # 프로젝트 설정
├── gradle.properties                           # Gradle 속성
├── local.properties                            # 로컬 설정 (API 키 등)
├── ARCHITECTURE.md                             # 시스템 아키텍처 문서
└── README.md                                   # 프로젝트 README
```

---

## 💻 Getting Started

### **Prerequisites**

- **Android Development**
  - Android Studio Hedgehog | 2023.1.1 or later
  - JDK 11 or later
  - Android SDK (API Level 24+)
  - Gradle 8.0+

- **Backend Development**
  - Python 3.13 or later
  - pip (Python Package Manager)
  - Supabase Account (Optional, for PostgreSQL)

### **Installation**

#### **1. Clone Repository**

```bash
git clone https://github.com/your-username/cosmetic.git
cd cosmetic
```

#### **2. Android App Setup**

1. **Open Project in Android Studio**
   ```bash
   # Android Studio에서 프로젝트 열기
   File > Open > cosmetic 폴더 선택
   ```

2. **Configure API Keys**
   
   프로젝트 루트에 `local.properties` 파일을 생성하고 다음 내용을 추가:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```

3. **Sync Gradle**
   ```
   Android Studio에서 File > Sync Project with Gradle Files
   ```

4. **Build & Run**
   ```
   Run > Run 'app' 또는 Shift+F10
   ```

#### **3. Backend Server Setup**

1. **Create Virtual Environment**
   ```bash
   cd backend
   python3 -m venv venv
   source venv/bin/activate  # Windows: venv\Scripts\activate
   ```

2. **Install Dependencies**
   ```bash
   pip install -r requirements.txt
   ```

3. **Configure Supabase (Optional)**
   
   `backend/` 디렉토리에 `.env` 파일을 생성:
   ```env
   SUPABASE_URL=your_supabase_project_url
   SUPABASE_KEY=your_supabase_anon_key
   ```
   
   Supabase를 사용하지 않으면 JSON 파일 모드로 자동 폴백됩니다.

4. **Initialize Database (Supabase 사용 시)**
   
   Supabase SQL Editor에서 `SUPABASE_SETUP.sql` 파일의 내용을 실행하여 테이블을 생성합니다.

5. **Run Server**
   ```bash
   # 개발 서버 실행 (JSON 모드)
   ./start_server.sh
   
   # Supabase 연동 서버 실행
   ./start_server_supabase.sh
   
   # 또는 직접 실행
   python rag_server_supabase.py
   ```
   
   서버는 기본적으로 `http://localhost:5000`에서 실행됩니다.

6. **Setup ngrok (Android App에서 접근하기 위해)**
   ```bash
   # ngrok 설치 후
   ngrok http 5000
   ```
   
   생성된 ngrok URL을 `RetrofitClient.kt`의 `BASE_URL`에 설정합니다.

### **Run Application**

1. **Start Backend Server**
   ```bash
   cd backend
   source venv/bin/activate
   python rag_server_supabase.py
   ```

2. **Configure API Endpoint in Android App**
   
   `app/src/main/java/com/example/cosmetic/network/RetrofitClient.kt`에서 `BASE_URL`을 백엔드 서버 주소로 설정:
   ```kotlin
   private const val BASE_URL = "https://your-ngrok-url.ngrok.io/"
   ```

3. **Run Android App**
   
   Android Studio에서 앱을 실행하거나:
   ```bash
   ./gradlew installDebug
   ```

### **Verify Installation**

1. **Backend Health Check**
   ```bash
   curl http://localhost:5000/health
   ```
   
   응답 예시:
   ```json
   {
     "status": "healthy",
     "message": "RAG 서버 정상 작동 중",
     "ingredients_count": 11111,
     "database": "supabase",
     "features": ["Supabase PostgreSQL", "ChromaDB Vector Store", "LangChain RAG Pipeline", "FastAPI Async"]
   }
   ```

2. **Android App**
   - 앱 실행 후 피부 타입 선택 화면이 표시되는지 확인
   - 카메라 권한 요청이 정상적으로 동작하는지 확인
   - 성분표 촬영 후 분석 결과가 표시되는지 확인

---

## 📚 Additional Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md) - 시스템 아키텍처 및 역할 분담 상세 문서

---

## 📄 License

[라이선스 정보를 여기에 추가하세요]

---

## 🙏 Acknowledgments

- [ingredients.json 데이터 소스 출처를 여기에 추가하세요]
- Google ML Kit for Korean Text Recognition
- Google Gemini AI for content generation
- Supabase for PostgreSQL hosting

