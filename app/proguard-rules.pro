# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================================
# 보안: API 키 및 민감한 정보 보호
# ============================================================

# BuildConfig의 API 키 필드 난독화
# 주의: 완전한 보호는 아니지만 리버스 엔지니어링을 어렵게 만듭니다.
-keepclassmembers class com.example.cosmetic.BuildConfig {
    public static final java.lang.String GEMINI_API_KEY;
    public static final java.lang.String API_BASE_URL;
}

# ============================================================
# Retrofit 및 네트워크 라이브러리
# ============================================================

# Retrofit은 인터페이스를 사용하므로 keep 규칙 필요
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Retrofit 인터페이스 유지
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Retrofit 어노테이션 유지
-keepattributes *Annotation*
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================================
# Gson (JSON 직렬화)
# ============================================================

# Gson은 리플렉션을 사용하므로 모델 클래스 유지 필요
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# API 모델 클래스 유지
-keep class com.example.cosmetic.network.** { *; }

# ============================================================
# Gemini AI SDK
# ============================================================

# Gemini SDK 클래스 유지
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# ============================================================
# ML Kit
# ============================================================

-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ============================================================
# CameraX
# ============================================================

-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ============================================================
# 디버깅 정보 (선택적)
# ============================================================

# 릴리스 빌드에서도 크래시 로그를 위해 라인 번호 유지 (선택적)
# 필요하면 주석 해제
# -keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile

# ============================================================
# 기타
# ============================================================

# Kotlin 코루틴
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}