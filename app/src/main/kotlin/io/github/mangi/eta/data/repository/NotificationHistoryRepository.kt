package io.github.mangi.eta.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision
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
    ) {
        // A silent notification still belongs in the received history.
        if (key.isBlank() || packageName.isBlank()) return
        val values = ContentValues().apply {
            put("notification_key", key.take(MAX_KEY_CHARS))
            put("package_name", packageName.take(MAX_PACKAGE_CHARS))
            put("title", title.bounded())
            put("text", text.bounded())
            put("sub_text", subText.bounded())
            put("posted_at", postedAt)
        }
        database.writableDatabase.transaction {
            // A swiped-away row remains hidden even when Android replays
            // active notifications after the listener reconnects.
            val alreadyDismissed = query(
                HIDDEN_TABLE, arrayOf("notification_key"), "notification_key=?",
                arrayOf(key.take(MAX_KEY_CHARS)), null, null, null, "1",
            ).use { it.moveToFirst() }
            if (alreadyDismissed) return@transaction
            query(
                TABLE, arrayOf("posted_at", "filter_state", "filter_label", "filter_score"),
                "notification_key=?", arrayOf(key.take(MAX_KEY_CHARS)), null, null, null, "1",
            ).use { cursor ->
                if (cursor.moveToFirst() && cursor.getLong(0) == postedAt) {
                    values.put("filter_state", cursor.getString(1))
                    values.put("filter_label", cursor.getString(2))
                    if (!cursor.isNull(3)) values.put("filter_score", cursor.getDouble(3))
                }
            }
            insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            delete(TABLE, "posted_at<?", arrayOf((System.currentTimeMillis() - RETENTION_MS).toString()))
            execSQL(
                "DELETE FROM $TABLE WHERE notification_key NOT IN " +
                    "(SELECT notification_key FROM $TABLE ORDER BY posted_at DESC LIMIT $MAX_RECORDS)",
            )
        }
    }

    /** A confirmed Android listener result is distinct from the model's proposal. */
    enum class DecisionState {
        RECEIVED, KEPT, OBSERVED, PROPOSED, SNOOZED_1H, SNOOZED_24H, DISMISSED,
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
    ): Boolean {
        if (key.isBlank() || score?.let { !it.isFinite() || it !in 0.0..1.0 } == true) return false
        val values = ContentValues().apply {
            put("filter_state", state.name)
            if (label != null) put("filter_label", label.take(100))
            if (score != null) put("filter_score", score)
        }
        val changed = database.writableDatabase.update(
            TABLE, values, "notification_key=? AND posted_at=?",
            arrayOf(key.take(MAX_KEY_CHARS), postedAt.toString()),
        )
        return changed == 1
    }

    /** Last seven days, including notifications never sent to the AI classifier.
     * Keep the key private to this on-device UI, for safe per-item dismissal.
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
    )

    fun recent(limit: Int = 200): List<RecentEntry> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return database.readableDatabase.query(
            TABLE,
            arrayOf("notification_key", "package_name", "title", "text", "sub_text",
                "posted_at", "filter_state", "filter_label", "filter_score"),
            "posted_at>=?", arrayOf(cutoff.toString()),
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
                    ))
                }
            }
        }
    }

    /** Dismiss a row from the personal briefing only; never cancel Android alerts. */
    fun removeFromBrief(keys: Collection<String>) {
        if (keys.isEmpty()) return
        database.writableDatabase.transaction {
            keys.forEach { key ->
                insertWithOnConflict(HIDDEN_TABLE, null, ContentValues().apply {
                    put("notification_key", key.take(MAX_KEY_CHARS))
                    put("dismissed_at", System.currentTimeMillis())
                }, SQLiteDatabase.CONFLICT_REPLACE)
                delete(TABLE, "notification_key=?", arrayOf(key))
            }
            delete(HIDDEN_TABLE, "dismissed_at<?",
                arrayOf((System.currentTimeMillis() - RETENTION_MS).toString()))
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

    private class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            createHiddenTable(db)
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "notification_key TEXT PRIMARY KEY NOT NULL," +
                    "package_name TEXT NOT NULL," +
                    "title TEXT,text TEXT,sub_text TEXT,posted_at INTEGER NOT NULL," +
                    "filter_state TEXT NOT NULL DEFAULT 'RECEIVED'," +
                    "filter_label TEXT,filter_score REAL)",
            )
            db.execSQL("CREATE INDEX notification_history_posted_at ON $TABLE(posted_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createHiddenTable(db)
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN filter_state TEXT NOT NULL DEFAULT 'RECEIVED'")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN filter_label TEXT")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN filter_score REAL")
            }
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
        private const val MAX_RECORDS = 1_000
        private const val RETENTION_DAYS = 7
        private const val HOUR_MS = 60L * 60 * 1_000
        private const val RETENTION_MS = RETENTION_DAYS * 24L * HOUR_MS
        private const val MAX_FIELD_CHARS = 4_000
        private const val MAX_KEY_CHARS = 1_000
        private const val MAX_PACKAGE_CHARS = 255
    }
}
