package io.github.mangi.eta.ui.screens.home.brief

internal data class BriefDemoCard(val headline: String, val detail: String)

internal fun briefDemoCard(card: BriefCardId): BriefDemoCard = when (card) {
    BriefCardId.FINANCE -> BriefDemoCard("Budget du mois · 62 %", "Exemple de dépenses et de suivi du budget.")
    BriefCardId.NEWS -> BriefDemoCard("Une sélection à lire", "Exemple de lecture · 4 minutes.")
    BriefCardId.WEATHER -> BriefDemoCard("11° · Éclaircies", "Des averses en fin de journée dans cet aperçu.")
    BriefCardId.HOME -> BriefDemoCard("20° à l’intérieur", "Exemple de consommation · 5,2 kWh aujourd’hui.")
    else -> error("This card has dedicated content: $card")
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

internal enum class BriefDemoTone { Blue, Azure, Orange, Violet, Purple, Green, Red, Yellow }

internal data class BriefDemoSuggestion(
    val id: String,
    val title: String,
    val detail: String,
    val action: String,
    val tone: BriefDemoTone,
)

internal data class BriefDemoEvent(
    val start: Int,
    val end: Int,
    val title: String,
    val place: String,
    val tone: BriefDemoTone,
)

internal data class BriefDemoTask(
    val title: String,
    val due: String,
    val priority: Boolean = false,
    val done: Boolean = false,
)

internal data class BriefDemoHabit(val title: String, val target: Int = 1, val completed: Int = 0)
internal data class BriefDemoGoal(val title: String, val completed: Int, val target: Int, val tone: BriefDemoTone)
internal data class BriefDemoSleep(val label: String, val minutes: Int, val tone: BriefDemoTone)
internal data class BriefDemoOccasion(val month: String, val day: String, val title: String, val detail: String)

internal object BriefDemoData {
    val suggestions = listOf(
        BriefDemoSuggestion("fuel", "Fais le plein ce soir", "Il te reste 168 km. Station à 1,4 km, à 1,72 €/L, ouverte jusqu’à 22:00.", "Lancer l’itinéraire", BriefDemoTone.Orange),
        BriefDemoSuggestion("reply", "Camille attend une réponse", "Un rappel est demandé avant 09:00.", "Répondre", BriefDemoTone.Blue),
        BriefDemoSuggestion("sport", "Vingt minutes pour bouger", "Un créneau se libère en fin de journée.", "Planifier", BriefDemoTone.Green),
        BriefDemoSuggestion("merge", "Regroupe deux tâches", "Un seul appel au garage pour la révision et les pneus.", "Regrouper", BriefDemoTone.Violet),
    )

    const val agendaDate = "Vendredi 20 novembre"
    const val agendaMonth = "NOV"
    const val agendaDay = "20"
    const val agendaDemoNow = 8 * 60 + 42
    const val departure = "08:45"
    const val destination = "Atelier de démonstration"
    val agenda = listOf(
        BriefDemoEvent(570, 630, "Point projet", destination, BriefDemoTone.Blue),
        BriefDemoEvent(750, 810, "Déjeuner avec Clara", "Café du quartier", BriefDemoTone.Orange),
        BriefDemoEvent(870, 930, "Revue de design", "Visio", BriefDemoTone.Violet),
        BriefDemoEvent(1020, 1050, "Appel fournisseur", "Téléphone", BriefDemoTone.Azure),
        BriefDemoEvent(1110, 1155, "Course fractionnée", "Parc du quartier", BriefDemoTone.Green),
    )
    val attendees = listOf("Inès" to BriefDemoTone.Violet, "Karim" to BriefDemoTone.Green, "Sophie" to BriefDemoTone.Orange)

    const val carModel = "208 · Blanc nacré"
    const val rangeKm = 168
    const val fuelPercent = 24
    const val odometerKm = 38_760
    const val serviceRemainingKm = 1_240
    const val serviceIntervalKm = 10_000
    const val lastService = "12 mai"
    const val nextService = "7 janv."
    const val tripKm = 18
    const val tripMinutes = 38
    const val tripTraffic = "Trafic dense"
    const val station = "Station à 1,4 km · 1,72 €/L · ouverte jusqu’à 22:00"
    const val tyreAlert = "Pneu arrière gauche à 1,9 bar"
    const val tyreDetail = "Pression recommandée · 2,3 bar"
    val traffic = listOf(BriefDemoTone.Green, BriefDemoTone.Green, BriefDemoTone.Yellow, BriefDemoTone.Orange, BriefDemoTone.Orange, BriefDemoTone.Yellow, BriefDemoTone.Green)

    const val garageCall = "Appeler le garage"
    const val tyreAppointment = "Prendre RDV pneus"
    val tasks = listOf(
        BriefDemoTask("Envoyer la facture de l’atelier", "Demain à 09:00", priority = true),
        BriefDemoTask("Préparer le support du point projet", "Ce soir", priority = true),
        BriefDemoTask("Relire le compte rendu", "Ce soir"),
        BriefDemoTask(garageCall, "Demain"),
        BriefDemoTask(tyreAppointment, "Cette semaine"),
        BriefDemoTask("Valider les maquettes", "Terminée", done = true),
        BriefDemoTask("Commander le cadeau de Théo", "Terminée", done = true),
        BriefDemoTask("Préparer le matériel", "Terminée", done = true),
    )

    const val steps = 7_248
    const val stepGoal = 10_000
    const val calories = 412
    const val calorieGoal = 600
    const val activeMinutes = 24
    const val minuteGoal = 30
    const val today = 4
    val weekDays = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
    val weeklySteps = listOf(8_120, 12_480, 10_240, 8_650, steps, 0, 0)
    val sleep = listOf(
        BriefDemoSleep("Profond", 74, BriefDemoTone.Violet),
        BriefDemoSleep("Léger", 216, BriefDemoTone.Azure),
        BriefDemoSleep("Paradoxal", 104, BriefDemoTone.Purple),
        BriefDemoSleep("Éveil", 38, BriefDemoTone.Orange),
    )
    const val sleepScore = 82
    const val recoveryPercent = 78
    const val hrv = 54
    val nextWorkout get() = agenda.last()

    const val habitStreak = 12
    const val habitsPerDay = 3
    val habitWeek = listOf(3, 3, 2, 3)
    val habits = listOf(
        BriefDemoHabit("Boire 2 L d’eau", target = 8, completed = 5),
        BriefDemoHabit("Lire 20 minutes", completed = 1),
        BriefDemoHabit("Méditer 10 minutes"),
        BriefDemoHabit("S’étirer"),
    )
    val goals = listOf(
        BriefDemoGoal("4 séances de sport", 1, 4, BriefDemoTone.Orange),
        BriefDemoGoal("5 jours à 8 000 pas", 4, 5, BriefDemoTone.Azure),
        BriefDemoGoal("3 lectures longues", 2, 3, BriefDemoTone.Purple),
    )
    val birthday = BriefDemoOccasion("NOV", "21", "Anniversaire de Théo", "Samedi · demain · cadeau commandé")
    val occasions = listOf(
        BriefDemoOccasion("NOV", "28", "Concert à la salle du quartier", "Samedi · 20:00 · 2 places réservées"),
        BriefDemoOccasion("DÉC", "03", "Dîner de fin d’année", "Jeudi · 19:30 · chez Clara"),
    )
    val achievements = listOf("Record du mois · 12 480 pas mardi", "12 soirs de suite sous 2 h d’écran")
}
