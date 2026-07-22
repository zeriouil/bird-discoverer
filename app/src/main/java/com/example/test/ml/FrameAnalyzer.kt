package com.example.test.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.test.data.ClassificationResult
import com.example.test.data.ClassifierRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

data class FrameMetadata(
    val width: Int,
    val height: Int,
    val rotation: Int,
)

interface DetectionListener {
    fun onDetectionResult(
        boundingBoxes: List<Rect>,
        labels: List<String>,
        confidences: List<Float>,
        metadata: FrameMetadata,
        bestCrop: Bitmap?,
        bestResult: ClassificationResult?
    )
}

class FrameAnalyzer(
    private val classifierRepository: ClassifierRepository,
    private val listener: DetectionListener
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "FrameAnalyzer"
    }

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )

    private var isProcessing = false
    private var frameCounter = 0
    
    // Configurable parameters from Settings
    var confidenceThreshold: Float = 0.5f

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        // Process every 3rd frame to conserve battery and CPU resources
        val currentFrame = frameCounter++
        if ((currentFrame % 3) != 0) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        
        val metadata = FrameMetadata(
            width = imageProxy.width,
            height = imageProxy.height,
            rotation = rotationDegrees
        )

        objectDetector.process(inputImage)
            .addOnSuccessListener { detectedObjects ->
                if (detectedObjects.isEmpty()) {
                    // Fallback pass: If no bounding box is detected, run classification on center crop
                    try {
                        val fullBitmap = imageProxy.toBitmap()
                        val centerCrop = getCenterCrop(fullBitmap, rotationDegrees)
                        val classification = classifierRepository.classifyImage(centerCrop)
                        
                        if (classification != null && classification.confidence >= confidenceThreshold) {
                            // Synthesize a bounding box for the centered bird
                            val width = fullBitmap.width
                            val height = fullBitmap.height
                            val size = minOf(width, height)
                            val left = (width - size) / 2
                            val top = (height - size) / 2
                            val centerRect = Rect(left, top, left + size, top + size)
                            
                            listener.onDetectionResult(
                                boundingBoxes = listOf(centerRect),
                                labels = listOf(classification.species.commonName),
                                confidences = listOf(classification.confidence),
                                metadata = metadata,
                                bestCrop = centerCrop,
                                bestResult = classification
                            )
                        } else {
                            // Nothing detected with sufficient confidence
                            listener.onDetectionResult(emptyList(), emptyList(), emptyList(), metadata, null, null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback analysis error", e)
                        listener.onDetectionResult(emptyList(), emptyList(), emptyList(), metadata, null, null)
                    }
                } else {
                    // Main pass: Process detected bounding boxes
                    try {
                        val fullBitmap = imageProxy.toBitmap()
                        val boxes = mutableListOf<Rect>()
                        val labels = mutableListOf<String>()
                        val confs = mutableListOf<Float>()
                        var bestClassification: ClassificationResult? = null
                        var bestCropBitmap: Bitmap? = null
                        var highestConf = 0.0f

                        for (obj in detectedObjects) {
                            val box = obj.boundingBox
                            
                            // Clamp bounding box to frame bounds to avoid IndexOutOfBoundsException during crop
                            val left = box.left.coerceIn(0, fullBitmap.width - 1)
                            val top = box.top.coerceIn(0, fullBitmap.height - 1)
                            val right = box.right.coerceIn(1, fullBitmap.width)
                            val bottom = box.bottom.coerceIn(1, fullBitmap.height)
                            val width = right - left
                            val height = bottom - top

                            if (width <= 0 || height <= 0) continue

                            // 1. Crop bounding box
                            val cropped = Bitmap.createBitmap(fullBitmap, left, top, width, height)

                            // 2. Rotate crop to stand upright (TFLite expects upright objects)
                            val uprightCrop = if (rotationDegrees != 0) {
                                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
                            } else {
                                cropped
                            }

                            // 3. Classify bird species
                            val classification = classifierRepository.classifyImage(uprightCrop)
                            if (classification != null && classification.confidence >= confidenceThreshold) {
                                boxes.add(box)
                                labels.add(classification.species.commonName)
                                confs.add(classification.confidence)

                                if (classification.confidence > highestConf) {
                                    highestConf = classification.confidence
                                    bestClassification = classification
                                    bestCropBitmap = uprightCrop
                                }
                            }
                        }

                        listener.onDetectionResult(
                            boundingBoxes = boxes,
                            labels = labels,
                            confidences = confs,
                            metadata = metadata,
                            bestCrop = bestCropBitmap,
                            bestResult = bestClassification
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Cropping/classification error", e)
                        listener.onDetectionResult(emptyList(), emptyList(), emptyList(), metadata, null, null)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit Object Detection failed", e)
                listener.onDetectionResult(emptyList(), emptyList(), emptyList(), metadata, null, null)
            }
            .addOnCompleteListener {
                imageProxy.close()
                isProcessing = false
            }
    }

    private fun getCenterCrop(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val size = minOf(width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2
        val cropped = Bitmap.createBitmap(bitmap, left, top, size, size)
        
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        } else {
            cropped
        }
    }
}
