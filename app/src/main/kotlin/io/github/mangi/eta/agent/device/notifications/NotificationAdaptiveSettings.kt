package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import org.json.JSONObject

/** Private preferences; learning examples themselves remain in the existing history database. */
internal object NotificationAdaptiveSettings {
    private const val FILE = "notification_local"
    private const val ENABLED = "adaptive_learning_enabled_v1"
    private const val MIN_EXAMPLES = "adaptive_min_examples_v1"
    private const val TOPIC_TITLES = "adaptive_topic_titles_v1"
    fun enabled(context: Context): Boolean = context.getSharedPreferences(FILE, 0).getBoolean(ENABLED, true)
    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, 0).edit().putBoolean(ENABLED, value).apply()
    }
    fun minExamples(context: Context): Int =
        context.getSharedPreferences(FILE, 0).getInt(MIN_EXAMPLES, 3).coerceIn(3, 20)
    fun setMinExamples(context: Context, value: Int) {
        require(value in 3..20)
        context.getSharedPreferences(FILE, 0).edit().putInt(MIN_EXAMPLES, value).apply()
    }
    fun title(context: Context, topic: String): String {
        val custom = runCatching { JSONObject(context.getSharedPreferences(FILE, 0)
            .getString(TOPIC_TITLES, "{}") ?: "{}") }.getOrDefault(JSONObject())
        return custom.optString(topic).takeIf { it.isNotBlank() } ?: NotificationTopics.titles[topic] ?: "Autres"
    }
    fun setTitle(context: Context, topic: String, title: String) {
        require(topic in NotificationTopics.titles && title.length in 3..50 && title.none(Char::isISOControl))
        val prefs = context.getSharedPreferences(FILE, 0)
        val json = runCatching { JSONObject(prefs.getString(TOPIC_TITLES, "{}") ?: "{}") }
            .getOrDefault(JSONObject()).put(topic, title.trim())
        prefs.edit().putString(TOPIC_TITLES, json.toString()).apply()
    }
    fun reset(context: Context) {
        context.getSharedPreferences(FILE, 0).edit().remove(TOPIC_TITLES).remove(MIN_EXAMPLES).apply()
    }
}
