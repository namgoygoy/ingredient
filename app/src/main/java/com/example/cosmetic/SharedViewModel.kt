package com.example.cosmetic

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cosmetic.network.AnalyzeProductResponse

class SharedViewModel : ViewModel() {
    val recognizedText = MutableLiveData<String>()
    val parsedIngredients = MutableLiveData<List<String>>() // 파싱된 성분 리스트
    val selectedIngredient = MutableLiveData<String>() // 선택된 성분명
    val analysisResult = MutableLiveData<AnalyzeProductResponse?>()
    val isLoading = MutableLiveData<Boolean>(false)
    val errorMessage = MutableLiveData<String?>()
}

