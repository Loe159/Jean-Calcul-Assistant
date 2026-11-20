package io.github.mangi.eta.data.repository

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.NotificationPersonalization
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import org.json.JSONArray
import org.json.JSONObject

/** 仅在用户授予通知访问后保存有限期通知，内容不进入 Agent 会话持久记录。 */
internal class NotificationHistoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = Database(context.applicationContext)

    /**
     * Structural Android metadata kept with the local copy, so the homelab request can
     * be built later, even after the notification left the shade. No business meaning.
     */
    data class StructuralMeta(
        val clearable: Boolean = true,
        val ongoing: Boolean = false,
        val groupSummary: Boolean = false,
        val hasRemoteInput: Boolean = false,
        val conversation: Boolean = false,
        val systemApp: Boolean = false,
        val appCategory: String? = null,
        val appLabel: String? = null,
        val hasProgress: Boolean = false,
    ) {
        fun toJson(): String = JSONObject().put("clearable", clearable).put("ongoing", ongoing)
            .put("groupSummary", groupSummary).put("hasRemoteInput", hasRemoteInput)
            .put("conversation", conversation).put("systemApp", systemApp).put("hasProgress", hasProgress)
            .put("appCategory", appCategory ?: JSONObject.NULL).put("appLabel", appLabel ?: JSONObject.NULL).toString()

        companion object {
            fun fromJson(raw: String?): StructuralMeta = runCatching {
                val o = JSONObject(raw ?: return StructuralMeta())
                StructuralMeta(o.optBoolean("clearable", true), o.optBoolean("ongoing"), o.optBoolean("groupSummary"),
                    o.optBoolean("hasRemoteInput"), o.optBoolean("conversation"), o.optBoolean("systemApp"),
                    o.optString("appCategory").takeIf { !o.isNull("appCategory") && it.isNotBlank() },
                    o.optString("appLabel").takeIf { !o.isNull("appLabel") && it.isNotBlank() },
                    o.optBoolean("hasProgress"))
            }.getOrDefault(StructuralMeta())
        }
    }

    /**
     * Saves the local copy. No classification happens here: when the homelab is enabled,
     * a new or changed notification is queued for Mastra under a new revision.
     * Returns true when the content is new or changed.
     */
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
        meta: StructuralMeta = StructuralMeta(),
        queueForRemote: Boolean = false,
        conversationTitle: String? = null,
        notificationTemplate: String? = null,
    ): Boolean {
        // A silent notification still belongs in the received history.
        if (key.isBlank() || packageName.isBlank()) return false
        var needsAnalysis = true
        val values = ContentValues().apply {
            put("notification_key", key.take(MAX_KEY_CHARS))
            put("package_name", packageName.take(MAX_PACKAGE_CHARS))
            put("title", title.bounded())
            put("conversation_title", conversationTitle.bounded())
            put("text", text.bounded())
            put("sub_text", subText.bounded())
            put("posted_at", postedAt)
            put("channel_id", channelId?.take(150))
            put("android_category", androidCategory?.take(100))
            put("notification_template", notificationTemplate?.take(150))
            put("content_fingerprint", fingerprint)
            put("meta_json", meta.toJson())
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
            val previous = query(TABLE, null, "notification_key=?", arrayOf(key.take(MAX_KEY_CHARS)),
                null, null, null, "1").use { cursor ->
                if (!cursor.moveToFirst()) null
                else (0 until cursor.columnCount).associate { i ->
                    cursor.getColumnName(i) to when (cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        else -> cursor.getString(i)
                    }
                }
            }
            if (previous != null && previous["posted_at"] == postedAt &&
                previous["content_fingerprint"] == fingerprint) {
                // Unchanged notification replayed by Android (e.g. listener reconnect):
                // keep every decision, remote state and Android action state.
                if (previous["filter_state"] in setOf("KEPT", "OBSERVED", "PROPOSED",
                        "ANALYZED_PROTECTED", "SNOOZED_1H", "SNOOZED_24H", "DISMISSED"))
                    needsAnalysis = false
                if (previous["sync_state"] in setOf("QUEUED", "SENT", "DECIDED")) needsAnalysis = false
                for ((column, value) in previous) if (!values.containsKey(column) && column != "archived_at") when (value) {
                    null -> values.putNull(column)
                    is Long -> values.put(column, value)
                    is Double -> values.put(column, value)
                    else -> values.put(column, value.toString())
                }
            } else {
                // New content for this key: the opaque id is kept, the revision increases,
                // so a late decision for older content can never act on the new one.
                values.put("event_id", previous?.get("event_id") as? String
                    ?: io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationClient.opaqueId("n"))
                values.put("revision", ((previous?.get("revision") as? Long) ?: 0L) + 1)
                if (queueForRemote) {
                    values.put("sync_state", SyncState.QUEUED.name)
                    values.put("filter_state", DecisionState.QUEUED.name)
                } else values.put("sync_state", SyncState.LOCAL.name)
                values.put("android_action_state", AndroidActionState.NONE.name)
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

    /** Detect an exact repeated editorial headline across distinct notification keys.
     * Indexed time window, bounded to 60 records and called only for known news contexts.
     * Never count repeated updates to the same Android notification as another headline.
     */
    fun isRepeatedNewsHeadline(key: String, pkg: String, title: String, body: String,
                               postedAt: Long): Boolean {
        val expected = newsHeadline(title, body) ?: return false
        if (key.isBlank() || pkg.isBlank() || postedAt <= 0L) return false
        val from = postedAt - 24L * 60 * 60 * 1000
        return database.readableDatabase.query(TABLE, arrayOf("title", "text"),
            "package_name=? AND notification_key!=? AND posted_at>=? AND posted_at<=?",
            arrayOf(pkg, key.take(MAX_KEY_CHARS), from.toString(), postedAt.toString()),
            null, null, "posted_at DESC", "60",
        ).use { cursor ->
            var repeated = false
            while (cursor.moveToNext()) {
                if (newsHeadline(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty()) == expected) {
                    repeated = true
                    break
                }
            }
            repeated
        }
    }

    private fun newsHeadline(title: String, body: String): String? {
        // News apps often put only the publisher in the Android title field.
        val candidate = if (title.trim().length >= 26) title else body
        val normalized = candidate.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
            .replace(Regex("\\s+"), " ").take(180)
        return normalized.takeIf { it.length >= 26 }
    }

    /** A confirmed Android listener result is distinct from the model's proposal. */
    enum class DecisionState {
        RECEIVED, QUEUED, PROTECTED, ANALYZED_PROTECTED, NO_CONTENT, MODEL_MISSING, BATTERY_PAUSED,
        LIMIT_REACHED, EXPIRED, ANALYSIS_ERROR,
        KEPT, OBSERVED, PROPOSED, SNOOZED_1H, SNOOZED_24H, DISMISSED,
    }

    /** Remote analysis lifecycle, separate from the Android action actually performed. */
    enum class SyncState { LOCAL, QUEUED, SENT, DECIDED, ERROR, REJECTED, EXPIRED }

    /** What really happened on Android. A DISMISS recommendation is not proof of removal. */
    enum class AndroidActionState { NONE, REQUESTED, CONFIRMED, SKIPPED_PROTECTED, SKIPPED_STALE, SKIPPED_NO_CONSENT, FAILED }

    /** One notification waiting for Mastra, with its local Android key (never sent). */
    data class PendingRemote(
        val key: String, val eventId: String, val revision: Int, val packageName: String,
        val title: String?, val text: String?, val subText: String?, val postedAt: Long,
        val channelId: String?, val androidCategory: String?, val fingerprint: String?, val meta: StructuralMeta,
    )

    /** Queued notifications, oldest first. Older than [maxAgeMs] become EXPIRED: kept, never acted upon. */
    fun pendingRemote(limit: Int, maxAgeMs: Long = 24L * HOUR_MS, now: Long = System.currentTimeMillis()): List<PendingRemote> {
        val db = database.writableDatabase
        db.update(TABLE, ContentValues().apply {
            put("sync_state", SyncState.EXPIRED.name)
            put("filter_state", DecisionState.EXPIRED.name)
        }, "sync_state IN ('QUEUED','SENT','ERROR') AND posted_at<?", arrayOf((now - maxAgeMs).toString()))
        return db.query(TABLE, arrayOf("notification_key", "event_id", "revision", "package_name", "title", "text",
            "sub_text", "posted_at", "channel_id", "android_category", "content_fingerprint", "meta_json"),
            "sync_state IN ('QUEUED','SENT','ERROR') AND event_id IS NOT NULL", null, null, null,
            "posted_at ASC", limit.coerceIn(1, 50).toString()).use { c ->
            buildList {
                while (c.moveToNext()) add(PendingRemote(c.getString(0), c.getString(1), c.getInt(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getLong(7), c.getString(8), c.getString(9),
                    c.getString(10), StructuralMeta.fromJson(c.getString(11))))
            }
        }
    }

    fun markRemote(eventIds: Collection<String>, state: SyncState, error: String? = null) {
        if (eventIds.isEmpty()) return
        database.writableDatabase.transaction {
            eventIds.forEach { id ->
                update(TABLE, ContentValues().apply {
                    put("sync_state", state.name)
                    if (state == SyncState.ERROR) put("filter_state", DecisionState.ANALYSIS_ERROR.name)
                    if (error != null) put("filter_reason", error.take(180))
                }, "event_id=? AND sync_state IN ('QUEUED','SENT','ERROR')", arrayOf(id))
            }
        }
    }

    data class RemoteTarget(val key: String, val postedAt: Long, val fingerprint: String?, val packageName: String)

    /** A classified notification can leave Android when its current Now Brief priority is deferred. */
    fun androidDismissalTarget(key: String, postedAt: Long): RemoteTarget? =
        database.readableDatabase.query(
            TABLE, arrayOf("notification_key", "posted_at", "content_fingerprint", "package_name"),
            "notification_key=? AND posted_at=? AND archived_at IS NULL AND sync_state='DECIDED' " +
                "AND android_action_state!='CONFIRMED' AND COALESCE(user_priority, remote_priority) IN ('TODAY','LOW')",
            arrayOf(key.take(MAX_KEY_CHARS), postedAt.toString()), null, null, null, "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) RemoteTarget(cursor.getString(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3))
            else null
        }

    /**
     * Stores a validated Mastra decision for the exact event, revision and content hash.
     * Returns the Android target only when this decision concerns the current local content.
     * A decision for an older revision is ignored: it can never act on newer content.
     */
    fun applyRemoteDecision(d: io.github.mangi.eta.agent.device.notifications.remote.RemoteDecision,
                            expectedContentHash: (PendingRemote) -> String): RemoteTarget? {
        var target: RemoteTarget? = null
        database.writableDatabase.transaction {
            val (row, userPriority) = query(TABLE, arrayOf("notification_key", "event_id", "revision", "package_name", "title", "text",
                "sub_text", "posted_at", "channel_id", "android_category", "content_fingerprint", "meta_json", "user_priority"),
                "event_id=? AND revision=?", arrayOf(d.id, d.revision.toString()), null, null, null, "1").use { c ->
                if (!c.moveToFirst()) null else PendingRemote(c.getString(0), c.getString(1), c.getInt(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getLong(7), c.getString(8), c.getString(9),
                    c.getString(10), StructuralMeta.fromJson(c.getString(11))) to c.getString(12)
            } ?: return@transaction
            if (expectedContentHash(row) != d.contentHash) return@transaction
            val keep = (userPriority ?: d.priority.name) == "NOW"
            update(TABLE, ContentValues().apply {
                put("sync_state", SyncState.DECIDED.name)
                put("remote_topic", d.topic); put("remote_subtype", d.subtype)
                put("remote_priority", d.priority.name); put("remote_interest", d.interest.name)
                put("remote_action", d.recommendedAndroidAction.name); put("remote_reason", d.reason.take(160))
                put("remote_uncertainty", d.uncertainty.name); put("remote_source", d.source.name)
                put("remote_protected", if (d.protected) 1 else 0)
                put("policy_version", d.policyVersion.take(64)); put("model_version", d.modelVersion?.take(96))
                put("actions_json", io.github.mangi.eta.agent.device.notifications.remote.MastraContract.json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        io.github.mangi.eta.agent.device.notifications.remote.ActionProposal.serializer()), d.actions))
                put("decided_at", System.currentTimeMillis())
                put("topic", d.topic)
                put("filter_state", (if (keep) DecisionState.KEPT else DecisionState.PROPOSED).name)
                put("filter_reason", d.reason.take(180))
                put("personal_interest", d.interest.name.takeIf { it != "NEUTRAL" })
            }, "event_id=? AND revision=?", arrayOf(d.id, d.revision.toString()))
            if (!keep) target = RemoteTarget(row.key, row.postedAt, row.fingerprint, row.packageName)
        }
        return target
    }

    /** Records what Android really did (or why nothing was done). */
    fun setAndroidActionState(key: String, postedAt: Long, state: AndroidActionState, reason: String? = null): Boolean {
        val values = ContentValues().apply {
            put("android_action_state", state.name)
            when (state) {
                AndroidActionState.CONFIRMED -> put("filter_state", DecisionState.DISMISSED.name)
                AndroidActionState.SKIPPED_PROTECTED, AndroidActionState.SKIPPED_STALE,
                AndroidActionState.SKIPPED_NO_CONSENT, AndroidActionState.FAILED -> put("filter_state", DecisionState.KEPT.name)
                else -> Unit
            }
            if (reason != null) put("filter_reason", reason.take(180))
        }
        return database.writableDatabase.update(TABLE, values, "notification_key=? AND posted_at=? AND android_action_state!='CONFIRMED'",
            arrayOf(key.take(MAX_KEY_CHARS), postedAt.toString())) == 1
    }

    /** Feedback waiting to be sent to Mastra, in order. */
    data class OutboxItem(val id: String, val payload: String)

    fun pendingFeedback(limit: Int = 100): List<OutboxItem> = database.readableDatabase.query(OUTBOX_TABLE,
        arrayOf("event_id", "payload"), null, null, null, null, "created_at ASC", limit.coerceIn(1, 100).toString()).use { c ->
        buildList { while (c.moveToNext()) add(OutboxItem(c.getString(0), c.getString(1))) }
    }

    fun removeFeedback(ids: Collection<String>) {
        if (ids.isEmpty()) return
        database.writableDatabase.transaction { ids.forEach { delete(OUTBOX_TABLE, "event_id=?", arrayOf(it)) } }
    }

    /** Remote events for one local feedback; nothing is queued for a notification Mastra never saw. */
    fun enqueueRemoteFeedback(key: String, postedAt: Long, type: String, correctedTopic: String? = null,
                              correctedPriority: String? = null, actionType: String? = null): Boolean {
        val row = database.readableDatabase.query(TABLE, arrayOf("event_id", "revision", "package_name", "channel_id",
            "remote_subtype", "sync_state"), "notification_key=? AND posted_at=?",
            arrayOf(key.take(MAX_KEY_CHARS), postedAt.toString()), null, null, null, "1").use { c ->
            if (!c.moveToFirst() || c.isNull(0)) null
            else listOf(c.getString(0), c.getInt(1).toString(), c.getString(2), c.getString(3), c.getString(4), c.getString(5))
        } ?: return false
        if (row[5] !in setOf(SyncState.DECIDED.name, SyncState.SENT.name)) return false
        val eventId = io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationClient.opaqueId("fb")
        val payload = JSONObject().put("eventId", eventId).put("notificationId", row[0]).put("revision", row[1].toInt())
            .put("type", type).put("occurredAt", java.time.OffsetDateTime.now().toString())
            .put("context", JSONObject().put("package", row[2]).put("channelId", row[3] ?: JSONObject.NULL)
                .put("subtype", row[4] ?: JSONObject.NULL))
        correctedTopic?.let { payload.put("correctedTopic", it) }
        correctedPriority?.let { payload.put("correctedPriority", it) }
        actionType?.let { payload.put("actionType", it) }
        database.writableDatabase.insert(OUTBOX_TABLE, null, ContentValues().apply {
            put("event_id", eventId); put("payload", payload.toString()); put("created_at", System.currentTimeMillis())
        })
        database.writableDatabase.execSQL("DELETE FROM $OUTBOX_TABLE WHERE event_id NOT IN " +
            "(SELECT event_id FROM $OUTBOX_TABLE ORDER BY created_at DESC LIMIT 1000)")
        return true
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
        val repeatedNewsHeadline: Boolean = false,
        val syncState: SyncState = SyncState.LOCAL,
        val remotePriority: String? = null,
        val remoteSubtype: String? = null,
        val remoteSource: String? = null,
        val remoteUncertainty: String? = null,
        val remoteProtected: Boolean = false,
        val androidActionState: AndroidActionState = AndroidActionState.NONE,
        val actionsJson: String? = null,
        val userPriority: String? = null,
    ) {
        val effectivePriority: NotificationPriority
            get() = NotificationPriority.effective(userPriority, remotePriority)
    }

    data class ThreadKey(val packageName: String, val title: String)

    data class BriefThread(
        val id: ThreadKey,
        val latest: RecentEntry,
        val count: Int,
        val priority: NotificationPriority,
    )

    data class BriefCounts(val now: Int, val today: Int, val low: Int) {
        val total get() = now + today + low
    }

    data class BriefOverview(val counts: BriefCounts, val threads: List<BriefThread>)

    /** Exact priority totals when the brief does not need the notification preview. */
    fun briefCounts(): BriefCounts {
        val counts = IntArray(NotificationPriority.entries.size)
        val sql = "SELECT $EFFECTIVE_TIER AS tier, COUNT(*) FROM $TABLE WHERE $ACTIVE_BRIEF AND $BRIEF_RELEVANT GROUP BY tier"
        database.readableDatabase.rawQuery(sql, arrayOf(briefCutoff())).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getInt(0)] = cursor.getInt(1)
        }
        return BriefCounts(
            counts[NotificationPriority.NOW.ordinal],
            counts[NotificationPriority.TODAY.ordinal],
            counts[NotificationPriority.LOW.ordinal],
        )
    }

    /** One snapshot for the exact counters and the bounded preview. Only the winning rows carry message content. */
    fun briefOverview(): BriefOverview {
        val sql = """
            WITH active AS (
                SELECT package_name, $THREAD_TITLE AS thread_title, posted_at,
                       $EFFECTIVE_TIER AS tier
                FROM $TABLE WHERE $ACTIVE_BRIEF AND $BRIEF_RELEVANT
            ), priority_counts AS (
                SELECT tier, COUNT(*) AS total FROM active GROUP BY tier
            ), thread_counts AS (
                SELECT package_name, thread_title, COUNT(*) AS thread_count,
                       MIN(tier) AS thread_tier, MAX(posted_at) AS posted_at
                FROM active GROUP BY package_name, thread_title
                HAVING MIN(tier) < ${NotificationPriority.LOW.ordinal}
            ), latest_keys AS (
                SELECT t.*, (
                    SELECT notification_key FROM $TABLE
                    WHERE archived_at IS NULL AND package_name = t.package_name AND $THREAD_TITLE = t.thread_title
                        AND posted_at = t.posted_at AND $BRIEF_RELEVANT
                    ORDER BY notification_key DESC LIMIT 1
                ) AS latest_key
                FROM thread_counts AS t
            ), ranked_threads AS (
                SELECT *, ROW_NUMBER() OVER (
                    PARTITION BY thread_tier ORDER BY posted_at DESC, latest_key DESC
                ) AS tier_rank
                FROM latest_keys
            )
            SELECT $RECENT_COLUMNS, preview.thread_title, preview.thread_count, preview.thread_tier,
                   (SELECT total FROM priority_counts WHERE tier = ${NotificationPriority.NOW.ordinal}) AS now_count,
                   (SELECT total FROM priority_counts WHERE tier = ${NotificationPriority.TODAY.ordinal}) AS today_count,
                   (SELECT total FROM priority_counts WHERE tier = ${NotificationPriority.LOW.ordinal}) AS low_count
            FROM (SELECT 1) AS snapshot
            LEFT JOIN ranked_threads AS preview ON tier_rank <= $BRIEF_THREAD_LIMIT
            LEFT JOIN $TABLE AS n ON n.notification_key = preview.latest_key
            ORDER BY thread_tier, preview.posted_at DESC, preview.latest_key DESC
        """.trimIndent()
        return database.readableDatabase.rawQuery(sql, arrayOf(briefCutoff())).use { cursor ->
            cursor.moveToFirst()
            val counts = BriefCounts(cursor.getInt(28), cursor.getInt(29), cursor.getInt(30))
            val threads = buildList {
                do {
                    if (!cursor.isNull(0)) {
                        val latest = cursor.recentEntry()
                        add(BriefThread(
                            ThreadKey(latest.packageName, cursor.getString(25)),
                            latest,
                            cursor.getInt(26),
                            NotificationPriority.entries[cursor.getInt(27)],
                        ))
                    }
                } while (cursor.moveToNext())
            }
            BriefOverview(counts, threads)
        }
    }

    fun recent(limit: Int = 200): List<RecentEntry> = entries(archived = false, limit = limit)

    /** Stable keyset pagination; filter before LIMIT so sparse priorities still return a full page. */
    fun recentPage(
        limit: Int = 40,
        before: RecentEntry? = null,
        priority: NotificationPriority? = null,
    ): List<RecentEntry> = entries(archived = false, limit = limit.coerceIn(1, 80), before, priority)

    /** Counts for the app headers, including copies outside the currently loaded page. */
    fun briefAppCounts(priority: NotificationPriority?): Map<String, Int> {
        val selection = ACTIVE_BRIEF + priorityClause(priority)
        return database.readableDatabase.query(
            TABLE, arrayOf("package_name", "COUNT(*)"), selection, arrayOf(briefCutoff()),
            "package_name", null, null,
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) }
        }
    }

    /** Copies hidden from Now Brief but still available locally for seven days. */
    fun archived(limit: Int = 200): List<RecentEntry> = entries(archived = true, limit = limit)

    private fun entries(
        archived: Boolean,
        limit: Int,
        before: RecentEntry? = null,
        priority: NotificationPriority? = null,
    ): List<RecentEntry> {
        val selection = (if (archived) "posted_at>=? AND archived_at IS NOT NULL" else ACTIVE_BRIEF) +
            priorityClause(priority) +
            if (before == null) "" else " AND (posted_at, notification_key) < (?, ?)"
        val arguments = arrayOf(briefCutoff()) + if (before == null) emptyArray<String>()
            else arrayOf(before.postedAt.toString(), before.key)
        return database.readableDatabase.rawQuery(
            "SELECT $RECENT_COLUMNS FROM $TABLE AS n WHERE $selection " +
                "ORDER BY posted_at DESC, notification_key DESC LIMIT ${limit.coerceIn(1, MAX_RECORDS)}",
            arguments,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.recentEntry()) }
        }
    }

    private fun Cursor.recentEntry() = RecentEntry(
        key = getString(0),
        packageName = getString(1),
        title = getString(2).orEmpty(),
        text = getString(3).orEmpty().ifBlank { getString(4).orEmpty() },
        postedAt = getLong(5),
        state = runCatching {
            DecisionState.valueOf(getString(6))
        }.getOrDefault(DecisionState.RECEIVED),
        category = getString(7),
        score = if (isNull(8)) null else getDouble(8),
        reason = getString(9),
        channelId = getString(10),
        androidCategory = getString(11),
        // Mastra's topic first; rows recorded before the migration keep their old topic.
        topic = getString(23) ?: getString(12) ?: "other",
        userTopic = getString(13),
        personalInterest = getString(14),
        syncState = runCatching { SyncState.valueOf(getString(15)) }.getOrDefault(SyncState.LOCAL),
        remotePriority = getString(16),
        remoteSubtype = getString(17),
        remoteSource = getString(18),
        remoteUncertainty = getString(19),
        remoteProtected = !isNull(20) && getInt(20) == 1,
        androidActionState = runCatching { AndroidActionState.valueOf(getString(21)) }
            .getOrDefault(AndroidActionState.NONE),
        actionsJson = getString(22),
        userPriority = getString(24),
    )

    internal data class ArchiveEntry(val key: String, val postedAt: Long)
    internal data class ThreadArchive(val token: String, val entries: List<ArchiveEntry>)

    /** Archive local copies without changing their Android notification. */
    fun archiveFromBrief(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val archivedAt = System.currentTimeMillis()
        database.writableDatabase.transaction {
            keys.forEach { key -> archiveCopy(key.take(MAX_KEY_CHARS), archivedAt) }
            pruneArchiveMarkers(archivedAt)
        }
        NotificationActivityFeed.notifyChanged()
    }

    fun archiveBriefEntries(entries: Collection<RecentEntry>): List<ArchiveEntry> {
        if (entries.isEmpty()) return emptyList()
        val archivedAt = System.currentTimeMillis()
        val archived = buildList {
            database.writableDatabase.transaction {
                entries.forEach { entry ->
                    if (archiveCopy(entry.key, archivedAt, postedAt = entry.postedAt)) {
                        add(ArchiveEntry(entry.key, entry.postedAt))
                    }
                }
                pruneArchiveMarkers(archivedAt)
            }
        }
        if (archived.isNotEmpty()) NotificationActivityFeed.notifyChanged()
        return archived
    }

    fun archiveBriefThread(thread: ThreadKey): ThreadArchive {
        val token = java.util.UUID.randomUUID().toString()
        val archivedAt = System.currentTimeMillis()
        val members = buildList {
            database.writableDatabase.transaction {
                query(
                    TABLE, arrayOf("notification_key", "posted_at"),
                    "$ACTIVE_BRIEF AND $BRIEF_RELEVANT AND package_name=? AND $THREAD_TITLE=?",
                    arrayOf(briefCutoff(), thread.packageName, thread.title), null, null, null,
                ).use { cursor ->
                    while (cursor.moveToNext()) add(ArchiveEntry(cursor.getString(0), cursor.getLong(1)))
                }
                forEach { archiveCopy(it.key, archivedAt, token) }
                pruneArchiveMarkers(archivedAt)
            }
        }
        if (members.isNotEmpty()) NotificationActivityFeed.notifyChanged()
        return ThreadArchive(token, members)
    }

    private fun SQLiteDatabase.archiveCopy(key: String, time: Long, token: String? = null, postedAt: Long? = null): Boolean {
        val selection = "notification_key=? AND archived_at IS NULL" + if (postedAt == null) "" else " AND posted_at=?"
        val args = if (postedAt == null) arrayOf(key) else arrayOf(key, postedAt.toString())
        val changed = update(TABLE, ContentValues().apply {
            put("archived_at", time)
            put("archive_token", token)
        }, selection, args)
        if (changed == 1) {
            insertWithOnConflict(HIDDEN_TABLE, null, ContentValues().apply {
                put("notification_key", key)
                put("dismissed_at", time)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        return changed == 1
    }

    private fun SQLiteDatabase.pruneArchiveMarkers(now: Long) {
        delete(HIDDEN_TABLE, "dismissed_at<?", arrayOf((now - RETENTION_MS).toString()))
    }

    fun undoBriefThreadArchive(archive: ThreadArchive) =
        restoreCopies(archive.entries, archive.token)

    /** Restore archived copies to Now Brief without touching Android alerts. */
    fun restoreToBrief(entries: Collection<RecentEntry>) =
        restoreCopies(entries.map { ArchiveEntry(it.key.take(MAX_KEY_CHARS), it.postedAt) })

    private fun restoreCopies(entries: Collection<ArchiveEntry>, token: String? = null) {
        if (entries.isEmpty()) return
        var restored = false
        val cutoff = briefCutoff()
        val batchCondition = if (token == null) "" else " AND archive_token=?"
        database.writableDatabase.transaction {
            entries.forEach { entry ->
                val args = arrayOf(entry.key, entry.postedAt.toString(), cutoff) +
                    if (token == null) emptyArray() else arrayOf(token)
                val changed = update(TABLE, ContentValues().apply {
                    putNull("archived_at")
                    putNull("archive_token")
                }, "notification_key=? AND posted_at=? AND posted_at>=? AND archived_at IS NOT NULL$batchCondition", args)
                if (changed == 1) {
                    delete(HIDDEN_TABLE, "notification_key=?", arrayOf(entry.key))
                    restored = true
                }
            }
        }
        if (restored) NotificationActivityFeed.notifyChanged()
    }

    /** Explicit and implicit human events only; duplicate delivery cannot train twice. */
    fun feedback(key: String, postedAt: Long, kind: NotificationUserFeedback,
                 correctedTopic: String? = null, correctedPriority: String? = null,
                 actionType: String? = null): Boolean {
        if (correctedTopic != null && correctedTopic !in NotificationTopics.titles) return false
        if (correctedPriority != null && NotificationPriority.entries.none { it.name == correctedPriority }) return false
        var recorded = false
        database.writableDatabase.transaction {
            val row = query(TABLE, arrayOf("package_name", "channel_id", "filter_label", "title", "text"),
                "notification_key=? AND posted_at=?", arrayOf(key, postedAt.toString()), null, null, null)
                .use { c -> if (c.moveToFirst()) listOf(c.getString(0), c.getString(1),
                    c.getString(2), c.getString(3), c.getString(4)) else null } ?: return@transaction
            val featureList = NotificationPersonalization.features(row[0].orEmpty(), row[1], row[2],
                "${row[3].orEmpty()} ${row[4].orEmpty()}")
            // Corrections are deduplicated by (key, post time, kind, topic).
            val id = "$key:$postedAt:${kind.name}:${correctedTopic.orEmpty()}${correctedPriority.orEmpty()}${actionType.orEmpty()}"
            val added = insertWithOnConflict(FEEDBACK_TABLE, null, ContentValues().apply {
                put("event_id", id.take(1200)); put("event_type", kind.name)
                put("created_at", System.currentTimeMillis()); put("model_version", NotificationPersonalization.VERSION)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            // Revisiting a prior correction still changes the current preference.
            if (correctedPriority != null) {
                update(TABLE, ContentValues().apply { put("user_priority", correctedPriority) },
                    "notification_key=? AND posted_at=?", arrayOf(key, postedAt.toString()))
            }
            if (correctedTopic != null) {
                update(TABLE, ContentValues().apply { put("user_topic", correctedTopic) },
                    "notification_key=? AND posted_at=?", arrayOf(key, postedAt.toString()))
            }
            if (added == -1L) return@transaction
            recorded = true
            if (kind in setOf(NotificationUserFeedback.INTERESTING, NotificationUserFeedback.UNWANTED,
                    NotificationUserFeedback.WRONG_REMOVAL)) {
                update(TABLE, ContentValues().apply { put("personal_interest", if (kind.wanted == true) "WANTED" else "UNWANTED") },
                    "notification_key=? AND posted_at=?", arrayOf(key, postedAt.toString()))
            }
            // The homelab learns from the same events, when it saw this notification.
            enqueueRemoteFeedback(key, postedAt, kind.remoteType, correctedTopic, correctedPriority, actionType)
            for (feature in featureList.distinct()) {
                if (kind.wanted != null) {
                    execSQL("INSERT INTO $LEARNING_TABLE(feature,wanted,unwanted,examples) VALUES(?,?,?,1) " +
                        "ON CONFLICT(feature) DO UPDATE SET wanted=wanted+excluded.wanted, " +
                        "unwanted=unwanted+excluded.unwanted,examples=examples+1",
                        arrayOf<Any>(feature, if (kind.wanted == true) kind.weight else 0.0,
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
            execSQL("UPDATE $TABLE SET user_topic=NULL, user_priority=NULL, personal_interest=NULL")
            delete(OUTBOX_TABLE, null, null)
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

    private class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, 9) {
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
            addRemoteColumns(db)
            addBriefColumns(db)
            addNotificationTemplate(db)
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
            if (oldVersion < 7) addRemoteColumns(db)
            if (oldVersion < 8) addBriefColumns(db)
            if (oldVersion < 9) addNotificationTemplate(db)
        }

        private fun addNotificationTemplate(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN notification_template TEXT")
        }

        private fun addBriefColumns(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN conversation_title TEXT")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN archive_token TEXT")
            db.execSQL("CREATE INDEX notification_history_brief_time ON $TABLE" +
                "(posted_at DESC, notification_key DESC) WHERE archived_at IS NULL")
            db.execSQL("CREATE INDEX notification_history_brief_thread ON $TABLE" +
                "(package_name, $THREAD_TITLE, posted_at DESC, notification_key DESC) WHERE archived_at IS NULL")
        }

        /** v7: Mastra decisions and Android action states. Existing history and preferences are kept. */
        private fun addRemoteColumns(db: SQLiteDatabase) {
            listOf("event_id TEXT", "revision INTEGER NOT NULL DEFAULT 0", "sync_state TEXT NOT NULL DEFAULT 'LOCAL'",
                "remote_topic TEXT", "remote_subtype TEXT", "remote_priority TEXT", "remote_interest TEXT",
                "remote_action TEXT", "remote_reason TEXT", "remote_uncertainty TEXT", "remote_source TEXT",
                "remote_protected INTEGER", "policy_version TEXT", "model_version TEXT", "actions_json TEXT",
                "decided_at INTEGER", "android_action_state TEXT NOT NULL DEFAULT 'NONE'", "meta_json TEXT", "user_priority TEXT")
                .forEach { db.execSQL("ALTER TABLE $TABLE ADD COLUMN $it") }
            db.execSQL("CREATE INDEX IF NOT EXISTS notification_history_event ON $TABLE(event_id, revision)")
            db.execSQL("CREATE INDEX IF NOT EXISTS notification_history_sync ON $TABLE(sync_state, posted_at)")
            db.execSQL("CREATE TABLE IF NOT EXISTS $OUTBOX_TABLE (event_id TEXT PRIMARY KEY, payload TEXT NOT NULL, created_at INTEGER NOT NULL)")
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
        private const val OUTBOX_TABLE = "notification_remote_feedback_outbox"
        internal const val BRIEF_THREAD_LIMIT = 12
        private const val ACTIVE_BRIEF = "archived_at IS NULL AND posted_at>=?"
        // Existing rows already hold category, ongoing and clearable. The template
        // and progress extras are refreshed on the next notification or listener replay.
        private val BRIEF_RELEVANT = """
            COALESCE(android_category, '') NOT IN ('${Notification.CATEGORY_TRANSPORT}', '${Notification.CATEGORY_PROGRESS}')
            AND COALESCE(notification_template, '') NOT IN (
                '${Notification.MediaStyle::class.java.name}', '${Notification.DecoratedMediaCustomViewStyle::class.java.name}',
                'android.app.Notification${'$'}ProgressStyle'
            )
            AND CASE WHEN json_valid(meta_json) THEN
                COALESCE(json_extract(meta_json, '$.hasProgress'), 0) = 0
                AND (
                    COALESCE(json_extract(meta_json, '$.ongoing'), 0) = 0
                    OR COALESCE(json_extract(meta_json, '$.clearable'), 1) = 1
                    OR android_category IN ('${Notification.CATEGORY_CALL}', '${Notification.CATEGORY_ALARM}',
                        '${Notification.CATEGORY_NAVIGATION}', '${Notification.CATEGORY_LOCATION_SHARING}')
                )
            ELSE 1 END
        """.trimIndent()
        private const val THREAD_TITLE = "COALESCE(NULLIF(TRIM(conversation_title), ''), TRIM(COALESCE(title, '')))"
        private val EFFECTIVE_TIER = NotificationPriority.entries.joinToString(
            separator = " ",
            prefix = "CASE COALESCE(user_priority, remote_priority) ",
            postfix = " ELSE ${NotificationPriority.default.ordinal} END",
        ) { "WHEN '${it.name}' THEN ${it.ordinal}" }

        private val RECENT_COLUMNS = listOf(
            "notification_key", "package_name", "title", "text", "sub_text",
            "posted_at", "filter_state", "filter_label", "filter_score", "filter_reason",
            "channel_id", "android_category", "topic", "user_topic", "personal_interest",
            "sync_state", "remote_priority", "remote_subtype", "remote_source", "remote_uncertainty",
            "remote_protected", "android_action_state", "actions_json", "remote_topic", "user_priority",
        ).joinToString { "n.$it" }

        private fun briefCutoff() = (System.currentTimeMillis() - RETENTION_MS).toString()
        private fun priorityClause(priority: NotificationPriority?) =
            if (priority == null) "" else " AND ($EFFECTIVE_TIER)=${priority.ordinal}"

        private const val MAX_RECORDS = 1_000
        private const val RETENTION_DAYS = 7
        private const val HOUR_MS = 60L * 60 * 1_000
        private const val RETENTION_MS = RETENTION_DAYS * 24L * HOUR_MS
        private const val MAX_FIELD_CHARS = 4_000
        private const val MAX_KEY_CHARS = 1_000
        private const val MAX_PACKAGE_CHARS = 255
    }
}
