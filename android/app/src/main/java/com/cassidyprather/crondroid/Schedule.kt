package com.cassidyprather.crondroid

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * One scheduled launch: at [hour]:[minute] on each of [days] (or once, when [days] is empty),
 * open [url], optionally in the app [packageName]. With no URL, just open that app.
 */
data class Schedule(
    val id: Int,
    val name: String,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val url: String,
    val packageName: String?,
    val enabled: Boolean,
) {
    val repeats: Boolean get() = days.isNotEmpty()

    /** The first time strictly after [now] that this schedule should fire. */
    fun nextTrigger(now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        var date = now.toLocalDate()
        while (true) {
            // ZonedDateTime.of moves a time that falls in a DST gap forward, so it still fires.
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now) && (days.isEmpty() || date.dayOfWeek in days)) {
                return candidate
            }
            date = date.plusDays(1)
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("hour", hour)
        .put("minute", minute)
        .put("days", JSONArray(days.map { it.value }.sorted()))
        .put("url", url)
        .put("packageName", packageName ?: JSONObject.NULL)
        .put("enabled", enabled)

    companion object {
        // "https:", "twitch:", "mailto:" etc., but not "host:8080".
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:(?!\\d)")

        fun fromJson(json: JSONObject): Schedule {
            val days = json.getJSONArray("days")
            return Schedule(
                id = json.getInt("id"),
                name = json.optString("name"),
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
                days = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
                url = json.optString("url"),
                packageName = if (json.isNull("packageName")) null else json.getString("packageName"),
                enabled = json.optBoolean("enabled", true),
            )
        }

        /** Adds `https://` when the user typed a bare address like `twitch.tv/somebody`. */
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim()
            return if (trimmed.isEmpty() || SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        }
    }
}
