package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

internal object AgentPhoneResultFormatter {
    fun summarize(toolName: String, json: JSONObject): String? {
        if (json.opt("ok") == false || json.optString("status") == "unconfirmed") return null
        when (toolName) {
            "list_calendars" -> return calendarCount(json)
            "list_alarms" -> return alarmCount(json)
            "list_active_timers" -> return timerCount(json)
            "read_note" -> return json.optJSONObject("note")?.let { "Note read" }
            "read_calendar_event" -> return json.optJSONObject("event")?.let { "Event read" }
        }
        if (json.optString("status") == "dispatched") {
            return when (toolName) {
                "set_alarm" -> withDetail("Alarm request submitted", alarmTime(json))
                "set_timer" -> withDetail("Timer request submitted", timerDuration(json))
                else -> null
            }
        }
        if (json.opt("verified") != true) return null
        return when (toolName) {
            "set_alarm" -> withDetail("Alarm created", alarmTime(json.optJSONObject("alarm")))
            "set_timer" -> withDetail("Timer set", timerDuration(json.optJSONObject("timer") ?: json))
            "update_alarm_time" -> alarmTimeChange(json)
            "set_alarm_enabled" -> alarmEnabled(json)
            "delete_alarm" -> withDetail("Alarm deleted", alarmTime(json.optJSONObject("before")))
            "create_calendar_event", "create_calendar_events" -> {
                val count = json.integer("count", 1, 30)
                if (count != null) "Created $count events" else "Event created"
            }
            "update_calendar_event" -> calendarChange(json)
            "delete_calendar_event" -> "Event deleted"
            "create_note" -> "Note created"
            "delete_note" -> if (json.optString("mode") == "recycle") "Note moved to trash" else null
            else -> null
        }
    }

    private fun calendarCount(json: JSONObject): String? {
        val items = json.optJSONArray("items") ?: return null
        val count = items.length()
        if (count == 0) return "No available calendars"
        return "Found $count available calendars" + if (json.opt("has_more") == true) " · More available" else ""
    }

    private fun alarmCount(json: JSONObject): String? {
        val items = json.optJSONArray("items") ?: return null
        val count = items.length()
        if (count == 0) return "No alarms"
        return "Found $count alarms" + if (json.opt("has_more") == true) " · More available" else ""
    }

    private fun timerCount(json: JSONObject): String? {
        val items = json.optJSONArray("items") ?: return null
        val count = items.length()
        if (count == 0) return "No active timers"
        return "Found $count timers" + if (json.opt("has_more") == true) " · More available" else ""
    }

    private fun alarmTime(json: JSONObject?): String? {
        val hour = json?.integer("hour", 0, 23) ?: return null
        val minute = json.integer("minute", 0, 59) ?: return null
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    private fun timerDuration(json: JSONObject): String? {
        val seconds = json.integer("duration_seconds", 1, 86400) ?: return null
        return buildList {
            if (seconds >= 3600) add("${seconds / 3600} hours")
            if (seconds % 3600 >= 60) add("${seconds % 3600 / 60} minutes")
            if (seconds % 60 > 0) add("${seconds % 60} seconds")
        }.joinToString(" ")
    }

    private fun alarmTimeChange(json: JSONObject): String {
        val before = alarmTime(json.optJSONObject("before"))
        val alarm = json.optJSONObject("alarm")
        val after = alarmTime(alarm)
        val summary = when {
            before != null && after != null && before == after -> "Alarm time unchanged · $after"
            before != null && after != null -> "Alarm time: $before → $after"
            else -> withDetail("Alarm time updated", after)
        }
        return summary + if (alarm?.opt("enabled") == false) " · disabled" else ""
    }

    private fun alarmEnabled(json: JSONObject): String? {
        val alarm = json.optJSONObject("alarm") ?: return null
        val enabled = alarm.opt("enabled") as? Boolean ?: return null
        val before = json.optJSONObject("before")?.opt("enabled") as? Boolean
        val state = if (enabled) "On" else "Off"
        val summary = if (before == enabled) "Alarm remains $state" else "Alarm $state"
        return withDetail(summary, alarmTime(alarm))
    }

    private fun calendarChange(json: JSONObject): String {
        val before = json.optJSONObject("before") ?: return "Event updated"
        val after = json.optJSONObject("event") ?: return "Event updated"
        val fields = listOf(
            "Title" to listOf("title"),
            "Time" to listOf("dtstart", "dtend", "eventTimezone", "allDay"),
            "Location" to listOf("eventLocation"),
            "Description" to listOf("description"),
            "Reminder" to listOf("reminders"),
        )
        val changes = fields.mapNotNull { (label, keys) ->
            label.takeIf {
                keys.any { key ->
                    before.has(key) && after.has(key) &&
                        calendarValue(before, key) != calendarValue(after, key)
                }
            }
        }
        if (changes.isNotEmpty()) return "Event updated · ${changes.joinToString(", ")}"
        val keys = fields.flatMap { it.second }
        val complete = keys.all { hasComparableCalendarValue(before, it) && hasComparableCalendarValue(after, it) }
        return if (complete) "Event unchanged" else "Event updated"
    }

    private fun hasComparableCalendarValue(json: JSONObject, key: String): Boolean {
        if (!json.has(key)) return false
        val value = json.opt(key)
        if (key != "reminders") return value == JSONObject.NULL || value is String || value is Number
        if (value !is JSONArray) return false
        return (0 until value.length()).all { index ->
            val reminder = value.optJSONObject(index)
            reminder != null && listOf("minutes", "method").all {
                reminder.opt(it) is String || reminder.opt(it) is Number
            }
        }
    }

    private fun calendarValue(json: JSONObject, key: String): Any? {
        val value = json.opt(key)
        if (key != "reminders" || value !is JSONArray) return value
        // 提醒顺序不影响效果，只比较分钟数与提醒方式，忽略 Provider 返回的记录标识。
        return (0 until value.length()).map { index ->
            value.optJSONObject(index)?.let { reminder ->
                "${reminder.opt("minutes")}:${reminder.opt("method")}"
            }
        }.sortedBy { it }
    }

    private fun JSONObject.integer(key: String, minimum: Int, maximum: Int): Int? =
        (opt(key) as? Number)?.toString()?.toLongOrNull()
            ?.takeIf { it in minimum.toLong()..maximum.toLong() }?.toInt()

    private fun withDetail(summary: String, detail: String?): String =
        if (detail == null) summary else "$summary · $detail"
}
