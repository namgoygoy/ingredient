package com.example.cosmetic.repository

import android.util.Log
import com.example.cosmetic.Constants.LogTag
import com.example.cosmetic.UserPreferences
import com.example.cosmetic.network.AnalyzeProductRequest
import com.example.cosmetic.network.AnalyzeProductResponse
import com.example.cosmetic.network.RAGApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 제품 분석을 위한 Repository 클래스
 * 
 * 네트워크 호출과 에러 처리를 중앙화하여 중복 코드를 제거합니다.
 * DetailsFragment와 ResultsFragment에서 공통으로 사용하는 분석 로직을 제공합니다.
 * 
 * 주요 기능:
 * - 제품 성분 분석 API 호출
 * - 통합된 에러 처리
 * - 사용자 피부 타입 자동 조회
 * 
 * @param apiService RAG API 서비스 인터페이스
 * @param userPreferences 사용자 설정 관리 클래스
 */
class ProductAnalysisRepository(
    private val apiService: RAGApiService,
    private val userPreferences: UserPreferences
) {
    
    /**
     * 제품 성분을 분석합니다.
     * 
     * 사용자의 피부 타입을 자동으로 조회하여 서버에 분석 요청을 보냅니다.
     * 모든 네트워크 에러를 통합 처리하여 Result 타입으로 반환합니다.
     * 
     * @param ingredients 분석할 성분명 리스트
     * @return 분석 결과를 포함한 Result 객체
     * 
     * @see Result 성공/실패를 나타내는 래퍼 클래스
     * @see NetworkError 네트워크 에러 타입 정의
     */
    suspend fun analyzeProduct(ingredients: List<String>): Result<AnalyzeProductResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // 사용자 피부 타입 조회
                val skinType = userPreferences.getSkinType()
                
                val request = AnalyzeProductRequest(
                    ingredients = ingredients,
                    skinType = skinType
                )
                
                val response = apiService.analyzeProduct(request).execute()
                
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    val errorMsg = response.errorBody()?.string() 
                        ?: "알 수 없는 오류가 발생했습니다."
                    Result.failure(
                        NetworkError.ServerError(
                            code = response.code(),
                            message = errorMsg
                        )
                    )
                }
            } catch (e: java.net.UnknownHostException) {
                // DNS 해석 실패 (네트워크 또는 서버 주소 문제)
                Log.e(LogTag.NETWORK, "DNS resolution failed", e)
                Result.failure(NetworkError.ConnectionFailed(e))
            } catch (e: java.net.ConnectException) {
                // 서버 연결 거부 (서버 미실행)
                Log.e(LogTag.NETWORK, "Connection refused", e)
                Result.failure(NetworkError.ConnectionRefused(e))
            } catch (e: java.net.SocketTimeoutException) {
                // 타임아웃 (서버 응답 지연)
                Log.e(LogTag.NETWORK, "Socket timeout", e)
                Result.failure(NetworkError.Timeout(e))
            } catch (e: java.io.IOException) {
                // 기타 네트워크 오류
                Log.e(LogTag.NETWORK, "Network I/O error", e)
                Result.failure(NetworkError.IOError(e))
            } catch (e: org.json.JSONException) {
                // JSON 파싱 오류 (서버 응답 형식 문제)
                Log.e(LogTag.NETWORK, "JSON parsing error", e)
                Result.failure(NetworkError.ParseError(e))
            } catch (e: Exception) {
                // 예상치 못한 예외
                Log.e(LogTag.NETWORK, "Unexpected error in analyzeProduct", e)
                Result.failure(NetworkError.Unknown(e))
            }
        }
    }
}

/**
 * 네트워크 에러를 나타내는 Sealed Class
 * 
 * 다양한 네트워크 에러 타입을 타입 안전하게 처리하기 위해 사용됩니다.
 * 각 에러 타입은 사용자에게 표시할 메시지를 제공합니다.
 */
sealed class NetworkError(val throwable: Throwable) {
    /**
     * DNS 해석 실패 또는 서버 주소 문제
     */
    class ConnectionFailed(throwable: Throwable) : NetworkError(throwable) {
        val userMessage = "서버에 연결할 수 없습니다.\n\n확인 사항:\n1. 인터넷 연결 확인\n2. 서버가 실행 중인지 확인\n3. 서버 주소가 올바른지 확인"
    }
    
    /**
     * 서버 연결 거부 (서버 미실행)
     */
    class ConnectionRefused(throwable: Throwable) : NetworkError(throwable) {
        val userMessage = "서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요."
    }
    
    /**
     * 타임아웃 (서버 응답 지연)
     */
    class Timeout(throwable: Throwable) : NetworkError(throwable) {
        val userMessage = "서버 응답 시간이 초과되었습니다. 다시 시도해주세요."
    }
    
    /**
     * 기타 네트워크 I/O 오류
     */
    class IOError(throwable: Throwable) : NetworkError(throwable) {
        val userMessage = "네트워크 오류: ${throwable.message ?: "알 수 없는 오류"}"
    }
    
    /**
     * JSON 파싱 오류
     */
    class ParseError(throwable: Throwable) : NetworkError(throwable) {
        val userMessage = "서버 응답 형식이 올바르지 않습니다."
    }
    
    /**
     * 서버 에러 (HTTP 에러 코드 포함)
     */
    class ServerError(val code: Int, message: String, throwable: Throwable? = null) : NetworkError(throwable ?: Exception(message)) {
        val userMessage = "분석 실패: $message"
    }
    
    /**
     * 예상치 못한 에러
     */
    class Unknown(throwable: Throwable) : NetworkError(throwable) {
        val userMessage = "예상치 못한 오류: ${throwable.message ?: "알 수 없는 오류"}"
    }
    
    /**
     * 사용자에게 표시할 메시지를 반환합니다.
     */
    fun getUserMessage(): String {
        return when (this) {
            is ConnectionFailed -> userMessage
            is ConnectionRefused -> userMessage
            is Timeout -> userMessage
            is IOError -> userMessage
            is ParseError -> userMessage
            is ServerError -> userMessage
            is Unknown -> userMessage
        }
    }
}

