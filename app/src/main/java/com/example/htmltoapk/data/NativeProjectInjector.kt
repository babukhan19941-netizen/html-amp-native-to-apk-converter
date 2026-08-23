package com.example.htmltoapk.data

import com.example.htmltoapk.data.model.ProjectFile

/**
 * For imported Native Android project ZIPs: preserves every original file untouched,
 * and only adds the specific build/CI artifacts the spec requires when they're absent.
 */
object NativeProjectInjector {

    fun injectMissingArtifacts(originalFiles: List<ProjectFile>): List<ProjectFile> {
        val result = originalFiles.toMutableList()
        val paths = originalFiles.map { it.relativePath }.toSet()

        if (paths.none { it == ".gitignore" }) {
            result += ProjectFile(".gitignore", textContent = WorkflowTemplates.GITIGNORE)
        }
        if (paths.none { it == ".env.example" }) {
            result += ProjectFile(".env.example", textContent = WorkflowTemplates.ENV_EXAMPLE)
        }
        if (paths.none { it.endsWith("gradle-wrapper.properties") }) {
            result += ProjectFile(
                "gradle/wrapper/gradle-wrapper.properties",
                textContent = WorkflowTemplates.GRADLE_WRAPPER_PROPERTIES
            )
        }
        if (paths.none { it == ".github/workflows/android-build.yml" }) {
            result += ProjectFile(
                ".github/workflows/android-build.yml",
                textContent = WorkflowTemplates.ANDROID_BUILD_WORKFLOW
            )
        }
        return result
    }
}
