package com.cassidyprather.crondroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Runs when a schedule's alarm goes off: brings up [LaunchActivity] and arms the next occurrence. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val store = ScheduleStore(context)
        val schedule = store.get(id) ?: return
        if (!schedule.enabled) return

        fire(context, schedule)

        if (schedule.repeats) {
            // Never re-arm for the same minute, even if the alarm was delivered a little early.
            val firedAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, 0L)
                .coerceAtLeast(System.currentTimeMillis())
            AlarmScheduler.update(context, schedule, ZonedDateTime.ofInstant(Instant.ofEpochMilli(firedAt), ZoneId.systemDefault()))
        } else {
            store.save(schedule.copy(enabled = false))
        }
    }

    companion object {
        private const val CHANNEL_ID = "launches"

        fun fire(context: Context, schedule: Schedule) {
            val launch = LaunchActivity.intent(context, schedule.id)
            if (Settings.canDrawOverlays(context)) {
                // "Display over other apps" exempts us from background activity-start limits.
                context.startActivity(launch)
                return
            }
            // Otherwise a full-screen notification opens LaunchActivity when the phone is locked or idle,
            // and shows as a heads-up notification to tap while the phone is in use.
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Scheduled launches", NotificationManager.IMPORTANCE_HIGH),
            )
            val pending = PendingIntent.getActivity(
                context,
                schedule.id,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(schedule.name.ifBlank { "Scheduled launch" })
                .setContentText("Tap to open ${schedule.url.ifBlank { schedule.packageName.orEmpty() }}")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)
                .setAutoCancel(true)
                .build()
            nm.notify(schedule.id, notification)
        }

        fun cancelNotification(context: Context, id: Int) {
            context.getSystemService(NotificationManager::class.java).cancel(id)
        }
    }
}
