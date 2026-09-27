package com.cassidyprather.crondroid

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/** Registers schedules with the system alarm clock so they fire on time, even in Doze. */
object AlarmScheduler {
    const val EXTRA_ID = "schedule_id"
    const val EXTRA_TRIGGER_AT = "trigger_at"

    /** Arms (or disarms) [schedule]; returns the time it will fire, or null if it is off. */
    fun update(context: Context, schedule: Schedule, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        cancel(context, schedule.id)
        if (!schedule.enabled) return null

        val next = schedule.nextTrigger(after)
        val triggerAt = next.toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context, schedule.id, triggerAt)
        if (alarmManager.canScheduleExactAlarms()) {
            val show = PendingIntent.getActivity(
                context,
                schedule.id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
        return next
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id, 0))
    }

    fun rescheduleAll(context: Context) {
        ScheduleStore(context).all().forEach { update(context, it) }
    }

    private fun alarmIntent(context: Context, id: Int, triggerAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TRIGGER_AT, triggerAt),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
