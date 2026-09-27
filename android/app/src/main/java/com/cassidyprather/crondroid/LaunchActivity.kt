package com.cassidyprather.crondroid

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cassidyprather.crondroid.ui.CrondroidTheme

/**
 * Shown when a schedule fires. It can appear over the lock screen, asks you to unlock if needed,
 * then opens the schedule's link or app and gets out of the way.
 */
class LaunchActivity : ComponentActivity() {
    private var schedule by mutableStateOf<Schedule?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CrondroidTheme {
                Surface(Modifier.fillMaxSize()) {
                    schedule?.let { LaunchScreen(it, onOpen = ::unlockAndOpen, onDismiss = ::dismiss) }
                }
            }
        }
        load(intent, autoOpen = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent, autoOpen = true)
    }

    private fun load(intent: Intent, autoOpen: Boolean) {
        val loaded = ScheduleStore(this).get(intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1))
        if (loaded == null) {
            finish()
            return
        }
        schedule = loaded
        if (autoOpen) unlockAndOpen()
    }

    private fun unlockAndOpen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            // If you cancel the unlock prompt, this screen stays up so you can try again.
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = open()
                },
            )
        } else {
            open()
        }
    }

    private fun open() {
        val current = schedule ?: return
        AlarmReceiver.cancelNotification(this, current.id)
        Launcher.open(this, current)
        finish()
    }

    private fun dismiss() {
        schedule?.let { AlarmReceiver.cancelNotification(this, it.id) }
        finish()
    }

    companion object {
        fun intent(context: Context, id: Int): Intent = Intent(context, LaunchActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun LaunchScreen(schedule: Schedule, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            schedule.name.ifBlank { "Scheduled launch" },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            schedule.url.ifBlank { schedule.packageName.orEmpty() },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            Button(onClick = onOpen) { Text("Open") }
        }
    }
}
