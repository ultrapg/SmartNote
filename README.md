# SmartNote

> **Simple on the surface, intelligent underneath.**

SmartNote is a native, offline-first Android note-taking application written in Kotlin with Jetpack Compose and **Material 3 Expressive**. Designed to capture quickly, organize automatically on-device, and search instantaneously across notes, checklists, links, and screenshot OCR text—without accounts, cloud sync, telemetry, or external API keys.

- **Package ID:** `io.github.ultrapg.smartnote`
- **Version:** `1.0.12` (`versionCode = 13`)
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

### On-Device Intelligence (100% Offline & Master-Switch Controlled)
- **Unified AI Control**: A single **Enable AI Features** switch (enabled by default) in **Settings → AI & Smart Features** toggles all AI capabilities together—graying out and disabling every AI toggle, hardware radio option, and diagnostics trigger while preserving saved preferences—and unloads active LiteRT models when disabled.
- **Bundled ML Kit Latin OCR**: Recognizes text inside screenshots and photos completely offline (`com.google.mlkit:text-recognition:16.0.1`) with SHA-256 deduplication caching.
- **SmartSearch (SQLite FTS5)**: Dual virtual tables (`porter unicode61 remove_diacritics 2` + `trigram`) backed by bundled `sqlite-bundled` give weighted BM25 + recency + pin + exact-title scoring, typo correction (*Did you mean*), offline synonym expansion, and operator filtering (`#tag`, OCR, images, checklists, date ranges).
- **SmartTags & SmartDuplicateDetector**: Bilingual English/German 27-category auto-tagging with inline `#hashtag` extraction, morphological stemming, German compound-word decomposition, personal confirmed-tag profile learning, optional LiteRT semantic category affinity, live editor suggestions inside a borderless `ManageTagsSheet` (respecting manual confirmations and permanent dismissals), plus SHA-256, SimHash-64, and canonical-URL duplicate detection without ever auto-deleting notes.
- **Offline Auto-Title Generation (`SmartTitleGenerator`)**: Optional (off by default) automatic titling for untitled notes and shared screenshots—plus an on-demand tonal **`Generate`** title button in the editor even when automatic titling is turned off (still governed by **Enable AI Features**).
- **On-Device LiteRT AI (`smartnote_text_embed_v1.tflite`)**: Uses LiteRT `2.3.2` with adaptive runtime delegation (`NPU` -> `GPU` -> `CPU XNNPACK` -> deterministic n-gram fallback) for local semantic search reranking (enabled by default, with NPU-prioritized energy-efficient dispatch under Battery Saver/thermal load), governed by the master switch and per-feature DataStore switches.

### Android System Integrations
- **Reminders, Android 16 Now Bar & Wear OS Bridging**: Streamlined borderless `ReminderPickerSheet` with progressive `More options` disclosure, full centered `TimePicker` dialog, offline EN/DE natural date/time detection (`NaturalDateTimeParser`), custom Mon–Sun weekday schedules, early pre-alerts, audible/vibrating due alerts (`smartnote_reminders_due_v2` / `smartnote_reminders_urgent_v2`), discreet **today-only** silent Android 16 Live Update / Now Bar cards (`CHANNEL_NOWBAR_TODAY`), zero-watch-app Wear OS bridged notifications (`WearableExtender` synchronized dismissal, wrist actions & voice `RemoteInput` **`Add note`** reply), and boot/date-change rescheduling.
- **Glance Widgets, Quick Settings Tile & Shortcuts**: Three Material 3 Glance widgets (Quick Capture, Pinned Note, Overview Summary), `QuickNoteTileService`, and static + dynamic App Shortcuts backed by validated local operations (`SmartNoteOperationsApi`).

---

## Architecture & Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language / Build** | Kotlin `2.2.21`, KSP `2.2.21-2.0.5`, Gradle `8.13`, AGP `8.13.2` |
| **UI / Motion** | Jetpack Compose BOM `2026.06.01`, Material 3 `1.5.0-alpha15`, Navigation Compose `2.9.8` |
| **Persistence / Search** | Room `2.8.5`, Bundled SQLite `2.7.1` (FTS5 Porter + Trigram), DataStore Preferences `1.1.2` |
| **On-Device ML / OCR** | Google LiteRT `2.3.2` (`litert` + `litert-gpu`), ML Kit Bundled Latin Text Recognition `16.0.1` |
| **Background / Platform** | WorkManager `2.11.2`, Glance `1.2.0`, Core KTX `1.18.0` |
| **Testing** | JUnit4, Robolectric `4.14.1`, Compose UI Test, Kotlinx Coroutines Test `1.11.0` |

---

## Installation

1. Download **`smartnote-release.apk`** from the [v1.0.12 GitHub Release](../../releases/tag/v1.0.12).
2. Verify the APK checksum before installing:
   - **File:** `smartnote-release.apk`
   - **Size:** `57,206,704` bytes (`54.56 MiB`)
   - **SHA-256:** `0c1486456b42bf8c65f69985032276425189ccef216fcc3de2015200dd728f7a`
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
  - **Debug Unit/Integration/UI Suites (`testDebugUnitTest`):** 10 suites, **34 tests passed, 0 failures, 0 errors, 0 skipped**.
  - **Release Unit/Integration/UI Suites (`testReleaseUnitTest`):** 10 suites, **34 tests passed, 0 failures, 0 errors, 0 skipped** (validating R8/ProGuard behavior alongside business logic, FTS5 search latency on 2,500 synthetic notes `< 250 ms`, ZIP-Slip traversal rejection, empty ghost note cleanup, image attachment/OCR OOM safety, opt-in auto-title + on-demand `Generate` button, NPU-first energy-saving dispatch, default-on semantic search, **"Enable AI Features"** gray-out/restore behavior, upgraded bilingual `#hashtag` / compound / semantic SmartTags, Room `MIGRATION_1_2`, simplified progressive-disclosure `ReminderPickerSheet` + centered `TimePicker` dialog, silent Today-only Now Bar channel + audible/vibrating due channels + Wear OS `WearableExtender`/`RemoteInput` bridging, full-viewport trailing-block focus, SmartSearch header clipping, upward-morphing `+` pill menu, pop-out bubble top-bar `Sort` and `Menu` menus, flying bottom-bar pill indicator, System Integrations & Diagnostics layouts, and end-to-end Compose UI flows).
- **Release APK Verification (`build-tools/37.0.0`):**
  - `zipalign -c -P 16 -v 4 smartnote-release.apk`: **Verification successful** (16 KB ELF page-aligned).
  - `apksigner verify --print-certs -v smartnote-release.apk`: **Verifies** under APK Signature Scheme v3 (RSA-4096).
  - `aapt2 dump badging smartnote-release.apk`: Confirmed `io.github.ultrapg.smartnote`, `versionCode='13'`, `versionName='1.0.12'`, `minSdkVersion:'28'`, `targetSdkVersion:'36'`, `arm64-v8a` & `x86_64`.
  - Archive inspection confirmed `assets/models/smartnote_text_embed_v1.tflite` and native libraries (`libsqliteJni.so`, `libmlkit_google_ocr_pipeline.so`, `libLiteRt.so`, `libLiteRtClGlAccelerator.so`) are packaged with zero Gemini/AppFunctions artifacts and zero keystores or credentials.

### Hardware-Dependent Checks (Require Physical Android Device)
The Linux build environment has no `/dev/kvm` or attached Android device, so the following behaviors are implemented and compile-verified but require physical-device validation:
- **LiteRT Hardware Delegates:** Physical vendor NPU (`CompiledModel` NPU) and GPU delegate acceleration paths (host tests verify model loading and CPU/fallback execution).
- **Android 16 Live Updates OEM Surfaces:** System status-chip / Samsung One UI 8 Now Bar promotion behavior under OEM lock-screen policies.
