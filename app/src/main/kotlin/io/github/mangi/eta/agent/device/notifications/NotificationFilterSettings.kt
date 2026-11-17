package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import org.json.JSONObject

/** These settings never contain notification text or identifiers. */
internal enum class NotificationFilterMode { OFF, OBSERVE, CAUTIOUS, PERSONALIZED }
internal enum class NotificationFilterAction { SNOOZE_1H, SNOOZE_24H, DISMISS }

internal data class NotificationFilterConfig(
    val mode: NotificationFilterMode = NotificationFilterMode.OFF,
    val action: NotificationFilterAction = NotificationFilterAction.SNOOZE_1H,
    val filterAds: Boolean = true,
    val filterSpam: Boolean = false,
    val onlyQuietHours: Boolean = false,
    val quietFrom: Int = 22,
    val quietUntil: Int = 7,
) {
    fun inQuietHours(hour: Int): Boolean = if (quietFrom == quietUntil) true
        else if (quietFrom < quietUntil) hour in quietFrom until quietUntil
        else hour >= quietFrom || hour < quietUntil
}

internal object NotificationFilterSettings {
    private const val PREFS = "notification_local"
    private const val KEY = "filter_config_v2"
    private const val LEGACY_KEY = "filter_config_v1"
    private const val LEGACY_OBSERVE = "observation_enabled"

    fun load(context: Context): NotificationFilterConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY, null)
        // V1 allowed permanent deletion only in individually approved apps.
        // Widening that permission to every eligible app needs renewed consent.
        val legacy = current == null
        val serialized = current ?: prefs.getString(LEGACY_KEY, null)
        if (serialized == null) return NotificationFilterConfig(
            mode = if (prefs.getBoolean(LEGACY_OBSERVE, false)) NotificationFilterMode.OBSERVE
            else NotificationFilterMode.OFF,
        )
        return runCatching {
            val json = JSONObject(serialized)
            val mode = enumValueOr(NotificationFilterMode.OFF, json.optString("mode"))
            val rawAction = enumValueOr(NotificationFilterAction.SNOOZE_1H, json.optString("action"))
            NotificationFilterConfig(
                mode = mode,
                action = if ((legacy || mode != NotificationFilterMode.PERSONALIZED) &&
                    rawAction == NotificationFilterAction.DISMISS)
                    NotificationFilterAction.SNOOZE_1H else rawAction,
                filterAds = json.optBoolean("ads", true),
                filterSpam = json.optBoolean("spam", false),
                onlyQuietHours = json.optBoolean("quiet", false),
                quietFrom = json.optInt("from", 22).coerceIn(0, 23),
                quietUntil = json.optInt("until", 7).coerceIn(0, 23),
            )
        }.getOrElse { NotificationFilterConfig() } // Corrupt settings fail closed.
    }

    @Synchronized
    fun save(context: Context, config: NotificationFilterConfig) {
        require(config.quietFrom in 0..23 && config.quietUntil in 0..23)
        // Irreversible deletion still requires a deliberate action in mode 3.
        require(config.action != NotificationFilterAction.DISMISS ||
            config.mode == NotificationFilterMode.PERSONALIZED)
        val json = JSONObject()
            .put("mode", config.mode.name)
            .put("action", config.action.name)
            .put("ads", config.filterAds)
            .put("spam", config.filterSpam)
            .put("quiet", config.onlyQuietHours)
            .put("from", config.quietFrom)
            .put("until", config.quietUntil)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.toString())
            .remove(LEGACY_KEY)
            .putBoolean(LEGACY_OBSERVE, config.mode != NotificationFilterMode.OFF)
            .commit()) { "Cannot persist notification filtering settings" }
    }

    fun setMode(context: Context, mode: NotificationFilterMode) {
        val current = load(context)
        save(context, current.copy(
            mode = mode,
            action = if (mode != NotificationFilterMode.PERSONALIZED &&
                current.action == NotificationFilterAction.DISMISS)
                NotificationFilterAction.SNOOZE_1H else current.action,
        ))
    }

    private inline fun <reified T : Enum<T>> enumValueOr(default: T, input: String): T =
        enumValues<T>().firstOrNull { it.name == input } ?: default
}
