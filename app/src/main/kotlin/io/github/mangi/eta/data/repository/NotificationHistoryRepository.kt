package io.github.mangi.eta.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.NotificationPersonalization
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import org.json.JSONArray
import org.json.JSONObject

/** 仅在用户授予通知访问后保存有限期通知，内容不进入 Agent 会话持久记录。 */
internal class NotificationHistoryRepository(context: Context) {
    private val database = Database(context.applicationContext)

    fun record(
        key: String,
        packageName: String,
        title: String?,
        text: String?,
        subText: String?,
        postedAt: Long,
        channelId: String? = null,
        androidCategory: String? = null,
        fingerprint: String? = null,
    ): Boolean {
        // A silent notification still belongs in the received history.
        if (key.isBlank() || packageName.isBlank()) return false
        var needsAnalysis = true
        val values = ContentValues().apply {
            put("notification_key", key.take(MAX_KEY_CHARS))
            put("package_name", packageName.take(MAX_PACKAGE_CHARS))
            put("title", title.bounded())
            put("text", text.bounded())
            put("sub_text", subText.bounded())
            put("posted_at", postedAt)
            put("channel_id", channelId?.take(150))
            put("android_category", androidCategory?.take(100))
            put("content_fingerprint", fingerprint)
            put("topic", NotificationTopics.classify(packageName, channelId, androidCategory,
                title.orEmpty(), text.orEmpty(), null))
        }
        database.writableDatabase.transaction {
            delete(HIDDEN_TABLE, "dismissed_at<?",
                arrayOf((System.currentTimeMillis() - RETENTION_MS).toString()))
            // An archived row remains hidden even when Android replays
            // active notifications after the listener reconnects.
            val alreadyDismissed = query(
                HIDDEN_TABLE, arrayOf("notification_key"), "notification_key=?",
                arrayOf(key.take(MAX_KEY_CHARS)), null, null, null, "1",
            ).use { it.moveToFirst() }
            if (alreadyDismissed) { needsAnalysis = false; return@transaction }
            query(
                TABLE, arrayOf("posted_at", "filter_state", "filter_label", "filter_score", "filter_reason", "topic", "user_topic", "personal_interest", "content_fingerprint"),
                "notification_key=?", arrayOf(key.take(MAX_KEY_CHARS)), null, null, null, "1",
            ).use { cursor ->
                if (cursor.moveToFirst() && cursor.getLong(0) == postedAt &&
                    cursor.getString(8) == fingerprint) {
                    if (cursor.getString(1) in setOf("KEPT", "OBSERVED", "PROPOSED",
                            "ANALYZED_PROTECTED", "SNOOZED_1H", "SNOOZED_24H", "DISMISSED"))
                        needsAnalysis = false
                    values.put("filter_state", cursor.getString(1))
                    values.put("filter_label", cursor.getString(2))
                    if (!cursor.isNull(3)) values.put("filter_score", cursor.getDouble(3))
                    values.put("filter_reason", cursor.getString(4))
                    values.put("topic", cursor.getString(5))
                    values.put("user_topic", cursor.getString(6))
                    values.put("personal_interest", cursor.getString(7))
                }
            }
            insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            delete(TABLE, "posted_at<?", arrayOf((System.currentTimeMillis() - RETENTION_MS).toString()))
            execSQL(
                "DELETE FROM $TABLE WHERE notification_key NOT IN " +
                    "(SELECT notification_key FROM $TABLE ORDER BY posted_at DESC LIMIT $MAX_RECORDS)",
            )
        }
        return needsAnalysis
    }

    /** A confirmed Android listener result is distinct from the model's proposal. */
    enum class DecisionState {
        RECEIVED, QUEUED, PROTECTED, ANALYZED_PROTECTED, NO_CONTENT, MODEL_MISSING, BATTERY_PAUSED,
        LIMIT_REACHED, EXPIRED, ANALYSIS_ERROR,
        KEPT, OBSERVED, PROPOSED, SNOOZED_1H, SNOOZED_24H, DISMISSED,
    }

    /**
     * Called after local inference or a verified Android removal callback.
     * Match the original post time to avoid attributing a stale classifier result
     * to a newer notification reusing the same Android key.
     */
    fun updateDecision(
        key: String,
        postedAt: Long,
        state: DecisionState,
        label: String? = null,
        score: Double? = null,
        reason: String? = null,
        topic: String? = null,
        personalInterest: String? = null,
    ): Boolean {
        if (key.isBlank() || score?.let { !it.isFinite() || it !in 0.0..1.0 } == true) return false
        val values = ContentValues().apply {
            put("filter_state", state.name)
            if (label != null) put("filter_label", label.take(100))
            if (score != null) put("filter_score", score)
            if (reason != null) put("filter_reason", reason.take(180))
            if (topic != null && topic in NotificationTopics.titles) put("topic", topic)
            if (personalInterest != null) put("personal_interest", personalInterest)
        }
        val changed = database.writableDatabase.update(
            TABLE, values, "notification_key=? AND posted_at=?",
            arrayOf(key.take(MAX_KEY_CHARS), postedAt.toString()),
        )
        return changed == 1
    }

    /** Last seven days, including notifications never sent to the AI classifier.
     * Keep the key private to this on-device UI, for safe per-item archiving.
     */
    data class RecentEntry(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val postedAt: Long,
        val state: DecisionState = DecisionState.RECEIVED,
        val category: String? = null,
        val score: Double? = null,
        val reason: String? = null,
        val channelId: String? = null,
        val androidCategory: String? = null,
        val topic: String = "other",
        val userTopic: String? = null,
        val personalInterest: String? = null,
    )

    fun recent(limit: Int = 200): List<RecentEntry> = entries(archived = false, limit = limit)

    /** Copies hidden from Now Brief but still available locally for seven days. */
    fun archived(limit: Int = 200): List<RecentEntry> = entries(archived = true, limit = limit)

    private fun entries(archived: Boolean, limit: Int): List<RecentEntry> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return database.readableDatabase.query(
            TABLE,
            arrayOf("notification_key", "package_name", "title", "text", "sub_text",
                "posted_at", "filter_state", "filter_label", "filter_score", "filter_reason",
                "channel_id", "android_category", "topic", "user_topic", "personal_interest"),
            "posted_at>=? AND archived_at IS ${if (archived) "NOT " else ""}NULL",
            arrayOf(cutoff.toString()),
            null, null, "posted_at DESC", limit.coerceIn(1, MAX_RECORDS).toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(RecentEntry(
                        key = cursor.getString(0),
                        packageName = cursor.getString(1),
                        title = cursor.getString(2).orEmpty(),
                        text = cursor.getString(3).orEmpty().ifBlank { cursor.getString(4).orEmpty() },
                        postedAt = cursor.getLong(5),
                        state = runCatching {
                            DecisionState.valueOf(cursor.getString(6))
                        }.getOrDefault(DecisionState.RECEIVED),
                        category = cursor.getString(7),
                        score = if (cursor.isNull(8)) null else cursor.getDouble(8),
                        reason = cursor.getString(9),
                        channelId = cursor.getString(10),
                        androidCategory = cursor.getString(11),
                        topic = cursor.getString(13) ?: cursor.getString(12) ?: "other",
                        userTopic = cursor.getString(13),
                        personalInterest = cursor.getString(14),
                    ))
                }
            }
        }
    }

    /** Archive local copies without changing their Android notification. */
    fun archiveFromBrief(keys: Collection<String>) {
        if (keys.isEmpty()) return
        database.writableDatabase.transaction {
            keys.forEach { key ->
                val boundedKey = key.take(MAX_KEY_CHARS)
                val archivedAt = System.currentTimeMillis()
                val changed = update(TABLE, ContentValues().apply { put("archived_at", archivedAt) },
                    "notification_key=? AND archived_at IS NULL", arrayOf(boundedKey))
                if (changed == 1) {
                    insertWithOnConflict(HIDDEN_TABLE, null, ContentValues().apply {
                        put("notification_key", boundedKey)
                        put("dismissed_at", archivedAt)
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            delete(HIDDEN_TABLE, "dismissed_at<?",
                arrayOf((System.currentTimeMillis() - RETENTION_MS).toString()))
        }
    }

    /** Restore archived copies to Now Brief without touching Android alerts. */
    fun restoreToBrief(entries: Collection<RecentEntry>) {
        if (entries.isEmpty()) return
        database.writableDatabase.transaction {
            entries.forEach { entry ->
                val key = entry.key.take(MAX_KEY_CHARS)
                if (entry.postedAt < System.currentTimeMillis() - RETENTION_MS) return@forEach
                val changed = update(TABLE, ContentValues().apply { putNull("archived_at") },
                    "notification_key=? AND posted_at=? AND archived_at IS NOT NULL",
                    arrayOf(key, entry.postedAt.toString()))
                if (changed == 1) delete(HIDDEN_TABLE, "notification_key=?", arrayOf(key))
            }
        }
    }

    /** Explicit and implicit human events only; duplicate delivery cannot train twice. */
    fun feedback(key: String, postedAt: Long, kind: NotificationUserFeedback,
                 correctedTopic: String? = null): Boolean {
        if (correctedTopic != null && correctedTopic !in NotificationTopics.titles) return false
        var recorded = false
        database.writableDatabase.transaction {
            val row = query(TABLE, arrayOf("package_name", "channel_id", "filter_label", "title", "text"),
                "notification_key=? AND posted_at=?", arrayOf(key, postedAt.toString()), null, null, null)
                .use { c -> if (c.moveToFirst()) listOf(c.getString(0), c.getString(1),
                    c.getString(2), c.getString(3), c.getString(4)) else null } ?: return@transaction
            val featureList = NotificationPersonalization.features(row[0].orEmpty(), row[1], row[2],
                "${row[3].orEmpty()} ${row[4].orEmpty()}")
            // Corrections are deduplicated by (key, post time, kind, topic).
            val id = "$key:$postedAt:${kind.name}:${correctedTopic.orEmpty()}"
            val added = insertWithOnConflict(FEEDBACK_TABLE, null, ContentValues().apply {
                put("event_id", id.take(1200)); put("event_type", kind.name)
                put("created_at", System.currentTimeMillis()); put("model_version", NotificationPersonalization.VERSION)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (added == -1L) return@transaction
            recorded = true
            if (kind in setOf(NotificationUserFeedback.INTERESTING, NotificationUserFeedback.UNWANTED,
                    NotificationUserFeedback.WRONG_REMOVAL)) {
                update(TABLE, ContentValues().apply { put("personal_interest", if (kind.wanted == true) "WANTED" else "UNWANTED") },
                    "notification_key=? AND posted_at=?", arrayOf(key, postedAt.toString()))
            }
            if (correctedTopic != null) {
                update(TABLE, ContentValues().apply { put("user_topic", correctedTopic) },
                    "notification_key=? AND posted_at=?", arrayOf(key, postedAt.toString()))
            }
            for (feature in featureList.distinct()) {
                if (kind.wanted != null) {
                    execSQL("INSERT INTO $LEARNING_TABLE(feature,wanted,unwanted,examples) VALUES(?,?,?,1) " +
                        "ON CONFLICT(feature) DO UPDATE SET wanted=wanted+excluded.wanted, " +
                        "unwanted=unwanted+excluded.unwanted,examples=examples+1",
                        arrayOf(feature, if (kind.wanted == true) kind.weight else 0.0,
                            if (kind.wanted == false) kind.weight else 0.0))
                }
                if (correctedTopic != null) {
                    val topicFeature = "$feature:$correctedTopic"
                    execSQL("INSERT INTO $TOPIC_TABLE(feature,examples) VALUES(?,1) " +
                        "ON CONFLICT(feature) DO UPDATE SET examples=examples+1", arrayOf(topicFeature))
                }
            }
        }
        return recorded
    }

    fun interest(pkg: String, channel: String?, label: String?, title: String, body: String, minExamples: Int = 3):
        NotificationPersonalization.Interest {
        val features = NotificationPersonalization.features(pkg, channel, label, "$title $body")
        if (features.isEmpty()) return NotificationPersonalization.Interest.NEUTRAL
        val data = mutableListOf<Pair<String, NotificationPersonalization.Evidence>>()
        database.readableDatabase.query(LEARNING_TABLE,
            arrayOf("feature", "wanted", "unwanted", "examples"),
            "feature IN (${features.joinToString(",") { "?" }})", features.toTypedArray(),
            null, null, null).use { c ->
            while (c.moveToNext()) data += c.getString(0) to NotificationPersonalization.Evidence(
                c.getDouble(1), c.getDouble(2), c.getInt(3))
        }
        return NotificationPersonalization.interest(data, minExamples)
    }

    fun learnedTopic(pkg: String, channel: String?, label: String?, title: String, body: String): String? {
        val features = NotificationPersonalization.features(pkg, channel, label, "$title $body")
        // Only context-dependent features may override the original topic.
        for (feature in features.filter { it.startsWith("app-channel:") || it.startsWith("app-label:") }) {
            val matches = mutableListOf<Pair<String, Int>>()
            database.readableDatabase.query(TOPIC_TABLE, arrayOf("feature", "examples"),
                "feature GLOB ?", arrayOf("${feature.replace("[", "[[]").replace("*", "[*]").replace("?", "[?]")}:*"), null, null, null).use { c ->
                while (c.moveToNext()) matches += c.getString(0).substringAfterLast(':') to c.getInt(1)
            }
            val top = matches.maxByOrNull { it.second }
            if (top != null && top.second >= 2 && top.second.toDouble() / matches.sumOf { it.second } >= .7)
                return top.first
        }
        return null
    }

    fun feedbackStats(): String = database.readableDatabase.rawQuery(
        "SELECT event_type,COUNT(*) FROM $FEEDBACK_TABLE GROUP BY event_type", null,
    ).use { c ->
        val counts = mutableListOf<String>()
        while (c.moveToNext()) counts += "${c.getString(0)} : ${c.getInt(1)}"
        if (counts.isEmpty()) "Aucun apprentissage enregistré" else counts.joinToString(" · ")
    }

    fun resetLearning() {
        database.writableDatabase.transaction {
            delete(FEEDBACK_TABLE, null, null)
            delete(LEARNING_TABLE, null, null)
            delete(TOPIC_TABLE, null, null)
            execSQL("UPDATE $TABLE SET user_topic=NULL, personal_interest=NULL")
        }
    }

    fun search(query: String, packageName: String, maxAgeHours: Int, limit: Int): String {
        val clauses = mutableListOf("posted_at>=?")
        val args = mutableListOf((System.currentTimeMillis() - maxAgeHours * HOUR_MS).toString())
        if (packageName.isNotBlank()) {
            clauses += "package_name=?"
            args += packageName
        }
        if (query.isNotBlank()) {
            clauses += "(title LIKE ? ESCAPE '\\' OR text LIKE ? ESCAPE '\\' OR sub_text LIKE ? ESCAPE '\\')"
            val pattern = "%${query.escapeLike()}%"
            repeat(3) { args += pattern }
        }
        val items = JSONArray()
        database.readableDatabase.query(
            TABLE,
            arrayOf("package_name", "title", "text", "sub_text", "posted_at"),
            clauses.joinToString(" AND "),
            args.toTypedArray(),
            null,
            null,
            "posted_at DESC",
            limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.put(
                    JSONObject()
                        .put("package_name", cursor.getString(0))
                        .put("title", cursor.getString(1))
                        .put("text", cursor.getString(2))
                        .put("sub_text", cursor.getString(3))
                        .put("posted_at", cursor.getLong(4)),
                )
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("tool", "search_notification_history")
            .put("items", items)
            .put("count", items.length())
            .put("retention_days", RETENTION_DAYS)
            .put("truncated", items.length() == limit)
            .toString()
    }

    private fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun String?.bounded(): String? = this?.take(MAX_FIELD_CHARS)

    private fun String.escapeLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, 6) {
        override fun onCreate(db: SQLiteDatabase) {
            createHiddenTable(db)
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "notification_key TEXT PRIMARY KEY NOT NULL," +
                    "package_name TEXT NOT NULL," +
                    "title TEXT,text TEXT,sub_text TEXT,posted_at INTEGER NOT NULL," +
                    "filter_state TEXT NOT NULL DEFAULT 'RECEIVED'," +
                    "filter_label TEXT,filter_score REAL,filter_reason TEXT,archived_at INTEGER," +
                    "channel_id TEXT,android_category TEXT,topic TEXT,user_topic TEXT,personal_interest TEXT,content_fingerprint TEXT)",
            )
            db.execSQL("CREATE INDEX notification_history_posted_at ON $TABLE(posted_at DESC)")
            createLearningTables(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createHiddenTable(db)
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN filter_state TEXT NOT NULL DEFAULT 'RECEIVED'")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN filter_label TEXT")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN filter_score REAL")
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN filter_reason TEXT")
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN archived_at INTEGER")
            }
            if (oldVersion < 6) {
                listOf("channel_id", "android_category", "topic", "user_topic", "personal_interest", "content_fingerprint")
                    .forEach { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $it TEXT") }
                createLearningTables(db)
            }
        }

        private fun createLearningTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $FEEDBACK_TABLE (event_id TEXT PRIMARY KEY, event_type TEXT NOT NULL, created_at INTEGER NOT NULL, model_version INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $LEARNING_TABLE (feature TEXT PRIMARY KEY, wanted REAL NOT NULL, unwanted REAL NOT NULL, examples INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $TOPIC_TABLE (feature TEXT PRIMARY KEY, examples INTEGER NOT NULL)")
        }

        private fun createHiddenTable(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $HIDDEN_TABLE (" +
                    "notification_key TEXT PRIMARY KEY NOT NULL, dismissed_at INTEGER NOT NULL)",
            )
        }
    }

    companion object {
        fun confirmedState(decision: NotificationFilterDecision): DecisionState? = when (decision) {
            NotificationFilterDecision.DISMISS -> DecisionState.DISMISSED
            NotificationFilterDecision.SNOOZE_1H -> DecisionState.SNOOZED_1H
            NotificationFilterDecision.SNOOZE_24H -> DecisionState.SNOOZED_24H
            else -> null
        }

        private const val DATABASE_NAME = "eta_notification_history.db"
        private const val TABLE = "notification_history"
        private const val HIDDEN_TABLE = "dismissed_from_brief"
        private const val FEEDBACK_TABLE = "notification_feedback"
        private const val LEARNING_TABLE = "notification_learning"
        private const val TOPIC_TABLE = "notification_topic_learning"
        private const val MAX_RECORDS = 1_000
        private const val RETENTION_DAYS = 7
        private const val HOUR_MS = 60L * 60 * 1_000
        private const val RETENTION_MS = RETENTION_DAYS * 24L * HOUR_MS
        private const val MAX_FIELD_CHARS = 4_000
        private const val MAX_KEY_CHARS = 1_000
        private const val MAX_PACKAGE_CHARS = 255
    }
}
