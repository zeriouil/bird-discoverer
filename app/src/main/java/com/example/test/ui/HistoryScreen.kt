package com.example.test.ui

import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.test.data.BirdCapture
import com.example.test.data.BirdSpecies
import com.example.test.data.CaptureDatabaseHelper
import com.example.test.data.ClassifierRepository
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: AppNavController,
    dbHelper: CaptureDatabaseHelper,
    classifierRepository: ClassifierRepository
) {
    var captures by remember { mutableStateOf(emptyList<BirdCapture>()) }

    // Fetch captures on load
    LaunchedEffect(Unit) {
        captures = dbHelper.getAllCaptures()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Sighting History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E272E)
                )
            )
        },
        containerColor = Color(0xFF1E272E)
    ) { innerPadding ->
        if (captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF1E272E)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Sightings Yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your identified birds will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E272E))
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(captures, key = { it.id }) { capture ->
                    HistoryCard(
                        capture = capture,
                        onCardClick = {
                            // Look up detailed species information from the repository
                            val species = classifierRepository.getSpeciesByName(capture.scientificName) ?: BirdSpecies(
                                scientificName = capture.scientificName,
                                commonName = capture.speciesName,
                                habitat = "Varied Habitats",
                                funFact = "This sighting was recorded with a confidence score of ${(capture.confidence * 100).toInt()}%."
                            )
                            navController.navigateTo(Screen.SpeciesInfo(species))
                        },
                        onDeleteClick = {
                            dbHelper.deleteCapture(capture.id)
                            // Delete the local file to save storage space
                            try {
                                val file = File(capture.imagePath)
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            // Refresh list
                            captures = dbHelper.getAllCaptures()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(
    capture: BirdCapture,
    onCardClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3E50)) // Deep slate blue card
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bird Thumbnail
            AsyncImage(
                model = File(capture.imagePath),
                contentDescription = capture.speciesName,
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = capture.speciesName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = capture.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = Color.LightGray
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Date
                val dateString = DateFormat.format("MMM dd, yyyy h:mm a", Date(capture.timestamp)).toString()
                Text(
                    text = dateString,
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                // Location tag
                if (capture.latitude != null && capture.longitude != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = Color(0xFF00CBC6),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.4f, %.4f", capture.latitude, capture.longitude),
                            color = Color(0xFF00CBC6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Right column: Confidence & Delete Action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                // Confidence badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF008080).copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${(capture.confidence * 100).toInt()}%",
                        color = Color(0xFF00CBC6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Delete button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
