# Changelog

All notable changes to **SmartNote** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.14] - 2026-11-19

### Added & Changed
- **Upgraded Tag Generation + System `TextClassifier`, ML Kit Image Labeling & On-Device Gemini Nano (`SmartTagsEngine.kt`, `SystemEntityAnalyzer.kt`, `ImageLabelingEngine.kt`, `OnDeviceGenAiAssistant.kt`, `NoteEditorViewModel.kt`, `NoteProcessingWorker.kt`, `AppContainer.kt`)**:
  - Added 3 modular on-device intelligence adapters: Android System `TextClassifier` (`AndroidSystemEntityAnalyzer`) for flight numbers, tracking numbers, addresses, and contact signals; Play Services ML Kit Image Labeling (`MlKitImageLabelingEngine`, `com.google.android.gms:play-services-mlkit-image-labeling:16.0.8`) for automatic visual object/scene tagging; and ML Kit GenAI Prompt / Gemini Nano (`MlKitGeminiNanoAssistant`, `com.google.mlkit:genai-prompt:1.0.0-beta5`) with safe availability probing, non-blocking background download when downloadable, and multimodal photo fallback when supported on device.
  - Expanded `SmartTagsEngine` to 32 bilingual EN/DE categories (adding `Personal`, `Tech`, `Pets`, `Photography`, and `Quotes`), expanded domain rules, German plural/inflection stemming, lower single-strong-anchor threshold, shared personal user-tag profile builder (`buildUserTagContext`), and Stage 4 open-vocabulary topic keyword extraction for explicit on-demand generation while preserving strict weak-filler rejection.
  - Upgraded on-demand `Generate tags` in `NoteEditorViewModel` and `NoteEditorScreen` to merge System `TextClassifier` entities, ML Kit Image Labeling labels, personal tag profiles, LiteRT neural semantic affinities, optional Gemini Nano tags, and open-vocabulary topic keywords—available directly in the editor and `ManageTagsSheet` whenever a note has content or images.
  - Surfaced live diagnostics for **Gemini Nano (On-Device)**, **Image Labeling**, and **System Entity Recognition** plus a **`Prepare Gemini Nano`** button (when downloadable) inside `AiProcessingSettingsScreen` (`SettingsScreens.kt`).
- **Upgraded Bilingual & Multi-Candidate Title Generation (`SmartTitleGenerator.kt`, `NoteEditorViewModel.kt`)**:
  - Added `generateTitleCandidates` returning up to 4 distinct clean candidates, enabling repeated taps on the editor's ✨ title button to cycle cleanly through alternatives.
  - Added bilingual EN/DE conversational filler-prefix stripping, subordinate/coordinating clause compression, dangling trailing function-word cleanup, inline `#hashtag` removal, meta/date/greeting line penalties, German shopping/packing & Workout/Watchlist checklist synthesis, keyphrase summaries, System `TextClassifier` flight-number synthesis (`"Flight LH402"`), ML Kit Image Labeling visual photo titles, and optional Gemini Nano synthesis when supported.
- **Refined Expressive Pop-Out Menu & AI Button Animations (`SmartNoteTheme.kt`, `NotesOverviewScreen.kt`, `NoteEditorScreen.kt`)**:
  - Added `SmartNoteMotion.expressivePopSpring()` and `SmartNoteMotion.expressiveMorphSpring()`.
  - Added an animated dimmed backdrop scrim behind open overview top-bar bubbles and the expanded `+` pill menu, removed vertical clip truncation on the pop-out bubble (`scaleIn` + `slideInVertically` + subtle border), added directional horizontal slide + smooth `SizeTransform` height morphing when switching between `Sort` and `Menu`, staggered row entrances, tactile press scaling, and spring badge/rotation animations on top-bar trigger icons.
  - Added spring scale+fade animations and pill <-> ✨ icon morphing on the editor's `Generate` title and `Generate tags` buttons with `"Generating..."` progress label and empty-result snackbar feedback.

### Verification
- `testDebugUnitTest`: **9 suites, 33 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **9 suites, 33 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,861,782` bytes (`55.18 MiB`), `SHA-256: 2bcf8f7f2de4bd316afe8e75153454cbcaa712dfd505f35966132586e7f12ba2`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.13] - 2026-11-19

### Changed & Removed
- **Complete Removal of Reminders, Android 16 Now Bar & Wear OS Support (`SmartNoteDatabase.kt`, `SmartNoteApplication.kt`, `NoteModels.kt`, `NoteRepository.kt`, `BackupManager.kt`, `SmartNoteOperationsApi.kt`, `AndroidManifest.xml`, `NoteEditorScreen.kt`, `NotesOverviewScreen.kt`, `SettingsScreens.kt`)**:
  - Removed Wear OS notification bridging (`WearableExtender`, `RemoteInput`, Wear OS actions) and Wear OS UI references.
  - Completely removed the entire Reminders feature and Android 16 Live Updates / Now Bar integration: deleted the `reminder/` package, `ReminderPickerSheet`, `ReminderSettingsScreen`, reminder notification icons/receivers, editor reminder bell/status card, overview `Reminders` filter chip and create-pill option, and all reminder/notification/alarm/vibrate manifest declarations (`POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `VIBRATE`, and app boot receivers).
  - Upgraded Room database to **`version = 3`** with safe non-destructive `MIGRATION_2_3` (`DROP TABLE IF EXISTS reminders`) while keeping `MIGRATION_1_2`, so upgrading preserves all existing notes, checklists, tags, attachments, OCR data, and link previews (`app/schemas/.../3.json`).
  - Added startup cleanup (`cleanupRetiredReminderNotifications`) in `SmartNoteApplication` to cancel lingering notifications and remove retired reminder/Now Bar notification channels on upgrade.
  - Bumped `.snbackup` `BackupManager` `schemaVersion = 3` while continuing to accept v1–v3 backup archives cleanly during restore.
- **On-Demand `Generate tags` Button (`NoteEditorViewModel.kt`, `NoteEditorScreen.kt`)**:
  - Added an on-demand tonal **`Generate tags`** pill button in the note editor and updated `ManageTagsSheet`, enabling manual 1-tap tag suggestions whenever the master **Enable AI Features** switch is ON (even when automatic SmartTags is turned OFF).
- **Polished User-Facing Copy (`NotesOverviewScreen.kt`, `NoteEditorScreen.kt`, `SmartSearchScreen.kt`, `SmartCaptureActivity.kt`, `SmartNoteGlanceWidgets.kt`, `ArchiveAndTrashScreens.kt`, `SettingsScreens.kt`)**:
  - Refined headlines, subtitles, status text, and settings descriptions across the app to sound natural and concise, removing repetitive `"offline"` marketing copy and internal implementation jargon (`FTS5`, `LiteRT`, `ML Kit`, `SimHash`, `Trigram`).

### Verification
- `testDebugUnitTest`: **9 suites, 31 tests passed, 0 failures, 0 errors, 0 skipped** (including `migratesVersion1And2DatabasesCleanlyToVersion3DroppingRemindersTable` and `onDemandTitleAndTagGenerationWorkEndToEndInEditor`).
- `testReleaseUnitTest`: **9 suites, 31 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `56,750,725` bytes (`54.12 MiB`), `SHA-256: 127386fae4e5f109c6404c1f0d209ba716cdaf4f6845eed2fca6507c952149b4`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.12] - 2026-11-19

### Added & Changed
- **On-Demand Title Generation Button When Auto-Title Is Off (`NoteEditorViewModel.kt`, `NoteEditorScreen.kt`)**:
  - When automatic titling (`autoTitleEnabled = false` by default) is off and the title is blank while the note has body, link, or OCR content, a borderless tonal **`Generate`** pill button appears next to the title field so users can generate an offline title on demand in 1 tap (while honoring the master **`Enable AI Features`** switch and preserving opt-out of automatic background/save titling).
- **Simpler Reminder UI, Centered Time Picker Dialog & Fixed Option Text Clipping (`ReminderPickerSheet.kt`, `SettingsScreens.kt`)**:
  - Replaced the tall hero box and redundant time-of-day strip with a compact inline countdown badge (`In 2h` / `Past time`) in the sheet header, quick presets, prominent tonal Date & Time cards, and a collapsible **`More options`** progressive-disclosure card (`Repeat`, `Early alert`, `Priority alert`, `Now Bar today`) so creating simple reminders fits cleanly without scrolling.
  - Replaced the half-extending bottom-sheet Time Picker with a centered Material 3 **`Dialog`** containing quick hour chips (`09:00`, `12:00`, `18:00`, `21:00`), clock/keyboard toggle, full scrollable `TimePicker` / `TimeInput`, and `Cancel` / `OK` buttons.
  - Fixed rounded-clip edge clipping on clickable option rows (`ReminderOptionSwitchRow` and `AiProcessingSettingsScreen`) by adding generous horizontal & vertical padding inside `.clip(RoundedCornerShape(...))`.
- **Discreet "Today Only" Now Bar Live Updates & Audible/Vibrating Due Alerts (`ReminderNotificationBuilder.kt`, `ReminderScheduler.kt`, `ReminderCalculator.kt`, `ReminderBroadcastReceiver.kt`, `SystemTimeBootReceiver.kt`, `SmartNoteApplication.kt`)**:
  - Added silent, phone-local `CHANNEL_NOWBAR_TODAY` (`smartnote_nowbar_today_v1`, `IMPORTANCE_DEFAULT`, no sound/vibration) so only reminders scheduled for **today** stay discreetly in Android 16's Live Update / Samsung Now Bar (with a `00:01` `ACTION_REMINDER_TODAY_SYNC` alarm + `DATE_CHANGED` & app-launch sync for future-day reminders).
  - Migrated due and urgent alert channels to fresh IDs (`smartnote_reminders_due_v2` and `smartnote_reminders_urgent_v2`) with explicit `RingtoneManager` sound URIs, `AudioAttributes`, vibration patterns, and automatic cancellation of the silent Today Now Bar card (`50_000+`) right before posting the fresh audible/vibrating due alert (`40_000+`).
- **Zero-Watch-App Wear OS Bridging (`ReminderNotificationBuilder.kt`, `NoteRepository.kt`, `ReminderBroadcastReceiver.kt`, `SettingsScreens.kt`)**:
  - Added `NotificationCompat.WearableExtender` metadata to due and pre-alert notifications (`setLocalOnly(false)`, synchronized `setDismissalId`, `setBridgeTag("smartnote_reminder")`, inline wrist actions for `Complete` / `Snooze` / `Snooze 1h`), plus a Wear OS `RemoteInput` **`Add note`** action that appends dictated/typed quick lines directly onto the note (`NoteRepository.appendQuickLineToNote`) without needing any companion Wear OS watch app.

### Verification
- `testDebugUnitTest`: **10 suites, 34 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 34 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,206,704` bytes (`54.56 MiB`), `SHA-256: 0c1486456b42bf8c65f69985032276425189ccef216fcc3de2015200dd728f7a`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.11] - 2026-11-19

### Added & Changed
- **Significantly Upgraded Offline Auto-Tagger (`SmartTagsEngine.kt`, `SmartNoteAiEngine.kt`, `NoteProcessingWorker.kt`, `NoteEditorScreen.kt`)**:
  - **Stage 0 Inline `#Hashtag` Extraction**: Parses `#TagName` directly from note titles and prose blocks (with up to 6 tags supported per note and explicit filtering for Markdown `# Heading` headings and hex colors like `#FFFFFF`).
  - **27-Category Bilingual EN+DE Taxonomy**: Expanded category rules and self-names with morphological stemming, umlaut folding (`ä/ö/ü/ß` -> `ae/oe/ue/ss`), multi-word phrase matching (`2.6f`), multi-root German compound-word decomposition (`2.4f`, e.g., `Steuererklärung`, `Flugbuchung`), and weak-word single-hit noise guards (`Shopping`, `Work`, `Tasks`).
  - **Personal Tag Profile Learning & Stage 3 LiteRT Semantic Affinity**: `NoteProcessingWorker` builds personal token-to-tag affinity profiles from user-confirmed `USER` tags, computes batched LiteRT prototype affinities (`computeEmbeddingAndTagAffinities` with cached 27-category embeddings), and reuses the precomputed note embedding across indexing steps.
  - **Live Editor Suggestions & Reworked `ManageTagsSheet`**: Shows live `Auto-tag (N)` controls while typing, an expandable `ManageTagsSheet` with borderless tonal custom-tag input, per-suggestion reason cards (`Suggested for this note`), unused personal vocabulary pills, and automatic scroll-to-top visibility when adding tags.
- **Completely Reworked Reminder Menu & End-to-End Reminder Engine (`ReminderPickerSheet.kt`, `NaturalDateTimeParser.kt`, `ReminderCalculator.kt`, `ReminderScheduler.kt`, `ReminderNotificationBuilder.kt`, `ReminderBroadcastReceiver.kt`)**:
  - **Borderless M3 Expressive `ReminderPickerSheet`**: Rebuilt around a pinned header & footer (`Cancel`/`Remove` + past-time-guarded `Save Reminder`/`Update Reminder`), live schedule & relative-countdown hero card, quick `Mark Done` / `+15m` / `+1h` action pills for active reminders, runtime `POST_NOTIFICATIONS` & Exact Alarm permission banners, horizontal quick-preset & time-of-day strips, tonal Date & Time cards with clock-dial/keyboard `TimeInput` toggle, repeat interval stepper (`Every N days/weeks/months`), `Yearly` & `Custom days` (Mon–Sun circle picker), early alert lead-time pills (`At time`, `10m before`, `30m before`, `1h before`, `1 day before`), and a Priority / Android 16 Live Update card.
  - **Offline EN+DE Natural Date/Time Detection (`NaturalDateTimeParser`)**: Detects relative durations, explicit dates (`dd.MM.yyyy`, `yyyy-MM-dd`), named days/weekdays (`tomorrow`, `morgen`, `nächsten Montag`, `Friday 14:30`), times, and recurrence hints from note text for 1-tap `From note:` application.
  - **Early Pre-Alerts, Urgent Notification Channel, 3 Notification Actions & Swipe-Dismiss Advance**: Schedules parallel pre-alert alarms (`PRE_ALERT_REQUEST_OFFSET = 10_000`), adds `CHANNEL_REMINDERS_URGENT` for priority reminders, upgrades notifications to 3 quick actions (`Complete`, configurable default `Snooze <m>m`, `Snooze 1h`), advances recurring schedules cleanly via `ACTION_REMINDER_DISMISSED`, sorts the `Reminders` overview filter chronologically (`triggerAtMs ASC`), adds a live `Notifications` permission toggle row in `ReminderSettingsScreen`, and upgrades Room schema to `version = 2` with `MIGRATION_1_2` and Backup `schemaVersion = 2`.

### Verification
- `testDebugUnitTest`: **10 suites, 34 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 34 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,190,229` bytes (`54.54 MiB`), `SHA-256: 82ef41f64a4f9692ab84c510d65e0e5f4f4e97d146eab35c89163a8c5a5bce30`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.10] - 2026-11-19

### Changed
- **Compact Pop-Out Bubble Top-Bar Menus (`NotesOverviewScreen.kt`)**:
  - Replaced the full-width downward-expanding top-bar drawer with compact, borderless floating **pop-out bubbles** (`surfaceContainerHigh`/`surfaceContainerHighest`, `26.dp` corner radius, `10.dp` shadow elevation) anchored neatly beneath the tapped `Sort` or `Menu ⋮` button (`TransformOrigin` scale + vertical expand + fade with reduced-motion fallback), leaving the `52.dp` top search pill calm and fixed in height so filter chips and note cards stay stationary.
  - Added smooth `38.dp` `CircleShape` `primaryContainer` close badges with soft dimming on inactive bar buttons, clean `PopOutBubbleMenuItem` rows (`18.dp` corner radius, transparent background when unselected, `primaryContainer` + trailing check on active sort), a subtle divider before `Settings` in the overflow bubble, and seamless 1-tap switching between `Sort` and `Menu`.

### Verification
- `testDebugUnitTest`: **10 suites, 30 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 30 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,048,745` bytes (`54.41 MiB`), `SHA-256: 945783f98c8752eea4d011d216530b6542b13c0b07e9f39563da75714535ffee`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.9] - 2026-11-19

### Changed
- **Renamed Master Switch & Grayed Out Disabled AI Controls (`SettingsScreens.kt`)**:
  - Renamed `"Enable All AI Features"` to **`"Enable AI Features"`** (enabled by default).
  - Turning **"Enable AI Features"** off now visually turns off effective switches, grays out and disables every AI control across `SmartFeaturesSettingsScreen` (`Individual AI Features` section, `Auto-Title Generation`, `SmartTags`, `Offline OCR`, `Duplicate Detection`, `Hybrid Semantic Search (LiteRT)`, and `AI Hardware & Diagnostics`) and `AiProcessingSettingsScreen` (`Hardware Acceleration Preference`, `Energy-Efficient Processing`, `Hybrid Semantic Re-Ranking (LiteRT)`, and `Verify Backend`), while leaving saved individual preferences intact so toggling AI back on restores prior settings cleanly.
- **Morphing Top-Bar `Sort` & `Menu` Pill Drawers (`NotesOverviewScreen.kt`)**:
  - Replaced popup `DropdownMenu`s in `NotesOverviewScreen`'s top search pill with integrated downward-rolling `CircleShape` tonal pill drawers (`MorphingMenuPillItem`, height `46.dp`, active checkmarks in `primaryContainer`) directly inside the morphing search bar surface so filter chips and cards glide smoothly below rather than being covered up, while horizontally squashing the leading search, layout, and sibling menu icons smoothly aside (`expandHorizontally`/`shrinkHorizontally` with reduced-motion fade fallback) and switching the active button to a highlighted `×` toggle.
- **Flying Bottom Navigation Bar Pill Highlighter (`SmartNoteApp.kt`)**:
  - Replaced the static `NavigationBar` indicator with `SmartNoteBottomBar`: a single continuous `secondaryContainer` pill driven by `animateFloatAsState` (`spring(dampingRatio = 0.88f, stiffness = 420f)`, with `tween(80)` reduced-motion fallback) and a subtle mid-flight horizontal stretch (`sin` curve) that flies smoothly across **Notes**, **SmartSearch**, and **Tags**.

### Verification
- `testDebugUnitTest`: **10 suites, 30 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 30 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,048,739` bytes (`54.41 MiB`), `SHA-256: 9a9da1733a3c9dc430190105ce87f9868162f72a4577fd8bbe8e815f2e958eef`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

## [1.0.8] - 2026-11-19

### Changed
- **Cleaner Morphing `+` Pill Menu Toggle (`NotesOverviewScreen.kt`)**:
  - Removed the redundant `"Close"` text label inside the bottom bar of `ExpressiveMorphingCreateFab` and enlarged the `+` / `×` icon (`26.dp` at rest → `30.dp` when expanded), centered in a `56.dp` trailing box.
- **NPU-First `Energy-Efficient Processing` (`SmartNoteAiEngine.kt`, `SettingsScreens.kt`)**:
  - Updated `SmartNoteAiEngine.buildAttemptOrder` and `scheduleIdleUnload` so `energyThrottled` mode (Battery Saver / thermal load) prioritizes low-power **NPU** execution first (when supported and ready, unless `CPU_ONLY` is selected), skips high-wattage **GPU** wakeup, falls back to CPU only when NPU is unavailable, and shortens idle model retention to `15_000 ms`.
  - Grouped `Hardware Acceleration Preference` radio items inside a clean tonal card in `AiProcessingSettingsScreen`, updated `Energy-Efficient Processing` descriptions, and added an `"Energy-Saving Mode"` row to `Honest Hardware Diagnostics`.
- **Hybrid Semantic Search Enabled by Default (`NoteModels.kt`, `SettingsRepository.kt`, `SmartNoteAiEngine.kt`)**:
  - Enabled `semanticRerankEnabled = true` by default (using key `semantic_rerank_enabled_v2` + boot-cache persistence) while keeping the master switch override and avoiding unnecessary model wakeups when candidate notes lack stored embeddings.

### Fixed
- **Redesigned `System Integrations` Screen & Global `DiagRow` Layout (`SettingsScreens.kt`)**:
  - Replaced `DiagRow` unweighted `SpaceBetween` rows—which caused labels and multi-line values to touch (`"App ShortcutsNew Note..."`)—with weighted columns (`0.44f` label / `0.56f` end-aligned value, `12.dp` spacing) and navigation-bar bottom clearance across settings screens.
  - Redesigned `IntegrationsSettingsScreen` into 4 dedicated `SystemIntegrationCard` cards (**Quick Settings Tile**, **Home Screen Widgets**, **Launcher App Shortcuts**, **Android Sharesheet**) with tonal status badges and native **"Add to Quick Settings"** (Android 13+) and **"Pin ..."** widget actions where supported.

### Verification
- `testDebugUnitTest`: **10 suites, 29 tests passed, 0 failures, 0 errors, 0 skipped**.
- `testReleaseUnitTest`: **10 suites, 29 tests passed, 0 failures, 0 errors, 0 skipped**.
- `smartnote-release.apk`: `57,048,742` bytes (`54.41 MiB`), `SHA-256: 3e2976c90cec4be0e2c343f17fb5e0c9c9a4dd521d5351f63b7a17c1934a9cbc`, R8-minified + resource-shrunk, 16 KB `zipalign` verified, APK Signature Scheme v3 verified with 4096-bit RSA key (`b5081b918508a0f39f7928a0355ff45e7a29299dc8281cbc6d2e6a2714c467c6`).

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
