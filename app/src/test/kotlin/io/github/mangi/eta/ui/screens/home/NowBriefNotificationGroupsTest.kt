package io.github.mangi.eta.ui.screens.home

import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Test

class NowBriefNotificationGroupsTest {
    private fun entry(id: String, pkg: String, time: Long, topic: String = "other") =
        NotificationHistoryRepository.RecentEntry(
            key = id, packageName = pkg,
            title = "Title $id", text = "Body $id", postedAt = time, topic = topic,
        )

    @Test fun notificationsAreGroupedByTopicAcrossAppsAndSortedNewestFirst() {
        val items = listOf(
            entry("a1", "org.alpha", 100, "messages"),
            entry("b1", "org.beta", 300, "promotions"),
            entry("a2", "org.alpha", 500, "messages"),
            entry("c1", "org.charlie", 200, "other"),
        )
        val groups = groupBriefNotifications(items)
        assertEquals(listOf("messages", "promotions", "other"), groups.map { it.category })
        assertEquals(listOf("a2", "a1"), groups.first().entries.map { it.key })
        assertEquals(4, groups.sumOf { it.entries.size })
    }

    @Test fun correctionOverridesModelTopic() {
        val corrected = entry("n", "fr.leboncoin", 20, "promotions").copy(userTopic = "messages")
        assertEquals("messages", groupBriefNotifications(listOf(corrected)).single().category)
    }

    @Test fun stickyMotionStartsResistantAcceleratesAndIsSymmetrical() {
        val width = 400f
        assertEquals(0f, briefStickySwipeOffset(0f, width), 0f)
        val positions = (0..8).map { briefStickySwipeOffset(it * 35f, width) }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        val first = positions[1] - positions[0]
        val last = positions[7] - positions[6]
        assertTrue("Motion should accelerate across the card", last > first * 1.5f)
        assertTrue("Initial movement has resistance", first < 35f)
        positions.forEachIndexed { i, pos ->
            assertEquals(-pos, briefStickySwipeOffset(-i * 35f, width), 0.00001f)
        }
        assertTrue(abs(briefStickySwipeOffset(2000f, width)) <= width * 1.4f)
        assertEquals(0f, briefStickySwipeOffset(Float.NaN, width), 0f)
        assertEquals(0f, briefStickySwipeOffset(10f, 0f), 0f)
    }

    @Test fun emptyFeedHasNoGroupsAndAllEntriesRemainPresent() {
        assertTrue(groupBriefNotifications(emptyList()).isEmpty())
        val groups = groupBriefNotifications((1..100).map { entry("id$it", "org.app", it.toLong()) })
        assertEquals(1, groups.size)
        assertEquals(100, groups.single().entries.size)
    }
}
