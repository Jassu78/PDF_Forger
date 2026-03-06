package dev.pdfforge.common.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.pdfforge.common.ui.theme.PdfForgeTheme
import java.io.File

private fun openPdfInExternalApp(context: android.content.Context, uri: Uri) {
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
    } catch (_: android.content.ActivityNotFoundException) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfResultScreen(
    pdfUriString: String?,
    title: String = "PDF Created",
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uri = remember(pdfUriString) {
        pdfUriString?.let { Uri.decode(it) }?.let { Uri.parse(it) }
    }
    val pdfPages = remember(uri) { mutableStateListOf<Bitmap>() }
    val pageCount = remember { mutableStateOf(0) }
    val error = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        if (uri == null) {
            error.value = "Invalid PDF URI"
            return@LaunchedEffect
        }
        val pfd = when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: "")
                if (!file.exists()) { error.value = "File not found"; return@LaunchedEffect }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
        if (pfd == null) {
            error.value = "Cannot open PDF"
            return@LaunchedEffect
        }
        try {
            PdfRenderer(pfd).use { renderer ->
                pageCount.value = renderer.pageCount
                repeat(renderer.pageCount) { i ->
                    renderer.openPage(i).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pdfPages.add(bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            error.value = e.message ?: "Failed to load PDF"
        } finally {
            pfd.close()
        }
    }

    PdfForgeTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (uri != null) {
                            IconButton(onClick = { openPdfInExternalApp(context, uri) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open in app")
                            }
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
                when {
                    error.value != null -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = error.value!!,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    pdfPages.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        Text(
                            text = "${pageCount.value} page(s)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            pdfPages.forEachIndexed { index, bitmap ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(bitmap.width.toFloat() / bitmap.height),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { uri?.let { openPdfInExternalApp(context, it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in PDF Viewer")
                        }
                    }
                }
            }
        }
    }
}
