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
    fun `query and package filters only return matching notification`() {
        val now = System.currentTimeMillis()
        repository.record("one", "com.example.food", "订单配送中", "骑手即将送达", null, now)
        repository.record("two", "com.example.chat", "新消息", "今晚见", null, now - 1)

        val result = JSONObject(repository.search("骑手", "com.example.food", 24, 20))

        assertEquals(1, result.getInt("count"))
        assertEquals("com.example.food", result.getJSONArray("items").getJSONObject(0).getString("package_name"))
    }

    @Test
    fun `brief history includes all received apps and dismisses only selected rows`() {
        val now = System.currentTimeMillis()
        repository.record("n1", "com.example.chat", "Alerte", "Message privé", null, now)
        repository.record("n2", "com.example.chat", "Nouveau", "Autre message", null, now - 10)
        repository.record("n3", "com.example.calendar", "Agenda", "Événement", null, now - 20)
        repository.record("silent", "com.example.system", null, null, null, now - 30)
        val entries = repository.recent(100)
        assertEquals(4, entries.size)
        assertEquals(listOf("n1", "n2", "n3", "silent"), entries.map { it.key })
        repository.removeFromBrief(listOf("n2"))
        // Listener reconnect may re-post currently active notifications.
        // A dismissed key must not silently reappear in Now Brief.
        repository.record("n2", "com.example.chat", "Nouveau", "Autre message", null, now)
        assertEquals(listOf("n1", "n3", "silent"), repository.recent(100).map { it.key })
        // Deleting a briefing entry doesn't change classifier counters.
        assertEquals(3, repository.recent(100).size)
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
