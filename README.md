# SmartNote

> **Fast to capture, private by design, intelligent on-device.**

![Android 9+](https://img.shields.io/badge/Android-9%2B%20(API%2028--36)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

**SmartNote** is a native Android note-taking application built in Kotlin with Jetpack Compose and **Material 3 Expressive**. Everything—note storage, full-text & semantic search, OCR, auto-tagging, title generation, duplicate detection, backups, and biometric/PIN locking—runs locally on your device with no accounts, no cloud sync, no telemetry, and no external AI API keys.

- **Package ID:** `io.github.ultrapg.smartnote`
- **Latest Release:** [`v1.0.18`](https://github.com/ultrapg/SmartNote/releases/tag/v1.0.18) (`versionCode = 19`)
- **Compatibility:** Android 9+ (`minSdk = 28`, `targetSdk = 36`, `compileSdk = 37`)
- **ABIs:** `arm64-v8a`, `x86_64`
- **License:** Apache License 2.0 ([LICENSE](LICENSE))

---

## Highlights

### Capture, Editor & Design
- **Material 3 Expressive UI**: Near-critically damped spring motion (`SmartNoteSoftMotionScheme` + refined menu springs in `SmartNoteMotion` with automatic `MotionScheme.standard()` reduced-motion fallback), dynamic Material You colors + **SmartNote Violet**, **Neutral**, and **True-Black AMOLED** themes, adaptive bottom bar / `NavigationRail` (`>= 600dp`), **Staggered Cards** or **Compact List** layouts, lockstep morphing `+` creation menu, and spring pop-out bubble overview menus.
- **Full-Screen Block Editor**: Author titles, Markdown-styled prose (`#` headings, `**bold**`, `*italic*`, `` `code` ``), reorderable checklists, embedded images with EXIF-correct thumbnails and OCR viewer/re-scan/insert actions, and rich link preview cards.
- **Crash-Safe Persistence, Share AI Pipeline & Backups**: Debounced `NoteAutosaveCoordinator` with `SavedStateHandle` draft recovery, atomic Room transactions, **Archive** & **30-day Recycle Bin**, **SmartCapture Sharesheet** (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` with live multi-engine SmartTags, on-demand ✨ title generation, and immediate post-save OCR / link preview / SmartTags / auto-title processing), Home Screen **Glance Widgets**, **Quick Settings Tile**, App Shortcuts, and **`.smartnote.zip` Local Backup/Restore** with strict ZIP-Slip protection.

### Security & Privacy
- **Native Android App Lock (`Settings → Security & App Lock`)**: Lock SmartNote behind your device's native **fingerprint, face unlock, or PIN / pattern / password** (`android.hardware.biometrics.BiometricPrompt` + `KeyguardManager` fallback across API 28–36). Requires native confirmation to enable or disable, supports configurable auto-lock timeouts (**Immediately**, **After 30 sec**, **After 1 min**, **After 5 min**), includes **Lock SmartNote now**, and enforces `FLAG_SECURE` (`Hide content in Recents`) to conceal note previews from app switchers and screenshots.
- **Zero Background Network by Default**: Network access is strictly off unless you explicitly opt into **Fetch Link Previews** in Settings (disabled by default). Every link preview fetch validates target URLs against private/loopback/link-local IP ranges on every redirect hop before loading webpage titles, descriptions, and cached WebP preview thumbnails.

### On-Device AI & Graceful Fallbacks

SmartNote automatically probes available device engines (**Gemini Nano `Fast` & `Full`** via Android AICore, **LiteRT** with NPU/GPU/CPU delegation, **Bundled ML Kit Text Recognition**, **Play Services ML Kit Image Labeling**, and **Android `TextClassifier`**). Unsupported engines are detected automatically and skipped without errors.

Inside **Settings → AI & Smart Features → Gemini Nano**, each AI feature has an independent **`Off` / `Auto` / `Force`** control plus **`Fast` / `Full`** variant selection:

| Feature | `Auto` / Hybrid Behavior | Local Fallback When `Off` or Unsupported |
| :--- | :--- | :--- |
| **SmartTags** | Runs fast local 34-category EN/DE rules + entities + image labels + LiteRT embeddings first; calls Gemini Nano when unsure (always invoked on `Force`). `Allow new topic tags` is **ON** by default. | `#hashtag` parsing, bilingual EN/DE rules, German compound decomposition, user-confirmed tag profiles (`>= 0.99f`), ML Kit Image Labeling, Android `TextClassifier`, signed-trigram LiteRT embeddings |
| **Title Generation** | Generates up to 4 cycleable titles; passes EXIF-upright attached images (`ImagePart`) alongside note/OCR/link context whenever prose is short (`< 160` chars) with text-only retry fallback (`Force` ranks Nano ahead of `#` headings) | Markdown `#` headings, EN/DE filler/clause distillation, checklist & flight-number synthesis, image-label synthesis, keyphrase scoring |
| **SmartSearch** | Stage 1 returns instant lexical + LiteRT results while typing; Stage 2 expands queries (`expandSearchQuery`) and runs semantic note matching (`matchNotesForSearchQuery`) on multi-word/sparse queries or **whenever local search finds 0 results** (`Force` always runs both) | SQLite **FTS5** (Porter + Trigram) weighted BM25, Damerau–Levenshtein *Did you mean* typo correction, bidirectional EN/DE synonyms with umlaut folding, `#tag` / `has:*` / `is:pinned` / `after:` operators, LiteRT cosine re-ranking |
| **Text Recognition (OCR)** | Runs upgraded ML Kit OCR first; Gemini Nano visually transcribes unreadable/empty images and cleans up noisy/low-confidence drafts (`Force` always enhances scans with Gemini Nano) | Spatial bounding-box row/column sorting (`OcrTextPostProcessor`), wrapped-hyphen rejoining, status-bar artifact filtering, `2560 px` upright decode, contrast/upscale Pass-2 retry, SHA-256 cache, interactive editor **Re-scan** & **Insert into note** |

---

## Installation

1. Download **`smartnote-release.apk`** from the [v1.0.18 GitHub Release](https://github.com/ultrapg/SmartNote/releases/tag/v1.0.18).
2. Verify the APK checksum before installing:
   - **File:** `smartnote-release.apk`
   - **Size:** `58,091,175` bytes (`55.40 MiB`)
   - **SHA-256:** `e5ed66574c837c25ced4df3d4d2408c4231897697fb4d3bcdd4d78db3a75012e`
   - **APK Signature Scheme v3 Signer Cert SHA-256 (4096-bit RSA):**
     `b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`
3. Install on an Android 9+ device via system package installer or `adb install smartnote-release.apk`.

---

## Architecture & Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Language / Build** | Kotlin `2.2.21`, KSP `2.2.21-2.0.5`, Gradle `8.13`, AGP `8.13.2` |
| **UI / Motion** | Jetpack Compose BOM `2026.06.01`, Material 3 `1.5.0-alpha15`, Navigation Compose `2.9.8` |
| **Persistence / Search** | Room `2.8.5`, Bundled SQLite `2.7.1` (FTS5 Porter + Trigram), DataStore Preferences `1.1.2` |
| **On-Device ML / OCR / GenAI** | Google LiteRT `2.3.2`, ML Kit Text Recognition `16.0.1`, ML Kit Image Labeling `16.0.8`, ML Kit GenAI Prompt `1.0.0-beta5`, Android `TextClassifier` |
| **Security / Platform** | Android `BiometricPrompt` / `KeyguardManager`, `FLAG_SECURE`, WorkManager `2.11.2`, Glance `1.2.0`, Core KTX `1.18.0`, OkHttp `4.12.0` |
| **Testing** | JUnit4, Robolectric `4.14.1`, Compose UI Test, Kotlinx Coroutines Test `1.11.0` |

---

## Building from Source

### Prerequisites
- JDK 21
- Android SDK with Platform `android-36` / `android-37` (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 28`) and Build-Tools `37.0.0`

### Debug & Test Builds
```bash
git clone https://github.com/ultrapg/SmartNote.git
cd SmartNote
# Set sdk.dir in local.properties if ANDROID_HOME is not set
./gradlew testDebugUnitTest testReleaseUnitTest assembleDebug --no-daemon
```

### Signed Release Builds
`app/build.gradle.kts` reads release signing credentials from environment variables (`SMARTNOTE_KEYSTORE_PATH`, `SMARTNOTE_KEYSTORE_PASSWORD`, `SMARTNOTE_KEY_ALIAS`, `SMARTNOTE_KEY_PASSWORD`) or from an untracked properties file at `~/.android/smartnote-keys/keystore.properties`:

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
- `./gradlew testDebugUnitTest --rerun-tasks --no-daemon --warning-mode=summary`, `./gradlew testReleaseUnitTest --rerun-tasks --no-daemon --warning-mode=summary`, and `./gradlew assembleRelease --no-daemon --warning-mode=summary` completed cleanly (`BUILD SUCCESSFUL`) with zero Kotlin source compiler warnings:
  - **Debug Unit/Integration/UI Suites (`testDebugUnitTest`):** 9 suites, **40 tests passed, 0 failures, 0 errors, 0 skipped**.
  - **Release Unit/Integration/UI Suites (`testReleaseUnitTest`):** 9 suites, **40 tests passed, 0 failures, 0 errors, 0 skipped** (verifying R8/ProGuard behavior alongside FTS5 + bilingual synonym + zero-result/forced semantic SmartSearch `< 250 ms` on 2,500 synthetic notes, ZIP-Slip traversal rejection, empty ghost note cleanup, Share-to-SmartNote `NoteProcessingPipeline` OCR/link-metadata/auto-title/SmartTags & revision deduplication, EXIF-upright & spatial layout OCR + `SmartOcrCoordinator` `Off`/`Auto`/`Force` routing, SSRF-safe `Fetch Link Previews` defaults & HTML entity/thumbnail resolution, native `AppLockSessionTracker` lifecycle/timeout locking & Recents settings, per-feature `Off`/`Auto`/`Force` Gemini Nano controls, 34-category SmartTags, Room migrations `1->2->3`, and end-to-end Compose UI + menu open/close/switch flows).
- **Release APK Verification (`build-tools/37.0.0`):**
  - `zipalign -c -P 16 -v 4 smartnote-release.apk`: **Verification successful** (16 KB ELF page-aligned).
  - `apksigner verify --verbose --print-certs smartnote-release.apk`: **Verifies** under APK Signature Scheme v3 (RSA-4096).
  - `aapt2 dump badging smartnote-release.apk`: Confirmed `io.github.ultrapg.smartnote`, `versionCode='19'`, `versionName='1.0.18'`, `minSdkVersion:'28'`, `targetSdkVersion:'36'`.

### Hardware-Dependent Checks (Require Physical Android Device)
Because this Linux build host has no `/dev/kvm` or attached physical Android device, the following behaviors are compile-verified and covered by host contract/fallback tests rather than executed on live phone hardware:
- **Native Android Biometric / Device PIN Prompt:** System `BiometricPrompt` / `KeyguardManager` UI dialogs and enrolled fingerprint/PIN verification across Android 9–16 devices.
- **On-Device Model Runtimes:** Live AICore Gemini Nano (`Fast`/`Full`) download & inference, bundled ML Kit OCR pipeline inference on camera/screenshot bitmaps, Play Services Image Labeling downloads, OEM `TextClassifier` extraction, and LiteRT NPU/GPU delegates.
- **Network Link Preview Fetching:** Live HTTP(S) OpenGraph metadata and image thumbnail downloads when **Fetch Link Previews** is explicitly enabled by the user.

---

## License

Licensed under the **Apache License, Version 2.0**. See [LICENSE](LICENSE) for full terms.
