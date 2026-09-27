package com.cassidyprather.crondroid.ui

import com.cassidyprather.crondroid.Schedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun formatTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun describeDays(days: Set<DayOfWeek>): String = when (days) {
    emptySet<DayOfWeek>() -> "Once"
    DayOfWeek.entries.toSet() -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKEND -> "Weekends"
    else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

fun describeNext(schedule: Schedule, now: ZonedDateTime = ZonedDateTime.now()): String {
    val next = schedule.nextTrigger(now)
    return "Opens in ${describeDuration(Duration.between(now, next))}"
}

fun describeDuration(duration: Duration): String {
    val totalMinutes = (duration.toMinutes() + if (duration.seconds % 60 > 0) 1 else 0).coerceAtLeast(1)
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes / 60 % 24
    val minutes = totalMinutes % 60
    return listOfNotNull(
        days.takeIf { it > 0 }?.let { "$it d" },
        hours.takeIf { it > 0 }?.let { "$it h" },
        minutes.takeIf { it > 0 }?.let { "$it min" },
    ).joinToString(" ")
}
