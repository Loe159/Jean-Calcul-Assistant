package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Settings contain only model hypotheses and numeric rules, never notification content. */
internal enum class NotificationFilterMode { OFF, OBSERVE, CAUTIOUS, PERSONALIZED }
internal enum class NotificationFilterAction { SNOOZE_1H, SNOOZE_24H, DISMISS }

internal data class NotificationLabelRule(
    val label: String,
    val enabled: Boolean = false,
    val minimumScore: Int = 65,
    val minimumMargin: Int = 20,
)

internal object NotificationLabelRules {
    const val MAX_LABELS = 10

    fun defaults(ads: Boolean = true, spam: Boolean = false): List<NotificationLabelRule> =
        NotificationNliScores.validatedLabels.map { label ->
            when (label) {
                "a promotional advertisement" -> NotificationLabelRule(label, ads, 40, 8)
                "spam or a scam" -> NotificationLabelRule(label, spam, 65, 20)
                else -> NotificationLabelRule(label)
            }
        }

    fun valid(rules: List<NotificationLabelRule>): Boolean =
        rules.size in 2..MAX_LABELS &&
            rules.map { it.label.lowercase() }.distinct().size == rules.size &&
            rules.all { rule ->
                rule.label.length in 3..96 &&
                    rule.label == rule.label.trim() &&
                    rule.label.none { it.isISOControl() } &&
                    rule.minimumScore in 0..100 && rule.minimumMargin in 0..100
            }
}

internal data class NotificationFilterConfig(
    val mode: NotificationFilterMode = NotificationFilterMode.OFF,
    val action: NotificationFilterAction = NotificationFilterAction.SNOOZE_1H,
    val labelRules: List<NotificationLabelRule> = NotificationLabelRules.defaults(),
    val onlyQuietHours: Boolean = false,
    val quietFrom: Int = 22,
    val quietUntil: Int = 7,
) {
    fun inferenceLabels(): List<String> =
        if (mode == NotificationFilterMode.CAUTIOUS) NotificationNliScores.validatedLabels
        else labelRules.map { it.label }

    fun inQuietHours(hour: Int): Boolean = if (quietFrom == quietUntil) true
        else if (quietFrom < quietUntil) hour in quietFrom until quietUntil
        else hour >= quietFrom || hour < quietUntil
}

internal object NotificationFilterSettings {
    private const val PREFS = "notification_local"
    private const val KEY = "filter_config_v3"
    private const val PREVIOUS_KEY = "filter_config_v2"
    private const val LEGACY_KEY = "filter_config_v1"
    private const val LEGACY_OBSERVE = "observation_enabled"

    fun load(context: Context): NotificationFilterConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v3 = prefs.getString(KEY, null)
        val v2 = prefs.getString(PREVIOUS_KEY, null)
        val v1 = prefs.getString(LEGACY_KEY, null)
        val serialized = v3 ?: v2 ?: v1
        if (serialized == null) return NotificationFilterConfig(
            mode = if (prefs.getBoolean(LEGACY_OBSERVE, false)) NotificationFilterMode.OBSERVE
            else NotificationFilterMode.OFF,
        )
        return runCatching {
            val json = JSONObject(serialized)
            val mode = enumValueOr(NotificationFilterMode.OFF, json.optString("mode"))
            val rawAction = enumValueOr(NotificationFilterAction.SNOOZE_1H, json.optString("action"))
            val labels = if (v3 != null) {
                val data = json.getJSONArray("labels")
                (0 until data.length()).map { index ->
                    val row = data.getJSONObject(index)
                    NotificationLabelRule(
                        label = row.getString("label"),
                        enabled = row.getBoolean("enabled"),
                        minimumScore = row.getInt("score"),
                        minimumMargin = row.getInt("margin"),
                    )
                }
            } else NotificationLabelRules.defaults(
                ads = json.optBoolean("ads", true),
                spam = json.optBoolean("spam", false),
            )
            require(NotificationLabelRules.valid(labels)) { "Invalid label rules" }
            NotificationFilterConfig(
                mode = mode,
                // Only the original v1 had a per-app deletion authorization.
                action = if ((v3 == null && v2 == null || mode != NotificationFilterMode.PERSONALIZED) &&
                    rawAction == NotificationFilterAction.DISMISS)
                    NotificationFilterAction.SNOOZE_1H else rawAction,
                labelRules = labels,
                onlyQuietHours = json.optBoolean("quiet", false),
                quietFrom = json.optInt("from", 22).coerceIn(0, 23),
                quietUntil = json.optInt("until", 7).coerceIn(0, 23),
            )
        }.getOrElse { NotificationFilterConfig() } // Corrupt settings fail closed.
    }

    @Synchronized
    fun save(context: Context, config: NotificationFilterConfig) {
        require(config.quietFrom in 0..23 && config.quietUntil in 0..23)
        require(NotificationLabelRules.valid(config.labelRules)) { "Invalid label rules" }
        require(config.action != NotificationFilterAction.DISMISS ||
            config.mode == NotificationFilterMode.PERSONALIZED)
        val labels = JSONArray()
        config.labelRules.forEach { rule ->
            labels.put(JSONObject()
                .put("label", rule.label)
                .put("enabled", rule.enabled)
                .put("score", rule.minimumScore)
                .put("margin", rule.minimumMargin))
        }
        val json = JSONObject()
            .put("mode", config.mode.name)
            .put("action", config.action.name)
            .put("labels", labels)
            .put("quiet", config.onlyQuietHours)
            .put("from", config.quietFrom)
            .put("until", config.quietUntil)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.toString())
            .remove(PREVIOUS_KEY)
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
