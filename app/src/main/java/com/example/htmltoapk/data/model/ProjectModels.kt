package com.example.htmltoapk.data.model

/** Broad classification of what the user imported. */
enum class ProjectType {
    SINGLE_HTML,
    WEB_ZIP,
    NATIVE_ANDROID_ZIP,
    UNKNOWN
}

enum class ScreenOrientation(val manifestValue: String, val label: String) {
    PORTRAIT("portrait", "Portrait"),
    LANDSCAPE("landscape", "Landscape"),
    SENSOR("fullSensor", "Sensor (auto-rotate)")
}

/** A single toggleable Android runtime/manifest permission. */
data class AppPermission(
    val manifestName: String,
    val displayName: String,
    var enabled: Boolean = false
)

fun defaultPermissionSet(): List<AppPermission> = listOf(
    AppPermission("android.permission.INTERNET", "Internet access", enabled = true),
    AppPermission("android.permission.CAMERA", "Camera"),
    AppPermission("android.permission.ACCESS_FINE_LOCATION", "Precise location"),
    AppPermission("android.permission.ACCESS_COARSE_LOCATION", "Approximate location"),
    AppPermission("android.permission.RECORD_AUDIO", "Microphone"),
    AppPermission("android.permission.READ_EXTERNAL_STORAGE", "Read storage"),
    AppPermission("android.permission.WRITE_EXTERNAL_STORAGE", "Write storage"),
    AppPermission("android.permission.VIBRATE", "Vibration"),
    AppPermission("android.permission.POST_NOTIFICATIONS", "Notifications")
)

/** In-memory representation of one file inside an imported/generated project. */
data class ProjectFile(
    val relativePath: String,
    var textContent: String? = null,   // non-null for text/code files
    val binaryContent: ByteArray? = null, // non-null for images/binary assets
    val isBinary: Boolean = false
) {
    override fun equals(other: Any?): Boolean = other is ProjectFile && other.relativePath == relativePath
    override fun hashCode(): Int = relativePath.hashCode()
}

/** Editable Android project configuration shown in the Config tab. */
data class ProjectConfig(
    var appName: String = "My App",
    var packageName: String = "com.example.myapp",
    var versionName: String = "1.0.0",
    var versionCode: Int = 1,
    var targetSdk: Int = 36,
    var minSdk: Int = 24,
    var orientation: ScreenOrientation = ScreenOrientation.SENSOR,
    var primaryColorHex: String = "#3B4CFF",
    var accentColorHex: String = "#10E0A0",
    var permissions: MutableList<AppPermission> = defaultPermissionSet().toMutableList()
)

/** Top level in-memory project state driving the whole app. */
data class LoadedProject(
    val type: ProjectType,
    val name: String,
    val files: MutableList<ProjectFile> = mutableListOf(),
    val entryHtmlPath: String? = null,   // for web projects: resolved index.html
    var config: ProjectConfig = ProjectConfig(),
    val detectedNativePermissions: List<String> = emptyList(),
    val detectedPackageName: String? = null
)

data class GitHubPushResult(
    val success: Boolean,
    val repoUrl: String? = null,
    val actionsUrl: String? = null,
    val message: String
)

data class BuildLogEntry(
    val level: String, // INFO / WARN / ERROR / JS
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)
