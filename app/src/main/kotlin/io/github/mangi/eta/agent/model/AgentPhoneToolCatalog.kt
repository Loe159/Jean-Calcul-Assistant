package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.tool.RootRequirement
import io.github.mangi.eta.agent.tool.SystemStateControls
import org.json.JSONArray
import org.json.JSONObject

/** 同一份领域目录用于模型、权限元数据和工具页，避免新增入口时漏接一端。 */
internal object AgentPhoneToolCatalog {
    data class Entry(
        val name: String,
        val title: String,
        val description: String,
        val write: Boolean,
        val root: RootRequirement,
        val colorOs: Boolean = false,
        val personal: Boolean = false,
        val direct: Boolean = false,
    )

    val entries =
        listOf(
            Entry(
                "get_flashlight",
                "Flashlight state",
                "Read actual flashlight state and camera occupancy.",
                false,
                RootRequirement.NONE,
                direct = true,
            ),
            Entry(
                "set_flashlight",
                "Set flashlight",
                "Enable/disable the flashlight with system callback verification. Avoid toggling again if already at the target state.",
                true,
                RootRequirement.NONE,
                direct = true,
            ),
            Entry(
                "get_device_state",
                "Read system switch",
                "Read a system switch directly without GUI.",
                false,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "get_display_state",
                "Display state",
                "Read brightness, automatic brightness, rotation and screen timeout.",
                false,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "set_brightness",
                "Set brightness",
                "Set system slider brightness percentage and verify readback. Fail explicitly if reliable percentages are unsupported.",
                true,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "set_screen_timeout",
                "Set screen timeout",
                "Set inactivity timeout in seconds and verify readback.",
                true,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "get_sound_state",
                "Sound state",
                "Read volume streams, ringer and Do Not Disturb states.",
                false,
                RootRequirement.NONE,
                direct = true,
            ),
            Entry(
                "set_sound_mode",
                "Set ringer mode",
                "Set normal, silent or vibrate mode and verify. App identity may require Do Not Disturb access.",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry("set_do_not_disturb", "Set Do Not Disturb", "Set Do Not Disturb policy directly and verify readback.", true, RootRequirement.REQUIRED),
            Entry(
                "get_hotspot",
                "Hotspot state",
                "Read hotspot state, requiring a compatible system API.",
                false,
                RootRequirement.REQUIRED,
                direct = true,
            ),
            Entry(
                "set_hotspot",
                "Set hotspot",
                "Enable/disable tethering with the existing configuration, preserving network name and password.",
                true,
                RootRequirement.REQUIRED,
            ),
            Entry(
                "list_calendars",
                "Available calendars",
                "List writable calendar IDs/names for this user. Creation must use a real calendar; never guess calendar_id.",
                false,
                RootRequirement.PARTIAL,
                personal = true,
            ),
            Entry(
                "read_calendar_event",
                "Event details",
                "Read a live event and reminders using original event_id, never a DMP document ID.",
                false,
                RootRequirement.PARTIAL,
                personal = true,
            ),
            Entry(
                "create_calendar_event",
                "Create event",
                "Create event/reminders directly and verify after transaction. One-off events use start/end; all-day events use UTC midnight with exclusive end midnight. Query after timeout before creating again.",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "create_calendar_events",
                "Create events in batch",
                "Validate all events/reminders first, then create in one calendar transaction and verify individually. Maximum 30. Query unconfirmed results before recreating.",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "update_calendar_event",
                "Update event",
                "Update event fields/reminders by original event_id and verify. Time changes require both start/end. Recurring events are not modified.",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "delete_calendar_event",
                "Delete event",
                "Delete exact original event_id. Recurring events require scope=series. Never use an index ID as an event ID.",
                true,
                RootRequirement.PARTIAL,
            ),
            Entry(
                "update_alarm_time",
                "Change alarm time",
                "Change hour/minute by alarm_id, preserving enabled state and verifying.",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "set_alarm_enabled",
                "Alarm switch",
                "Enable/disable exact alarm_id and verify. Do not fuzzy-match multiple alarms by time.",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "delete_alarm",
                "Delete alarm",
                "Delete exact alarm_id and verify. Never use a DMP index ID.",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "create_note",
                "Create note",
                "Create a system note directly, parse its actual UUID and verify. Never repeat creation on unconfirmed results.",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
            Entry(
                "read_note",
                "Note details",
                "Read note content by original note_id UUID, never a DMP document ID.",
                false,
                RootRequirement.REQUIRED,
                true,
                true,
            ),
            Entry(
                "delete_note",
                "Recycle note",
                "Move a note to trash by original note_id UUID and verify; this is not permanent deletion.",
                true,
                RootRequirement.REQUIRED,
                true,
            ),
        )
    val names = entries.mapTo(linkedSetOf()) { it.name }
    val direct = entries.filter { it.direct }.mapTo(linkedSetOf()) { it.name }
    val reads = entries.filter { !it.write && !it.direct }.mapTo(linkedSetOf()) { it.name }
    val writes = entries.filter { it.write && !it.direct }.mapTo(linkedSetOf()) { it.name }

    fun appendTo(tools: JSONArray, direct: Boolean, reads: Boolean, writes: Boolean) {
        entries
            .filter { if (it.direct) direct else if (it.write) writes else reads }
            .forEach { tools.put(schema(it)) }
    }

    private fun schema(entry: Entry): JSONObject {
        val p = JSONObject()
        val required = mutableListOf<String>()
        fun field(key: String, schema: JSONObject, need: Boolean = true) {
            p.put(key, schema)
            if (need) required += key
        }
        fun id(key: String) = field(key, text("Original object ID", 36))
        when (entry.name) {
            "set_flashlight",
            "set_hotspot" -> field("enabled", JSONObject().put("type", "boolean"))
            "get_device_state" -> field("target", enum(SystemStateControls.targets))
            "set_brightness" -> field("percent", integer(0, 100))
            "set_screen_timeout" -> field("seconds", integer(15, 1800))
            "set_sound_mode" -> field("mode", enum(listOf("normal", "silent", "vibrate")))
            "set_do_not_disturb" ->
                field("mode", enum(listOf("off", "priority", "alarms", "silent")))
            "read_calendar_event" -> id("event_id")
            "create_calendar_events" -> {
                field("calendar_id", text("Writable calendar ID", 19), false)
                val single =
                    schema(entries.first { it.name == "create_calendar_event" })
                        .getJSONObject("function")
                        .getJSONObject("parameters")
                single.getJSONObject("properties").remove("calendar_id")
                field(
                    "events",
                    JSONObject()
                        .put("type", "array")
                        .put("minItems", 1)
                        .put("maxItems", 30)
                        .put("items", single),
                )
            }
            "create_calendar_event",
            "update_calendar_event" -> {
                val create = entry.name == "create_calendar_event"
                if (create)
                    field("calendar_id", text("Writable calendar ID from list_calendars; optional when only one exists", 19), false)
                else id("event_id")
                field("title", text("Event title", 200), create)
                field("start_time", text("ISO 8601 start time with offset", 64), create)
                field("end_time", text("ISO 8601 end time with offset", 64), create)
                field("timezone", text("IANA timezone, e.g. Europe/Paris", 80), false)
                field("all_day", JSONObject().put("type", "boolean"), false)
                field("location", text("Location", 500), false)
                field("description", text("Description", 8000), false)
                field(
                    "reminder_minutes",
                    JSONObject()
                        .put("type", "array")
                        .put("items", integer(0, 40320))
                        .put("maxItems", 5),
                    false,
                )
            }
            "delete_calendar_event" -> {
                id("event_id")
                field("scope", enum(listOf("event", "series")), false)
            }
            "update_alarm_time" -> {
                id("alarm_id")
                field("hour", integer(0, 23))
                field("minute", integer(0, 59))
            }
            "set_alarm_enabled" -> {
                id("alarm_id")
                field("enabled", JSONObject().put("type", "boolean"))
            }
            "delete_alarm" -> id("alarm_id")
            "create_note" -> {
                field("title", text("Title", 200), false)
                field("content", text("Note content", 20000))
            }
            "read_note",
            "delete_note" -> id("note_id")
        }
        return AgentPersonalSearchToolCatalog.function(
            entry.name,
            entry.description,
            p,
            *required.toTypedArray(),
        )
    }

    private fun text(description: String, max: Int) =
        JSONObject().put("type", "string").put("description", description).put("maxLength", max)

    private fun integer(min: Int, max: Int) =
        JSONObject().put("type", "integer").put("minimum", min).put("maximum", max)

    private fun enum(values: List<String>) =
        JSONObject().put("type", "string").put("enum", JSONArray(values))
}
