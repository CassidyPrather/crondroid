package com.cassidyprather.crondroid

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/** Persists schedules as a JSON array in shared preferences. */
class ScheduleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("schedules", Context.MODE_PRIVATE)

    fun all(): List<Schedule> {
        val array = JSONArray(prefs.getString(KEY_SCHEDULES, "[]"))
        return (0 until array.length()).map { Schedule.fromJson(array.getJSONObject(it)) }
    }

    fun get(id: Int): Schedule? = all().firstOrNull { it.id == id }

    /** Inserts or replaces the schedule with the same id. */
    fun save(schedule: Schedule) {
        val others = all().filterNot { it.id == schedule.id }
        write(others + schedule)
    }

    fun delete(id: Int) = write(all().filterNot { it.id == id })

    fun newId(): Int {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit { putInt(KEY_NEXT_ID, id + 1) }
        return id
    }

    private fun write(schedules: List<Schedule>) {
        val array = JSONArray()
        schedules.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id })).forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_SCHEDULES, array.toString()) }
    }

    private companion object {
        const val KEY_SCHEDULES = "schedules"
        const val KEY_NEXT_ID = "next_id"
    }
}
