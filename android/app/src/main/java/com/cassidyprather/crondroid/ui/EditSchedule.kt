package com.cassidyprather.crondroid.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cassidyprather.crondroid.Launcher
import com.cassidyprather.crondroid.Schedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScheduleScreen(
    initial: Schedule,
    isNew: Boolean,
    onSave: (Schedule) -> Unit,
    onDelete: (Schedule) -> Unit,
    onTest: (Schedule) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var url by rememberSaveable { mutableStateOf(initial.url) }
    var packageName by rememberSaveable { mutableStateOf(initial.packageName) }
    var days by rememberSaveable { mutableStateOf(initial.days.map { it.value }.toSet()) }
    val time = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))

    val normalizedUrl = Schedule.normalizeUrl(url)
    val edited = initial.copy(
        name = name.trim(),
        hour = time.hour,
        minute = time.minute,
        days = days.map { DayOfWeek.of(it) }.toSet(),
        url = normalizedUrl,
        packageName = packageName,
        enabled = if (isNew) true else initial.enabled,
    )
    val valid = normalizedUrl.isNotBlank() || packageName != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New schedule" else "Edit schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(edited.copy(enabled = true)) }, enabled = valid) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }

            DayPicker(days) { days = it }
            Text(
                if (days.isEmpty()) "No days picked: runs once, then turns itself off." else describeDays(edited.days),
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link to open") },
                placeholder = { Text("twitch.tv/channelname") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            AppPicker(url = normalizedUrl, selected = packageName, onSelect = { packageName = it })

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onTest(edited) }, enabled = valid) { Text("Test now") }
                if (!isNew) {
                    OutlinedButton(
                        onClick = { onDelete(initial) },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Monday-to-Sunday toggles. [selected] holds [DayOfWeek.getValue] numbers. */
@Composable
private fun DayPicker(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = day.value in selected
            Surface(
                onClick = { onChange(if (on) selected - day.value else selected + day.value) },
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()))
                }
            }
        }
    }
}

/** "Open with" menu: the system default, or any installed app that can handle [url]. */
@Composable
private fun AppPicker(url: String, selected: String?, onSelect: (String?) -> Unit) {
    val context = LocalContext.current
    val choices = remember(url) { Launcher.appChoices(context, url) }
    var expanded by remember { mutableStateOf(false) }
    val defaultLabel = if (url.isBlank()) "Pick an app" else "Default app"

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Open with: " + (selected?.let { Launcher.appLabel(context, it) } ?: defaultLabel),
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (url.isNotBlank()) {
                DropdownMenuItem(text = { Text(defaultLabel) }, onClick = {
                    onSelect(null)
                    expanded = false
                })
            }
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                    onSelect(choice.packageName)
                    expanded = false
                })
            }
        }
    }
}
