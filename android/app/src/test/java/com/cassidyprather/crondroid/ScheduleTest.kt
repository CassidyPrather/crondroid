package com.cassidyprather.crondroid

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("America/Chicago")

    private fun schedule(hour: Int, minute: Int, vararg days: DayOfWeek) = Schedule(
        id = 1,
        name = "Stream",
        hour = hour,
        minute = minute,
        days = days.toSet(),
        url = "https://www.twitch.tv/example",
        packageName = "tv.twitch.android.app",
        enabled = true,
    )

    // 2026-09-28 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, 9, day, hour, minute, second, 0, zone)

    @Test
    fun oneTimeFiresLaterToday() {
        assertEquals(at(28, 9, 0), schedule(9, 0).nextTrigger(at(28, 8, 30)))
    }

    @Test
    fun oneTimeRollsToTomorrowOncePassed() {
        assertEquals(at(29, 9, 0), schedule(9, 0).nextTrigger(at(28, 9, 0)))
        assertEquals(at(29, 9, 0), schedule(9, 0).nextTrigger(at(28, 9, 0, 1)))
    }

    @Test
    fun repeatingSkipsUnselectedDays() {
        val weekdays = schedule(9, 0, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(at(30, 9, 0), weekdays.nextTrigger(at(28, 10, 0)))
    }

    @Test
    fun repeatingWrapsToNextWeek() {
        val mondays = schedule(9, 0, DayOfWeek.MONDAY)
        assertEquals(ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone), mondays.nextTrigger(at(28, 9, 0)))
    }

    @Test
    fun timeInDstGapStillFires() {
        // Clocks jump from 2:00 to 3:00 on 2027-03-14 in Chicago.
        val s = schedule(2, 30)
        val next = s.nextTrigger(ZonedDateTime.of(2027, 3, 14, 0, 0, 0, 0, zone))
        assertEquals(ZonedDateTime.of(2027, 3, 14, 3, 30, 0, 0, zone), next)
    }

    @Test
    fun jsonRoundTrip() {
        val original = schedule(21, 45, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        assertEquals(original, Schedule.fromJson(original.toJson()))
        val noApp = original.copy(packageName = null, days = emptySet())
        assertEquals(noApp, Schedule.fromJson(noApp.toJson()))
        val audio = original.copy(audioOnly = true)
        assertEquals(audio, Schedule.fromJson(audio.toJson()))
    }

    @Test
    fun normalizeUrl() {
        assertEquals("https://twitch.tv/example", Schedule.normalizeUrl(" twitch.tv/example "))
        assertEquals("https://www.twitch.tv/example", Schedule.normalizeUrl("https://www.twitch.tv/example"))
        assertEquals("twitch://stream/example", Schedule.normalizeUrl("twitch://stream/example"))
        assertEquals("https://localhost:8080/x", Schedule.normalizeUrl("localhost:8080/x"))
        assertEquals("", Schedule.normalizeUrl("  "))
    }
}
