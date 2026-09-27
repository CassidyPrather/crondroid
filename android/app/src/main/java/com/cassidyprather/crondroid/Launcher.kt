package com.cassidyprather.crondroid

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

/** Builds and starts the intent a schedule describes. */
object Launcher {
    /** The intent to start for [schedule], or null if nothing can handle it. */
    fun targetIntent(context: Context, schedule: Schedule): Intent? {
        val pm = context.packageManager
        val pkg = schedule.packageName
        if (schedule.url.isBlank()) {
            return pkg?.let { pm.getLaunchIntentForPackage(it) }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(schedule.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) {
            val targeted = Intent(view).setPackage(pkg)
            if (targeted.resolveActivity(pm) != null) return targeted
            // The chosen app can't open this link (or was uninstalled): fall back to the default.
        }
        return view.takeIf { it.resolveActivity(pm) != null }
    }

    /** Opens the schedule's target now. Must be called from a foreground context. */
    fun open(context: Context, schedule: Schedule): Boolean {
        val intent = targetIntent(context, schedule)
        if (intent != null) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(context, "Couldn't open ${schedule.url.ifBlank { schedule.packageName }}", Toast.LENGTH_LONG).show()
        return false
    }

    /** An installed app that can be picked as a schedule's target. */
    data class AppChoice(val packageName: String, val label: String)

    /** Apps that can open [url], or every launchable app when [url] is blank. */
    fun appChoices(context: Context, url: String): List<AppChoice> {
        val pm = context.packageManager
        val query = if (url.isBlank()) {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        return pm.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .map { AppChoice(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
