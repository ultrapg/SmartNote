package io.github.ultrapg.smartnote.ui.editor

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ultrapg.smartnote.domain.model.ReminderInfo
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import io.github.ultrapg.smartnote.reminder.DetectedReminderSuggestion
import io.github.ultrapg.smartnote.reminder.ReminderCalculator
import io.github.ultrapg.smartnote.ui.designsystem.components.smartNoteTonalFilterChipColors
import io.github.ultrapg.smartnote.ui.designsystem.theme.LocalSmartNoteThemeMeta
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

private data class QuickReminderPreset(
    val id: String,
    val label: String,
    val computeTarget: (LocalDateTime) -> LocalDateTime
)

private val TIME_24_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderPickerSheet(
    existingReminder: ReminderInfo?,
    detectedSuggestion: DetectedReminderSuggestion? = null,
    onSaveReminder: (
        triggerAtMs: Long,
        repeatRule: RepeatRule,
        useLiveUpdate: Boolean,
        repeatDaysMask: Int,
        repeatInterval: Int,
        preAlertMinutes: Int,
        isImportant: Boolean
    ) -> Unit,
    onCompleteReminder: (() -> Unit)? = null,
    onSnoozeReminder: ((Int) -> Unit)? = null,
    onDeleteReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = LocalSmartNoteThemeMeta.current.isReducedMotion
    val zone = ZoneId.systemDefault()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialDateTime = remember(existingReminder) {
        existingReminder?.let {
            Instant.ofEpochMilli(it.triggerAtMs).atZone(zone).toLocalDateTime()
        } ?: run {
            val now = LocalDateTime.now()
            val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
            if (nextHour.isBefore(now.plusMinutes(15))) nextHour.plusHours(1) else nextHour
        }
    }

    var selectedDate by remember(existingReminder) { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember(existingReminder) { mutableStateOf(initialDateTime.toLocalTime()) }
    var selectedRepeat by remember(existingReminder) {
        mutableStateOf(existingReminder?.repeatRule ?: RepeatRule.NONE)
    }
    var repeatDaysMask by remember(existingReminder) {
        mutableIntStateOf(existingReminder?.repeatDaysMask ?: 0)
    }
    var repeatInterval by remember(existingReminder) {
        mutableIntStateOf((existingReminder?.repeatInterval ?: 1).coerceIn(1, 30))
    }
    var preAlertMinutes by remember(existingReminder) {
        mutableIntStateOf((existingReminder?.preAlertMinutes ?: 0).coerceAtLeast(0))
    }
    var isImportant by remember(existingReminder) {
        mutableStateOf(existingReminder?.isImportant ?: false)
    }
    var useLiveUpdate by remember(existingReminder) {
        mutableStateOf(existingReminder?.useLiveUpdate ?: true)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var useKeyboardTimeInput by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    var canExactAlarm by remember {
        mutableStateOf(checkExactAlarmPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || checkNotificationPermission(context)
        if (!granted) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = checkNotificationPermission(context)
                canExactAlarm = checkExactAlarmPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val triggerMs = remember(selectedDate, selectedTime) {
        LocalDateTime.of(selectedDate, selectedTime)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
    val isPastTime = triggerMs <= nowMs + 15_000L

    val presets = remember {
        listOf(
            QuickReminderPreset("p30m", "+30 min") { now ->
                now.plusMinutes(30).withSecond(0).withNano(0)
            },
            QuickReminderPreset("p2h", "+2 hours") { now ->
                now.plusHours(2).withMinute(0).withSecond(0).withNano(0)
            },
            QuickReminderPreset("evening", "Tonight 20:00") { now ->
                val target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(20, 0))
                if (target.isAfter(now.plusMinutes(10))) target else target.plusDays(1)
            },
            QuickReminderPreset("tomorrow", "Tomorrow 09:00") { now ->
                LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(9, 0))
            },
            QuickReminderPreset("weekend", "Weekend") { now ->
                LocalDateTime.of(
                    now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SATURDAY)),
                    LocalTime.of(10, 0)
                )
            },
            QuickReminderPreset("next_week", "Next week") { now ->
                LocalDateTime.of(now.toLocalDate().plusWeeks(1), LocalTime.of(9, 0))
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Pinned header (stays fixed so Robolectric & short viewports never lose it)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Note Reminder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = if (existingReminder != null && !existingReminder.isCompleted) "Active" else "New",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Scrollable configuration body
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Schedule Preview Card
                val heroBgColor by animateColorAsState(
                    targetValue = when {
                        isPastTime -> MaterialTheme.colorScheme.errorContainer
                        isImportant -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    animationSpec = tween(if (reducedMotion) 60 else 180),
                    label = "reminder_hero_bg"
                )
                val heroContentColor = when {
                    isPastTime -> MaterialTheme.colorScheme.onErrorContainer
                    isImportant -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = heroBgColor,
                    contentColor = heroContentColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${ReminderCalculator.formatHumanDate(selectedDate)} · ${selectedTime.format(TIME_24_FORMATTER)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (isImportant) {
                                Surface(
                                    shape = CircleShape,
                                    color = heroContentColor.copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PriorityHigh,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text("Urgent", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Text(
                            text = if (isPastTime) {
                                "Selected time has passed — pick a future time"
                            } else {
                                ReminderCalculator.formatRelativeCountdown(triggerMs, nowMs)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        val metaSummary = buildList {
                            add(ReminderCalculator.formatRepeatSummary(selectedRepeat, repeatDaysMask, repeatInterval))
                            if (preAlertMinutes > 0) {
                                add("Alert ${ReminderCalculator.formatPreAlertLabel(preAlertMinutes)}")
                            }
                            if (Build.VERSION.SDK_INT >= 36 && useLiveUpdate) {
                                add("Live Update")
                            }
                        }.joinToString(" · ")
                        Text(
                            text = metaSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = heroContentColor.copy(alpha = 0.8f)
                        )
                    }
                }

                // Active reminder quick actions (Complete / Snooze directly from sheet)
                if (existingReminder != null && !existingReminder.isCompleted &&
                    (onCompleteReminder != null || onSnoozeReminder != null)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onCompleteReminder != null) {
                            TonalQuickActionPill(
                                icon = Icons.Rounded.Check,
                                label = "Mark Done",
                                highlighted = true,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCompleteReminder()
                                }
                            )
                        }
                        if (onSnoozeReminder != null) {
                            TonalQuickActionPill(
                                icon = Icons.Rounded.Snooze,
                                label = "+15m",
                                highlighted = false,
                                modifier = Modifier.weight(0.85f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSnoozeReminder(15)
                                }
                            )
                            TonalQuickActionPill(
                                icon = Icons.Rounded.Schedule,
                                label = "+1h",
                                highlighted = false,
                                modifier = Modifier.weight(0.85f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSnoozeReminder(60)
                                }
                            )
                        }
                    }
                }

                // Runtime Permission helper card (shown only when needed)
                if (!hasNotificationPermission || !canExactAlarm) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Allow notifications so due reminders can alert you on time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    ) { Text("Allow") }
                                }
                            }
                            if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Alarm,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Exact alarms are currently using an approximate window.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                )
                                            }
                                        }
                                    ) { Text("Enable") }
                                }
                            }
                        }
                    }
                }

                // Smart date/time detected from note text
                if (detectedSuggestion != null) {
                    val detectedEpochMs = remember(detectedSuggestion) {
                        detectedSuggestion.dateTime.atZone(zone).toInstant().toEpochMilli()
                    }
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedDate = detectedSuggestion.dateTime.toLocalDate()
                            selectedTime = detectedSuggestion.dateTime.toLocalTime()
                            if (detectedSuggestion.suggestedRepeat != RepeatRule.NONE) {
                                selectedRepeat = detectedSuggestion.suggestedRepeat
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "From note: ${ReminderCalculator.formatHumanDateTime(detectedEpochMs, nowMs, zone)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Matched \"${detectedSuggestion.matchedPhrase}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Apply",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Quick Presets Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val previewTarget = remember(nowMs, preset.id) {
                            preset.computeTarget(LocalDateTime.now())
                        }
                        val previewMs = previewTarget.atZone(zone).toInstant().toEpochMilli()
                        val isSelectedPreset = abs(triggerMs - previewMs) < 90_000L
                        FilterChip(
                            selected = isSelectedPreset,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val resolved = preset.computeTarget(LocalDateTime.now())
                                selectedDate = resolved.toLocalDate()
                                selectedTime = resolved.toLocalTime()
                                nowMs = System.currentTimeMillis()
                            },
                            label = { Text(preset.label) },
                            border = null,
                            colors = smartNoteTonalFilterChipColors()
                        )
                    }
                }

                // Borderless Tonal Date & Time Picker Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TonalDateTimeCard(
                        icon = Icons.Rounded.CalendarMonth,
                        caption = "Date",
                        value = ReminderCalculator.formatHumanDate(selectedDate),
                        modifier = Modifier.weight(1f),
                        onClick = { showDatePicker = true }
                    )
                    TonalDateTimeCard(
                        icon = Icons.Rounded.Schedule,
                        caption = "Time",
                        value = selectedTime.format(TIME_24_FORMATTER),
                        modifier = Modifier.weight(1f),
                        onClick = { showTimePicker = true }
                    )
                }

                // Time-of-day quick strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        9 to "Morning",
                        12 to "Noon",
                        18 to "Evening",
                        21 to "Night"
                    ).forEach { (hour, label) ->
                        val isTimeMatch = selectedTime.hour == hour && selectedTime.minute == 0
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTime = LocalTime.of(hour, 0)
                                nowMs = System.currentTimeMillis()
                            },
                            shape = CircleShape,
                            color = if (isTimeMatch) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isTimeMatch) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isTimeMatch) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "%02d:00".format(hour),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Repeat schedule section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Repeat",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RepeatRule.entries.forEach { rule ->
                            val label = when (rule) {
                                RepeatRule.NONE -> "Once"
                                RepeatRule.DAILY -> "Daily"
                                RepeatRule.WEEKDAYS -> "Weekdays"
                                RepeatRule.WEEKLY -> "Weekly"
                                RepeatRule.MONTHLY -> "Monthly"
                                RepeatRule.YEARLY -> "Yearly"
                                RepeatRule.CUSTOM_DAYS -> "Custom days"
                            }
                            FilterChip(
                                selected = selectedRepeat == rule,
                                onClick = {
                                    selectedRepeat = rule
                                    if (rule == RepeatRule.CUSTOM_DAYS && (repeatDaysMask and 0x7F) == 0) {
                                        repeatDaysMask = ReminderCalculator.dayMaskBit(selectedDate.dayOfWeek)
                                    }
                                },
                                label = { Text(label) },
                                border = null,
                                colors = smartNoteTonalFilterChipColors()
                            )
                        }
                    }

                    // Custom Mon..Sun day selector pills
                    AnimatedVisibility(visible = selectedRepeat == RepeatRule.CUSTOM_DAYS) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DayOfWeek.entries.forEach { day ->
                                val active = ReminderCalculator.isDaySelected(repeatDaysMask, day)
                                val shortLetter = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
                                val fullDayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val toggled = ReminderCalculator.toggleDay(repeatDaysMask, day)
                                        if ((toggled and 0x7F) != 0) {
                                            repeatDaysMask = toggled
                                        }
                                    },
                                    shape = CircleShape,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    contentColor = if (active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .semantics { contentDescription = fullDayName }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = shortLetter,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Repeat interval stepper for Daily / Weekly / Monthly
                    AnimatedVisibility(
                        visible = selectedRepeat == RepeatRule.DAILY ||
                            selectedRepeat == RepeatRule.WEEKLY ||
                            selectedRepeat == RepeatRule.MONTHLY
                    ) {
                        val unitLabel = when (selectedRepeat) {
                            RepeatRule.DAILY -> if (repeatInterval == 1) "day" else "days"
                            RepeatRule.WEEKLY -> if (repeatInterval == 1) "week" else "weeks"
                            RepeatRule.MONTHLY -> if (repeatInterval == 1) "month" else "months"
                            else -> ""
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Interval",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (repeatInterval > 1) repeatInterval-- },
                                        enabled = repeatInterval > 1,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease interval")
                                    }
                                    Text(
                                        text = "Every $repeatInterval $unitLabel",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = { if (repeatInterval < 30) repeatInterval++ },
                                        enabled = repeatInterval < 30,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Increase interval")
                                    }
                                }
                            }
                        }
                    }
                }

                // Early Alert lead-time pills
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Early alert",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 10, 30, 60, 1440).forEach { mins ->
                            FilterChip(
                                selected = preAlertMinutes == mins,
                                onClick = { preAlertMinutes = mins },
                                label = { Text(ReminderCalculator.formatPreAlertLabel(mins)) },
                                border = null,
                                colors = smartNoteTonalFilterChipColors()
                            )
                        }
                    }
                }

                // Priority & Android 16 Live Update option card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { isImportant = !isImportant }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "Priority alert",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Uses high-priority alert channel and emphasizes urgent reminders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isImportant,
                                onCheckedChange = { isImportant = it }
                            )
                        }

                        if (Build.VERSION.SDK_INT >= 36) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { useLiveUpdate = !useLiveUpdate }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                ) {
                                    Text(
                                        text = "Live Update when due",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Requests Android 16 Live Update / Now Bar visibility only when active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useLiveUpdate,
                                    onCheckedChange = { useLiveUpdate = it }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            // Pinned bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existingReminder != null) {
                    TextButton(onClick = onDeleteReminder) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
                Button(
                    enabled = !isPastTime,
                    modifier = Modifier.testTag("save_reminder_button"),
                    onClick = {
                        val effectiveMask = if (selectedRepeat == RepeatRule.CUSTOM_DAYS) {
                            (repeatDaysMask and 0x7F).let {
                                if (it == 0) ReminderCalculator.dayMaskBit(selectedDate.dayOfWeek) else it
                            }
                        } else {
                            0
                        }
                        onSaveReminder(
                            triggerMs,
                            selectedRepeat,
                            useLiveUpdate,
                            effectiveMask,
                            repeatInterval,
                            preAlertMinutes,
                            isImportant
                        )
                    }
                ) {
                    Text(if (existingReminder != null) "Update Reminder" else "Save Reminder")
                }
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute
        )
        ModalBottomSheet(onDismissRequest = { showTimePicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (useKeyboardTimeInput) "Enter time" else "Select time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { useKeyboardTimeInput = !useKeyboardTimeInput }) {
                        Icon(
                            imageVector = if (useKeyboardTimeInput) {
                                Icons.Rounded.Schedule
                            } else {
                                Icons.Rounded.Keyboard
                            },
                            contentDescription = if (useKeyboardTimeInput) {
                                "Switch to clock dial"
                            } else {
                                "Switch to keyboard input"
                            }
                        )
                    }
                }
                if (useKeyboardTimeInput) {
                    TimeInput(state = timeState)
                } else {
                    TimePicker(state = timeState)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    Button(
                        onClick = {
                            selectedTime = LocalTime.of(timeState.hour, timeState.minute)
                            nowMs = System.currentTimeMillis()
                            showTimePicker = false
                        }
                    ) { Text("Confirm") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TonalDateTimeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TonalQuickActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun checkExactAlarmPermission(context: Context): Boolean {
    val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return AlarmManagerCompat.canScheduleExactAlarms(manager)
}
