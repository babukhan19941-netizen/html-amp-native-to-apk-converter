package com.example.htmltoapk.data

/**
 * Text templates for files that get auto-injected into every generated/pushed project.
 * The GitHub Actions workflow is intentionally manual-trigger-only (workflow_dispatch),
 * per the hard requirement that CI must never run automatically on push/PR.
 */
object WorkflowTemplates {

    const val ANDROID_BUILD_WORKFLOW = """name: Android Debug APK Build

# CRITICAL: manual trigger only - do not add push/pull_request triggers here.
on:
  workflow_dispatch:
    inputs:
      build_variant:
        description: 'Build variant to assemble'
        required: false
        default: 'assembleDebug'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.11.1

      - name: Create debug keystore
        run: |
          mkdir -p ~/.android
          keytool -genkeypair -v \
            -keystore ~/.android/debug.keystore \
            -storepass android -alias androiddebugkey \
            -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Android Debug,O=Android,C=US"

      - name: Build debug APK
        run: ./gradlew ${'$'}{{ github.event.inputs.build_variant }} --stacktrace --no-daemon

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: '**/build/outputs/apk/**/*.apk'
          if-no-files-found: error
"""

    const val GITIGNORE = """*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
*.apk
*.keystore
!debug.keystore
"""

    const val ENV_EXAMPLE = """# Copy to .env and fill in before local scripting use.
# (Not read by Gradle directly - for optional local tooling only.)
GITHUB_TOKEN=
"""

    const val GRADLE_WRAPPER_PROPERTIES = """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
}
