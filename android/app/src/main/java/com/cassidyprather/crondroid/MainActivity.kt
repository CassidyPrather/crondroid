package com.cassidyprather.crondroid

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cassidyprather.crondroid.ui.CrondroidTheme
import com.cassidyprather.crondroid.ui.EditScheduleScreen
import com.cassidyprather.crondroid.ui.ScheduleListScreen
import com.cassidyprather.crondroid.ui.describeNext

class MainActivity : ComponentActivity() {
    private lateinit var store: ScheduleStore
    private var schedules by mutableStateOf(emptyList<Schedule>())
    private var editing by mutableStateOf<Schedule?>(null)
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = ScheduleStore(this)
        setContent {
            CrondroidTheme {
                val current = editing
                if (current == null) {
                    ScheduleListScreen(
                        schedules = schedules,
                        refreshKey = resumeCount,
                        onRefresh = { resumeCount++ },
                        onAdd = { editing = newSchedule() },
                        onEdit = { editing = it },
                        onToggle = { schedule, on -> save(schedule.copy(enabled = on)) },
                    )
                } else {
                    BackHandler { editing = null }
                    EditScheduleScreen(
                        initial = current,
                        isNew = store.get(current.id) == null,
                        onSave = {
                            save(it)
                            editing = null
                        },
                        onDelete = {
                            AlarmScheduler.cancel(this, it.id)
                            store.delete(it.id)
                            reload()
                            editing = null
                        },
                        onTest = { if (!(it.audioOnly && AudioService.start(this, it))) Launcher.open(this, it) },
                        onBack = { editing = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Schedules may have changed while we were away (a one-time schedule turns itself off after firing).
        reload()
        resumeCount++
    }

    private fun reload() {
        schedules = store.all()
    }

    private fun newSchedule() = Schedule(
        id = store.newId(),
        name = "",
        hour = 9,
        minute = 0,
        days = emptySet(),
        url = "",
        packageName = null,
        enabled = true,
    )

    private fun save(schedule: Schedule) {
        store.save(schedule)
        AlarmScheduler.update(this, schedule)
        reload()
        if (schedule.enabled) Toast.makeText(this, describeNext(schedule), Toast.LENGTH_SHORT).show()
    }
}
