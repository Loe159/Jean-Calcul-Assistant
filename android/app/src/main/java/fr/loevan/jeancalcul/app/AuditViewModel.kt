package fr.loevan.jeancalcul.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.loevan.jeancalcul.domain.AuditEvent
import fr.loevan.jeancalcul.domain.AuditFilter
import fr.loevan.jeancalcul.domain.AuditOutcome
import fr.loevan.jeancalcul.domain.AuditRepository
import fr.loevan.jeancalcul.observability.RedactedAuditExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuditTimeWindow {
    ALL,
    LAST_DAY,
    LAST_WEEK,
}

data class AuditUiState(
    val events: List<AuditEvent> = emptyList(),
    val filter: AuditFilter = AuditFilter(),
    val timeWindow: AuditTimeWindow = AuditTimeWindow.ALL,
    val retentionDays: Int = 30,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AuditViewModel
    @Inject
    constructor(
        private val repository: AuditRepository,
        private val exporter: RedactedAuditExporter,
    ) : ViewModel() {
        private val controls = MutableStateFlow(AuditControls())
        private val errorMessage = MutableStateFlow<String?>(null)
        private val events =
            controls.flatMapLatest { control ->
                repository.observeEvents(control.filter, control.limit)
            }

        val uiState =
            combine(
                events,
                repository.observeRetentionDays(),
                controls,
                errorMessage,
            ) { current, days, control, error ->
                AuditUiState(
                    events = current,
                    filter = control.filter,
                    timeWindow = control.timeWindow,
                    retentionDays = days,
                    canLoadMore = current.size >= control.limit,
                    errorMessage = error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuditUiState())

        fun setTimeWindow(window: AuditTimeWindow) {
            val now = System.currentTimeMillis()
            val from =
                when (window) {
                    AuditTimeWindow.ALL -> null
                    AuditTimeWindow.LAST_DAY -> now - MILLIS_PER_DAY
                    AuditTimeWindow.LAST_WEEK -> now - (7 * MILLIS_PER_DAY)
                }
            controls.update { it.copy(filter = it.filter.copy(fromEpochMillis = from), timeWindow = window) }
        }

        fun setToolName(value: String) {
            controls.update { it.copy(filter = it.filter.copy(toolName = value.trim().takeIf(String::isNotEmpty))) }
        }

        fun setOutcome(outcome: AuditOutcome?) {
            controls.update { it.copy(filter = it.filter.copy(outcome = outcome)) }
        }

        fun loadMore() {
            controls.update { it.copy(limit = it.limit + PAGE_SIZE) }
        }

        fun setRetentionDays(days: Int) {
            viewModelScope.launch {
                runCatching { repository.setRetentionDays(days) }
                    .onFailure { errorMessage.value = it.message ?: "Impossible de modifier la rétention." }
            }
        }

        fun purgeExpired() {
            viewModelScope.launch {
                val cutoff = System.currentTimeMillis() - (uiState.value.retentionDays * MILLIS_PER_DAY)
                runCatching { repository.purgeOlderThan(cutoff.coerceAtLeast(0)) }
                    .onFailure { errorMessage.value = it.message ?: "Impossible de purger le journal." }
            }
        }

        fun export(onExported: (String) -> Unit) {
            viewModelScope.launch {
                runCatching { exporter.export(controls.value.filter) }
                    .onSuccess {
                        errorMessage.value = null
                        onExported(it)
                    }.onFailure { errorMessage.value = it.message ?: "Impossible d'exporter le journal." }
            }
        }

        private data class AuditControls(
            val filter: AuditFilter = AuditFilter(),
            val timeWindow: AuditTimeWindow = AuditTimeWindow.ALL,
            val limit: Int = PAGE_SIZE,
        )

        private companion object {
            const val PAGE_SIZE = 25
            const val MILLIS_PER_DAY = 86_400_000L
        }
    }
