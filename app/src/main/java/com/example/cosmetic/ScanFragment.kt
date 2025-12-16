package com.example.cosmetic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.cosmetic.Constants.LogTag.SCAN_FRAGMENT
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.camera.core.*
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.util.concurrent.Executors

class ScanFragment : Fragment() {
    
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()
    // 한글 인식을 위한 KoreanTextRecognizerOptions 사용
    private val textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val sharedViewModel: SharedViewModel by activityViewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_PERMISSIONS
            )
        }
        
        // 촬영 버튼 (실제 카메라 촬영)
        view.findViewById<View>(R.id.captureButton).setOnClickListener {
            takePhoto()
        }
    }
    
    private fun allPermissionsGranted() = 
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "카메라 권한이 필요합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun startCamera() {
        val previewView = view?.findViewById<PreviewView>(R.id.previewView) ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(SCAN_FRAGMENT, "카메라 시작 실패: ${e.message}", e)
                Toast.makeText(
                    requireContext(),
                    "카메라 시작 실패: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(
                requireContext(),
                "카메라가 준비되지 않았습니다. 잠시 후 다시 시도해주세요.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // 촬영 시작 알림
        Toast.makeText(
            requireContext(),
            "📸 촬영 중...",
            Toast.LENGTH_SHORT
        ).show()
        
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "✅ 촬영 완료! OCR 처리 중...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    processImage(imageProxy)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "❌ 사진 촬영 실패: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val recognizedText = visionText.text
                    imageProxy.close()
                    
                    if (recognizedText.isNotEmpty()) {
                        // 상세 화면으로 텍스트 전달
                        activity?.runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "✅ 텍스트 인식 완료! 분석 화면으로 이동합니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                            sharedViewModel.recognizedText.value = recognizedText
                            findNavController().navigate(R.id.action_nav_scan_to_nav_results)
                        }
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "❌ 텍스트를 인식할 수 없습니다.\n\n다시 촬영해주세요.\n💡 조명이 밝고 글자가 선명한지 확인하세요.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    imageProxy.close()
                    activity?.runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "❌ 텍스트 인식 실패: ${e.message}\n\n다시 시도해주세요.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        } else {
            imageProxy.close()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cameraProvider?.unbindAll()
    }
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}


