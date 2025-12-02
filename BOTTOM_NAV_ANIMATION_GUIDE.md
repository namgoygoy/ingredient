# 바텀 네비게이션 애니메이션 가이드

## 구현된 기능

### 1. 바운스 효과 (Bouncy Spring Animation)
아이콘 클릭 시 다음과 같은 애니메이션이 실행됩니다:
- **0.8배 축소** (100ms) → **1.2배 확대** (150ms) → **1.0배 복귀** (100ms)
- `OvershootInterpolator(2.0f)`를 사용한 스프링 탄성 효과

### 2. 색상 변경 (Color Change)
선택된 아이콘과 텍스트의 색상이 자동으로 변경됩니다:
- **아이콘 색상**: 형광 초록색 (`#00FF7F`, 선택 시) / 회색 (기본)
- **텍스트 색상**: 검정색 (항상 동일)

---

## 파일 구조

### 1. `colors.xml`
```xml
<color name="vibrant_green">#00FF7F</color>
<color name="nav_icon_default">#888888</color>
<color name="nav_text_black">#000000</color>
```

### 2. `drawable/bottom_nav_icon_color.xml` (아이콘 색상)
```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/vibrant_green" android:state_checked="true" />
    <item android:color="@color/nav_icon_default" />
</selector>
```

### 3. `drawable/bottom_nav_text_color.xml` (텍스트 색상)
```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/nav_text_black" android:state_checked="true" />
    <item android:color="@color/nav_text_black" />
</selector>
```

### 4. `activity_main.xml`
```xml
<BottomNavigationView
    app:itemIconTint="@drawable/bottom_nav_icon_color"
    app:itemTextColor="@drawable/bottom_nav_text_color"
    ... />
```

### 5. `MainActivity.kt`
- `setupBounceAnimation()`: 애니메이션 설정
- `playBounceAnimation()`: 바운스 효과 실행

---

## 애니메이션 파라미터

### 바운스 효과
```kotlin
// 1단계: 축소
scaleX: 1.0f → 0.8f (100ms)
scaleY: 1.0f → 0.8f (100ms)

// 2단계: 확대 (스프링 효과)
scaleX: 0.8f → 1.2f (150ms, OvershootInterpolator(2.0f))
scaleY: 0.8f → 1.2f (150ms, OvershootInterpolator(2.0f))

// 3단계: 복귀
scaleX: 1.2f → 1.0f (100ms)
scaleY: 1.2f → 1.0f (100ms)
```

### 색상 변경
```xml
<!-- selector로 자동 처리 -->
<!-- 선택 시: 형광 초록색 (#00FF7F) -->
<!-- 기본: 회색 (#888888) -->
```

---

## 커스터마이징

### 1. 바운스 강도 조절

**현재**:
```kotlin
OvershootInterpolator(2.0f) // 탄성 계수
```

**강하게**:
```kotlin
OvershootInterpolator(3.0f) // 더 큰 탄성
```

**약하게**:
```kotlin
OvershootInterpolator(1.0f) // 부드러운 탄성
```

### 2. 애니메이션 속도 조절

**현재**:
```kotlin
scaleDownX.duration = 100  // 축소 100ms
scaleUpX.duration = 150    // 확대 150ms
scaleNormalX.duration = 100 // 복귀 100ms
```

**빠르게**:
```kotlin
scaleDownX.duration = 50
scaleUpX.duration = 100
scaleNormalX.duration = 50
```

**느리게**:
```kotlin
scaleDownX.duration = 200
scaleUpX.duration = 300
scaleNormalX.duration = 200
```

### 3. 색상 변경

`colors.xml`에서:
```xml
<!-- 형광 핑크 -->
<color name="vibrant_green">#FF1493</color>

<!-- 형광 파랑 -->
<color name="vibrant_green">#00BFFF</color>

<!-- 형광 노랑 -->
<color name="vibrant_green">#FFFF00</color>
```

### 4. Glow 효과 강도

**현재**:
```kotlin
ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.7f, 1.0f, 0.85f, 1.0f)
```

**강한 Glow**:
```kotlin
ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.5f, 1.0f, 0.7f, 1.0f)
```

**약한 Glow**:
```kotlin
ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.9f, 1.0f)
```

---

## 트러블슈팅

### Q1: 애니메이션이 실행되지 않아요
**A**: `MainActivity.kt`에서 `setupBounceAnimation()`이 호출되는지 확인하세요.

### Q2: 색상이 변경되지 않아요
**A**: `activity_main.xml`에 `app:itemIconTint`과 `app:itemTextColor`가 설정되어 있는지 확인하세요.

### Q3: 애니메이션이 너무 빨라요/느려요
**A**: `MainActivity.kt`의 `duration` 값을 조절하세요.

### Q4: 바운스가 너무 강해요/약해요
**A**: `OvershootInterpolator`의 파라미터를 조절하세요 (1.0 ~ 3.0 권장).

### Q5: 아이콘 색상을 변경하고 싶어요
**A**: `colors.xml`에서 `vibrant_green` 색상을 변경하세요.

---

## 기술 상세

### ObjectAnimator
- `scaleX`, `scaleY`: 크기 조절
- `alpha`: 투명도 조절
- `duration`: 애니메이션 지속 시간
- `interpolator`: 애니메이션 곡선

### OvershootInterpolator
- 스프링 탄성 효과
- 파라미터: 탄성 계수 (값이 클수록 더 큰 탄성)

### AnimatorSet
- 여러 애니메이션을 순서대로 실행
- `play().with()`: 동시 실행
- `play().after()`: 순차 실행

---

## 참고 자료

- [Android ObjectAnimator 공식 문서](https://developer.android.com/reference/android/animation/ObjectAnimator)
- [AnimatorSet 가이드](https://developer.android.com/guide/topics/graphics/prop-animation)
- [Interpolator 종류](https://developer.android.com/reference/android/view/animation/Interpolator)

---

**작성일**: 2025년 1월  
**버전**: 1.0

