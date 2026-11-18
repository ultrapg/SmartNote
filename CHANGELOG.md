# Changelog

All notable changes to **SmartNote** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-11-18

### Added
- **Offline Auto-Title Generation** (`SmartTitleGenerator`):
  - Synthesizes concise, readable titles completely offline from Markdown `#` headings, grocery/packing/task checklists, OCR screenshot text (filtering noisy mobile status-bar timestamps and carrier headers), URL slugs/domains, and `SmartNoteAiEngine` semantic sentence scoring.
  - Added a ✨ **Generate smart title** button and animated `Auto-title:` suggestion `AssistChip` in `NoteEditorScreen`.
  - Automatically populates blank untitled notes when flushing/saving or exiting the editor (respecting manual title clears and Settings toggles), during blank-title `SmartNoteOperationsApi.createNote` calls, and after background `NoteProcessingWorker` OCR/link extraction completes.
  - Added an **Auto-Title Generation** toggle in **Settings → Smart Features** (`autoTitleEnabled`, default enabled).

### Changed
- **Softer Gooey / Flexible Motion Physics**:
  - Replaced crisp snap springs with `SmartNoteGooeyMotionScheme` (underdamped, low-stiffness spatial springs) and `SmartNoteMotion` gooey placement/size/corner/bounds helpers.
  - Added anisotropic `expressivePressable` squash-and-stretch feedback, `rememberGooeyRoundedShape` corner morphing, GPU-rendered `ExpressiveGooeyFab` (`Cookie4Sided` ↔ `Cookie9Sided` morphing on press/expand), interactive morphed `ExpressiveShapeBadge`, persistent `NavigationSuiteScaffold` show/hide motion, elastic `NavHost` + `gooeyBoundsTransform` shared transitions, and `Modifier.animateItem` list/grid item physics.

### Fixed
- **Edge-to-Edge Status Bar, Navigation Bar & IME Insets**:
  - Added reactive `ComponentActivity.enableEdgeToEdge` bar style updates and `window.isNavigationBarContrastEnforced = false` inside `SmartNoteTheme` so status bar and navigation bar icons contrast properly in Light, Dark, and AMOLED themes without translucent system scrims.
  - Added `.statusBarsPadding()` around custom floating top search pills in `NotesOverviewScreen`, `SmartSearchScreen`, and `TagsScreen` so top-bar content never overlaps status-bar clocks or icons.
  - Fixed `NoteEditorScreen` bottom `HorizontalFloatingToolbar` insets with `WindowInsets.navigationBars.union(WindowInsets.ime)` to eliminate double bottom padding when the keyboard is open, added `.navigationBarsPadding()` to modal bottom sheets, added `.systemBarsPadding()` to the fullscreen image viewer and `SmartCaptureActivity`, and made `EmptyNotesPlaceholder` vertically scrollable on compact/split-screen viewports.

### Verification
- `testDebugUnitTest`: **10 suites, 20 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 20 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,474,433` bytes (`54.81 MiB`), `SHA-256: 0a033c86efcf19b3cd0982a07839d549f303ccdf0d49e27be6d95fc610c7a80e`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.0] - 2026-11-18

### Added

#### Architecture & Design System (Material 3 Expressive)
- **Material 3 Expressive UI** (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, shared-element transitions, Predictive Back support, and subtle haptic feedback) built with Jetpack Compose BOM `2026.06.01` and Material 3 `1.5.0-alpha15`.
- **Expressive Component Suite** using `HorizontalFloatingToolbar`, `SplitButtonLayout`, `ButtonGroup`, `LoadingIndicator`, and `MaterialShapes` pill/squircle morphing across overview, editor, search, tags, and settings screens.
- **Four Theme Palettes** with Light / Dark / System mode persistence via Jetpack DataStore:
  - Dynamic Color (Material You wallpaper extraction, default on supported Android versions)
  - SmartNote Violet
  - Neutral
  - True-Black AMOLED (`#000000` background surfaces in dark mode, high-contrast light mode)
- **Adaptive Layout & Navigation**: `NavigationSuiteScaffold` switching automatically between Bottom Navigation Bar on compact phones and Navigation Rail on expanded screens, tablets, and foldables, paired with **Staggered Cards** (default) and **Compact List** layouts.

#### Core Note-Taking & Reliability
- **Block + Markdown + Checklist Editor**: Distraction-free editor supporting titles, multiline prose with lightweight inline Markdown visual transformation (`#` headings, `**bold**`, `*italic*`, `` `code` ``), reorderable interactive checklists, embedded images, and clickable link cards.
- **Debounced & Crash-Safe Autosave** (`NoteAutosaveCoordinator`): `650 ms` debounce with `4000 ms` maximum flush latency, lifecycle/navigation flush triggers, `SavedStateHandle` draft recovery, and SQLite `PRAGMA foreign_keys = ON` preserved via Room `@Upsert` + `immediateTransaction`.
- **Archive & 30-Day Recycle Bin**: Soft deletion with automatic 30-day purge scheduling via WorkManager (`MaintenanceCleanupWorker`) and attachment reference-count protection so permanently deleting one note never breaks shared duplicate attachments.

#### SmartCapture & Offline Intelligence
- **SmartCapture Sharesheet Receiver** (`SmartCaptureActivity`): Accepts `ACTION_SEND` and `ACTION_SEND_MULTIPLE` (`text/plain`, `image/png`, `image/jpeg`, `image/webp`, `image/*`, URLs, and multi-image shares) with immediate private staging, screenshot heuristics, quick-save UX, and private app directory traversal blocking.
- **Bundled On-Device OCR** (`MlKitBundledOcrEngine`): Offline Latin text recognition via `com.google.mlkit:text-recognition:16.0.1`, SHA-256 OCR deduplication cache, and asynchronous WorkManager processing (`NoteProcessingWorker`).
- **Offline SmartTags & Duplicate Detection**: Deterministic local categorization (`SmartTagsEngine`) respecting manual user confirmations and permanent dismissals, plus `SmartDuplicateDetector` combining SHA-256 exact match, normalized URL match, and 64-bit SimHash Hamming similarity without ever auto-deleting notes.
- **SmartSearch (SQLite FTS5)** (`SmartSearchEngine`): Bundled `androidx.sqlite:sqlite-bundled` FTS5 virtual tables combining `porter unicode61 remove_diacritics 2` + `trigram` tokenizers, field weighting (Title > Checklist > Content > OCR > URL), Damerau-Levenshtein typo tolerance (*Did you mean*), offline synonym expansion, filter chips (`#tags`, pinned, checklists, images, OCR, date range), and latency verified under `250 ms` at 2,500 notes on JVM/Robolectric.
- **On-Device LiteRT Engine** (`SmartNoteAiEngine`): Local `smartnote_text_embed_v1.tflite` (`33,840` bytes) model via `com.google.ai.edge.litert:litert:2.3.2` with runtime backend negotiation (`CompiledModel` NPU -> GPU -> CPU XNNPACK -> deterministic normalized bag-of-n-grams fallback) for local semantic reranking, gated by master and per-feature toggles.

#### System Integration, Gemini AppFunctions & Portability
- **Exact Reminders & Android 16 Live Updates**: `AlarmManager` exact-alarm scheduling with boot/timezone recovery (`SystemTimeBootReceiver`) and Android 16 promoted Live Update notification builder (`setRequestPromotedOngoing`, `setShortCriticalText`, `ProgressStyle` when supported, clean standard fallback otherwise).
- **Android System Surfaces**: 3 Jetpack Glance Material 3 home-screen widgets (Quick Capture, Pinned/Recent Note, Overview Summary), `QuickNoteTileService` Quick Settings Tile, and static + dynamic App Shortcuts.
- **Jetpack AppFunctions & Operations API**: Exposed `@AppFunction` endpoints via `androidx.appfunctions:appfunctions-service:1.0.0-alpha08` (`SmartNoteAppFunctions`, packaged `app_functions.xml` / `app_functions_v2.xml` / `app_functions_schema.xsd`) backed by a validated, audit-friendly `SmartNoteOperationsApi` that enforces soft-delete-only mutations and honors SmartNote's offline AI/integration master toggles.
- **Local Backup & Restore** (`BackupManager`): Self-contained `.smartnote.zip` archive with strict ZIP-Slip canonical path validation, `MERGE` and `REPLACE_ALL` modes, attachment/tag integrity, and immediate FTS5 reindexing.

### Verification
- `testDebugUnitTest`: **10 suites, 19 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 19 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,425,224` bytes (`54.77 MiB`), R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`SHA-256: b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).
