package dev.pdfforge.feature.pdf_creation

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.pdfforge.common.ui.components.ProgressScreen
import dev.pdfforge.common.ui.theme.PdfForgeTheme
import dev.pdfforge.domain.core.tools.PageSize
import java.io.File

private fun openPdfWithViewer(context: android.content.Context, uri: Uri) {
    val contentUri = when (uri.scheme) {
        "file" -> {
            val file = File(uri.path ?: return)
            if (!file.exists()) return
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        else -> uri
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    } catch (_: android.content.ActivityNotFoundException) {
        // No PDF viewer installed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    viewModel: ImageToPdfViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.onImagesSelected(uris)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.resultUri) {
        uiState.resultUri?.let { uri ->
            snackbarHostState.showSnackbar(
                message = "PDF created successfully.",
                duration = SnackbarDuration.Short,
                actionLabel = "Open",
                withDismissAction = true
            ).let { result ->
                if (result == SnackbarResult.ActionPerformed) {
                    openPdfWithViewer(context, uri)
                }
            }
            viewModel.clearResult()
        }
    }

    if (uiState.isProcessing) {
        ProgressScreen(
            title = "Creating PDF...",
            statusText = uiState.statusText,
            progress = uiState.progress,
            onCancel = { viewModel.cancelOperation() }
        )
    } else {
        PdfForgeTheme {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text("Image to PDF", fontWeight = FontWeight.Bold) },
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
                    // Selection Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { launcher.launch("image/*") }
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap to Select Images",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Image Preview List
                    if (uiState.selectedImages.isNotEmpty()) {
                        Text(
                            "${uiState.selectedImages.size} images selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(uiState.selectedImages) { uri ->
                                ImageThumbnail(
                                    uri = uri,
                                    onRemove = { viewModel.onRemoveImage(uri) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Action Button
                    Button(
                        onClick = { viewModel.createPdf("Forger_Export_${System.currentTimeMillis()}.pdf") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = uiState.selectedImages.isNotEmpty()
                    ) {
                        Text(
                            "Create PDF",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageThumbnail(
    uri: Uri,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Gray)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
