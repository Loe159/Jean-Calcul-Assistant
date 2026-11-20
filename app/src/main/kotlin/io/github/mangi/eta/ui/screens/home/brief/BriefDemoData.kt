package io.github.mangi.eta.ui.screens.home.brief

import java.net.URI
import java.time.Instant

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

internal data class BriefDemoCategory(val label: String, val cents: Long, val tone: BriefDemoTone)
internal data class BriefDemoPayment(val label: String, val detail: String, val cents: Long, val tone: BriefDemoTone)
internal data class BriefDemoPosition(
    val name: String,
    val ticker: String,
    val quantity: Int,
    val priceCents: Long,
    val previousCloseCents: Long,
) {
    val valueCents get() = quantity * priceCents
    val previousValueCents get() = quantity * previousCloseCents
}

internal enum class BriefPortfolioPeriod(val label: String, val description: String) {
    Day("1J", "sur un jour"), Week("1S", "sur une semaine"), Month("1M", "sur un mois"), Year("1A", "sur un an"),
}

internal enum class BriefHomeControl(val label: String, val onLabel: String, val offLabel: String) {
    Lights("Lumières", "3 allumées", "Éteintes"),
    Heating("Chauffage", "Actif · Éco", "À l’arrêt"),
    Shutters("Volets", "Fermés", "Ouverts"),
    Lock("Serrure", "Verrouillée", "Déverrouillée"),
}

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

    const val budgetCents = 180_000L
    const val budgetDay = 19
    const val budgetDays = 30
    val budgetCategories = listOf(
        BriefDemoCategory("Courses", 41_200, BriefDemoTone.Green),
        BriefDemoCategory("Restaurants", 24_600, BriefDemoTone.Orange),
        BriefDemoCategory("Transport", 19_800, BriefDemoTone.Azure),
        BriefDemoCategory("Abonnements", 12_400, BriefDemoTone.Purple),
        BriefDemoCategory("Loisirs", 16_800, BriefDemoTone.Yellow),
        BriefDemoCategory("Autres", 13_600, BriefDemoTone.Blue),
    )
    val recentExpenses = listOf(
        BriefDemoPayment("Supermarché du quartier", "Courses · aujourd’hui", 3_460, BriefDemoTone.Green),
        BriefDemoPayment("Station-service", "Transport · hier", 5_210, BriefDemoTone.Azure),
        BriefDemoPayment("Café de la Place", "Restaurants · hier", 1_820, BriefDemoTone.Orange),
        BriefDemoPayment("Boulangerie", "Courses · mardi", 480, BriefDemoTone.Green),
    )
    val upcomingPayments = listOf(
        BriefDemoPayment("Musique en ligne", "Demain", 1_099, BriefDemoTone.Purple),
        BriefDemoPayment("Stockage cloud", "22 nov.", 299, BriefDemoTone.Azure),
        BriefDemoPayment("Salle de sport", "1 déc.", 2_990, BriefDemoTone.Orange),
        BriefDemoPayment("Assurance auto", "3 déc.", 4_120, BriefDemoTone.Red),
    )
    val portfolio = listOf(
        BriefDemoPosition("ETF Monde", "XWLD", 42, 11_842, 11_768),
        BriefDemoPosition("ETF S&P 500", "XSP5", 30, 6_574, 6_532),
        BriefDemoPosition("Énergie Europe", "XEUE", 24, 4_286, 4_325),
        BriefDemoPosition("Industrie Europe", "XIEU", 18, 7_328, 7_270),
    )
    private val portfolioDayMoves = listOf(0L, -450, -1_280, 850, 1_930, 1_240, 3_860, 3_170)
    private val portfolioWeekOffsets = listOf(-15_410L, -11_210, -13_540, -8_020, -9_660, -5_120)
    private val portfolioMonthOffsets = listOf(
        -31_610L, -32_100, -33_480, -35_860, -32_600, -30_200, -28_600, -26_420,
        -25_810, -26_590, -28_110, -26_500, -25_920, -23_770, -21_150, -19_620,
        -19_400, -18_260, -16_120, -14_850, -15_760, -16_200, -17_290,
    )
    private val portfolioYearOffsets = listOf(
        -91_450L, -98_140, -80_820, -75_190, -88_540, -69_510, -60_140, -64_530, -51_600, -44_860, -38_110,
    )

    fun portfolioHistory(period: BriefPortfolioPeriod): List<Long> {
        val total = portfolio.sumOf { it.valueCents }
        val previousClose = portfolio.sumOf { it.previousValueCents }
        val week = portfolioWeekOffsets.map { total + it } + previousClose + total
        return when (period) {
            BriefPortfolioPeriod.Day -> portfolioDayMoves.map { previousClose + it } + total
            BriefPortfolioPeriod.Week -> week
            BriefPortfolioPeriod.Month -> portfolioMonthOffsets.map { total + it } + week
            BriefPortfolioPeriod.Year -> portfolioYearOffsets.map { total + it } + (total + portfolioMonthOffsets.first()) + total
        }
    }

    val news = listOf(
        NewsArticle(
            "Des assistants vocaux qui tournent sans connexion", "Le Signal Numérique",
            URI.create("https://news.example/assistants-locaux"), Instant.parse("2026-11-20T07:30:00Z"),
            NewsCategory.Tech, 3, listOf(
                "Un prototype exécute les tâches simples directement sur le téléphone.",
                "Les modèles compressés occupent moins de mémoire et consomment moins d’énergie.",
                "Les essais se poursuivront sur des appareils d’entrée de gamme.",
            ),
        ),
        NewsArticle(
            "Un atelier coopératif teste la livraison à vélo", "La Gazette des Ateliers",
            URI.create("https://news.example/livraison-velo"), Instant.parse("2026-11-20T07:10:00Z"),
            NewsCategory.Economy, 4, listOf(
                "Plusieurs commerces mutualisent leurs livraisons de proximité.",
                "La phase d’essai se concentre sur les trajets de moins de trois kilomètres.",
            ),
        ),
        NewsArticle(
            "La médiathèque ouvre un espace de prêt d’outils", "Le Courrier du Quartier",
            URI.create("https://news.example/pret-outils"), Instant.parse("2026-11-20T06:50:00Z"),
            NewsCategory.Local, 2, listOf(
                "Une carte de lecteur suffit pour emprunter du matériel de bricolage.",
                "Des permanences accompagneront les premiers emprunts le samedi.",
            ),
        ),
        NewsArticle(
            "Une bibliothèque libre allège l’entraînement des petits modèles", "Le Signal Numérique",
            URI.create("https://news.example/modeles-compacts"), Instant.parse("2026-11-20T06:30:00Z"),
            NewsCategory.Tech, 5, listOf(
                "Le projet réutilise une partie des calculs entre les sessions d’entraînement.",
                "Le gain de mémoire permet de lancer les essais sur des machines modestes.",
            ),
        ),
    )

    const val weatherLocation = "Les Tilleuls"
    const val windKmh = 14
    const val feelsLikeOffset = 2
    val hourlyTemperatures = listOf(
        5, 5, 4, 4, 4, 4, 5, 6, 7, 8, 9, 10, 11, 11, 11, 11, 10, 10, 9, 9, 8, 8, 7, 7,
        7, 7, 6, 6, 6, 6, 6, 7, 8, 9, 10, 11, 12, 12, 12, 11, 10, 9, 9, 8, 8, 7, 7, 7,
    )
    val hourlyRain = listOf(
        10, 10, 5, 5, 5, 5, 5, 5, 10, 10, 15, 20, 30, 35, 40, 50, 60, 70, 75, 80, 60, 35, 15, 10,
        10, 10, 5, 5, 5, 5, 10, 25, 55, 60, 40, 25, 15, 10, 10, 15, 20, 35, 60, 50, 30, 20, 10, 5,
    )

    const val indoorTemperature = 20.5f
    const val thermostatTarget = 21f
    const val thermostatMin = 16f
    const val thermostatMax = 26f
    const val humidityPercent = 46
    const val energyVsAverage = -12
    const val homeDemoHour = 19
    const val homePowerKw = 1.2
    val homeEnergy = listOf(
        .6f, .5f, .3f, .2f, .2f, .2f, .2f, .2f, .2f, .3f, .5f, .6f,
        .4f, .2f, .2f, .3f, .4f, .3f, .3f, .4f, .5f, .8f, 1.0f, 1.2f,
    )
    val homeEnergyToday = homeEnergy.takeLast(homeDemoHour + 1).sumOf { it.toDouble() }
    val homeEnabledControls = setOf(BriefHomeControl.Lights, BriefHomeControl.Heating, BriefHomeControl.Lock)
    const val openWindowMinutes = 20
    const val washerMinutes = 24
    const val washerCycleMinutes = 90
    const val robotPercent = 100
}
