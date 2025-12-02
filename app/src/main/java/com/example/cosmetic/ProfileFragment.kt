package com.example.cosmetic

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class ProfileFragment : Fragment() {
    
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userPreferences = UserPreferences(requireContext())
        
        // 현재 피부 타입 표시
        val currentSkinTypeText = view.findViewById<TextView>(R.id.currentSkinType)
        val skinType = userPreferences.getSkinType()
        currentSkinTypeText.text = skinType
        
        // 피부 타입 변경 클릭
        view.findViewById<View>(R.id.skinTypeContainer)?.setOnClickListener {
            showSkinTypeDialog()
        }
        
        // 앱 버전 표시
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            view.findViewById<TextView>(R.id.appVersion)?.text = packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            view.findViewById<TextView>(R.id.appVersion)?.text = "1.0.0"
        }
        
        // 사용 방법
        view.findViewById<CardView>(R.id.howToUse)?.setOnClickListener {
            showHowToUseDialog()
        }
        
        // 문의하기
        view.findViewById<CardView>(R.id.contactUs)?.setOnClickListener {
            Toast.makeText(requireContext(), "문의: cosmetic@example.com", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 피부 타입 선택 다이얼로그 표시 (다중 선택)
     */
    private fun showSkinTypeDialog() {
        val skinTypes = arrayOf(
            UserPreferences.SKIN_TYPE_DRY,
            UserPreferences.SKIN_TYPE_OILY,
            UserPreferences.SKIN_TYPE_COMBINATION,
            UserPreferences.SKIN_TYPE_SENSITIVE,
            UserPreferences.SKIN_TYPE_NORMAL
        )
        
        // 현재 선택된 피부 타입들 (콤마로 분리)
        val currentSkinType = userPreferences.getSkinType()
        val selectedSkinTypes = if (currentSkinType == UserPreferences.SKIN_TYPE_UNKNOWN) {
            emptySet()
        } else {
            currentSkinType.split(", ").toSet()
        }
        
        // 체크 상태 배열 생성
        val checkedItems = BooleanArray(skinTypes.size) { index ->
            selectedSkinTypes.contains(skinTypes[index])
        }
        
        val tempSelectedTypes = mutableSetOf<String>()
        tempSelectedTypes.addAll(selectedSkinTypes)
        
        AlertDialog.Builder(requireContext())
            .setTitle("피부 타입 선택 (복수 선택 가능)")
            .setMultiChoiceItems(skinTypes, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    tempSelectedTypes.add(skinTypes[which])
                } else {
                    tempSelectedTypes.remove(skinTypes[which])
                }
            }
            .setPositiveButton("확인") { dialog, _ ->
                if (tempSelectedTypes.isNotEmpty()) {
                    val newSkinType = tempSelectedTypes.joinToString(", ")
                    userPreferences.setSkinType(newSkinType)
                    
                    // UI 업데이트
                    view?.findViewById<TextView>(R.id.currentSkinType)?.text = newSkinType
                    
                    Toast.makeText(
                        requireContext(),
                        "피부 타입이 변경되었습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // 아무것도 선택하지 않은 경우
                    userPreferences.setSkinType(UserPreferences.SKIN_TYPE_UNKNOWN)
                    view?.findViewById<TextView>(R.id.currentSkinType)?.text = UserPreferences.SKIN_TYPE_UNKNOWN
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    /**
     * 사용 방법 다이얼로그 표시
     */
    private fun showHowToUseDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("사용 방법")
            .setMessage("""
                1. 스캔 탭에서 화장품 성분 라벨을 촬영하세요
                
                2. OCR이 자동으로 성분을 인식합니다
                
                3. 결과 탭에서 전체 제품 분석을 확인하세요
                
                4. 개별 성분을 탭하면 상세 정보를 볼 수 있습니다
                
                5. 프로필에서 피부 타입을 설정하면 맞춤형 분석을 받을 수 있습니다
            """.trimIndent())
            .setPositiveButton("확인", null)
            .show()
    }
}

