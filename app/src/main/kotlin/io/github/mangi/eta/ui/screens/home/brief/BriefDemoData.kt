package io.github.mangi.eta.ui.screens.home.brief

internal data class BriefDemoCard(val headline: String, val detail: String)

internal fun briefDemoCard(card: BriefCardId): BriefDemoCard = when (card) {
    BriefCardId.SUGGESTIONS -> BriefDemoCard("Un créneau à garder libre", "Réserve 20 minutes pour préparer le prochain rendez-vous.")
    BriefCardId.AGENDA -> BriefDemoCard("Atelier de démonstration · 09:30", "Un créneau libre de 10:30 à 11:15.")
    BriefCardId.CAR -> BriefDemoCard("168 km d’autonomie", "38 min de trajet estimé pour le prochain départ.")
    BriefCardId.TASKS -> BriefDemoCard("2 tâches à faire", "Préparer les documents et vérifier le matériel.")
    BriefCardId.HEALTH -> BriefDemoCard("4 280 pas aujourd’hui", "Objectif de démonstration · 7 000 pas.")
    BriefCardId.HABITS -> BriefDemoCard("3 jours de suite", "Lecture du soir · objectif de 10 minutes.")
    BriefCardId.FINANCE -> BriefDemoCard("Budget du mois · 62 %", "Exemple de dépenses et de suivi du budget.")
    BriefCardId.NEWS -> BriefDemoCard("Une sélection à lire", "Exemple de lecture · 4 minutes.")
    BriefCardId.WEATHER -> BriefDemoCard("11° · Éclaircies", "Des averses en fin de journée dans cet aperçu.")
    BriefCardId.HOME -> BriefDemoCard("20° à l’intérieur", "Exemple de consommation · 5,2 kWh aujourd’hui.")
    BriefCardId.NOTIFICATIONS -> error("Notifications uses the local history")
}

internal data class BriefBarSuggestion(
    val card: BriefCardId,
    val title: String,
    val detail: String,
    val progress: Float,
)

internal fun briefBarSuggestion(moment: BriefMoment): BriefBarSuggestion = when (moment) {
    BriefMoment.MORNING -> BriefBarSuggestion(BriefCardId.CAR, "Départ à 08:45", "Atelier de démonstration · 38 min", .08f)
    BriefMoment.DAY -> BriefBarSuggestion(BriefCardId.AGENDA, "Déjeuner à 12:30", "9 min à pied", .50f)
    BriefMoment.EVENING -> BriefBarSuggestion(BriefCardId.CAR, "Départ demain à 08:45", "Atelier de démonstration · 38 min", .30f)
    BriefMoment.NIGHT -> BriefBarSuggestion(BriefCardId.HEALTH, "Coucher conseillé à 00:45", "Réveil à 08:15 · 7 h 30 de sommeil", .70f)
}
