package com.cassidyprather.crondroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cassidyprather.crondroid.Launcher
import com.cassidyprather.crondroid.Schedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleListScreen(
    schedules: List<Schedule>,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onToggle: (Schedule, Boolean) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Crondroid") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New schedule") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionsCard(refreshKey, onRefresh) }
            if (schedules.isEmpty()) {
                item {
                    Text(
                        "No schedules yet. Tap \"New schedule\" to open a link or app at a set time — " +
                            "for example a Twitch channel at 9:00 every weekday.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(schedules, key = { it.id }) { schedule ->
                ScheduleCard(schedule, refreshKey, onClick = { onEdit(schedule) }, onToggle = { onToggle(schedule, it) })
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER") // refreshKey keeps the "opens in" countdown current on resume.
private fun ScheduleCard(schedule: Schedule, refreshKey: Int, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val target = if (schedule.audioOnly) {
        "Audio only · ${schedule.url}"
    } else {
        listOfNotNull(
            schedule.url.ifBlank { null },
            schedule.packageName?.let { "in " + Launcher.appLabel(context, it) },
        ).joinToString(" ")
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatTime(schedule.hour, schedule.minute), style = MaterialTheme.typography.displaySmall)
                Text(
                    listOf(schedule.name.ifBlank { null }, describeDays(schedule.days)).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    target,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (schedule.enabled) {
                    Text(
                        describeNext(schedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        }
    }
}
