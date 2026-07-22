package com.example.test.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.test.data.CaptureDatabaseHelper
import com.example.test.data.ClassifierRepository
import com.example.test.ml.DetectionListener
import com.example.test.ml.FrameAnalyzer
import com.example.test.ml.FrameMetadata
import com.google.android.gms.location.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun DetectorScreen(
    navController: AppNavController,
    classifierRepository: ClassifierRepository,
    dbHelper: CaptureDatabaseHelper,
    confidenceThreshold: Float,
    isBackCamera: Boolean,
    isAutoCaptureEnabled: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for live detections
    var boundingBoxes by remember { mutableStateOf(emptyList<Rect>()) }
    var labels by remember { mutableStateOf(emptyList<String>()) }
    var confidences by remember { mutableStateOf(emptyList<Float>()) }
    var frameMetadata by remember { mutableStateOf<FrameMetadata?>(null) }
    var activeCrop by remember { mutableStateOf<Bitmap?>(null) }
    var activeResult by remember { mutableStateOf<com.example.test.data.ClassificationResult?>(null) }

    // Auto-capture debounce logic
    var lastAutoCaptureTime by remember { mutableStateOf(0L) }

    // Camera Executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Location provider
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var lastKnownLocation by remember { mutableStateOf<Location?>(null) }

    // Permission launcher
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    // Request permissions and start location updates
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Fetch location (if permitted)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) lastKnownLocation = loc
                }
            } catch (e: SecurityException) {
                Log.e("DetectorScreen", "SecurityException fetching location", e)
            }
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This screen needs camera access to detect and identify birds in real time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008080))
                ) {
                    Text("Grant Permission")
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Camera preview and analysis
            val frameAnalyzer = remember(classifierRepository) {
                FrameAnalyzer(classifierRepository, object : DetectionListener {
                    override fun onDetectionResult(
                        boxes: List<Rect>,
                        lbls: List<String>,
                        confs: List<Float>,
                        metadata: FrameMetadata,
                        bestCrop: Bitmap?,
                        bestResult: com.example.test.data.ClassificationResult?
                    ) {
                        boundingBoxes = boxes
                        labels = lbls
                        confidences = confs
                        frameMetadata = metadata
                        activeCrop = bestCrop
                        activeResult = bestResult

                        // Auto capture logic: If auto capture is enabled, confidence matches threshold,
                        // and we haven't captured in the last 5 seconds to avoid flooding
                        if (isAutoCaptureEnabled && bestResult != null && bestResult.confidence >= confidenceThreshold) {
                            val now = System.currentTimeMillis()
                            if (now - lastAutoCaptureTime > 5000L) {
                                lastAutoCaptureTime = now
                                saveCapture(context, dbHelper, bestResult, bestCrop, lastKnownLocation)
                            }
                        }
                    }
                })
            }

            // Sync threshold to analyzer
            frameAnalyzer.confidenceThreshold = confidenceThreshold

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor, frameAnalyzer)

                        val cameraSelector = if (isBackCamera) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("DetectorScreen", "Use case binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    // Update camera selector dynamically if isBackCamera changes
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    if (cameraProviderFuture.isDone) {
                        val cameraProvider = cameraProviderFuture.get()
                        val cameraSelector = if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        val preview = Preview.Builder().build()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysis.setAnalyzer(cameraExecutor, frameAnalyzer)

                        // Rebind
                        try {
                            cameraProvider.unbindAll()
                            // Re-bind to lifecycle
                            // We need to fetch the previewView from the factory, but update is run on the view itself.
                            // In this case, AndroidView update block runs with the previewView as `it`
                            preview.setSurfaceProvider(it.surfaceProvider)
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("DetectorScreen", "Dynamic switch failed", e)
                        }
                    }
                }
            )

            // 2. Bounding Box Overlay Canvas
            val metadata = frameMetadata
            if (metadata != null && boundingBoxes.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // Match rotation dimensions
                    val rotatedWidth = if (metadata.rotation % 180 != 0) metadata.height else metadata.width
                    val rotatedHeight = if (metadata.rotation % 180 != 0) metadata.width else metadata.height

                    // Calculate scale factor (CameraX fills center)
                    val scaleX = canvasWidth / rotatedWidth.toFloat()
                    val scaleY = canvasHeight / rotatedHeight.toFloat()
                    val scale = maxOf(scaleX, scaleY)

                    val offsetX = (canvasWidth - rotatedWidth * scale) / 2f
                    val offsetY = (canvasHeight - rotatedHeight * scale) / 2f

                    for (i in boundingBoxes.indices) {
                        val box = boundingBoxes[i]
                        val label = labels.getOrNull(i) ?: "Bird"
                        val conf = confidences.getOrNull(i) ?: 0.0f

                        // Calculate bounds on canvas
                        val left = box.left * scale + offsetX
                        val top = box.top * scale + offsetY
                        val right = box.right * scale + offsetX
                        val bottom = box.bottom * scale + offsetY

                        // Draw bounding box
                        drawRect(
                            color = Color(0xFF00CBC6), // Teal accent color
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }

            // 3. UI Overlays (Quick Sighting Card & Manual Capture Button)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp), // Height of navigation bar
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Info Bar (Auto-Capture & GPS status indicators)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: GPS indicator
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (lastKnownLocation != null) Color.Green else Color.Red,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lastKnownLocation != null) "GPS Tagged" else "No GPS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Right: Auto capture indicator
                    if (isAutoCaptureEnabled) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF008080).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Auto Capture On",
                                tint = Color.Yellow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auto-Capture Active",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom Content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Dynamic result card with glassmorphism design
                    AnimatedVisibility(
                        visible = activeResult != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        val result = activeResult
                        if (result != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .clickable { navController.navigateTo(Screen.SpeciesInfo(result.species)) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xAA1E272E)) // Dark glassmorphic background
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.08f),
                                                    Color.White.copy(alpha = 0.02f)
                                                )
                                            )
                                        )
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.species.commonName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = result.species.scientificName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Confidence: ${(result.confidence * 100).toInt()}%",
                                            color = Color(0xFF00CBC6),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { navController.navigateTo(Screen.SpeciesInfo(result.species)) },
                                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Details",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Manual Sighting Capture Button
                    FloatingActionButton(
                        onClick = {
                            val result = activeResult
                            val crop = activeCrop
                            if (result != null && crop != null) {
                                saveCapture(context, dbHelper, result, crop, lastKnownLocation)
                                // Show dynamic feedback
                                Log.d("DetectorScreen", "Manual sighting saved: ${result.species.commonName}")
                            } else {
                                Log.d("DetectorScreen", "No bird in frame to save")
                            }
                        },
                        containerColor = if (activeResult != null) Color(0xFF008080) else Color(0x4D1E272E),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Capture Sighting",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun saveCapture(
    context: Context,
    dbHelper: CaptureDatabaseHelper,
    result: com.example.test.data.ClassificationResult,
    crop: Bitmap?,
    location: Location?
) {
    if (crop == null) return
    
    // Save thumbnail crop to app private files directory
    val filename = "bird_${System.currentTimeMillis()}.jpg"
    val file = File(context.filesDir, filename)
    try {
        FileOutputStream(file).use { out ->
            crop.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // Save metadata into database
        dbHelper.insertCapture(
            speciesName = result.species.commonName,
            scientificName = result.species.scientificName,
            confidence = result.confidence,
            imagePath = file.absolutePath,
            latitude = location?.latitude,
            longitude = location?.longitude
        )
    } catch (e: Exception) {
        Log.e("DetectorScreen", "Failed to save capture photo", e)
    }
}
