# SmartNote

> **Simple on the surface, intelligent underneath.**

SmartNote is a native, offline-first Android note-taking application written in Kotlin with Jetpack Compose and **Material 3 Expressive**. Designed to capture quickly, organize automatically on-device, and search instantaneously across notes, checklists, links, and screenshot OCR text—without accounts, cloud sync, telemetry, or external API keys.

- **Package ID:** `io.github.ultrapg.smartnote`
- **Version:** `1.0.14` (`versionCode = 15`)
- **Compatibility:** Android 9+ (`minSdk = 28`, `targetSdk = 36`, `compileSdk = 37`)
- **ABIs:** `arm64-v8a`, `x86_64`
- **License:** Apache License 2.0 ([LICENSE](LICENSE))

---

## Core Capabilities

### Design & Adaptive Experience
- **Material 3 Expressive**: Implements `MaterialExpressiveTheme` with critically damped calm motion (`SmartNoteSoftMotionScheme` with automatic `MotionScheme.standard()` reduced-motion fallback), opaque flash-free screen transitions, borderless tonal surfaces, Predictive Back gestures, subtle haptic feedback, and expressive components (`SplitButtonLayout`, `ButtonGroup`, `LoadingIndicator`, and `MaterialShapes` badges).
- **Edge-to-Edge Polish**: Full system-bar and IME inset integration (dark/light window backgrounds synced cleanly, transparent status/navigation bars, light/dark icon contrast across themes, status-bar-padded search bars, and keyboard-unioned editor toolbars).
- **Four Cohesive Themes**: **Dynamic Color** (Material You wallpaper default), **SmartNote Violet**, **Neutral**, and **True-Black AMOLED**—each supporting Light, Dark, and System appearance via Jetpack DataStore with synchronous boot-cache restoration.
- **Adaptive Window Support**: Persistent outer navigation chrome in `SmartNoteApp` switches automatically between a custom bottom navigation bar with a spring-animated flying active pill indicator (auto-hiding when typing in SmartSearch/Tags) and a `NavigationRail` on screens `>= 600dp` (tablets/foldables), paired with compact floating pop-out bubble `Sort` and `Menu` menus in the notes overview.
- **Two Overview Layouts**: **Staggered Card** grid (default) and single-column **Compact List**, toggleable live with persisted preference.

### Capture, Editor & Data Integrity
- **Full-Screen Distraction-Free Block Editor**: Supports titles, multiline Markdown-highlighted text (`#` headings, `**bold**`, `*italic*`, `` `code` ``), reorderable checklists, embedded private-storage images, and clickable link cards—with full-viewport tap-to-edit canvas expansion and a scroll indicator bar when notes extend below the screen.
- **Crash-Safe Autosave**: `NoteAutosaveCoordinator` debounces rapid edits (`650 ms` debounce, `4000 ms` max latency), flushes on lifecycle/navigation pause, preserves drafts via `SavedStateHandle`, and commits atomically via Room `@Upsert` + `immediateTransaction` with `PRAGMA foreign_keys = ON`.
- **Archive & 30-Day Recycle Bin**: Prevents accidental permanent deletion, schedules 30-day expiration cleanup via WorkManager, and protects deduplicated attachments using cross-note reference counting.
- **SmartCapture Sharesheet Integration**: Receives `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intents (`text/plain`, `image/png`, `image/jpeg`, `image/webp`, `image/*`, URLs, and multi-image shares) with immediate private staging, screenshot heuristics, quick-save UX, and canonical-path rejection of internal `file://` URIs.
- **Local Backup & Restore**: Exports and restores portable `.smartnote.zip` archives with strict ZIP-Slip traversal protection, `MERGE` (UUID-conflict safe) and `REPLACE_ALL` modes, and full FTS5 reindexing.

### On-Device Intelligence (Master-Switch Controlled)
- **Unified AI Control**: A single **Enable AI Features** switch (enabled by default) in **Settings → AI & Smart Features** toggles all AI capabilities together—graying out and disabling every AI toggle, hardware radio option, and diagnostics trigger while preserving saved preferences—and unloads active LiteRT models when disabled.
- **Bundled ML Kit Latin OCR & Image Labeling**: Recognizes text inside screenshots and photos locally on-device (`com.google.mlkit:text-recognition:16.0.1`) with SHA-256 deduplication caching, paired with Play Services ML Kit Image Labeling (`com.google.android.gms:play-services-mlkit-image-labeling:16.0.8`) when available to identify visual objects and scenes in photo-heavy notes.
- **SmartSearch (SQLite FTS5)**: Dual virtual tables (`porter unicode61 remove_diacritics 2` + `trigram`) backed by bundled `sqlite-bundled` give weighted BM25 + recency + pin + exact-title scoring, typo correction (*Did you mean*), local synonym expansion, and operator filtering (`#tag`, OCR, images, checklists, date ranges).
- **SmartTags & SmartDuplicateDetector**: Bilingual English/German 32-category auto-tagging with inline `#hashtag` extraction, morphological stemming (including German plural inflections), compound-word decomposition, personal confirmed-tag profile learning, Android System `TextClassifier` entity recognition, ML Kit Image Labeling, LiteRT semantic category affinity, optional on-device Gemini Nano suggestions (when supported), live editor suggestions inside `ManageTagsSheet`, and an on-demand **`Generate tags`** button with open-vocabulary topic extraction—alongside SHA-256, SimHash-64, and canonical-URL duplicate detection without ever auto-deleting notes.
- **On-Device Auto-Title Generation (`SmartTitleGenerator`)**: Optional (off by default) automatic titling for untitled notes and shared screenshots plus an animated tonal **`Generate`** button that cycles through up to 4 clean alternatives—combining optional Gemini Nano synthesis (when supported via AICore), EN/DE filler-prefix stripping, clause compression, dangling-word cleanup, System `TextClassifier` flight/entity synthesis, image-label fallback, and checklist synthesis.
- **On-Device LiteRT AI (`smartnote_text_embed_v1.tflite`)**: Uses LiteRT `2.3.2` with adaptive runtime delegation (`NPU` -> `GPU` -> `CPU XNNPACK` -> deterministic n-gram fallback) and optional ML Kit GenAI Prompt (`1.0.0-beta5` Gemini Nano via AICore) when supported, governed by **Enable AI Features** and per-feature DataStore switches with hardware diagnostics in Settings.

### Android System Integrations
- **Glance Widgets, Quick Settings Tile & Shortcuts**: Three Material 3 Glance widgets (Quick Capture, Pinned Note, Overview Summary), `QuickNoteTileService`, and static + dynamic App Shortcuts backed by validated local operations (`SmartNoteOperationsApi`).

---

## Architecture & Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language / Build** | Kotlin `2.2.21`, KSP `2.2.21-2.0.5`, Gradle `8.13`, AGP `8.13.2` |
| **UI / Motion** | Jetpack Compose BOM `2026.06.01`, Material 3 `1.5.0-alpha15`, Navigation Compose `2.9.8` |
| **Persistence / Search** | Room `2.8.5`, Bundled SQLite `2.7.1` (FTS5 Porter + Trigram), DataStore Preferences `1.1.2` |
| **On-Device ML / OCR / GenAI** | Google LiteRT `2.3.2`, ML Kit Text Recognition `16.0.1`, ML Kit Image Labeling `16.0.8`, ML Kit GenAI Prompt `1.0.0-beta5`, Android `TextClassifier` |
| **Background / Platform** | WorkManager `2.11.2`, Glance `1.2.0`, Core KTX `1.18.0` |
| **Testing** | JUnit4, Robolectric `4.14.1`, Compose UI Test, Kotlinx Coroutines Test `1.11.0` |

---

## Installation

1. Download **`smartnote-release.apk`** from the [v1.0.14 GitHub Release](../../releases/tag/v1.0.14).
2. Verify the APK checksum before installing:
   - **File:** `smartnote-release.apk`
   - **Size:** `57,861,782` bytes (`55.18 MiB`)
   - **SHA-256:** `2bcf8f7f2de4bd316afe8e75153454cbcaa712dfd505f35966132586e7f12ba2`
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

### Verified on Build Host (2026-11-19)
- `./gradlew clean assembleRelease --no-daemon --warning-mode=summary && ./gradlew testDebugUnitTest --rerun-tasks --no-daemon --warning-mode=summary && ./gradlew testReleaseUnitTest --rerun-tasks --no-daemon --warning-mode=summary` completed cleanly (`BUILD SUCCESSFUL`) with zero source Kotlin warnings:
  - **Debug Unit/Integration/UI Suites (`testDebugUnitTest`):** 9 suites, **33 tests passed, 0 failures, 0 errors, 0 skipped**.
  - **Release Unit/Integration/UI Suites (`testReleaseUnitTest`):** 9 suites, **33 tests passed, 0 failures, 0 errors, 0 skipped** (validating R8/ProGuard behavior alongside business logic, FTS5 search latency on 2,500 synthetic notes `< 250 ms`, ZIP-Slip traversal rejection, empty ghost note cleanup, image attachment/OCR OOM safety, opt-in auto-title + candidate-cycling `Generate` button + EN/DE filler/clause/entity/image-label/Gemini Nano synthesis, NPU-first energy-saving dispatch, default-on semantic search, **"Enable AI Features"** gray-out/restore behavior, 32-category bilingual SmartTags with open-vocabulary topic keywords, System `TextClassifier`, Image Labeling & on-demand `Generate tags` button, Room `MIGRATION_1_2` + `MIGRATION_2_3`, full-viewport trailing-block focus, SmartSearch header clipping, upward-morphing `+` pill menu, scrim-backed spring pop-out bubble top-bar `Sort` and `Menu` menus, flying bottom-bar pill indicator, System Integrations & Diagnostics layouts, and end-to-end Compose UI flows).
- **Release APK Verification (`build-tools/37.0.0`):**
  - `zipalign -c -P 16 -v 4 smartnote-release.apk`: **Verification successful** (16 KB ELF page-aligned).
  - `apksigner verify --print-certs -v smartnote-release.apk`: **Verifies** under APK Signature Scheme v3 (RSA-4096).
  - `aapt2 dump badging smartnote-release.apk`: Confirmed `io.github.ultrapg.smartnote`, `versionCode='15'`, `versionName='1.0.14'`, `minSdkVersion:'28'`, `targetSdkVersion:'36'`, `arm64-v8a` & `x86_64`, and confirmed `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and `VIBRATE` permissions are absent.
  - Archive inspection confirmed `assets/models/smartnote_text_embed_v1.tflite` and native libraries (`libsqliteJni.so`, `libmlkit_google_ocr_pipeline.so`, `libLiteRt.so`, `libLiteRtClGlAccelerator.so`) are packaged with zero keystores or credentials.

### Hardware-Dependent Checks (Require Physical Android Device)
The Linux build environment has no `/dev/kvm` or attached Android device, so the following behaviors are implemented and compile-verified but require physical-device validation:
- **LiteRT Hardware Delegates:** Physical vendor NPU (`CompiledModel` NPU) and GPU delegate acceleration paths (host tests verify model loading and CPU/fallback execution).
- **Gemini Nano (AICore), Play Services ML Kit Image Labeling & System `TextClassifier`:** On-device model download/inference via AICore, dynamic Play Services image-labeling model availability, and OEM system text-classifier entity extraction (host tests cover adapter contracts, fallback paths, and entity/image-label/Nano synthesis integration).
