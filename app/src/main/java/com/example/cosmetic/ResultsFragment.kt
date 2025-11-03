package com.example.cosmetic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class ResultsFragment : Fragment() {
    
    private val sharedViewModel: SharedViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 인식된 텍스트 받기 (ViewModel에서)
        sharedViewModel.recognizedText.observe(viewLifecycleOwner) { recognizedText ->
            if (recognizedText.isNotEmpty()) {
                // TODO: 텍스트 분석 및 결과 표시 로직 구현
                // 임시로 인식된 텍스트 표시
                view.findViewById<android.widget.TextView>(R.id.productIngredients)?.text = recognizedText
            }
        }
    }
}


