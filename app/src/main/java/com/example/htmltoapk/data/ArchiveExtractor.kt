package com.example.htmltoapk.data

import android.content.Context
import android.net.Uri
import com.example.htmltoapk.data.model.LoadedProject
import com.example.htmltoapk.data.model.ProjectFile
import com.example.htmltoapk.data.model.ProjectType
import java.io.File
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Safely extracts an uploaded archive into isolated app storage and classifies it as either
 * a Native Android project or a Web (HTML/CSS/JS) project, per the auto-detection rules:
 *
 *  - Presence of AndroidManifest.xml, build.gradle(.kts), or .java/.kt sources -> NATIVE_ANDROID_ZIP
 *  - Presence of an .html file (e.g. index.html) with no native markers -> WEB_ZIP
 */
class ArchiveExtractor(private val context: Context) {

    private val textExtensions = setOf(
        "html", "htm", "css", "js", "json", "xml", "kt", "kts", "java", "gradle",
        "pro", "properties", "md", "txt", "gitignore", "yml", "yaml", "cfg", "sh"
    )

    /** Extracts into /data/data/<pkg>/files/projects/<uuid>/ to prevent zip-slip / path traversal. */
    fun extractZip(uri: Uri, projectDirName: String): LoadedProject {
        val destRoot = File(context.filesDir, "projects/$projectDirName").apply {
            deleteRecursively(); mkdirs()
        }

        val extractedFiles = mutableListOf<ProjectFile>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val safeName = sanitizeZipEntryName(entry.name, destRoot)
                    if (safeName != null && !entry.isDirectory) {
                        val outFile = File(destRoot, safeName)
                        outFile.parentFile?.mkdirs()
                        val bytes = zis.readBytes()
                        outFile.writeBytes(bytes)

                        val ext = safeName.substringAfterLast('.', "").lowercase()
                        if (ext in textExtensions) {
                            extractedFiles += ProjectFile(safeName, textContent = runCatching {
                                bytes.toString(Charsets.UTF_8)
                            }.getOrNull())
                        } else {
                            extractedFiles += ProjectFile(safeName, binaryContent = bytes, isBinary = true)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val type = detectProjectType(extractedFiles.map { it.relativePath })
        val entryHtml = if (type == ProjectType.WEB_ZIP) findEntryHtml(extractedFiles.map { it.relativePath }) else null
        val detectedPackage = if (type == ProjectType.NATIVE_ANDROID_ZIP) {
            extractedFiles.firstOrNull { it.relativePath.endsWith("AndroidManifest.xml") }
                ?.textContent?.let { extractPackageFromManifest(it) }
        } else null
        val detectedPerms = if (type == ProjectType.NATIVE_ANDROID_ZIP) {
            extractedFiles.firstOrNull { it.relativePath.endsWith("AndroidManifest.xml") }
                ?.textContent?.let { extractPermissionsFromManifest(it) } ?: emptyList()
        } else emptyList()

        return LoadedProject(
            type = type,
            name = projectDirName,
            files = extractedFiles,
            entryHtmlPath = entryHtml,
            detectedNativePermissions = detectedPerms,
            detectedPackageName = detectedPackage
        )
    }

    fun loadSingleHtml(uri: Uri, fileName: String): LoadedProject {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: ByteArray(0)
        val html = ProjectFile(fileName, textContent = bytes.toString(Charsets.UTF_8))
        return LoadedProject(
            type = ProjectType.SINGLE_HTML,
            name = fileName.substringBeforeLast('.'),
            files = mutableListOf(html),
            entryHtmlPath = fileName
        )
    }

    /** Prevents zip-slip: rejects/normalizes entries that would escape destRoot. */
    private fun sanitizeZipEntryName(rawName: String, destRoot: File): String? {
        val normalized = rawName.replace('\\', '/').trimStart('/')
        if (normalized.isEmpty() || normalized.contains("..")) return null
        val resolved = File(destRoot, normalized).canonicalFile
        if (!resolved.path.startsWith(destRoot.canonicalFile.path)) return null
        return normalized
    }

    private fun detectProjectType(paths: List<String>): ProjectType {
        val hasNativeMarkers = paths.any {
            it.endsWith("AndroidManifest.xml") ||
                it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") ||
                it.endsWith(".java") || it.endsWith(".kt")
        }
        if (hasNativeMarkers) return ProjectType.NATIVE_ANDROID_ZIP

        val hasHtml = paths.any { it.endsWith(".html") || it.endsWith(".htm") }
        if (hasHtml) return ProjectType.WEB_ZIP

        return ProjectType.UNKNOWN
    }

    /** Prefers root index.html, then any index.html, then the first .html file found. */
    private fun findEntryHtml(paths: List<String>): String? {
        return paths.firstOrNull { it.equals("index.html", ignoreCase = true) }
            ?: paths.firstOrNull { it.endsWith("/index.html", ignoreCase = true) }
            ?: paths.firstOrNull { it.endsWith(".html") || it.endsWith(".htm") }
    }

    private fun extractPackageFromManifest(xml: String): String? {
        val matcher = Pattern.compile("package=\"([^\"]+)\"").matcher(xml)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractPermissionsFromManifest(xml: String): List<String> {
        val matcher = Pattern.compile("uses-permission[^>]*android:name=\"([^\"]+)\"").matcher(xml)
        val result = mutableListOf<String>()
        while (matcher.find()) result += matcher.group(1)
        return result
    }
}
