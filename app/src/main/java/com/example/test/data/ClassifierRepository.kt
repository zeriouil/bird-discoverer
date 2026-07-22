package com.example.test.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class BirdSpecies(
    val scientificName: String,
    val commonName: String,
    val habitat: String,
    val funFact: String
)

data class ClassificationResult(
    val species: BirdSpecies,
    val confidence: Float,
    val index: Int
)

class ClassifierRepository(private val context: Context) {

    private companion object {
        const val TAG = "ClassifierRepository"
        const val MODEL_PATH = "birds_V1.tflite"
        const val DB_PATH = "species_db.json"
    }

    private var interpreter: Interpreter? = null
    private val speciesList = mutableListOf<BirdSpecies>()
    
    private var inputImageWidth = 224
    private var inputImageHeight = 224
    private var isQuantized = true
    private var outputSize = 965

    init {
        loadSpeciesDatabase()
        loadModel()
    }

    private fun loadSpeciesDatabase() {
        try {
            val jsonString = context.assets.open(DB_PATH).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val sciName = obj.optString("scientific_name", "")
                val comName = obj.optString("common_name", sciName)
                val habitat = obj.optString("habitat", "Varied Habitats")
                val funFact = obj.optString("fun_fact", "")
                
                speciesList.add(BirdSpecies(sciName, comName, habitat, funFact))
            }
            Log.d(TAG, "Loaded ${speciesList.size} species from local database.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading species database", e)
        }
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile(context, MODEL_PATH)
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Optimize for multi-core mobile CPUs
            }
            val interp = Interpreter(modelBuffer, options)
            interpreter = interp

            // Inspect input tensor specifications
            val inputTensor = interp.getInputTensor(0)
            val inputShape = inputTensor.shape() // Typically [1, 224, 224, 3]
            inputImageHeight = inputShape[1]
            inputImageWidth = inputShape[2]
            isQuantized = inputTensor.dataType() == DataType.UINT8

            // Inspect output tensor specifications
            val outputTensor = interp.getOutputTensor(0)
            outputSize = outputTensor.shape()[1]

            Log.d(TAG, "Model loaded: inputSize=${inputImageWidth}x${inputImageHeight}, quantized=$isQuantized, outputSize=$outputSize")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TFLite model", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Run bird classification on the provided bitmap.
     * Returns the top classification result or null if the model is not loaded or inference fails.
     */
    fun classifyImage(bitmap: Bitmap): ClassificationResult? {
        val interp = interpreter ?: return null

        try {
            // 1. Prepare the input TensorImage matching the model's required input type (UINT8 or FLOAT32)
            val inputDataType = if (isQuantized) DataType.UINT8 else DataType.FLOAT32
            var tensorImage = TensorImage(inputDataType)
            tensorImage.load(bitmap)

            // 2. Process image (Resize to required dimensions)
            // For UINT8, TensorImage handles pixel values in [0, 255] automatically.
            // For FLOAT32, we normalize to [0, 1] as standard MobileNet model float behavior.
            val imageProcessor = ImageProcessor.Builder().apply {
                add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            }.build()

            tensorImage = imageProcessor.process(tensorImage)

            // 3. Prepare the output buffer. TensorBuffer handles dequantization automatically!
            val outputTensor = interp.getOutputTensor(0)
            val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())

            // 4. Run inference
            interp.run(tensorImage.buffer, outputBuffer.buffer.rewind())

            // 5. Get float probabilities (automatically dequantized by the Support library)
            val probabilities = outputBuffer.floatArray

            // 6. Find index with highest confidence
            var maxIdx = -1
            var maxConfidence = -1.0f
            for (i in probabilities.indices) {
                if (probabilities[i] > maxConfidence) {
                    maxConfidence = probabilities[i]
                    maxIdx = i
                }
            }

            if (maxIdx in speciesList.indices) {
                val species = speciesList[maxIdx]
                return ClassificationResult(
                    species = species,
                    confidence = maxConfidence,
                    index = maxIdx
                )
            } else if (maxIdx != -1) {
                // Fallback species if index falls out of list (should not happen)
                val fallbackSpecies = BirdSpecies(
                    scientificName = "Class $maxIdx",
                    commonName = "Unknown Species $maxIdx",
                    habitat = "Varied Habitats",
                    funFact = "No detailed information available for index $maxIdx."
                )
                return ClassificationResult(
                    species = fallbackSpecies,
                    confidence = maxConfidence,
                    index = maxIdx
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during image classification", e)
        }
        return null
    }

    /**
     * Retrieve bird details from database by index
     */
    fun getSpeciesByIndex(index: Int): BirdSpecies? {
        return speciesList.getOrNull(index)
    }

    /**
     * Retrieve bird details from database by scientific name
     */
    fun getSpeciesByName(scientificName: String): BirdSpecies? {
        return speciesList.find { it.scientificName.equals(scientificName, ignoreCase = true) }
    }

    /**
     * Close the interpreter to release resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
