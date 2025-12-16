# 🔧 설정 가이드

## API Base URL 설정

리팩토링 후 API Base URL은 `local.properties` 파일에서 관리됩니다.

### 설정 방법

1. 프로젝트 루트에 `local.properties` 파일이 있는지 확인합니다.

2. 다음 내용을 추가하거나 수정합니다:

```properties
# Gemini API Key
GEMINI_API_KEY=your_gemini_api_key_here

# API Base URL (백엔드 서버 주소)
API_BASE_URL=https://your-server-url.com/
```

### 예시

#### 개발 환경 (ngrok 사용)
```properties
API_BASE_URL=https://prefearfully-bimanous-carmon.ngrok-free.dev/
```

#### 로컬 개발
```properties
API_BASE_URL=http://localhost:5000/
```

#### 프로덕션 환경
```properties
API_BASE_URL=https://api.cosmetic-app.com/
```

### 주의사항

- `local.properties` 파일은 `.gitignore`에 포함되어 있어 Git에 커밋되지 않습니다.
- 각 개발자는 자신의 환경에 맞게 설정해야 합니다.
- URL 끝에 슬래시(`/`)를 포함해야 합니다.

### 빌드 확인

설정이 올바르게 되었는지 확인하려면:

1. Android Studio에서 `Build > Rebuild Project` 실행
2. `app/build/generated/source/buildConfig/.../BuildConfig.java` 파일에서 확인:
   ```java
   public static final String API_BASE_URL = "https://your-server-url.com/";
   ```

---

## 변경 사항 요약

### ✅ 해결된 문제

1. **하드코딩된 Base URL 제거**
   - `RetrofitClient.kt`에서 하드코딩된 URL 제거
   - `BuildConfig.API_BASE_URL`로 변경
   - `local.properties`에서 관리

2. **중복 코드 제거**
   - `ProductAnalysisRepository` 클래스 생성
   - `DetailsFragment`와 `ResultsFragment`의 중복된 `analyzeProduct` 메서드 제거
   - 통합된 에러 처리 (`NetworkError` sealed class)

3. **LiveData 노출 패턴 개선**
   - `SharedViewModel`에서 `MutableLiveData`를 private으로 변경
   - `LiveData`만 노출하여 외부에서 수정 불가능하도록 개선

### 📁 새로 생성된 파일

- `app/src/main/java/com/example/cosmetic/repository/ProductAnalysisRepository.kt`
  - 네트워크 호출과 에러 처리를 중앙화한 Repository 클래스

### 🔄 수정된 파일

- `app/build.gradle.kts` - `API_BASE_URL` BuildConfig 필드 추가
- `app/src/main/java/com/example/cosmetic/network/RetrofitClient.kt` - BuildConfig에서 URL 읽기
- `app/src/main/java/com/example/cosmetic/SharedViewModel.kt` - LiveData 노출 패턴 개선
- `app/src/main/java/com/example/cosmetic/DetailsFragment.kt` - Repository 사용으로 변경
- `app/src/main/java/com/example/cosmetic/ResultsFragment.kt` - Repository 사용으로 변경
- `app/src/main/java/com/example/cosmetic/Constants.kt` - NETWORK 로그 태그 추가

---

## 다음 단계

리팩토링이 완료되었습니다. 다음을 확인하세요:

1. ✅ `local.properties`에 `API_BASE_URL` 설정
2. ✅ 프로젝트 Rebuild
3. ✅ 앱 실행 및 네트워크 호출 테스트

