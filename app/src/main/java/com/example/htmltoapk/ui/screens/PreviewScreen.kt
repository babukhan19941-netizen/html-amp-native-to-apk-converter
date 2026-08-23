package com.example.htmltoapk.ui.screens

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.htmltoapk.data.model.LoadedProject
import com.example.htmltoapk.data.model.ProjectType
import java.io.File

@Composable
fun PreviewScreen(project: LoadedProject, onLog: (String, String) -> Unit) {
    if (project.type == ProjectType.NATIVE_ANDROID_ZIP) {
        NativeProjectDashboard(project)
    } else {
        WebPreview(project, onLog)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPreview(project: LoadedProject, onLog: (String, String) -> Unit) {
    var consoleExpanded by remember { mutableStateOf(false) }
    val consoleLines = remember { mutableStateListOf<String>() }
    var zoomLevel by remember { mutableFloatStateOf(1f) }

    val entryFile = remember(project) {
        project.files.firstOrNull { it.relativePath == project.entryHtmlPath }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.allowFileAccess = true
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                            message?.let {
                                val line = "[${it.messageLevel()}] ${it.message()} (line ${it.lineNumber()})"
                                consoleLines.add(line)
                                onLog("JS", line)
                            }
                            return true
                        }
                    }
                    loadDataOrFile(this, project, entryFile?.textContent)
                }
            },
            update = { webView ->
                webView.setInitialScale((zoomLevel * 100).toInt().coerceIn(25, 400))
            }
        )

        // Zoom controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(4.dp)
        ) {
            IconButton(onClick = { zoomLevel = (zoomLevel - 0.1f).coerceAtLeast(0.5f) }) {
                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
            }
            IconButton(onClick = { zoomLevel = (zoomLevel + 0.1f).coerceAtMost(2f) }) {
                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
            }
        }

        // Floating JS console toggle + panel
        FloatingActionButton(
            onClick = { consoleExpanded = !consoleExpanded },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.tertiary
        ) {
            Icon(Icons.Filled.Terminal, contentDescription = "JS console")
        }

        if (consoleExpanded) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1023))
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Console", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { consoleLines.clear() }) { Text("Clear") }
                    }
                    LazyColumn {
                        items(consoleLines.reversed()) { line ->
                            Text(
                                line,
                                color = Color(0xFF10E0A0),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Loads from extracted files on disk (multi-file web zips) or straight HTML string (single-file imports). */
private fun loadDataOrFile(webView: WebView, project: LoadedProject, htmlContent: String?) {
    val onDiskEntry = project.files.firstOrNull { it.relativePath == project.entryHtmlPath }
    val diskPath = File(webView.context.filesDir, "projects/${project.name}/${project.entryHtmlPath}")
    if (diskPath.exists()) {
        webView.loadUrl("file://${diskPath.absolutePath}")
    } else if (htmlContent != null) {
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
}

@Composable
private fun NativeProjectDashboard(project: LoadedProject) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("Native Android Project", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Detected as a native project — original structure preserved as-is.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        StatCard("Package", project.detectedPackageName ?: "Not found in manifest")
        StatCard("Files detected", project.files.size.toString())
        StatCard(
            "Source files",
            project.files.count { it.relativePath.endsWith(".kt") || it.relativePath.endsWith(".java") }.toString()
        )
        StatCard("Has AndroidManifest.xml", project.files.any { it.relativePath.endsWith("AndroidManifest.xml") }.toString())
        StatCard("Has Gradle build files", project.files.any { it.relativePath.contains("build.gradle") }.toString())

        Spacer(Modifier.height(16.dp))
        Text("Detected permissions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (project.detectedNativePermissions.isEmpty()) {
            Text("None found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            project.detectedNativePermissions.forEach { perm ->
                Text("• $perm", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
