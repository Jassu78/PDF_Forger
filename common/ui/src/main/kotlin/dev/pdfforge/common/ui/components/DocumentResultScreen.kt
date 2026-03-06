package dev.pdfforge.common.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.pdfforge.common.ui.theme.PdfForgeTheme
import java.io.File

private val MIME_BY_KEY = mapOf(
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "txt" to "text/plain",
    "zip" to "application/zip"
)

private fun toContentUri(context: android.content.Context, uri: Uri): Uri? {
    return when (uri.scheme) {
        "file" -> {
            val file = uri.path?.let(::File) ?: return null
            if (!file.exists()) return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        else -> uri
    }
}

private fun openDocumentInExternalApp(context: android.content.Context, uri: Uri, mimeKey: String) {
    val mimeType = MIME_BY_KEY[mimeKey.lowercase()] ?: "application/octet-stream"
    val contentUri = toContentUri(context, uri) ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Open document"))
    } catch (_: android.content.ActivityNotFoundException) {}
}

private fun shareDocument(context: android.content.Context, uri: Uri, mimeKey: String) {
    val mimeType = MIME_BY_KEY[mimeKey.lowercase()] ?: "application/octet-stream"
    val contentUri = toContentUri(context, uri) ?: return
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, contentUri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Save or share document"))
    } catch (_: android.content.ActivityNotFoundException) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentResultScreen(
    documentUriString: String?,
    mimeKey: String = "docx",
    title: String = "Conversion Complete",
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uri = remember(documentUriString) {
        documentUriString?.let { Uri.decode(it) }?.let { Uri.parse(it) }
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
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (uri == null) {
                    Text(
                        "Invalid document",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Your document has been converted successfully.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Use \"Save / Share\" to copy to Drive or Downloads if your app shows \"not saved yet\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { shareDocument(context, uri, mimeKey) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save / Share")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { openDocumentInExternalApp(context, uri, mimeKey) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in App")
                    }
                }
            }
        }
    }
}
