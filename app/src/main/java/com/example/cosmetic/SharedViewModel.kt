package com.example.cosmetic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cosmetic.network.AnalyzeProductResponse
import com.example.cosmetic.repository.NetworkError
import com.example.cosmetic.repository.ProductAnalysisRepository
import kotlinx.coroutines.launch

class SharedViewModel(
    private val repository: ProductAnalysisRepository? = null
) : ViewModel() {
    
    // LiveData 노출 패턴: MutableLiveData는 private으로 숨기고 LiveData만 노출
    private val _recognizedText = MutableLiveData<String>()
    val recognizedText: LiveData<String> = _recognizedText
    
    private val _parsedIngredients = MutableLiveData<List<String>>()
    val parsedIngredients: LiveData<List<String>> = _parsedIngredients
    
    private val _selectedIngredient = MutableLiveData<String>()
    val selectedIngredient: LiveData<String> = _selectedIngredient
    
    private val _analysisResult = MutableLiveData<AnalyzeProductResponse?>()
    val analysisResult: LiveData<AnalyzeProductResponse?> = _analysisResult
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    /**
     * 제품 분석을 수행합니다.
     * 
     * Repository를 통해 네트워크 호출을 수행하고 결과를 LiveData에 업데이트합니다.
     * Repository가 없으면 이 메서드는 동작하지 않습니다 (하위 호환성).
     * 
     * @param ingredients 분석할 성분명 리스트
     */
    fun analyzeProduct(ingredients: List<String>) {
        val repo = repository ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            when (val result = repo.analyzeProduct(ingredients)) {
                is kotlin.Result.Success -> {
                    _analysisResult.value = result.getOrNull()
                }
                is kotlin.Result.Failure -> {
                    val error = result.exceptionOrNull()
                    when (error) {
                        is NetworkError -> {
                            _errorMessage.value = error.getUserMessage()
                        }
                        else -> {
                            _errorMessage.value = "예상치 못한 오류가 발생했습니다."
                        }
                    }
                }
            }
            
            _isLoading.value = false
        }
    }
    
    // 하위 호환성을 위한 setter 메서드들
    fun setRecognizedText(text: String) {
        _recognizedText.value = text
    }
    
    fun setParsedIngredients(ingredients: List<String>) {
        _parsedIngredients.value = ingredients
    }
    
    fun setSelectedIngredient(ingredient: String) {
        _selectedIngredient.value = ingredient
    }
    
    fun setAnalysisResult(result: AnalyzeProductResponse?) {
        _analysisResult.value = result
    }
    
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
    
    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }
}

