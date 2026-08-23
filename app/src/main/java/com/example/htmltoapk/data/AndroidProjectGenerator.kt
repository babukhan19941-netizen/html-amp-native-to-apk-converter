package com.example.htmltoapk.data

import com.example.htmltoapk.data.model.LoadedProject
import com.example.htmltoapk.data.model.ProjectConfig
import com.example.htmltoapk.data.model.ProjectFile

/**
 * Builds a complete, standalone Android project (Compose host + WebView) around an
 * imported HTML/web bundle, ready for local ZIP export or GitHub push.
 *
 * Output layout mirrors a real Android Studio project:
 *   settings.gradle.kts, build.gradle.kts, app/build.gradle.kts,
 *   app/src/main/AndroidManifest.xml, MainActivity.kt, res/*, assets/www/*
 */
class AndroidProjectGenerator {

    fun generate(project: LoadedProject): List<ProjectFile> {
        val cfg = project.config
        val out = mutableListOf<ProjectFile>()

        out += ProjectFile("settings.gradle.kts", textContent = settingsGradle(cfg))
        out += ProjectFile("build.gradle.kts", textContent = rootBuildGradle())
        out += ProjectFile("gradle/wrapper/gradle-wrapper.properties",
            textContent = WorkflowTemplates.GRADLE_WRAPPER_PROPERTIES)
        out += ProjectFile(".gitignore", textContent = WorkflowTemplates.GITIGNORE)
        out += ProjectFile(".env.example", textContent = WorkflowTemplates.ENV_EXAMPLE)
        out += ProjectFile(".github/workflows/android-build.yml",
            textContent = WorkflowTemplates.ANDROID_BUILD_WORKFLOW)

        out += ProjectFile("app/build.gradle.kts", textContent = appBuildGradle(cfg))
        out += ProjectFile("app/proguard-rules.pro", textContent = "# generated - no extra rules\n")
        out += ProjectFile("app/src/main/AndroidManifest.xml", textContent = manifest(cfg))

        out += ProjectFile(
            "app/src/main/java/${cfg.packageName.replace('.', '/')}/MainActivity.kt",
            textContent = mainActivityKt(cfg)
        )

        out += ProjectFile("app/src/main/res/values/strings.xml", textContent = stringsXml(cfg))
        out += ProjectFile("app/src/main/res/values/themes.xml", textContent = themesXml())
        out += ProjectFile("app/src/main/res/values/colors.xml", textContent = colorsXml(cfg))

        out += adaptiveIcons(cfg)

        // Bundle the user's web assets under app/src/main/assets/www so file:// loads work offline.
        project.files.forEach { f ->
            val destPath = "app/src/main/assets/www/${f.relativePath}"
            out += if (f.isBinary) {
                ProjectFile(destPath, binaryContent = f.binaryContent, isBinary = true)
            } else {
                ProjectFile(destPath, textContent = f.textContent)
            }
        }

        return out
    }

    private fun settingsGradle(cfg: ProjectConfig) = """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
        }
        rootProject.name = "${cfg.appName.replace(" ", "")}"
        include(":app")
    """.trimIndent() + "\n"

    private fun rootBuildGradle() = """
        plugins {
            id("com.android.application") version "8.9.2" apply false
            id("org.jetbrains.kotlin.android") version "2.0.20" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
        }
        tasks.register("clean", Delete::class) {
            delete(rootProject.layout.buildDirectory)
        }
    """.trimIndent() + "\n"

    private fun appBuildGradle(cfg: ProjectConfig) = """
        plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
            id("org.jetbrains.kotlin.plugin.compose")
        }

        android {
            namespace = "${cfg.packageName}"
            compileSdk = ${cfg.targetSdk}

            defaultConfig {
                applicationId = "${cfg.packageName}"
                minSdk = ${cfg.minSdk}
                targetSdk = ${cfg.targetSdk}
                versionCode = ${cfg.versionCode}
                versionName = "${cfg.versionName}"
            }

            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlin { jvmToolchain(17) }
            buildFeatures { compose = true }
        }

        dependencies {
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation(platform("androidx.compose:compose-bom:2024.09.03"))
            implementation("androidx.compose.ui:ui")
            implementation("androidx.compose.material3:material3")
            implementation("androidx.webkit:webkit:1.11.0")
        }
    """.trimIndent() + "\n"

    private fun manifest(cfg: ProjectConfig): String {
        val perms = cfg.permissions.filter { it.enabled }
            .joinToString("\n") { "    <uses-permission android:name=\"${it.manifestName}\" />" }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            $perms

                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:supportsRtl="true"
                    android:hardwareAccelerated="true"
                    android:theme="@style/Theme.GeneratedApp">

                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:screenOrientation="${cfg.orientation.manifestValue}"
                        android:theme="@style/Theme.GeneratedApp">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent() + "\n"
    }

    private fun mainActivityKt(cfg: ProjectConfig): String {
        val pkg = cfg.packageName
        return """
            package $pkg

            import android.annotation.SuppressLint
            import android.os.Bundle
            import android.webkit.GeolocationPermissions
            import android.webkit.JsResult
            import android.webkit.ValueCallback
            import android.webkit.WebChromeClient
            import android.webkit.WebView
            import android.webkit.WebViewClient
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.result.contract.ActivityResultContracts
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.runtime.remember
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.viewinterop.AndroidView

            /**
             * Hosts the imported web bundle (assets/www/index.html) inside a full-featured
             * WebView: JS + DOM storage enabled, file uploads, and geolocation callbacks wired up.
             */
            class MainActivity : ComponentActivity() {

                private var filePathCallback: ValueCallback<Array<android.net.Uri>>? = null
                private val fileChooserLauncher = registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val data = result.data
                    val uris = if (data?.data != null) arrayOf(data.data!!) else null
                    filePathCallback?.onReceiveValue(uris)
                    filePathCallback = null
                }

                @SuppressLint("SetJavaScriptEnabled")
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MaterialTheme {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                val webView = remember {
                                    WebView(this).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.allowFileAccess = true
                                        settings.setGeolocationEnabled(true)
                                        webViewClient = WebViewClient()
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onGeolocationPermissionsShowPrompt(
                                                origin: String?,
                                                callback: GeolocationPermissions.Callback?
                                            ) {
                                                callback?.invoke(origin, true, false)
                                            }

                                            override fun onShowFileChooser(
                                                webView: WebView?,
                                                filePathCallback: ValueCallback<Array<android.net.Uri>>?,
                                                fileChooserParams: FileChooserParams?
                                            ): Boolean {
                                                this@MainActivity.filePathCallback = filePathCallback
                                                val intent = fileChooserParams?.createIntent()
                                                return if (intent != null) {
                                                    fileChooserLauncher.launch(intent)
                                                    true
                                                } else false
                                            }

                                            override fun onJsAlert(
                                                view: WebView?,
                                                url: String?,
                                                message: String?,
                                                result: JsResult?
                                            ): Boolean {
                                                // Forwarded to the floating console log in the builder app;
                                                // in the exported standalone app this simply confirms.
                                                result?.confirm()
                                                return true
                                            }
                                        }
                                        loadUrl("file:///android_asset/www/${'$'}{ENTRY_HTML}")
                                    }
                                }
                                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }

                companion object {
                    private const val ENTRY_HTML = "index.html"
                }
            }
        """.trimIndent() + "\n"
    }

    private fun stringsXml(cfg: ProjectConfig) = """
        <resources>
            <string name="app_name">${cfg.appName}</string>
        </resources>
    """.trimIndent() + "\n"

    private fun themesXml() = """
        <resources>
            <style name="Theme.GeneratedApp" parent="android:Theme.Material.Light.NoActionBar" />
        </resources>
    """.trimIndent() + "\n"

    private fun colorsXml(cfg: ProjectConfig) = """
        <resources>
            <color name="app_primary">${cfg.primaryColorHex}</color>
            <color name="app_accent">${cfg.accentColorHex}</color>
        </resources>
    """.trimIndent() + "\n"

    /** Minimal adaptive icon set (vector vector-drawable based, no PNG rasterization needed). */
    private fun adaptiveIcons(cfg: ProjectConfig): List<ProjectFile> {
        val fg = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp" android:height="108dp"
                android:viewportWidth="108" android:viewportHeight="108">
                <path
                    android:fillColor="${cfg.accentColorHex}"
                    android:pathData="M54,20 L86,54 L54,88 L22,54 Z" />
            </vector>
        """.trimIndent() + "\n"
        val bg = """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp" android:height="108dp"
                android:viewportWidth="108" android:viewportHeight="108">
                <path android:fillColor="${cfg.primaryColorHex}" android:pathData="M0,0h108v108h-108z" />
            </vector>
        """.trimIndent() + "\n"
        val adaptive = """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
        """.trimIndent() + "\n"
        return listOf(
            ProjectFile("app/src/main/res/drawable/ic_launcher_foreground.xml", textContent = fg),
            ProjectFile("app/src/main/res/drawable/ic_launcher_background.xml", textContent = bg),
            ProjectFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", textContent = adaptive)
        )
    }
}
