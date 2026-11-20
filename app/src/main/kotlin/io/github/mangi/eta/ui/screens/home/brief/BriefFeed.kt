package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.runtime.Composable
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId.*

internal enum class BriefCardId(val key: String, val title: String) {
    SUGGESTIONS("suggestions", "Jean-Calcul te suggère"),
    NOTIFICATIONS("notifications", "Notifications"),
    AGENDA("agenda", "Agenda"),
    CAR("car", "Voiture"),
    TASKS("tasks", "Tâches"),
    HEALTH("health", "Sport et santé"),
    HABITS("habits", "Habitudes"),
    FINANCE("finance", "Finances"),
    NEWS("news", "Actualités"),
    WEATHER("weather", "Météo"),
    HOME("home", "Maison"),
}

internal fun briefFeedOrder(moment: BriefMoment, hidden: Set<BriefCardId> = emptySet()): List<BriefCardId> {
    val ordered = when (moment) {
        BriefMoment.MORNING -> listOf(AGENDA, CAR, TASKS, NOTIFICATIONS, SUGGESTIONS, HEALTH, NEWS, FINANCE, HABITS)
        BriefMoment.DAY -> listOf(AGENDA, SUGGESTIONS, NOTIFICATIONS, TASKS, FINANCE, HEALTH, NEWS, CAR, HABITS)
        BriefMoment.EVENING -> listOf(NOTIFICATIONS, SUGGESTIONS, AGENDA, CAR, HEALTH, TASKS, FINANCE, NEWS, HABITS)
        BriefMoment.NIGHT -> listOf(AGENDA, HEALTH, SUGGESTIONS, TASKS, NOTIFICATIONS, CAR, FINANCE, NEWS, HABITS)
    }
    return (ordered + listOf(WEATHER, HOME)).filterNot(hidden::contains)
}


internal data class BriefFeedItem(
    val key: String,
    val contentType: String,
    val content: @Composable () -> Unit,
)
