package io.github.mangi.eta.data.repository

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationHistoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: NotificationHistoryRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("eta_notification_history.db")
        repository = NotificationHistoryRepository(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase("eta_notification_history.db")
    }

    @Test
    fun `user corrections persist across repository instances without duplicate learning`() {
        val time = System.currentTimeMillis()
        repository.record("one", "fr.leboncoin", "Offres", "Une offre", null, time,
            channelId = "marketing", fingerprint = "snapshot-1")
        repository.updateDecision("one", time, NotificationHistoryRepository.DecisionState.KEPT,
            "a promotional advertisement", 0.85, topic = "promotions")
        assertEquals(true, repository.feedback("one", time,
            io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback.CATEGORY_CORRECTED,
            "shopping"))
        assertEquals(false, repository.feedback("one", time,
            io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback.CATEGORY_CORRECTED,
            "shopping"))
        assertEquals("shopping", NotificationHistoryRepository(context).recent().single().userTopic)
        assertEquals(true, repository.feedback("one", time,
            io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback.UNWANTED))
        assertEquals(false, repository.feedback("one", time,
            io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback.UNWANTED))
        assertEquals("UNWANTED", NotificationHistoryRepository(context).recent().single().personalInterest)
        repository.resetLearning()
        assertEquals(null, NotificationHistoryRepository(context).recent().single().userTopic)
        assertEquals("Aucun apprentissage enregistré", repository.feedbackStats())
    }

    @Test
    fun `unchanged classified notification is not inferred a second time`() {
        val time = System.currentTimeMillis()
        assertEquals(true, repository.record("same", "fr.leboncoin", "Promo", "-20%", null, time,
            fingerprint = "abc"))
        repository.updateDecision("same", time, NotificationHistoryRepository.DecisionState.KEPT, "a promotional advertisement", .91)
        assertEquals(false, repository.record("same", "fr.leboncoin", "Promo", "-20%", null, time,
            fingerprint = "abc"))
        assertEquals(NotificationHistoryRepository.DecisionState.KEPT, repository.recent().single().state)
        assertEquals(true, repository.record("same", "fr.leboncoin", "Promo", "-30%", null, time,
            fingerprint = "xyz"))
        assertEquals(NotificationHistoryRepository.DecisionState.RECEIVED, repository.recent().single().state)
    }

    @Test
    fun `brief pagination returns each notification once even with identical timestamps`() {
        val now = System.currentTimeMillis()
        repeat(105) { index ->
            repository.record("page-$index", "org.example.app", "Title $index", null, null, now - index / 3)
        }
        val pages = mutableListOf<NotificationHistoryRepository.RecentEntry>()
        var cursor: NotificationHistoryRepository.RecentEntry? = null
        repeat(4) {
            val page = repository.recentPage(40, cursor)
            pages += page
            cursor = page.lastOrNull()
        }
        assertEquals(105, pages.size)
        assertEquals(105, pages.map { it.key }.distinct().size)
        assertEquals(pages, pages.sortedWith(
            compareByDescending<NotificationHistoryRepository.RecentEntry> { it.postedAt }
                .thenByDescending { it.key }))
    }

    @Test
    fun `game notifications are categorized even before onnx inference and historical rows stay visible`() {
        val now = System.currentTimeMillis()
        repository.record("brawl-quest", "com.supercell.brawlstars", "Brawl Stars",
            "De nouvelles quêtes sont disponibles !", null, now)
        val row = repository.recent().first { it.key == "brawl-quest" }
        assertEquals("games", row.topic)
        assertEquals(NotificationHistoryRepository.DecisionState.RECEIVED, row.state)
        assertEquals(true, repository.feedback("brawl-quest", now,
            io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback.CATEGORY_CORRECTED, "other"))
        assertEquals("other", repository.recent().first { it.key == "brawl-quest" }.userTopic)
    }

    @Test
    fun `query and package filters only return matching notification`() {
        val now = System.currentTimeMillis()
        repository.record("one", "com.example.food", "订单配送中", "骑手即将送达", null, now)
        repository.record("two", "com.example.chat", "新消息", "今晚见", null, now - 1)

        val result = JSONObject(repository.search("骑手", "com.example.food", 24, 20))

        assertEquals(1, result.getInt("count"))
        assertEquals("com.example.food", result.getJSONArray("items").getJSONObject(0).getString("package_name"))
    }

    @Test
    fun `brief history archives only selected rows and keeps them retrievable`() {
        val now = System.currentTimeMillis()
        repository.record("n1", "com.example.chat", "Alerte", "Message privé", null, now)
        repository.record("n2", "com.example.chat", "Nouveau", "Autre message", null, now - 10)
        repository.record("n3", "com.example.calendar", "Agenda", "Événement", null, now - 20)
        repository.record("silent", "com.example.system", null, null, null, now - 30)
        val entries = repository.recent(100)
        assertEquals(4, entries.size)
        assertEquals(listOf("n1", "n2", "n3", "silent"), entries.map { it.key })
        repository.archiveFromBrief(listOf("n2"))
        // Listener reconnect may re-post currently active notifications.
        // A dismissed key must not silently reappear in Now Brief.
        repository.record("n2", "com.example.chat", "Nouveau", "Autre message", null, now)
        assertEquals(listOf("n1", "n3", "silent"), repository.recent(100).map { it.key })
        assertEquals(listOf("n2"), repository.archived(100).map { it.key })
        assertEquals("Autre message", repository.archived(100).single().text)
        // Archiving a briefing entry doesn't change classifier counters.
        assertEquals(3, repository.recent(100).size)
    }

    @Test
    fun `undo restores only archived local copies with their decision details`() {
        val now = System.currentTimeMillis()
        repository.record("ad", "com.example.shop", "Promotion", "Réduction", null, now)
        repository.updateDecision("ad", now,
            NotificationHistoryRepository.DecisionState.DISMISSED,
            "a promotional advertisement", 0.8, "Retrait confirmé")
        val original = repository.recent().single()
        repository.archiveFromBrief(listOf("ad"))
        assertEquals(emptyList<NotificationHistoryRepository.RecentEntry>(), repository.recent())
        assertEquals(listOf(original), repository.archived())

        repository.restoreToBrief(listOf(original))
        val restored = repository.recent().single()
        assertEquals(original, restored)
        assertEquals(emptyList<NotificationHistoryRepository.RecentEntry>(), repository.archived())
        // Repeated Undo cannot overwrite a later notification with the same key.
        repository.record("ad", "com.example.shop", "Nouvelle offre", null, null, now + 1)
        repository.restoreToBrief(listOf(original))
        assertEquals("Nouvelle offre", repository.recent().single().title)
    }

    @Test
    fun `version four history remains visible after archive column migration`() {
        val now = System.currentTimeMillis()
        context.openOrCreateDatabase("eta_notification_history.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE notification_history (" +
                "notification_key TEXT PRIMARY KEY NOT NULL,package_name TEXT NOT NULL," +
                "title TEXT,text TEXT,sub_text TEXT,posted_at INTEGER NOT NULL," +
                "filter_state TEXT NOT NULL DEFAULT 'RECEIVED'," +
                "filter_label TEXT,filter_score REAL,filter_reason TEXT)")
            db.execSQL("CREATE TABLE dismissed_from_brief (" +
                "notification_key TEXT PRIMARY KEY NOT NULL,dismissed_at INTEGER NOT NULL)")
            db.execSQL("INSERT INTO notification_history " +
                "(notification_key,package_name,title,text,posted_at) VALUES (?,?,?,?,?)",
                arrayOf<Any>("old", "com.example.app", "Ancienne alerte", "Encore visible", now))
            db.version = 4
        }

        assertEquals("Ancienne alerte", repository.recent().single().title)
        repository.archiveFromBrief(listOf("old"))
        assertEquals("Ancienne alerte", repository.archived().single().title)
    }

    @Test
    fun `queued and skipped statuses are local and can transition to a confirmed decision`() {
        val now = System.currentTimeMillis()
        repository.record("queue-test", "com.example.store",
            "Promotion", "Une offre locale", null, now)
        assertEquals(true, repository.updateDecision("queue-test", now,
            NotificationHistoryRepository.DecisionState.QUEUED))
        assertEquals(NotificationHistoryRepository.DecisionState.QUEUED,
            repository.recent().single().state)
        assertEquals(true, repository.updateDecision("queue-test", now,
            NotificationHistoryRepository.DecisionState.KEPT,
            "a personal message", 0.93))
        assertEquals(NotificationHistoryRepository.DecisionState.KEPT,
            repository.recent().single().state)
        assertEquals(false, repository.updateDecision("queue-test", now + 1,
            NotificationHistoryRepository.DecisionState.DISMISSED))
        assertEquals(NotificationHistoryRepository.DecisionState.KEPT,
            repository.recent().single().state)
        repository.record("system", "com.android.systemui",
            "Alerte protégée", "Une alerte système", null, now - 1)
        assertEquals(true, repository.updateDecision("system", now - 1,
            NotificationHistoryRepository.DecisionState.PROTECTED))
        assertEquals(NotificationHistoryRepository.DecisionState.PROTECTED,
            repository.recent().last().state)
    }

    @Test
    fun `model proposal and Android confirmed removal remain in local history`() {
        val now = System.currentTimeMillis()
        repository.record("ad-1", "com.example.shop", "Offre -70 %", "Ventes flash", null, now)
        assertEquals(NotificationHistoryRepository.DecisionState.RECEIVED,
            repository.recent().single().state)
        assertFalse(repository.updateDecision("ad-1", now + 1,
            NotificationHistoryRepository.DecisionState.DISMISSED))
        assertEquals(NotificationHistoryRepository.DecisionState.RECEIVED,
            repository.recent().single().state)

        assertEquals(true, repository.updateDecision("ad-1", now,
            NotificationHistoryRepository.DecisionState.PROPOSED, "a promotional advertisement", 0.999))
        assertEquals(NotificationHistoryRepository.DecisionState.PROPOSED,
            repository.recent().single().state)

        assertEquals(true, repository.updateDecision("ad-1", now,
            NotificationHistoryRepository.DecisionState.DISMISSED))
        val removed = repository.recent().single()
        assertEquals(NotificationHistoryRepository.DecisionState.DISMISSED, removed.state)
        assertEquals("Offre -70 %", removed.title)
        assertEquals("Ventes flash", removed.text)
        assertEquals("a promotional advertisement", removed.category)
        assertEquals(0.999, removed.score!!, 0.00001)
        // A reconnect re-records still-active notifications. Keep classification.
        repository.record("ad-1", "com.example.shop", "Offre -70 %", "Ventes flash", null, now)
        assertEquals(NotificationHistoryRepository.DecisionState.DISMISSED,
            repository.recent().single().state)
        // But a newly posted update with a different post time gets a fresh state.
        repository.record("ad-1", "com.example.shop", "New promotion", "Another sale", null, now + 200)
        assertEquals(NotificationHistoryRepository.DecisionState.RECEIVED,
            repository.recent().single().state)
    }

    @Test
    fun `reason persists after Android cancellation and listener reconnect`() {
        val now = System.currentTimeMillis()
        repository.record("reason-test", "fr.leboncoin", "Offre spéciale", "Offre promotionnelle", null, now)
        assertEquals(true, repository.updateDecision("reason-test", now,
            NotificationHistoryRepository.DecisionState.KEPT,
            "a promotional advertisement", 0.448, "Écart insuffisant : 2,6 < 8 points"))
        assertEquals("Écart insuffisant : 2,6 < 8 points", repository.recent().single().reason)
        repository.record("reason-test", "fr.leboncoin", "Offre spéciale", "Offre promotionnelle", null, now)
        assertEquals("Écart insuffisant : 2,6 < 8 points", repository.recent().single().reason)
        assertEquals(true, repository.updateDecision("reason-test", now,
            NotificationHistoryRepository.DecisionState.DISMISSED))
        assertEquals("Écart insuffisant : 2,6 < 8 points", repository.recent().single().reason)
    }

    @Test
    fun `system filtering outcomes map to distinct dashboard states`() {
        val repo = NotificationHistoryRepository
        assertEquals(NotificationHistoryRepository.DecisionState.DISMISSED,
            repo.confirmedState(io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision.DISMISS))
        assertEquals(NotificationHistoryRepository.DecisionState.SNOOZED_1H,
            repo.confirmedState(io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision.SNOOZE_1H))
        assertEquals(null,
            repo.confirmedState(io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision.KEEP))
    }

    @Test
    fun `same notification key replaces prior content and expired records are excluded`() {
        val now = System.currentTimeMillis()
        repository.record("same", "com.example.food", "旧状态", null, null, now - 1)
        repository.record("same", "com.example.food", "已送达", null, null, now)
        repository.record("expired", "com.example.food", "很久以前", null, null, now - 8L * 24 * 60 * 60 * 1_000)

        val serialized = repository.search("", "", 168, 20)
        val result = JSONObject(serialized)

        assertEquals(1, result.getInt("count"))
        assertFalse(serialized.contains("旧状态"))
        assertFalse(serialized.contains("很久以前"))
    }
}
