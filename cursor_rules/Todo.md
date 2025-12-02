✅ 인그리디언트 (Ingrediant) 프로젝트 To-do List (v1.2 - RAG 반영)
Phase 1: UI 설계 및 기본 설정 (UI First)

[✅] Android 프로젝트 생성 (Kotlin, MVVM 아키텍처 설정) - 기본 구조 완료, SharedViewModel 구현 완료

[✅] Jetpack Compose를 사용한 화면별 UI 레이아웃 설계 (Mockup) - XML 기반 레이아웃으로 완료 (Compose 미사용)

[⚠️] 카메라 스캔 화면 (CameraX 뷰 포함) - 레이아웃 완료, CameraX 구현 필요

[✅] 분석 결과 요약 화면 (✅추천 / ⚠️주의 섹션 포함) - 레이아웃 완료

[✅] 전체 성분 목록 표시 (리스트) - 레이아웃 완료

[✅] ingredients.json 파일을 앱 내에 포함 (Asset) - assets 폴더에 복사 완료

[✅] JSON 데이터 구조에 맞는 데이터 클래스 설계 (id, name, good_for, bad_for 등) - AnalyzeProductModels.kt에 구현 완료

Phase 2: Android 핵심 기능 개발 (Offline Logic)

[✅] 카메라 및 OCR 기능 연동 - CameraX 및 ML Kit 통합 완료

[✅] Google ML Kit Text Recognition을 연동하여 성분표 텍스트 추출 기능 구현 - 완료

[✅] 촬영 버튼을 누르면 인식된 텍스트를 분석 모듈로 전달하는 기능 구현 - SharedViewModel을 통한 데이터 전달 완료

[ ] OCR 전처리를 통한 인식률 개선

[✅] 핵심 요약 분석 기능 개발 (JSON 기반) - 백엔드 analyze_product_ingredients 함수로 구현 완료

[✅] ingredients.json 파싱 로직 구현 - 백엔드에서 처리 완료

[✅] 추출된 텍스트(성분명)와 ingredients.json 데이터를 매칭하는 로직 개발 - 백엔드에서 정확/부분 매칭 구현 완료

[✅] 모든 성분의 good_for, bad_for 태그를 집계하는 기능 구현 - 백엔드에서 집계 로직 완료

[✅] 집계된 데이터를 바탕으로 "추천 피부 타입"과 "주의 피부 타입"을 결정하는 분석 로직 개발 - 백엔드에서 분석 리포트 생성 완료

[✅] 화면 기능 연동 - ResultsFragment, DetailsFragment에서 연동 완료

[✅] 분석 결과를 1단계에서 설계한 UI(요약 + 목록)에 바인딩 - displayAnalysisResult, displayAnalysisDetails 함수로 완료

[✅] 전체 성분 목록에서 특정 아이템 터치 시, (데이터만 전달하며) 상세/채팅 화면으로 이동하는 기능 구현 - DetailsFragment의 IngredientsAdapter에서 구현 완료

Phase 3: RAG 백엔드 구축 (Python / LangChain)

[✅] 기본 환경 설정 - backend 폴더, requirements.txt, venv 설정 완료

[✅] Python (FastAPI/Flask) 기반 API 서버 환경 구축 - Flask 기반 rag_server.py 완료

[⚠️] ingredients.json 및 추가 전문 자료(논문, 기사) 수집 - ingredients.json은 완료, 추가 자료는 미완료

[✅] Vector DB 구축 - ChromaDB 사용, 완료

[✅] 수집된 데이터를 임베딩하여 ChromaDB Vector Store 구축 - create_vectorstore 함수로 완료

[✅] LangChain 파이프라인 구현 - EnterpriseRAG 클래스로 완료

[✅] LangChain을 활용한 RAG 체인(Chain) 구현 (Retrieve-Augment-Generate) - create_qa_runnable 함수로 완료

[❌] Chat History (대화 기록) 연동 로직 구현 (세션 기반) - v1.0 범위에서 제외됨

[✅] Few-shot 기법을 활용한 프롬프트 템플릿 엔지니어링 - create_few_shot_prompt 함수로 완료

[✅] API 개발 및 평가 - /analyze_product, /search, /chat/history 등 엔드포인트 완료, evaluation.py 모듈 완료

[✅] /search 엔드포인트 개발 (Request: query / Response: answer) - 구현 완료 (채팅 기능 없이 단일 질문만 지원)

Phase 4: AI 기능 연동 (Android-Backend)

[✅] AI 연동 모듈 개발 (Android) - RetrofitClient, RAGApiService 완료

[✅] Retrofit/OkHttp 등 RAG 백엔드 API 연동을 위한 네트워크 모듈 설정 - RetrofitClient.kt 완료

[✅] 상세 화면 진입 시, 성분명을 기반으로 RAG 백엔드(/analyze_product)를 호출하는 기능 구현 - 완료

[✅] RAG API 응답 결과를 상세 정보 UI에 표시 - 성분 상세 화면(ResultsFragment) UI 완료 (채팅 UI는 v1.0 범위에서 제외됨)

[⚠️] 로딩 및 오류 상태 처리 UI 구현 - SharedViewModel에 상태는 있지만 UI 표시는 부분적 완료

Phase 5: 고도화 및 테스트 (2주)

[ ] 데이터베이스 확장

[ ] ingredients.json에 더 많은 성분 데이터 추가

[ ] (✨신규) 백엔드 Vector DB 데이터(논문 등) 지속 보강

[ ] 디자인 및 사용성 개선

[ ] 전체적인 앱 디자인 개선 및 일관성 확보

[ ] 사용자 피드백을 기반으로 UI/UX 개선

[ ] 안정성 테스트 및 배포 준비

[ ] OCR 인식률 테스트 및 예외 처리

[ ] (✨신규) RAG 백엔드 API 부하 테스트 및 응답 속도 최적화

[ ] 다양한 기기 및 OS 버전에서 호환성 테스트

[ ] 앱 스토어 배포를 위한 최종 준비 (아이콘, 스크린샷, 설명 등)