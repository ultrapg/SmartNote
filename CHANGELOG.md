# Changelog

All notable changes to **SmartNote** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.7] - 2026-11-18

### Added
- **Upward-Morphing Pill `+` Creation Menu (`NotesOverviewScreen.kt`, `SmartNoteRoutes.kt`, `SmartNoteApp.kt`, `NoteEditorScreen.kt`)**:
  - Replaced the static `+` FAB with `ExpressiveMorphingCreateFab`: tapping `+` smoothly morphs the `56.dp` (`16.dp` corner) button upward into a `176.dp`-wide vertical pill container (`28.dp` corner radius) using calm critically damped `SmartNoteMotion.softSizeSpring()` physics (with automatic reduced-motion fallback), rotating `+` by `45°` into a bottom-anchored `Close` control while rolling out **Reminder**, **Image** (launches Android Photo Picker + offline OCR, cancelling cleanly with zero ghost draft if dismissed untouched), **Checklist**, and **Note** (`NoteCreationOption`) directly above the thumb.
  - Added outside-tap dismissal overlay, `BackHandler`, and automatic collapse on selection mode, plus atomic `saveReminder` hardening (`activeImportsCount` + `NonCancellable` + snapshot refresh) in `NoteEditorViewModel`.

### Removed
- **Empty-State Action Chips (`NotesOverviewScreen.kt`)**:
  - Removed the redundant `+ New note` and `New checklist` chips below `"Capture anything, offline"` in `EmptyNotesPlaceholder` so empty overview screens stay clean and uncluttered.

### Verification
- `testDebugUnitTest`: **10 suites, 27 tests passed, 0 failures, 0 errors, 0 skipped** (including new `morphingCreatePillMenuRollsOutUpwardAndLaunchesOptions` Compose UI test).
- `testReleaseUnitTest`: **10 suites, 27 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,032,365` bytes (`54.39 MiB`), `SHA-256: 6e5688f2a2bcd5ccf23883741c1e28759f3ed27835a8bcfbc4ee271f9706ece9`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.6] - 2026-11-18

### Fixed
- **SmartSearch Header Overlap & Scroll Clipping (`SmartSearchScreen.kt`)**:
  - Added an explicit opaque `MaterialTheme.colorScheme.background` behind `SmartSearchScreen`'s `Scaffold` `topBar` (search field + filter chips strip + status bar) and hard-clipped the results `LazyColumn` below `innerPadding.calculateTopPadding()` (`Modifier.padding(top = innerPadding.calculateTopPadding())` with `contentPadding(top = 4.dp)`), eliminating `"Did you mean"` banners and result cards bleeding visibly behind filter chips when scrolled or when the soft keyboard opens.
  - Added `rememberLazyListState()` and `LaunchedEffect(uiState.query, uiState.filter, uiState.didYouMeanSuggestion)` resetting scroll to index `0` so Compose `LazyColumn` key-anchoring no longer shifts prepended `"Did you mean"` banners and result-count rows above the fold when transitioning from an empty query to a typed query.
  - Moved horizontal `16.dp` padding off `topBar`'s outer `Column` onto `OutlinedTextField` and gave the filter-chip `LazyRow` `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` so chips align at `16.dp` while scrolling smoothly edge-to-edge without getting cut off `16.dp` inset from the right screen margin.
- **App-Wide `Scaffold` Top-Bar Clipping Hardening**:
  - Added explicit `MaterialTheme.colorScheme.background` to `NotesOverviewScreen`'s custom `topBar` `Box`, clipped `NoteEditorScreen`'s `BoxWithConstraints` + `VerticalLazyScrollbar` cleanly below `innerPadding.calculateTopPadding()`, and hard-clipped `LazyColumn` viewports below `innerPadding.calculateTopPadding()` in `ArchiveAndTrashScreens.kt` and `SettingsScreens.kt`.

### Verification
- `testDebugUnitTest`: **10 suites, 26 tests passed, 0 failures, 0 errors, 0 skipped** (including new `smartSearchHeaderClipsScrolledResultsAndEdgeToEdgeChips` Compose UI layout regression test).
- `testReleaseUnitTest`: **10 suites, 26 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,032,370` bytes (`54.39 MiB`), `SHA-256: 8f5a5c2573063cfc8b068f396d89824e673cf6c209fc598c3f4206599dee8836`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.5] - 2026-11-18

### Added
- **Full-Viewport Clickable Note Editor**:
  - Dynamically expands single-block note editors (`BoxWithConstraints` + live title-header measurement) down to the bottom toolbar without triggering false scrollability on blank/short notes, and adds `NoteEditorViewModel.ensureTrailingEditableBlock()`, `pointerInput(detectTapGestures)` fallback, and a trailing `120.dp` tap zone under images/links/checklists so tapping anywhere below note content focuses the bottom input and opens the keyboard cleanly with the cursor at the end (`TextFieldValue` + `FocusRequester` + `moveCursorToEndSignal`) without creating untouched ghost notes.
- **Visible Vertical Scrollbar Indicator**:
  - Added `VerticalLazyScrollbar` (`ExpressiveComponents.kt`) overlaid between the editor top bar and bottom toolbar/IME, computing pixel-accurate progress from `LazyListLayoutInfo` and displaying a calm `0.38f` resting / `0.78f` active thumb whenever content can scroll.

### Verification
- `testDebugUnitTest`: **10 suites, 25 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 25 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,032,365` bytes (`54.39 MiB`), `SHA-256: 0ffccc43a4c7e883c3b66e18eab8692d5b0850de3859ce2011db3d9fb077caa4`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.4] - 2026-11-18

### Changed
- **Auto-Title Generation Disabled by Default**:
  - Changed `AppSettings.autoTitleEnabled`, `NoteEditorUiState.autoTitleEnabled`, and `SettingsRepository` DataStore/boot-cache defaults (`auto_title_enabled_v2`) to `false` so untitled notes remain untitled unless users opt in under **Settings → AI & Smart Features → Auto-Title Generation**.

### Fixed
- **Eliminated Image Attachment Crashes Across Editor, Coil, & Offline OCR**:
  - Fixed a deterministic `SQLiteConstraintException` (`FOREIGN KEY constraint failed`) when adding images to a blank note: separated `NoteEditorViewModel.flushPendingEditsOnly()` (`ON_STOP` / Photo Picker lifecycle) from `finishEditorSession()` (Back exit), introduced `prepareNoteForForeignMutation()` and `activeImportsCount` + `NonCancellable` guards, and added defensive parent-note insertion inside `NoteRepository.addAttachmentToNote(...)`.
  - Fixed text/image concurrency races by preserving active database attachments inside `NoteRepository.saveNoteContent(...)`, deleting attachment files/rows prior to block cleanup in `removeAttachmentBlock(...)`, and refreshing UI state snapshots immediately after import (`refreshUiFromDetailSnapshot()`).
  - Eliminated huge-bitmap UI / Canvas crashes (`RecordingCanvas` / GPU OOM) by implementing `coil.ImageLoaderFactory` (`allowHardware(false)`, `allowRgb565(true)`) on `SmartNoteApplication`, adding `rememberBoundedImageRequest(...)`, sizing `ImageBlockCard` explicitly (`220.dp`, capped at `1280px`) and overview/capture thumbnails (`512px`) / fullscreen viewer (`2048px`).
  - Eliminated full-resolution `OutOfMemoryError` crashes in `MlKitBundledOcrEngine` by replacing direct `InputImage.fromFilePath(...)` decoding with `SafeOcrBitmapLoader.decodeDownsampledBitmap(...)` (`<= 2048px`) + EXIF orientation metadata, and guarding `BitmapRegionDecoder` with clean fallback.
  - Added EXIF orientation normalization in `AttachmentStorage.generateThumbnail(...)` / `readImageDimensions(...)` and `runCatching` guards around third-party `ContentResolver` queries and MIME lookups in `AttachmentImporter` (plus HEIC/HEIF/AVIF/GIF/BMP mappings).

### Verification
- `testDebugUnitTest`: **10 suites, 23 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 23 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,015,980` bytes (`54.37 MiB`), `SHA-256: b2735258df390b9959b1475b618d97c8fbd6dafa4389a8286ac48c24078d0281`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.3] - 2026-11-18

### Fixed
- **Removed Empty Ghost Bars & Unwanted Outline Lines**:
  - Switched new notes in `NoteEditorViewModel` to lazy database creation (`persistedInDb`) and added `NoteRepository.discardIfCompletelyBlank(...)` + `NoteRepository.pruneBlankUntouchedNotes(...)` (triggered automatically on app startup in `SmartNoteApplication` and editor exit via `NoteAutosaveCoordinator.discardAndDeleteIfBlankAsync`) so backing out of an untouched blank note or checklist never leaves empty pill bars, and existing empty ghost drafts from prior versions are cleaned up automatically.
  - Filtered blank untouched ghost summaries (`isBlankUntouchedGhost`) in `NotesOverviewViewModel` and added an `"Untitled note"` card fallback when a pinned/tagged note has empty title/body.
  - Lowered `LazyVerticalStaggeredGrid` adaptive cell minimum from `168.dp` to `150.dp` so `360dp+` phones render a true 2-column staggered card grid instead of collapsing into full-width horizontal bars.
  - Removed wireframe outline strokes across note cards, compact rows, `smartNoteCardBorder()`, tonal `FilterChip`s (`smartNoteTonalFilterChipColors()`), and search bars on `SmartSearchScreen` and `TagsScreen`, while raising `AmoledDark` tonal container contrast (`#121218`–`#282834` over `#000000`) so cards separate cleanly without borders.
- **Eliminated Screen Flashing & Made Motion Sleek and Calm**:
  - Added dark-safe `Theme.SmartNote` (`android:windowBackground` `#13121A` in `values-night/themes.xml` and `#FBF8FF` in `values/themes.xml`), an opaque root `Surface` in `SmartNoteApp`, explicit `MaterialTheme.colorScheme.background` containers on all screen `Scaffold`s, synchronous window background synchronization in `SmartNoteTheme` (`syncWindowBackground = true` for `MainActivity`), and synchronous `SharedPreferences` boot caching in `SettingsRepository.currentSnapshot` to eliminate white window bleed and theme/layout cold-start flashes.
  - Lifted `NavigationBar` and `NavigationRail` into a persistent outer chrome shell in `SmartNoteApp` outside `NavHost` so switching top-level tabs never crossfades or flickers the navigation bar.
  - Replaced semi-transparent overlapping `NavHost` crossfades with opaque `alpha = 1.0` transitions (`delayMillis` hold under `110 ms` tab fades, stationary opaque `EnterTransition.None` pops, and calm `170 ms` / `140 ms` vertical glides for detail routes), removed `AnimatedContent` layout swaps and `Modifier.animateItem` fade-from-zero flashes across lists/grids, and tuned `SmartNoteSoftMotionScheme` / `SmartNoteMotion` springs to critical damping (`dampingRatio = 1.0f`).

### Verification
- `testDebugUnitTest`: **10 suites, 21 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 21 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `56,999,598` bytes (`54.36 MiB`), `SHA-256: 825d0ed4d951cf69b3dbc72327f93e2d8c363dcafd6a43f02f2552c614648890`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`), with zero Gemini/AppFunctions artifacts packaged.

## [1.0.2] - 2026-11-18

### Added
- **Master AI Control (`aiFeaturesEnabled`)**:
  - Added a top-level **Enable All AI Features** switch in **Settings → AI & Smart Features** gating all on-device AI capabilities (`Auto-Title Generation`, `SmartTags`, `Offline OCR`, `Duplicate Detection`, and `Hybrid Semantic Search (LiteRT)`), automatically disabling sub-switches and unloading any active LiteRT model when turned off while preserving individual preferences.
  - Wired effective AI guards consistently across `NoteEditorViewModel`, `NoteProcessingWorker`, `SmartCaptureActivity`, `SmartSearchViewModel`, `SmartTitleGenerator`, `SmartNoteAiEngine`, and `SmartNoteOperationsApi`.

### Changed
- **Refined Animations & Restored Crisp Material 3 Visuals**:
  - Replaced underdamped jello springs (`SmartNoteGooeyMotionScheme`, shared-element `sharedBounds`, corner-morphing cards, and `ExpressiveGooeyFab`) with calm, well-damped `SmartNoteSoftMotionScheme` springs (`0.85`–`0.90` damping ratio) and restored clean `v1.0.0` Material 3 `FloatingActionButton` and static rounded card shapes.
  - Replaced full-screen bounce navigation with pure crossfades between top-level tabs and crisp `180 ms` vertical slide-fade transitions for detail screens.
- **Removed Gemini / AppFunctions Integration**:
  - Removed `SmartNoteAppFunctions`, `androidx.appfunctions` dependencies, KSP schema generation, ProGuard keep rules, and Gemini UI settings while keeping the local `SmartNoteOperationsApi` for internal validated note operations and Glance widgets/shortcuts.

### Fixed
- **Usability & Layout Bugs Across Screens**:
  - Replaced `NavigationSuiteScaffold` with an explicit `TopLevelAdaptiveShell` (`NavigationBar` on compact screens that hides cleanly when the IME opens on Search/Tags, `NavigationRail` on screens `>= 600dp`), eliminating navigation-inset jumps during route transitions.
  - Replaced the fixed-width editor floating toolbar with a horizontally scrollable `CircleShape` floating toolbar so all formatting, image, and tag actions remain accessible on narrow (`320dp`–`360dp`) displays.
  - Fixed `MarkdownVisualTransformation` heading/code colors to adapt dynamically to Dark and AMOLED themes, moved duplicate detection in `NoteEditorViewModel` to `Dispatchers.IO` gated on `contentRevision`, fixed `SmartSearchScreen` keyboard overlap (`WindowInsets.ime` padding without double offset), fixed `SwipeableCompactNoteRow` stuck swipe state via `dismissState.reset()`, fixed `SmartCaptureActivity` navigation-bar gap by moving window insets inside the sheet `Surface`, and eliminated all Kotlin deprecation warnings.

### Verification
- `testDebugUnitTest`: **10 suites, 20 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 20 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `56,999,557` bytes (`54.36 MiB`), `SHA-256: 872475ebde2f9ef8d5dab73139a16a3b91e05aa602ad6adb32e3ce62c8bcaca0`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`), with zero Gemini/AppFunctions artifacts packaged.

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
