package com.example.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    confidenceThreshold: Float,
    onConfidenceThresholdChange: (Float) -> Unit,
    isBackCamera: Boolean,
    onCameraChange: (Boolean) -> Unit,
    isAutoCaptureEnabled: Boolean,
    onAutoCaptureChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E272E)
                )
            )
        },
        containerColor = Color(0xFF1E272E)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E272E))
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Confidence Threshold Slider Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Threshold Icon",
                            tint = Color(0xFF00CBC6),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Confidence Threshold",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Filter classifications with lower accuracy.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(confidenceThreshold * 100).toInt()}% Minimum",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00CBC6),
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = confidenceThreshold,
                        onValueChange = onConfidenceThresholdChange,
                        valueRange = 0.1f..0.9f,
                        steps = 7, // Increment by 0.1
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF008080),
                            activeTrackColor = Color(0xFF00CBC6),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            // Camera Toggle Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraRear,
                            contentDescription = "Camera Selection",
                            tint = Color(0xFF00CBC6),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Use Back Camera",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Switch between rear and selfie cameras.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Switch(
                        checked = isBackCamera,
                        onCheckedChange = onCameraChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00CBC6),
                            checkedTrackColor = Color(0xFF008080),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            // Auto-Capture Toggle Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Auto Capture",
                            tint = Color(0xFF00CBC6),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Auto-Capture Sightings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Automatically save high-confidence detections.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Switch(
                        checked = isAutoCaptureEnabled,
                        onCheckedChange = onAutoCaptureChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00CBC6),
                            checkedTrackColor = Color(0xFF008080),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }
    }
}
