package io.github.mangi.eta.agent.model

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

internal object AgentDeviceResultFormatter {
    fun summarize(toolName: String, json: JSONObject): String? {
        if (json.opt("ok") == false || json.optString("status") in setOf("unconfirmed", "failed")) return null
        when (toolName) {
            "get_current_context" -> return currentContext(json)
            "get_current_location" -> return currentLocation(json)
            "get_device_environment" -> return "Device context read"
            "app_usage_summary" -> return "App usage statistics read"
            "recent_app_activity" -> return "App activity read"
            "get_device_state", "get_flashlight", "get_hotspot" -> {
                if (json.opt("ok") != true) return null
                val label = when (toolName) {
                    "get_flashlight" -> "Flashlight"
                    "get_hotspot" -> "Hotspot"
                    else -> deviceLabel(json.optString("target")) ?: return null
                }
                val enabled = json.opt("enabled") as? Boolean ?: return null
                return "$label: ${booleanState(enabled)}"
            }
            "media_control" -> return mediaControl(json)
            "network_info" -> return networkInfo(json)
            "device_status" -> return deviceStatus(json)
        }
        if (json.opt("verified") != true) return null
        return when (toolName) {
            "set_device_state" -> booleanChange(deviceLabel(json.optString("target")) ?: return null, json)
            "set_flashlight" -> booleanChange("Flashlight", json)
            "set_hotspot" -> booleanChange("Hotspot", json)
            "set_volume" -> volume(json)
            "set_brightness" -> brightness(json)
            "set_screen_timeout" -> timeout(number(json, "before"), number(json, "after"))
            "set_sound_mode" -> change("Ringer mode", soundMode(integer(json, "before")), soundMode(integer(json, "after")))
            "set_do_not_disturb" -> change("Do Not Disturb", dndMode(integer(json, "before")), dndMode(integer(json, "after")))
            "app_state_control" -> applicationState(json)
            "set_setting" -> setting(json)
            else -> null
        }
    }

    private fun currentContext(json: JSONObject): String? {
        val datetime = json.opt("datetime") as? String ?: return null
        val time = try {
            OffsetDateTime.parse(datetime).format(CONTEXT_TIME_FORMAT)
        } catch (_: DateTimeParseException) {
            return null
        }
        val location = json.optJSONObject("location") ?: return time
        val status = when (location.optString("status")) {
            "permission_required" -> "Location permission not granted"
            "background_permission_required" -> "Background location permission not granted"
            "location_disabled" -> "System location disabled"
            "unavailable" -> "No recent location"
            else -> null
        }
        if (status != null) return "$time · $status"
        val latitude = number(location, "latitude")?.takeIf { it in -90.0..90.0 }
        val longitude = number(location, "longitude")?.takeIf { it in -180.0..180.0 }
        if (latitude == null || longitude == null) return "$time · location unavailable"
        val age = integer(location, "age_s")?.let {
            when {
                it < 60 -> "$it seconds ago"
                it < 3600 -> "${it / 60} minutes ago"
                it < 86400 -> "${it / 3600} hours ago"
                else -> "${it / 86400} days ago"
            }
        }
        return "$time · recent location: ${decimal(latitude)}, ${decimal(longitude)}" +
            if (age != null) " ($age)" else ""
    }

    private fun currentLocation(json: JSONObject): String? {
        val latitude = number(json, "latitude")?.takeIf { it in -90.0..90.0 }
        val longitude = number(json, "longitude")?.takeIf { it in -180.0..180.0 }
        if (latitude == null || longitude == null) return "Location unavailable"
        val ageMs = number(json, "age_ms")
        val age = ageMs?.let { ms ->
            val seconds = (ms / 1000).toLong()
            when {
                seconds < 60 -> "$seconds seconds ago"
                seconds < 3600 -> "${seconds / 60} minutes ago"
                seconds < 86400 -> "${seconds / 3600} hours ago"
                else -> "${seconds / 86400} days ago"
            }
        }
        return "Recent location: ${decimal(latitude)}, ${decimal(longitude)}" +
            if (age != null) " ($age)" else ""
    }

    private fun booleanChange(label: String, json: JSONObject): String? = change(
        label,
        (json.opt("before") as? Boolean)?.let(::booleanState),
        (json.opt("after") as? Boolean)?.let(::booleanState),
        withoutBefore = "Done: ",
    )

    private fun volume(json: JSONObject): String? {
        val stream = when (json.optString("stream")) {
            "media" -> "Media"
            "alarm" -> "Alarm"
            "ring" -> "Ringtone"
            "notification" -> "Notifications"
            "voice_call" -> "Calls"
            "system" -> "System"
            else -> return null
        }
        val before = integer(json, "before")
        val after = integer(json, "after") ?: return null
        val min = integer(json, "min_level")
        val max = integer(json, "max_level")
        val range = if (min != null && max != null && max > min && after in min..max) min..max else null
        fun display(level: Int?): String? = level?.let {
            if (range != null) {
                if (it !in range) null else "${((it - range.first) * 100.0 / (range.last - range.first)).roundToInt()}%"
            } else {
                "$it level"
            }
        }
        return change("${stream} volume", display(before), display(after), unchanged = before == after)
    }

    private fun brightness(json: JSONObject): String? {
        val before = number(json, "before")?.takeIf { it in 0.0..100.0 }
        val after = number(json, "after")?.takeIf { it in 0.0..100.0 } ?: return null
        fun display(value: Double): String = "${decimal((value * 10).roundToInt() / 10.0)}%"
        return change("Brightness", before?.let(::display), display(after), unchanged = before == after)
    }

    private fun timeout(before: Double?, after: Double?): String? {
        fun display(value: Double?): String? = value?.takeIf { it >= 0 && it <= Long.MAX_VALUE.toDouble() && it % 1 == 0.0 }?.let {
            if (it > 0 && it % 60_000 == 0.0) "${decimal(it / 60_000)} minutes"
            else "${decimal(it / 1000)} seconds"
        }
        return change("Screen timeout", display(before), display(after), unchanged = before == after)
    }

    private fun applicationState(json: JSONObject): String? {
        val action = json.optString("action")
        val field = when (action) {
            "force_stop" -> "stopped"
            "freeze", "unfreeze" -> "enabled"
            else -> return null
        }
        if (json.optString("state_field") != field) return null
        val after = json.opt("after") as? Boolean ?: return null
        val before = json.opt("before") as? Boolean
        val packageName = (json.opt("package_name") as? String)
            ?.takeIf { it.length <= 255 && PACKAGE_NAME.matches(it) }
            ?.let { if (it.length > 60) it.take(59) + "…" else it }
        val target = packageName?.let { "App $it" } ?: "App"
        return when (action) {
            "force_stop" -> when {
                before == true && after -> "$target: remains stopped"
                after -> "Stopped $target"
                else -> "$target: not stopped"
            }
            "freeze" -> when {
                before == false && !after -> "$target: remains frozen"
                !after -> "Frozen $target"
                else -> "$target: remains enabled"
            }
            "unfreeze" -> when {
                before == true && after -> "$target: remains enabled"
                after -> "Unfrozen $target"
                else -> "$target: remains frozen"
            }
            else -> null
        }
    }

    private fun setting(json: JSONObject): String? {
        val before = json.opt("before") as? String
        val after = json.opt("after") as? String ?: return null
        val namespace = json.optString("namespace")
        val key = json.optString("key")
        val booleanLabel = when (namespace to key) {
            "system" to "accelerometer_rotation" -> "Auto-rotate"
            "system" to "screen_brightness_mode" -> "Auto brightness"
            "global" to "low_power" -> "Battery saver"
            "global" to "airplane_mode_on" -> "Airplane mode"
            else -> null
        }
        if (booleanLabel != null) {
            fun display(value: String?): String? = when (value) {
                "0" -> "Off"
                "1" -> "On"
                else -> null
            }
            return change(booleanLabel, display(before), display(after), withoutBefore = "Done: ")
        }
        if (namespace == "system" && key == "screen_off_timeout") {
            return timeout(before?.toLongOrNull()?.toDouble(), after.toLongOrNull()?.toDouble())
        }
        if (namespace == "global" && key == "zen_mode") {
            return change("Do Not Disturb", dndMode(before?.toIntOrNull()), dndMode(after.toIntOrNull()))
        }
        // 任意设置值可能含认证信息或用户正文，摘要只保留是否变化。
        return if (before == after) "System settings unchanged" else "System settings changed"
    }

    private fun mediaControl(json: JSONObject): String? {
        if (json.opt("ok") != true) return null
        val action = when (json.optString("action")) {
            "play" -> "Play"
            "pause" -> "Pause"
            "play_pause" -> "Toggle playback"
            "next" -> "Next track"
            "previous" -> "Previous track"
            "stop" -> "Stop playback"
            else -> return null
        }
        return "Sent ${action} command"
    }

    private fun networkInfo(json: JSONObject): String? {
        if (json.opt("ok") != true) return null
        val connected = json.opt("connected") as? Boolean ?: return null
        val validated = json.opt("validated") as? Boolean ?: return null
        val transports = json.optJSONArray("transports") ?: return null
        val labels = (0 until transports.length()).mapNotNull { index ->
            when (transports.opt(index) as? String ?: return null) {
                "wifi" -> "Wi-Fi"
                "cellular" -> "Cellular network"
                "ethernet" -> "Ethernet"
                "vpn" -> "VPN"
                else -> null
            }
        }.distinct()
        if (!connected) return "Network: disconnected"
        val label = labels.joinToString(" / ").ifEmpty { "Network" }
        return "$label: ${if (validated) "Connected to the internet" else "Connected; internet not verified"}"
    }

    private fun deviceStatus(json: JSONObject): String? {
        if (json.opt("ok") != true) return null
        val percent = integer(json, "battery_percent")?.takeIf { it in 0..100 } ?: return null
        val charging = json.opt("charging") as? Boolean ?: return null
        return "Battery $percent% · ${if (charging) "Charging" else "Not charging"}"
    }

    private fun change(
        label: String,
        before: String?,
        after: String?,
        unchanged: Boolean = before == after,
        withoutBefore: String = "set to ",
    ): String? {
        if (after == null) return null
        val prefix = withoutBefore.trimEnd() + " "
        val keep = "Keep "
        return when {
            before == null -> "$label: $prefix$after"
            unchanged -> "$label: $keep$after"
            before == after -> "$label: $prefix$after"
            else -> "$label: $before → $after"
        }
    }

    private fun booleanState(value: Boolean): String = if (value) "On" else "Off"

    private fun deviceLabel(target: String): String? = when (target) {
        "wifi" -> "Wi-Fi"
        "bluetooth" -> "Bluetooth"
        "mobile_data" -> "Mobile data"
        "airplane_mode" -> "Airplane mode"
        "location" -> "Location"
        "nfc" -> "NFC"
        "battery_saver" -> "Battery saver"
        "auto_rotate" -> "Auto-rotate"
        "auto_brightness" -> "Auto brightness"
        "dark_mode" -> "Dark mode"
        "night_light" -> "Eye comfort mode"
        else -> null
    }

    private fun soundMode(value: Int?): String? = when (value) {
        0 -> "Silent"
        1 -> "Vibrate"
        2 -> "Normal"
        else -> null
    }

    private fun dndMode(value: Int?): String? = when (value) {
        0 -> "Off"
        1 -> "Priority"
        2 -> "Total silence"
        3 -> "Alarms only"
        else -> null
    }

    private fun number(json: JSONObject, key: String): Double? =
        (json.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun integer(json: JSONObject, key: String): Int? = number(json, key)
        ?.takeIf { it in 0.0..Int.MAX_VALUE.toDouble() && it % 1 == 0.0 }?.toInt()

    private fun decimal(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private val CONTEXT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx", Locale.ROOT)
}
