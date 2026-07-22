package com.example.test

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.test.data.CaptureDatabaseHelper
import com.example.test.data.ClassifierRepository
import com.example.test.ui.*
import com.example.test.ui.theme.TestTheme

class MainActivity : ComponentActivity() {

    private companion object {
        const val PREFS_NAME = "bird_detector_prefs"
        const val KEY_CONFIDENCE = "confidence_threshold"
        const val KEY_CAMERA = "use_back_camera"
        const val KEY_AUTO_CAPTURE = "enable_auto_capture"
    }

    private lateinit var classifierRepository: ClassifierRepository
    private lateinit var dbHelper: CaptureDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize database and machine learning classifier
        classifierRepository = ClassifierRepository(this)
        dbHelper = CaptureDatabaseHelper(this)

        // Request runtime permissions on launch
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val camera = permissions[Manifest.permission.CAMERA] ?: false
            val location = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            Log.d("MainActivity", "Permissions results: camera=$camera, location=$location")
        }
        
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        // Load persisted settings
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val initialConfidence = try {
            sharedPrefs.getFloat(KEY_CONFIDENCE, 0.5f)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reading confidence threshold", e)
            0.5f
        }
        val initialCamera = try {
            sharedPrefs.getBoolean(KEY_CAMERA, true)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reading camera preference", e)
            true
        }
        val initialAutoCapture = try {
            sharedPrefs.getBoolean(KEY_AUTO_CAPTURE, false)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reading auto capture preference", e)
            false
        }

        setContent {
            TestTheme {
                var confidenceThreshold by remember { mutableFloatStateOf(initialConfidence) }
                var isBackCamera by remember { mutableStateOf(initialCamera) }
                var isAutoCaptureEnabled by remember { mutableStateOf(initialAutoCapture) }

                val navController = remember { AppNavController(Screen.Detector) }

                // Intercept back button for detail screens
                BackHandler(enabled = navController.canNavigateBack()) {
                    navController.navigateBack()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Render bottom navigation bar if not on detail screens (like SpeciesInfoScreen)
                        if (navController.currentScreen !is Screen.SpeciesInfo) {
                            BottomNavBar(
                                currentScreen = navController.currentScreen,
                                onTabSelected = { navController.navigateTo(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (val screen = navController.currentScreen) {
                            is Screen.Detector -> {
                                DetectorScreen(
                                    navController = navController,
                                    classifierRepository = classifierRepository,
                                    dbHelper = dbHelper,
                                    confidenceThreshold = confidenceThreshold,
                                    isBackCamera = isBackCamera,
                                    isAutoCaptureEnabled = isAutoCaptureEnabled
                                )
                            }
                            is Screen.History -> {
                                HistoryScreen(
                                    navController = navController,
                                    dbHelper = dbHelper,
                                    classifierRepository = classifierRepository
                                )
                            }
                            is Screen.Settings -> {
                                SettingsScreen(
                                    confidenceThreshold = confidenceThreshold,
                                    onConfidenceThresholdChange = {
                                        confidenceThreshold = it
                                        sharedPrefs.edit { putFloat(KEY_CONFIDENCE, it) }
                                    },
                                    isBackCamera = isBackCamera,
                                    onCameraChange = {
                                        isBackCamera = it
                                        sharedPrefs.edit { putBoolean(KEY_CAMERA, it) }
                                    },
                                    isAutoCaptureEnabled = isAutoCaptureEnabled,
                                    onAutoCaptureChange = {
                                        isAutoCaptureEnabled = it
                                        sharedPrefs.edit { putBoolean(KEY_AUTO_CAPTURE, it) }
                                    }
                                )
                            }
                            is Screen.SpeciesInfo -> {
                                SpeciesInfoScreen(
                                    navController = navController,
                                    species = screen.species
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classifierRepository.close()
    }
}

@Composable
fun BottomNavBar(
    currentScreen: Screen,
    onTabSelected: (Screen) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(Color(0xFF1E272E)),
        containerColor = Color(0xFF1E272E),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen is Screen.Detector,
            onClick = { onTabSelected(Screen.Detector) },
            icon = { Icon(Icons.Default.Visibility, contentDescription = "Detector") },
            label = { Text("Detector") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00CBC6),
                selectedTextColor = Color(0xFF00CBC6),
                indicatorColor = Color(0x1A00CBC6),
                unselectedIconColor = Color.LightGray,
                unselectedTextColor = Color.LightGray
            )
        )
        NavigationBarItem(
            selected = currentScreen is Screen.History,
            onClick = { onTabSelected(Screen.History) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History") },
            label = { Text("History") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00CBC6),
                selectedTextColor = Color(0xFF00CBC6),
                indicatorColor = Color(0x1A00CBC6),
                unselectedIconColor = Color.LightGray,
                unselectedTextColor = Color.LightGray
            )
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Settings,
            onClick = { onTabSelected(Screen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00CBC6),
                selectedTextColor = Color(0xFF00CBC6),
                indicatorColor = Color(0x1A00CBC6),
                unselectedIconColor = Color.LightGray,
                unselectedTextColor = Color.LightGray
            )
        )
    }
}