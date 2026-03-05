package dev.pdfforge.feature.merge_split

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pdfforge.common.ui.components.ProgressScreen
import dev.pdfforge.common.ui.components.ResultScreen
import dev.pdfforge.common.ui.theme.PdfForgeTheme
import dev.pdfforge.domain.models.PdfDocument
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    viewModel: MergePdfViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.onFilesSelected(uris)
    }

    if (uiState.isProcessing) {
        ProgressScreen(
            title = "Merging PDFs...",
            statusText = uiState.statusText,
            progress = uiState.progress,
            onCancel = { viewModel.cancelOperation() }
        )
    } else if (uiState.resultUri != null || uiState.error != null) {
        ResultScreen(
            isSuccess = uiState.resultUri != null,
            message = if (uiState.resultUri != null) "Multiple PDFs merged successfully into one." else uiState.error ?: "An unknown error occurred.",
            resultUri = uiState.resultUri,
            onDone = { viewModel.clearResult() },
            onOpenFile = { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open Merged PDF"))
            }
        )
    } else {
        PdfForgeTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Merge PDFs", fontWeight = FontWeight.Bold) },
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
                    // Add File Button
                    Button(
                        onClick = { launcher.launch("application/pdf") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add PDF File", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // PDF List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(uiState.selectedFiles) { index, file ->
                            PdfFileItem(
                                file = file,
                                onRemove = { viewModel.removeFile(file) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.selectedFiles.isNotEmpty()) {
                        Text(
                            "Drag items to reorder",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Action Button
                    Button(
                        onClick = { viewModel.mergeFiles("Merged_Document_${System.currentTimeMillis()}.pdf") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF7B72) // ForgeRed matching UI
                        ),
                        enabled = uiState.selectedFiles.size >= 2
                    ) {
                        Text(
                            "Merge All",
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
fun PdfFileItem(
    file: PdfDocument,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${"%.2f".format(file.sizeBytes / 1024.0 / 1024.0)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove")
            }
        }
    }
}
