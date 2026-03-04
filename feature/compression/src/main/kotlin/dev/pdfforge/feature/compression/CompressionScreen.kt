package dev.pdfforge.feature.compression

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pdfforge.common.ui.theme.PdfForgeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionScreen(
    viewModel: CompressionViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    PdfForgeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Compress PDF", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Source PDF Selector Card
                Card(
                    onClick = { launcher.launch("application/pdf") },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                uiState.selectedFile?.name ?: "Select source PDF",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            if (uiState.selectedFile != null) {
                                Text(
                                    "${"%.2f".format(uiState.selectedFile!!.sizeBytes / 1024.0 / 1024.0)} MB",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Compression Strategy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Strategy List
                StrategyToggle(
                    title = "Reduce Image Quality",
                    checked = uiState.strategy.reduceImageQuality,
                    color = Color(0xFF3FB950), // ForgeGreen
                    onCheckedChange = { checked ->
                        viewModel.updateStrategy { it.copy(reduceImageQuality = checked) }
                    }
                )
                StrategyToggle(
                    title = "Downscale Image",
                    checked = uiState.strategy.downscaleImages,
                    color = Color(0xFF39D353),
                    onCheckedChange = { checked ->
                        viewModel.updateStrategy { it.copy(downscaleImages = checked) }
                    }
                )
                StrategyToggle(
                    title = "Remove Metadata",
                    checked = uiState.strategy.removeMetadata,
                    color = Color(0xFF2EA043),
                    onCheckedChange = { checked ->
                        viewModel.updateStrategy { it.copy(removeMetadata = checked) }
                    }
                )
                StrategyToggle(
                    title = "Font Subsetting",
                    checked = uiState.strategy.fontSubsetting,
                    color = Color(0xFF238636),
                    onCheckedChange = { checked ->
                        viewModel.updateStrategy { it.copy(fontSubsetting = checked) }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Action Button
                Button(
                    onClick = { viewModel.compress() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3FB950) // ForgeGreen
                    ),
                    enabled = uiState.selectedFile != null && !uiState.isProcessing
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Text(
                            "Compress Now",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StrategyToggle(
    title: String,
    checked: Boolean,
    color: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (checked) androidx.compose.foundation.BorderStroke(1.dp, color) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) color else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
