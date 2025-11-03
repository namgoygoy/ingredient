package com.example.cosmetic

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    val recognizedText = MutableLiveData<String>()
}

