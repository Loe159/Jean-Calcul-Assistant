package io.github.mangi.eta.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationPriority.LOW
import io.github.mangi.eta.agent.device.notifications.NotificationPriority.NOW
import io.github.mangi.eta.agent.device.notifications.NotificationPriority.TODAY
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.BriefCounts
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.ThreadKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationBriefOverviewTest {
    private lateinit var context: Context
    private lateinit var repository: NotificationHistoryRepository

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DatabaseName)
        repository = NotificationHistoryRepository(context)
    }

    @After fun tearDown() {
        context.deleteDatabase(DatabaseName)
    }

    @Test fun countsAndBoundedThreadsMatchFiveThousandSyntheticNotifications() {
        val now = System.currentTimeMillis()
        val fixture = (0 until 5000).map { index ->
            val group = index / 20
            Row(
                key = "n%04d".format(index),
                packageName = "org.synthetic.app${group % 5}",
                thread = "Fil ${group / 5}",
                postedAt = now - 60_000 + index / 2,
                remote = when (group % 4) {
                    0 -> "NOW"
                    1 -> "TODAY"
                    2 -> "LOW"
                    else -> null
                },
                user = when {
                    index % 89 == 0 -> "LOW"
                    index % 143 == 0 -> "NOW"
                    index % 111 == 0 -> "UNKNOWN"
                    else -> null
                },
                archived = index % 53 == 0,
            )
        }
        assertEquals(BriefCounts(0, 0, 0), repository.briefOverview().counts)
        // Seed the database directly to stress the query above the app's retention cap.
        openDatabase().use { database ->
            database.beginTransaction()
            try {
                database.compileStatement(
                    """INSERT INTO notification_history
                       (notification_key, package_name, title, conversation_title, text, posted_at,
                        remote_priority, user_priority, archived_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ).use { statement ->
                    fixture.forEach { row ->
                        statement.bindString(1, row.key)
                        statement.bindString(2, row.packageName)
                        statement.bindString(3, "Expéditeur ${row.key}")
                        statement.bindString(4, row.thread)
                        statement.bindString(5, "Message ${row.key}")
                        statement.bindLong(6, row.postedAt)
                        if (row.remote == null) statement.bindNull(7) else statement.bindString(7, row.remote)
                        if (row.user == null) statement.bindNull(8) else statement.bindString(8, row.user)
                        if (row.archived) statement.bindLong(9, now) else statement.bindNull(9)
                        statement.executeInsert()
                    }
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
        val active = fixture.filterNot { it.archived }
        val expectedThreads = active.groupBy { it.packageName to it.thread }.values.map { rows ->
            Triple(rows.maxWith(compareBy<Row> { it.postedAt }.thenBy { it.key }), rows.size, rows.minOf { it.priority })
        }.sortedWith(compareBy<Triple<Row, Int, NotificationPriority>> { it.third }
            .thenByDescending { it.first.postedAt }.thenByDescending { it.first.key })
        val overview = repository.briefOverview()
        assertEquals(BriefCounts(active.count { it.priority == NOW }, active.count { it.priority == TODAY },
            active.count { it.priority == LOW }), overview.counts)
        assertEquals(24, overview.threads.size)
        val expectedPreview = listOf(NOW, TODAY).flatMap { tier ->
            expectedThreads.filter { it.third == tier }.take(12)
        }
        assertEquals(expectedPreview.map { (row, _, _) -> ThreadKey(row.packageName, row.thread) }, overview.threads.map { it.id })
        assertEquals(expectedPreview.map { it.first.key }, overview.threads.map { it.latest.key })
        assertEquals(expectedPreview.map { it.first.postedAt }, overview.threads.map { it.latest.postedAt })
        assertEquals(expectedPreview.map { it.second }, overview.threads.map { it.count })
        assertEquals(expectedPreview.map { it.third }, overview.threads.map { it.priority })
        assertEquals(expectedPreview.map { "Message ${it.first.key}" }, overview.threads.map { it.latest.text })

        val sortedNow = active.filter { it.priority == NOW }
            .sortedWith(compareByDescending<Row> { it.postedAt }.thenByDescending { it.key })
        val first = repository.recentPage(priority = NOW)
        val next = repository.recentPage(before = first.last(), priority = NOW)
        assertEquals(sortedNow.take(80).map { it.key }, (first + next).map { it.key })
        assertEquals(sortedNow.groupingBy { it.packageName }.eachCount(), repository.briefAppCounts(NOW))
        val elapsed = measureTimeMillis { repeat(5) { repository.briefOverview() } }
        println("Five aggregate reads over 5,000 synthetic notifications took ${elapsed}ms")
        assertTrue("Five warm aggregate reads took ${elapsed}ms", elapsed < 5_000)
    }

    @Test fun archiveAndUndoAffectTheExactThreadIncludingEntriesOutsideTheFirstPage() {
        val now = System.currentTimeMillis()
        repeat(45) { index ->
            repository.record(
                "msg-$index", "org.synthetic", "Sender $index", "Synthetic message", null, now - index,
                conversationTitle = " Salon ",
            )
        }
        repository.record("legacy", "org.synthetic", "Salon", "", null, now + 1)
        repository.record("other-app", "org.other", "Salon", "", null, now + 2)
        repository.record("other-thread", "org.synthetic", "Autre", "", null, now + 3)
        repository.record("expired", "org.synthetic", "Salon", "", null, now - 1_000)
        openDatabase().use { db ->
            db.execSQL("UPDATE notification_history SET posted_at=1 WHERE notification_key='expired'")
        }
        repository.archiveFromBrief(listOf("msg-44"))

        val thread = ThreadKey("org.synthetic", "Salon")
        val preview = repository.briefOverview().threads.single { it.id == thread }
        assertEquals("legacy", preview.latest.key)
        assertEquals(45, preview.count)
        val batch = repository.archiveBriefThread(thread)
        assertEquals(45, batch.entries.size)
        assertEquals(setOf("other-app", "other-thread"), repository.recent().map { it.key }.toSet())

        repository.undoBriefThreadArchive(batch)
        assertEquals(listOf("msg-44"), repository.archived().map { it.key })
        assertEquals(47, repository.briefOverview().counts.total)
        val newerBatch = repository.archiveBriefThread(thread)
        repository.undoBriefThreadArchive(batch)
        assertEquals(2, repository.briefOverview().counts.total)
        repository.undoBriefThreadArchive(newerBatch)
        assertEquals(47, repository.briefOverview().counts.total)
    }

    @Test fun v7HistoryKeepsItsTitlePriorityAndArchivesAfterMigration() {
        val now = System.currentTimeMillis()
        repository.record("visible", "org.synthetic", "Titre historique", "Message", null, now)
        repository.record("archived", "org.synthetic", "Archive historique", "", null, now)
        repository.archiveFromBrief(listOf("archived"))
        openDatabase().use { db ->
            db.execSQL("UPDATE notification_history SET user_priority='NOW' WHERE notification_key='visible'")
            db.execSQL("DROP INDEX notification_history_brief_time")
            db.execSQL("DROP INDEX notification_history_brief_thread")
            db.execSQL("ALTER TABLE notification_history DROP COLUMN conversation_title")
            db.execSQL("ALTER TABLE notification_history DROP COLUMN archive_token")
            db.version = 7
        }

        repository = NotificationHistoryRepository(context)
        val overview = repository.briefOverview()
        assertEquals(BriefCounts(1, 0, 0), overview.counts)
        assertEquals(ThreadKey("org.synthetic", "Titre historique"), overview.threads.single().id)
        assertEquals("archived", repository.archived().single().key)
    }

    private fun openDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(context.getDatabasePath(DatabaseName).path, null, SQLiteDatabase.OPEN_READWRITE)

    private data class Row(
        val key: String,
        val packageName: String,
        val thread: String,
        val postedAt: Long,
        val remote: String?,
        val user: String?,
        val archived: Boolean,
    ) {
        val priority get() = NotificationPriority.effective(user, remote)
    }

    private companion object {
        const val DatabaseName = "eta_notification_history.db"
    }
}
