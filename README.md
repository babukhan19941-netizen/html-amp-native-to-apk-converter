# HTML & Native Android to APK Converter & Cloud Builder

Kotlin + Jetpack Compose (Material 3) Android app that imports HTML files, web project ZIPs,
or native Android project ZIPs; previews them on-device; lets you edit project config and
files; exports a full Android project ZIP; and pushes directly to GitHub with a
manual-trigger GitHub Actions workflow that builds a debug APK on demand.

## Opening the project

1. Open this folder in Android Studio (Ladybug or newer recommended).
2. Let Gradle sync — it will download Gradle 9.3.1 via the wrapper.
3. Run on a device/emulator running Android 8.0 (API 26) or newer.

## Architecture

- `data/ArchiveExtractor.kt` — safe ZIP extraction (zip-slip guarded) with auto-detection:
  native markers (`AndroidManifest.xml`, `build.gradle(.kts)`, `.java`/`.kt`) win over HTML.
- `data/AndroidProjectGenerator.kt` — turns an imported web bundle into a full Compose+WebView
  Android project (manifest, MainActivity, Gradle files, adaptive icons, bundled assets).
- `data/NativeProjectInjector.kt` — for native ZIPs, preserves everything as-is and only adds
  missing `.gitignore` / `.env.example` / `gradle-wrapper.properties` / CI workflow.
- `data/GitHubApiClient.kt` — Git Data API push: blobs → tree → commit → ref update, so an
  entire project is committed atomically in one commit.
- `data/WorkflowTemplates.kt` — the injected `workflow_dispatch`-only CI YAML (never runs on
  push/PR automatically, per the manual-trigger requirement).
- `data/AppViewModel.kt` — single source of truth (`StateFlow<UiState>`) wiring the extractor,
  generator, injector, and GitHub client to the UI.
- `ui/screens/*` — Home (upload + templates), Preview (WebView w/ JS console, or native
  project dashboard), Code Viewer (file tree + editor), Config (identity/version/SDK/
  orientation/colors/permissions), GitHub Push (PAT + owner/repo + progress).

## Build toolchain versions (important)

This project pins **AGP 8.9.2 + Gradle 8.11.1 + Kotlin 2.0.20**, which is the tested
combination that supports `compileSdk`/`targetSdk` 36. Since Kotlin 2.0, the Jetpack
Compose compiler is a separate Gradle plugin (`org.jetbrains.kotlin.plugin.compose`)
rather than the old `composeOptions { kotlinCompilerExtensionVersion = ... }` block —
both the app module and the generator that builds exported/pushed projects apply it.

If you bump `compileSdk` further, check the AGP↔Gradle compatibility table at
https://developer.android.com/build/releases/gradle-plugin before changing versions —
mismatched pairs (e.g. an AGP 8.x version paired with a Gradle 9.x wrapper) fail with a
generic "BUILD FAILED with an exception" and no useful detail in the CI logs.

## Known scaffold limitations (things to harden before shipping)

- The Code Viewer's file tree renders all folders expanded rather than truly collapsible —
  fine for browsing, but swap in per-node expand state for a large project.
- There's no syntax highlighting in the code editor (plain monospace `OutlinedTextField`).
  A real syntax highlighter (e.g. tokenizing + `AnnotatedString` spans) is a sizable addition
  on its own and was left as a follow-up.
- GitHub push assumes the target repo either doesn't exist yet (auto-created with
  `auto_init: true`) or already exists with a `main` branch; pushing into a repo with a
  differently-named default branch needs the branch name parameterized.
- No persistence layer (Room/DataStore) — imported projects live in memory + app-private
  storage for the current session only.
- This was generated without access to an Android SDK/Gradle network sandbox, so it has not
  been compiled here — expect the normal first-sync errors (dependency version bumps, etc.)
  that any hand-written project also gets ironed out in Android Studio.

## GitHub Actions

The generated/pushed `.github/workflows/android-build.yml` only runs when you click
**Run workflow** in the Actions tab (`workflow_dispatch`) — it will never trigger on a
push or pull request.
