package com.example.htmltoapk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.example.htmltoapk.data.AppViewModel
import com.example.htmltoapk.data.MainTab
import com.example.htmltoapk.ui.screens.*
import com.example.htmltoapk.ui.theme.HtmlToApkTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HtmlToApkTheme {
                Surface {
                    AppRoot(viewModel = viewModel, onOpenUrl = ::openUrl, onShareZip = ::shareZip)
                }
            }
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun shareZip(path: String) {
        val file = File(path)
        val uri = FileProvider.getUriForFile(this, "com.example.htmltoapk.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share project ZIP"))
    }
}

@Composable
fun AppRoot(viewModel: AppViewModel, onOpenUrl: (String) -> Unit, onShareZip: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val project = state.project

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.config?.appName ?: "HTML/Native \u2192 APK Converter") }
            )
        },
        bottomBar = {
            if (project != null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.currentTab == MainTab.PREVIEW,
                        onClick = { viewModel.selectTab(MainTab.PREVIEW) },
                        icon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                        label = { Text("Preview") }
                    )
                    NavigationBarItem(
                        selected = state.currentTab == MainTab.CODE,
                        onClick = { viewModel.selectTab(MainTab.CODE) },
                        icon = { Icon(Icons.Filled.Code, contentDescription = null) },
                        label = { Text("Code") }
                    )
                    NavigationBarItem(
                        selected = state.currentTab == MainTab.CONFIG,
                        onClick = { viewModel.selectTab(MainTab.CONFIG) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Config") }
                    )
                    NavigationBarItem(
                        selected = state.currentTab == MainTab.GITHUB,
                        onClick = { viewModel.selectTab(MainTab.GITHUB) },
                        icon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                        label = { Text("GitHub") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (project != null && state.currentTab != MainTab.GITHUB) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.exportLocalZip() },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    text = { Text("Export ZIP") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                project == null -> HomeScreen(
                    onHtmlPicked = { uri, name -> viewModel.importHtml(uri, name) },
                    onZipPicked = { uri -> viewModel.importZip(uri) },
                    onTemplateSelected = { name -> viewModel.loadTemplate(name) }
                )
                else -> when (state.currentTab) {
                    MainTab.PREVIEW -> PreviewScreen(project = project, onLog = viewModel::log)
                    MainTab.CODE -> CodeViewerScreen(
                        project = project,
                        selectedPath = state.selectedFilePath,
                        onSelectFile = viewModel::selectFile,
                        onEditFile = viewModel::updateFileContent
                    )
                    MainTab.CONFIG -> ConfigScreen(
                        config = project.config,
                        onUpdate = { mutate -> viewModel.updateConfig(mutate) }
                    )
                    MainTab.GITHUB -> GitHubPushScreen(
                        isBusy = state.isBusy,
                        busyMessage = state.busyMessage,
                        pushProgress = state.pushProgress,
                        lastResult = state.lastPushResult,
                        onPush = { owner, repo, token, priv -> viewModel.pushToGitHub(owner, repo, token, priv) },
                        onOpenUrl = onOpenUrl
                    )
                }
            }

            LaunchedEffect(state.exportedZipPath) {
                state.exportedZipPath?.let { onShareZip(it) }
            }
        }
    }
}
