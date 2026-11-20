package io.github.mangi.eta.ui.screens.home.brief.cards

import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationPriority.LOW
import io.github.mangi.eta.agent.device.notifications.NotificationPriority.NOW
import io.github.mangi.eta.agent.device.notifications.NotificationPriority.TODAY
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.BriefThread
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.RecentEntry
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.ThreadKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BriefNotificationsCardTest {
    private fun threads(priority: NotificationPriority, count: Int) = (count downTo 1).map { index ->
        val title = "${priority.name}-$index"
        BriefThread(ThreadKey("org.synthetic", title), RecentEntry(title, "org.synthetic", title, "", index.toLong()), 1, priority)
    }

    @Test fun effectivePriorityHonorsCorrectionThenMastraThenToday() {
        assertEquals(LOW, NotificationPriority.effective("LOW", "NOW"))
        assertEquals(NOW, NotificationPriority.effective(null, "NOW"))
        assertEquals(TODAY, NotificationPriority.effective(null, null))
        assertEquals(TODAY, NotificationPriority.effective(null, "UNKNOWN"))
        assertEquals(TODAY, NotificationPriority.effective("UNKNOWN", "NOW"))
    }

    @Test fun mediumCapsUrgentAtThreeAndFillsFromTodayInOrder() {
        val urgent = threads(NOW, 10)
        val today = threads(TODAY, 10)
        val low = threads(LOW, 10)
        assertEquals(urgent.take(3) + today.take(2), selectBriefThreads(urgent + today + low, false))
        assertEquals(urgent.take(1) + today.take(4), selectBriefThreads(urgent.take(1) + today + low, false))
        assertEquals(urgent.take(3), selectBriefThreads(urgent + low, false))
        assertEquals(emptyList<BriefThread>(), selectBriefThreads(low, false))
    }

    @Test fun largeTakesTwelveAcrossBothPrioritySections() {
        val urgent = threads(NOW, 20)
        val today = threads(TODAY, 20)
        assertEquals(urgent.take(12), selectBriefThreads(urgent + today, true))
        assertEquals(urgent.take(7) + today.take(5), selectBriefThreads(urgent.take(7) + today, true))
    }

    @Test fun extraLabelCountsOnlyMessagesBesidesTheLatest() {
        assertNull(briefThreadExtraLabel(0))
        assertNull(briefThreadExtraLabel(1))
        assertEquals("+1", briefThreadExtraLabel(2))
        assertEquals("+4999", briefThreadExtraLabel(5000))
    }
}
