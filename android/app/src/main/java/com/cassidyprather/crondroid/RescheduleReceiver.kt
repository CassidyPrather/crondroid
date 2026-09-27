package com.cassidyprather.crondroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms every schedule after a reboot, app update, clock change, or exact-alarm permission change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> AlarmScheduler.rescheduleAll(context)
        }
    }
}
