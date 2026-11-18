package io.github.mangi.eta.agent.device.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A cheap invalidation signal, not a background poller. The notification
 * listener emits when it has stored a notification; disk reads happen only
 * while the briefing is visible. No notification content travels through it.
 */
internal object NotificationActivityFeed {
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    fun notifyChanged() {
        _changes.value += 1L
    }
}
