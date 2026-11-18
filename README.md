# SmartNote

> **Simple on the surface, intelligent underneath.**

SmartNote is a native, offline-first Android note-taking application written in Kotlin with Jetpack Compose and **Material 3 Expressive**. Designed to capture quickly, organize automatically on-device, and search instantaneously across notes, checklists, links, and screenshot OCR text—without accounts, cloud sync, telemetry, or external API keys.

- **Package ID:** `io.github.ultrapg.smartnote`
- **Version:** `1.0.0` (`versionCode = 1`)
- **Compatibility:** Android 9+ (`minSdk = 28`, `targetSdk = 36`, `compileSdk = 37`)
- **ABIs:** `arm64-v8a`, `x86_64`
- **License:** Apache License 2.0 ([LICENSE](LICENSE))

---

## Core Capabilities

### Design & Adaptive Experience
- **Material 3 Expressive**: Implements `MaterialExpressiveTheme`, `MotionScheme.expressive()`, shared-element card-to-editor transitions, Predictive Back gestures, subtle haptic feedback, and expressive components (`HorizontalFloatingToolbar`, `SplitButtonLayout`, `ButtonGroup`, `LoadingIndicator`, `MaterialShapes` morphing).
- **Four Cohesive Themes**: **Dynamic Color** (Material You wallpaper default), **SmartNote Violet**, **Neutral**, and **True-Black AMOLED**—each supporting Light, Dark, and System appearance via Jetpack DataStore.
- **Adaptive Window Support**: `NavigationSuiteScaffold` switches between a compact Navigation Bar and an expanded Navigation Rail across compact phones, foldables, tablets, and multi-window layouts.
- **Two Overview Layouts**: **Staggered Card** grid (default) and single-column **Compact List**, toggleable live with persisted preference.

### Capture, Editor & Data Integrity
- **Distraction-Free Block Editor**: Supports titles, multiline Markdown-highlighted text (`#` headings, `**bold**`, `*italic*`, `` `code` ``), reorderable checklists, embedded private-storage images, and clickable link cards.
- **Crash-Safe Autosave**: `NoteAutosaveCoordinator` debounces rapid edits (`650 ms` debounce, `4000 ms` max latency), flushes on lifecycle/navigation pause, preserves drafts via `SavedStateHandle`, and commits atomically via Room `@Upsert` + `immediateTransaction` with `PRAGMA foreign_keys = ON`.
- **Archive & 30-Day Recycle Bin**: Prevents accidental permanent deletion, schedules 30-day expiration cleanup via WorkManager, and protects deduplicated attachments using cross-note reference counting.
- **SmartCapture Sharesheet Integration**: Receives `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intents (`text/plain`, `image/png`, `image/jpeg`, `image/webp`, `image/*`, URLs, and multi-image shares) with immediate private staging, screenshot heuristics, quick-save UX, and canonical-path rejection of internal `file://` URIs.
- **Local Backup & Restore**: Exports and restores portable `.smartnote.zip` archives with strict ZIP-Slip traversal protection, `MERGE` (UUID-conflict safe) and `REPLACE_ALL` modes, and full FTS5 reindexing.

### On-Device Intelligence (100% Offline by Default)
- **Bundled ML Kit Latin OCR**: Recognizes text inside screenshots and photos completely offline (`com.google.mlkit:text-recognition:16.0.1`) with SHA-256 deduplication caching.
- **SmartSearch (SQLite FTS5)**: Dual virtual tables (`porter unicode61 remove_diacritics 2` + `trigram`) backed by bundled `sqlite-bundled` give weighted BM25 + recency + pin + exact-title scoring, typo correction (*Did you mean*), offline synonym expansion, and operator filtering (`#tag`, OCR, images, checklists, date ranges).
- **SmartTags & SmartDuplicateDetector**: Rule/keyword auto-tagging that respects manual user confirmations and permanent dismissals, plus SHA-256, SimHash-64, and canonical-URL duplicate detection without ever auto-deleting notes.
- **On-Device LiteRT AI (`smartnote_text_embed_v1.tflite`)**: Uses LiteRT `2.3.2` with adaptive runtime delegation (`NPU` -> `GPU` -> `CPU XNNPACK` -> deterministic n-gram fallback) for optional local semantic search reranking, governed by a master toggle and per-feature DataStore switches.

### Android Platform & Assistant Integrations
- **Reminders & Android 16 Live Updates**: Exact-alarm reminders with boot/time-change rescheduling and Android 16 promoted Live Update support (`setRequestPromotedOngoing`, `setShortCriticalText`, `ProgressStyle` where supported, standard notification fallback elsewhere).
- **Glance Widgets, Quick Settings Tile & Shortcuts**: Three Material 3 Glance widgets (Quick Capture, Pinned Note, Overview Summary), `QuickNoteTileService`, and static + dynamic App Shortcuts.
- **Jetpack AppFunctions & Operations API**: Exposes offline note search/creation/tagging/reminder `@AppFunction` entry points (`appfunctions-service:1.0.0-alpha08`, packaged `app_functions.xml` & `app_functions_v2.xml`) routed through `SmartNoteOperationsApi`, which enforces soft-delete-only mutations and honors SmartNote's offline AI/integration master toggle.

---

## Architecture & Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language / Build** | Kotlin `2.2.21`, KSP `2.2.21-2.0.5`, Gradle `8.13`, AGP `8.13.2` |
| **UI / Motion** | Jetpack Compose BOM `2026.06.01`, Material 3 `1.5.0-alpha15`, Navigation 3 / Compose, Adaptive `1.2.0` |
| **Persistence / Search** | Room `2.8.5`, Bundled SQLite `2.7.1` (FTS5 Porter + Trigram), DataStore Preferences `1.2.1` |
| **On-Device ML / OCR** | Google LiteRT `2.3.2` (`litert` + `litert-gpu`), ML Kit Bundled Latin Text Recognition `16.0.1` |
| **Background / Platform** | WorkManager `2.11.2`, Glance `1.2.0`, Core KTX `1.18.0`, Jetpack AppFunctions `1.0.0-alpha08` |
| **Testing** | JUnit4, Robolectric `4.16.1`, Compose UI Test, Kotlinx Coroutines Test `1.10.2` |

---

## Installation

1. Download **`smartnote-release.apk`** from the [v1.0.0 GitHub Release](../../releases/tag/v1.0.0).
2. Verify the APK checksum before installing:
   - **File:** `smartnote-release.apk`
   - **Size:** `57,425,224` bytes (`54.77 MiB`)
   - **SHA-256:** `61fd45226be034d84994500ed1b569573a6987f4021c6f6c45c13154ac8b7af9`
   - **APK Signature Scheme v3 Signer Cert SHA-256 (4096-bit RSA):**
     `b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`
3. Install on an Android 9+ device via system package installer or `adb install smartnote-release.apk`.

---

## Building from Source

### Prerequisites
- JDK 21
- Android SDK with Platform `android-36` / `android-37` (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 28`) and Build-Tools `37.0.0`

### Debug & Test Builds
```bash
git clone <repo-url>
cd smartnotes
# Set sdk.dir in local.properties if ANDROID_HOME is not set
./gradlew testDebugUnitTest testReleaseUnitTest assembleDebug --no-daemon
```

### Signed Release Builds
`app/build.gradle.kts` reads release signing credentials from environment variables (`SMARTNOTE_KEYSTORE_PATH`, `SMARTNOTE_KEYSTORE_PASSWORD`, `SMARTNOTE_KEY_ALIAS`, `SMARTNOTE_KEY_PASSWORD`) or from a local untracked properties file at `~/.android/smartnote-keys/keystore.properties`:

```properties
storeFile=/path/to/your/release-keystore.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=smartnote
keyPassword=YOUR_KEY_PASSWORD
```

Then run:
```bash
./gradlew assembleRelease --no-daemon
```
The R8-minified, resource-shrunk, signed APK is output to `app/build/outputs/apk/release/app-release.apk`.

---

## Verification & Known Hardware Limitations

### Verified on Build Host (2026-11-18)
- `./gradlew clean testDebugUnitTest testReleaseUnitTest assembleRelease --no-daemon` completed cleanly (`BUILD SUCCESSFUL`) with **0 Kotlin warnings**:
  - **Debug Unit/Integration/UI Suites (`testDebugUnitTest`):** 10 suites, **19 tests passed, 0 failures, 0 errors, 0 skipped**.
  - **Release Unit/Integration/UI Suites (`testReleaseUnitTest`):** 10 suites, **19 tests passed, 0 failures, 0 errors, 0 skipped** (validating R8/ProGuard behavior alongside business logic, FTS5 search latency on 2,500 synthetic notes `< 250 ms`, ZIP-Slip traversal rejection, AppFunctions input/toggle validation, and Compose UI rendering at 200% font scale).
- **Release APK Verification (`build-tools/37.0.0`):**
  - `zipalign -c -P 16 -v 4 smartnote-release.apk`: **Verification successful** (16 KB ELF page-aligned).
  - `apksigner verify --print-certs -v smartnote-release.apk`: **Verifies** under APK Signature Scheme v3 (RSA-4096).
  - `aapt2 dump badging smartnote-release.apk`: Confirmed `io.github.ultrapg.smartnote`, `versionCode='1'`, `versionName='1.0.0'`, `minSdkVersion:'28'`, `targetSdkVersion:'36'`, `arm64-v8a` & `x86_64`.
  - Archive inspection confirmed `assets/models/smartnote_text_embed_v1.tflite`, `assets/app_functions.xml`, `assets/app_functions_v2.xml`, and native libraries (`libsqliteJni.so`, `libmlkit_google_ocr_pipeline.so`, `libLiteRt.so`, `liblitert_jni.so`, `libLiteRtClGlAccelerator.so`) are packaged with zero keystores or credentials.

### Hardware-Dependent Checks (Require Physical Android Device)
The Linux build environment has no `/dev/kvm` or attached Android device, so the following behaviors are implemented and compile-verified but require physical-device validation:
- **LiteRT Hardware Delegates:** Physical vendor NPU (`CompiledModel` NPU) and GPU delegate acceleration paths (host tests verify model loading and CPU/fallback execution).
- **Android 16 Live Updates OEM Surfaces:** System status-chip / Samsung One UI 8 Now Bar promotion behavior under OEM lock-screen policies.
- **Consumer Gemini Connected Apps Routing:** End-to-end OS Assistant / Gemini app invocation against SmartNote's packaged `appfunctions-service` declaration.
