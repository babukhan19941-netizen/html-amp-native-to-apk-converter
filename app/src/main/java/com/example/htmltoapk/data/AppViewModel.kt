package com.example.htmltoapk.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.htmltoapk.data.model.BuildLogEntry
import com.example.htmltoapk.data.model.GitHubPushResult
import com.example.htmltoapk.data.model.LoadedProject
import com.example.htmltoapk.data.model.ProjectFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class MainTab { PREVIEW, CODE, CONFIG, GITHUB }

data class UiState(
    val project: LoadedProject? = null,
    val currentTab: MainTab = MainTab.PREVIEW,
    val selectedFilePath: String? = null,
    val logs: List<BuildLogEntry> = emptyList(),
    val isBusy: Boolean = false,
    val busyMessage: String = "",
    val pushProgress: Float = 0f,
    val lastPushResult: GitHubPushResult? = null,
    val exportedZipPath: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val extractor = ArchiveExtractor(app)
    private val generator = AndroidProjectGenerator()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun importZip(uri: Uri) = viewModelScope.launch {
        setBusy(true, "Extracting archive…")
        val loaded = withContext(Dispatchers.IO) {
            extractor.extractZip(uri, "proj_${UUID.randomUUID().toString().take(8)}")
        }
        applyDetectedConfig(loaded)
        log("INFO", "Imported ${loaded.type} with ${loaded.files.size} files")
        setBusy(false)
    }

    fun importHtml(uri: Uri, fileName: String) = viewModelScope.launch {
        setBusy(true, "Reading HTML file…")
        val loaded = withContext(Dispatchers.IO) { extractor.loadSingleHtml(uri, fileName) }
        applyDetectedConfig(loaded)
        log("INFO", "Imported single HTML file: $fileName")
        setBusy(false)
    }

    fun loadTemplate(name: String) = viewModelScope.launch {
        setBusy(true, "Loading template…")
        val files = Templates.build(name)
        val loaded = LoadedProject(
            type = com.example.htmltoapk.data.model.ProjectType.WEB_ZIP,
            name = name,
            files = files.toMutableList(),
            entryHtmlPath = "index.html"
        )
        applyDetectedConfig(loaded)
        log("INFO", "Loaded template: $name")
        setBusy(false)
    }

    private fun applyDetectedConfig(loaded: LoadedProject) {
        loaded.detectedPackageName?.let { loaded.config.packageName = it }
        if (loaded.detectedNativePermissions.isNotEmpty()) {
            loaded.config.permissions.forEach { perm ->
                perm.enabled = perm.enabled || loaded.detectedNativePermissions.contains(perm.manifestName)
            }
        }
        loaded.config.appName = loaded.name
        _state.update { it.copy(project = loaded, currentTab = MainTab.PREVIEW) }
    }

    fun selectTab(tab: MainTab) = _state.update { it.copy(currentTab = tab) }
    fun selectFile(path: String) = _state.update { it.copy(selectedFilePath = path) }

    fun updateFileContent(path: String, newContent: String) {
        val project = _state.value.project ?: return
        project.files.find { it.relativePath == path }?.let { it.textContent = newContent }
        _state.update { it.copy(project = project) }
    }

    fun updateConfig(mutator: (com.example.htmltoapk.data.model.ProjectConfig) -> Unit) {
        val project = _state.value.project ?: return
        mutator(project.config)
        _state.update { it.copy(project = project) }
    }

    fun log(level: String, message: String) {
        _state.update { it.copy(logs = it.logs + BuildLogEntry(level, message)) }
    }

    /** Builds the full Android project (native passthrough or web->Android generation) and exports a zip. */
    fun exportLocalZip(): Unit {
        val project = _state.value.project ?: return
        viewModelScope.launch {
            setBusy(true, "Building project files…")
            val filesToWrite = withContext(Dispatchers.Default) { buildOutputFiles(project) }

            setBusy(true, "Writing ZIP…")
            val zipPath = withContext(Dispatchers.IO) { writeZip(filesToWrite, project.config.appName) }

            _state.update { it.copy(exportedZipPath = zipPath) }
            log("INFO", "Exported project ZIP to $zipPath")
            setBusy(false)
        }
    }

    fun pushToGitHub(owner: String, repo: String, token: String, makePrivate: Boolean) {
        val project = _state.value.project ?: return
        viewModelScope.launch {
            setBusy(true, "Preparing files for push…")
            val filesToPush = withContext(Dispatchers.Default) { buildOutputFiles(project) }

            val client = GitHubApiClient(token)
            val result = withContext(Dispatchers.IO) {
                client.pushProject(
                    owner = owner,
                    repo = repo,
                    files = filesToPush,
                    makePrivate = makePrivate,
                    onProgress = { progress, message ->
                        _state.update { it.copy(pushProgress = progress, busyMessage = message) }
                    }
                )
            }
            log(if (result.success) "INFO" else "ERROR", result.message)
            _state.update { it.copy(lastPushResult = result) }
            setBusy(false)
        }
    }

    private fun buildOutputFiles(project: LoadedProject): List<ProjectFile> {
        return if (project.type == com.example.htmltoapk.data.model.ProjectType.NATIVE_ANDROID_ZIP) {
            // Native projects are preserved as-is, with missing CI/build artifacts injected.
            NativeProjectInjector.injectMissingArtifacts(project.files)
        } else {
            generator.generate(project)
        }
    }

    private fun writeZip(files: List<ProjectFile>, projectName: String): String {
        val exportsDir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exportsDir, "${projectName.replace(" ", "_")}.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            files.forEach { f ->
                zos.putNextEntry(ZipEntry(f.relativePath))
                if (f.isBinary && f.binaryContent != null) {
                    zos.write(f.binaryContent)
                } else {
                    zos.write((f.textContent ?: "").toByteArray(Charsets.UTF_8))
                }
                zos.closeEntry()
            }
        }
        return zipFile.absolutePath
    }

    private fun setBusy(busy: Boolean, message: String = "") {
        _state.update { it.copy(isBusy = busy, busyMessage = message) }
    }
}
