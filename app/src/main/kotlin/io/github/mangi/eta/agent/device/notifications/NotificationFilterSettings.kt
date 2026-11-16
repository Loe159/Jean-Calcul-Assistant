package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import org.json.JSONObject
import java.time.LocalTime

/** Persisted non-sensitive user choices; never stores notification text or keys. */
internal enum class NotificationFilterMode { OFF, OBSERVE, CAUTIOUS, PERSONALIZED }
internal enum class NotificationFilterAction { SNOOZE_1H, SNOOZE_24H, DISMISS }
internal enum class NotificationAppRule { DEFAULT, KEEP, FILTER }

internal data class NotificationFilterConfig(
    val mode: NotificationFilterMode = NotificationFilterMode.OFF,
    val action: NotificationFilterAction = NotificationFilterAction.SNOOZE_1H,
    val filterAds: Boolean = true,
    val filterSpam: Boolean = false,
    val onlyQuietHours: Boolean = false,
    val quietFrom: Int = 22,
    val quietUntil: Int = 7,
    // An explicitly selected app is required in PERSONALIZED mode.
    val appRules: Map<String, NotificationAppRule> = emptyMap(),
) {
    fun inQuietHours(hour: Int): Boolean = if (quietFrom == quietUntil) true
        else if (quietFrom < quietUntil) hour in quietFrom until quietUntil
        else hour >= quietFrom || hour < quietUntil
}

internal object NotificationFilterSettings {
    private const val PREFS = "notification_local"
    private const val KEY = "filter_config_v1"
    private const val LEGACY_OBSERVE = "observation_enabled"

    fun load(context: Context): NotificationFilterConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY, null)
        if (serialized == null) return NotificationFilterConfig(
            mode = if (prefs.getBoolean(LEGACY_OBSERVE, false)) NotificationFilterMode.OBSERVE
            else NotificationFilterMode.OFF,
        )
        return runCatching {
            val json = JSONObject(serialized)
            val rules = json.optJSONObject("apps")
            val apps = buildMap {
                if (rules != null) {
                    val keys = rules.keys()
                    while (keys.hasNext()) {
                        val packageName = keys.next()
                        if (!isValidPackageName(packageName) || size >= 150) continue
                        val rule = enumValueOr(NotificationAppRule.DEFAULT, rules.optString(packageName))
                        if (rule != NotificationAppRule.DEFAULT) put(packageName, rule)
                    }
                }
            }
            NotificationFilterConfig(
                mode = enumValueOr(NotificationFilterMode.OFF, json.optString("mode")),
                action = enumValueOr(NotificationFilterAction.SNOOZE_1H, json.optString("action")),
                filterAds = json.optBoolean("ads", true),
                filterSpam = json.optBoolean("spam", false),
                onlyQuietHours = json.optBoolean("quiet", false),
                quietFrom = json.optInt("from", 22).coerceIn(0, 23),
                quietUntil = json.optInt("until", 7).coerceIn(0, 23),
                appRules = apps,
            )
        }.getOrElse { NotificationFilterConfig() } // Invalid settings: stop filtering.
    }

    @Synchronized
    fun save(context: Context, config: NotificationFilterConfig) {
        require(config.quietFrom in 0..23 && config.quietUntil in 0..23)
        require(config.appRules.size <= 150 && config.appRules.keys.all(::isValidPackageName))
        // Dismiss is only offered in an explicitly personalized configuration.
        require(config.action != NotificationFilterAction.DISMISS ||
            config.mode == NotificationFilterMode.PERSONALIZED)
        val rules = JSONObject()
        config.appRules.filterValues { it != NotificationAppRule.DEFAULT }
            .forEach { (key, value) -> rules.put(key, value.name) }
        val json = JSONObject()
            .put("mode", config.mode.name)
            .put("action", config.action.name)
            .put("ads", config.filterAds)
            .put("spam", config.filterSpam)
            .put("quiet", config.onlyQuietHours)
            .put("from", config.quietFrom)
            .put("until", config.quietUntil)
            .put("apps", rules)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.toString()).putBoolean(LEGACY_OBSERVE, config.mode != NotificationFilterMode.OFF)
            .commit()
    }

    fun setMode(context: Context, mode: NotificationFilterMode) {
        val current = load(context)
        save(context, current.copy(mode = mode,
            action = if (mode != NotificationFilterMode.PERSONALIZED && current.action ==
                NotificationFilterAction.DISMISS) NotificationFilterAction.SNOOZE_1H else current.action))
    }

    fun isValidPackageName(name: String): Boolean = name.length in 3..200 &&
        name.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+"))

    private inline fun <reified T : Enum<T>> enumValueOr(default: T, input: String): T =
        enumValues<T>().firstOrNull { it.name == input } ?: default
}
