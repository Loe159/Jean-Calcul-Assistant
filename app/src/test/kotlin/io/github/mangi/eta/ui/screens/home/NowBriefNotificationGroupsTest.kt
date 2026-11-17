package io.github.mangi.eta.ui.screens.home

import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBriefNotificationGroupsTest {
    private fun entry(id: String, pkg: String, time: Long) =
        NotificationHistoryRepository.RecentEntry(
            key = id, packageName = pkg,
            title = "Title $id", text = "Body $id", postedAt = time,
        )

    @Test fun sameAppIsGroupedAndSortedNewestFirst() {
        val items = listOf(
            entry("a1", "org.alpha", 100),
            entry("b1", "org.beta", 300),
            entry("a2", "org.alpha", 500),
            entry("c1", "org.charlie", 200),
        )
        val groups = groupBriefNotifications(items)
        assertEquals(listOf("org.alpha", "org.beta", "org.charlie"), groups.map { it.packageName })
        assertEquals(listOf("a2", "a1"), groups.first().entries.map { it.key })
        assertEquals(4, groups.sumOf { it.entries.size })
    }

    @Test fun emptyFeedHasNoGroupsAndAllEntriesRemainPresent() {
        assertTrue(groupBriefNotifications(emptyList()).isEmpty())
        val groups = groupBriefNotifications((1..100).map { entry("id$it", "org.app", it.toLong()) })
        assertEquals(1, groups.size)
        assertEquals(100, groups.single().entries.size)
    }
}
