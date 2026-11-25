package io.github.mangi.eta.ui.screens.food

import io.github.mangi.eta.food.*
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate

/** The four internal tabs of the Cuisine page (docs/design/cuisine.md). */
internal enum class CuisineTab { RECIPES, PANTRY, SHOPPING, PLANNING }

internal data class RecipeFilters(
    val maxMinutes: Int? = null,
    val difficulty: Difficulty? = null,
    val favoritesOnly: Boolean = false,
) {
    val active: Boolean get() = maxMinutes != null || difficulty != null || favoritesOnly
}

/**
 * Deterministic reading of the free search field: « 20 min », « 1 h » or « 45 minutes » become a time limit,
 * the remaining words must all appear in the title, tags, cuisine or description. No AI involved.
 */
internal data class RecipeIntent(val words: List<String>, val maxMinutes: Int?)

private val minutesPattern = Regex("""(\d{1,3})\s*(?:min(?:utes?)?|mn)\b""", RegexOption.IGNORE_CASE)
private val hoursPattern = Regex("""(\d{1,2})\s*h(?:eures?)?\b""", RegexOption.IGNORE_CASE)
private val fillerWords = setOf(
    "j", "ai", "j'ai", "j’ai", "en", "moins", "de", "des", "du", "la", "le", "les", "un", "une", "pour", "avec",
    "propose", "proposer", "moi", "quelque", "chose", "rapide", "max", "maximum",
)

internal fun parseRecipeIntent(query: String): RecipeIntent {
    var rest = query
    val minutes = minutesPattern.find(rest)?.let { match ->
        rest = rest.removeRange(match.range)
        match.groupValues[1].toInt()
    } ?: hoursPattern.find(rest)?.let { match ->
        rest = rest.removeRange(match.range)
        match.groupValues[1].toInt() * 60
    }
    val words = rest.foldForSearch().split(Regex("""[^\p{L}\p{N}']+"""))
        .filter { it.length > 1 && it !in fillerWords }
    return RecipeIntent(words, minutes?.takeIf { it > 0 })
}

internal fun String.foldForSearch(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")

internal val RecipeSummary.totalMinutes: Int get() = prepTimeMinutes + cookTimeMinutes

internal fun RecipeSummary.matches(intent: RecipeIntent, filters: RecipeFilters): Boolean {
    val limit = listOfNotNull(intent.maxMinutes, filters.maxMinutes).minOrNull()
    if (limit != null && totalMinutes > limit) return false
    if (filters.difficulty != null && difficulty != filters.difficulty) return false
    if (filters.favoritesOnly && !favorite) return false
    if (intent.words.isEmpty()) return true
    val haystack = (listOf(title, description, cuisine.orEmpty()) + tags).joinToString(" ").foldForSearch()
    return intent.words.all { it in haystack }
}

/** Library order: favorites, then recently cooked, then alphabetical. */
internal fun List<RecipeSummary>.libraryOrder(): List<RecipeSummary> = sortedWith(
    compareByDescending<RecipeSummary> { it.favorite }
        .thenByDescending { it.lastCookedAt.orEmpty() }
        .thenBy { it.title.foldForSearch() },
)

internal fun AvailabilitySummary.toAvailabilityUi(verification: StockVerification = StockVerification.VERIFIED) =
    RecipeAvailabilityUi(
        when (category) {
            AvailabilityCategory.READY -> RecipeAvailability.All
            AvailabilityCategory.READY_WITH_SUBSTITUTIONS -> RecipeAvailability.Partial(missingCount.coerceAtLeast(1))
            AvailabilityCategory.FEW_PURCHASES, AvailabilityCategory.MANY_PURCHASES ->
                RecipeAvailability.Missing(missingCount.coerceAtLeast(1))
        },
        verification,
    )

/** Product names a suggestion would use up before they expire (server reason `USES_EXPIRING:<name>`). */
internal val RecipeSuggestion.expiringProducts: List<String>
    get() = reasons.mapNotNull { it.removePrefix("USES_EXPIRING:").takeIf { name -> name != it && name.isNotBlank() } }

internal data class RecipeSections(
    /** Cookable now with the known stock (substitutions allowed). */
    val withWhatIHave: List<RecipeSuggestion>,
    /** Recipes that use products close to their date, best first. */
    val useSoon: List<RecipeSuggestion>,
)

internal fun List<RecipeSuggestion>.toSections(intent: RecipeIntent, filters: RecipeFilters): RecipeSections {
    val visible = filter { it.recipe.matches(intent, filters) }.sortedByDescending { it.score }
    val ready = visible.filter {
        it.availability.category == AvailabilityCategory.READY ||
            it.availability.category == AvailabilityCategory.READY_WITH_SUBSTITUTIONS
    }
    val soon = visible.filter { it.expiringProducts.isNotEmpty() }
    return RecipeSections(ready.take(8), soon.take(4))
}

internal fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

/** Tonight's dinner, or today's lunch when no dinner is planned. */
internal fun MealWeek.tonight(today: LocalDate): MealSlot? {
    val day = today.toString()
    return slots.firstOrNull { it.date == day && it.meal == Meal.DINNER }
        ?: slots.firstOrNull { it.date == day && it.meal == Meal.LUNCH }
}

internal data class CuisineShortcutsUi(
    /** Products with stock, null while unknown. */
    val stockCount: Int? = null,
    val expiringCount: Int = 0,
    /** Shopping rows left to buy, null while unknown. */
    val shoppingRemaining: Int? = null,
    val tonight: MealSlot? = null,
    val planningKnown: Boolean = false,
)

internal fun PantryResponse.shortcutCounts(): Pair<Int, Int> {
    val stocked = items.filter { it.amount > 0 && it.status != PantryStatus.OUT_OF_STOCK }
    return stocked.size to stocked.count { it.status == PantryStatus.DUE_SOON || it.status == PantryStatus.EXPIRED }
}
