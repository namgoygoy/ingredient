package com.example.cosmetic

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import java.io.InputStream
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
        
        view.findViewById<View>(R.id.captureButton).setOnClickListener {
            takePhoto()
        }
        
        // 테스트 버튼들 설정
        view.findViewById<View>(R.id.testButton1).setOnClickListener {
            testOCRFromAssets("OCR_cosmetic_sample/image_sample.jpg")
        }
        
        view.findViewById<View>(R.id.testButton2).setOnClickListener {
            testOCRFromAssets("OCR_cosmetic_sample/image_sample2.jpg")
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
                Toast.makeText(
                    requireContext(),
                    "카메라 시작 실패: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    processImage(imageProxy)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        "사진 촬영 실패: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                        // 결과 화면으로 텍스트 전달
                        activity?.runOnUiThread {
                            sharedViewModel.recognizedText.value = recognizedText
                            findNavController().navigate(R.id.nav_results)
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "텍스트를 인식할 수 없습니다. 다시 촬영해주세요.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    imageProxy.close()
                    Toast.makeText(
                        requireContext(),
                        "텍스트 인식 실패: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            imageProxy.close()
        }
    }
    
    /**
     * Assets 폴더에서 이미지를 로드하여 OCR 테스트 수행
     */
    private fun testOCRFromAssets(assetPath: String) {
        try {
            val inputStream: InputStream = requireContext().assets.open(assetPath)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap != null) {
                val image = InputImage.fromBitmap(bitmap, 0)
                
                Toast.makeText(
                    requireContext(),
                    "OCR 처리 중...",
                    Toast.LENGTH_SHORT
                ).show()
                
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val recognizedText = visionText.text
                        
                        if (recognizedText.isNotEmpty()) {
                            // 결과 화면으로 텍스트 전달
                            sharedViewModel.recognizedText.value = recognizedText
                            findNavController().navigate(R.id.nav_results)
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "텍스트를 인식할 수 없습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            requireContext(),
                            "OCR 처리 실패: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                Toast.makeText(
                    requireContext(),
                    "이미지를 로드할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "파일 로드 실패: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
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


