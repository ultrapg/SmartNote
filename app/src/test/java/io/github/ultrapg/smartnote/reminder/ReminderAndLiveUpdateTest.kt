package io.github.ultrapg.smartnote.reminder

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ReminderAndLiveUpdateTest {

    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var dsFile: File
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var database: SmartNoteDatabase
    private lateinit var notifBuilder: ReminderNotificationBuilder
    private lateinit var scheduler: ReminderScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dsFile = File(context.cacheDir, "rem_test_${System.nanoTime()}.preferences_pb")
        settingsRepo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { dsFile })
        )
        database = SmartNoteDatabase.createInMemory(context)
        notifBuilder = ReminderNotificationBuilder(context, settingsRepo)
        scheduler = ReminderScheduler(context, database, notifBuilder)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        dsFile.delete()
    }

    @Test
    fun calculatesWeekdayRecurrenceAndDstZoneShiftsAccurately() {
        val zone = "America/New_York"
        // Friday 2026-11-20 09:00 -> next weekday MUST skip Saturday & Sunday -> Monday 2026-11-23
        val fridayMs = LocalDateTime.of(2026, 11, 20, 9, 0)
            .atZone(ZoneId.of(zone))
            .toInstant()
            .toEpochMilli()

        val next = ReminderCalculator.computeNextOccurrence(
            currentTriggerMs = fridayMs,
            zoneId = zone,
            repeatRule = RepeatRule.WEEKDAYS,
            nowMs = fridayMs
        )!!
        val nextLocal = Instant.ofEpochMilli(next.first).atZone(ZoneId.of(zone)).toLocalDateTime()
        assertEquals(DayOfWeek.MONDAY, nextLocal.dayOfWeek)
        assertEquals(23, nextLocal.dayOfMonth)
        assertEquals(9, nextLocal.hour)

        assertNull(
            ReminderCalculator.computeNextOccurrence(fridayMs, zone, RepeatRule.NONE, fridayMs)
        )

        val snoozed = ReminderCalculator.computeSnoozeTriggerMs(1_000_000L, 15)
        assertEquals(1_000_000L + 15 * 60_000L, snoozed)
    }

    @Test
    fun buildsValidReminderNotificationWithOpenCompleteSnoozeActionsWithoutCrashing() = runTest {
        val triggerMs = System.currentTimeMillis() + 600_000L
        val notification = notifBuilder.buildDueReminderNotification(
            noteId = "note-rem-1",
            noteTitle = "Submit expense report",
            notePreview = "Attach PDF receipt",
            triggerAtMs = triggerMs,
            useLiveUpdate = true,
            isSnoozeCountdown = true
        )

        assertNotNull(notification)
        assertEquals(NotificationCompat.CATEGORY_REMINDER, notification.category)
        assertEquals(2, notification.actions?.size ?: 0)
        assertEquals("Complete", notification.actions[0].title.toString())
        assertTrue(notification.actions[1].title.toString().startsWith("Snooze"))
        // On SDK 34 test runner, Live Update ongoing flag should NOT be set
        assertFalse((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    }
}
