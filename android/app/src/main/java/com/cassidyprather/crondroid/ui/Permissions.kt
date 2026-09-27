package com.cassidyprather.crondroid.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private data class Requirement(
    val title: String,
    val why: String,
    val granted: Boolean,
    val grant: () -> Unit,
)

/**
 * Lists the permissions a schedule needs to open things reliably, with a button for each missing one.
 * [refreshKey] should change whenever the app returns to the foreground so the list re-reads settings.
 */
@Composable
@Suppress("UNUSED_PARAMETER") // A changed refreshKey is what makes this recompose and re-check.
fun PermissionsCard(refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    val missing = requirements(context) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        .filterNot { it.granted }
    if (missing.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Finish setting up", style = MaterialTheme.typography.titleMedium)
            missing.forEach { requirement ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(requirement.title, style = MaterialTheme.typography.bodyLarge)
                        Text(requirement.why, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = requirement.grant) { Text("Allow") }
                }
            }
        }
    }
}

private fun requirements(context: Context, requestNotifications: () -> Unit): List<Requirement> {
    val packageUri = Uri.parse("package:${context.packageName}")
    fun openSettings(action: String) = {
        context.startActivity(Intent(action, packageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val list = mutableListOf<Requirement>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list += Requirement(
            title = "Notifications",
            why = "Shows a prompt when a schedule fires.",
            granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            grant = requestNotifications,
        )
    }
    list += Requirement(
        title = "Display over other apps (recommended)",
        why = "Lets schedules open the app straight away, even while you're using your phone.",
        granted = Settings.canDrawOverlays(context),
        grant = openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        list += Requirement(
            title = "Full-screen alerts",
            why = "Lets schedules wake the screen when your phone is locked.",
            granted = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
            grant = openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT),
        )
    }
    list += Requirement(
        title = "Alarms & reminders",
        why = "Lets schedules fire at the exact minute.",
        granted = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        grant = openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
    )
    return list
}
