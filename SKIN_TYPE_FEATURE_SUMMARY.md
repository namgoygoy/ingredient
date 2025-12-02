# 피부 타입 설정 기능 구현 완료

## 📋 구현 내용

### 1. 파일 생성
- ✅ `UserPreferences.kt` - SharedPreferences 관리 클래스
- ✅ `SkinTypeActivity.kt` - 피부 타입 선택 Activity
- ✅ `activity_skin_type.xml` - 피부 타입 선택 화면 레이아웃
- ✅ `ProfileFragment.kt` - 프로필 탭 Fragment
- ✅ `fragment_profile.xml` - 프로필 화면 레이아웃

### 2. 파일 수정
- ✅ `MainActivity.kt` - 온보딩 체크 로직 추가
- ✅ `AndroidManifest.xml` - SkinTypeActivity 등록
- ✅ `bottom_nav_menu.xml` - 프로필 탭 추가
- ✅ `nav_graph.xml` - 프로필 프래그먼트 네비게이션 추가
- ✅ `DetailsFragment.kt` - 사용자 피부 타입 사용하도록 수정
- ✅ `ResultsFragment.kt` - 사용자 피부 타입 사용하도록 수정

## 🎯 주요 기능

### 1. 온보딩 플로우
- 앱 최초 실행 시 `SkinTypeActivity` 표시
- 5가지 피부 타입 선택 가능:
  - 💧 건성
  - ✨ 지성
  - 🌗 복합성
  - ⚠️ 민감성
  - 😊 중성
- "나중에 하기" 옵션 제공
- 선택 완료 시 `MainActivity`로 이동

### 2. 프로필 관리
- 바텀 네비게이션에 "프로필" 탭 추가
- 현재 피부 타입 표시
- 피부 타입 변경 기능 (다이얼로그)
- 앱 버전 정보 표시
- 사용 방법 안내
- 문의하기 기능

### 3. 맞춤형 분석
- 사용자가 설정한 피부 타입을 RAG API 요청에 포함
- `DetailsFragment`와 `ResultsFragment`에서 자동 적용
- 피부 타입에 따른 개인화된 성분 분석 제공

## 💾 데이터 저장

### UserPreferences (SharedPreferences)
- `skin_type` - 선택된 피부 타입
- `onboarding_completed` - 온보딩 완료 여부

## 🎨 UI/UX

### SkinTypeActivity
- 헤더: "피부 타입을 선택해주세요"
- 서브 헤더: "맞춤형 성분 분석을 위해 피부 타입을 알려주세요"
- 5개 CardView로 피부 타입 표시 (아이콘 + 설명)
- 선택 시 하이라이트 효과 (highlight_bg)
- 완료 버튼 (선택 시 활성화)
- 우측 상단 "나중에 하기" 버튼
- 뒤로가기 비활성화

### ProfileFragment
- 내 피부 타입 카드 (클릭 시 변경)
- 앱 정보 섹션:
  - 앱 버전
  - 사용 방법
  - 문의하기

## 🔄 사용자 플로우

```
앱 실행
   ↓
온보딩 체크
   ↓
┌──────────────┐
│ 미완료       │ 완료
│ SkinType     │ MainActivity
│ Activity     │
└──────────────┘
   ↓
피부 타입 선택
   ↓
완료/나중에 하기
   ↓
MainActivity
   ↓
프로필 탭에서 언제든 변경 가능
```

## 🚀 다음 단계 (선택사항)

1. 피부 타입별 추천 제품 기능
2. 피부 타입 진단 퀴즈
3. 피부 타입별 성분 가이드
4. 사용자 리뷰/히스토리 저장

## ✅ 테스트 체크리스트

- [ ] 앱 최초 실행 시 SkinTypeActivity 표시
- [ ] 피부 타입 선택 후 MainActivity로 이동
- [ ] "나중에 하기" 클릭 시 MainActivity로 이동
- [ ] 프로필 탭에서 현재 피부 타입 표시
- [ ] 프로필에서 피부 타입 변경 가능
- [ ] 분석 시 선택한 피부 타입이 API 요청에 포함
- [ ] 앱 재실행 시 온보딩 화면 표시 안 됨
- [ ] 바텀 네비게이션 4개 탭 모두 정상 동작

