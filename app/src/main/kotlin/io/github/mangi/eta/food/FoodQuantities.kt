package io.github.mangi.eta.food

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import kotlin.math.floor
import kotlinx.serialization.Serializable

@Serializable internal enum class ScalingNote { ROUNDED }

@Serializable
internal data class ScaledQuantity(
    val exactAmount: Double?,
    val displayAmount: Double?,
    val unit: UnitCode,
    val note: ScalingNote?,
)

@Serializable
internal data class ScalableIngredient(
    val id: String,
    val quantity: Quantity,
    val scaling: Scaling = Scaling.LINEAR,
    val rounding: Rounding? = null,
)

internal object FoodQuantities {
    private val marker = Regex("""\{\{ing:([^{}]*)\}\}""")
    private val quarterGlyphs = listOf("", "¼", "½", "¾")

    fun scale(
        quantity: Quantity,
        baseServings: Int,
        targetServings: Int,
        scaling: Scaling = Scaling.LINEAR,
        rounding: Rounding? = null,
    ): ScaledQuantity = display(exact(quantity, baseServings, targetServings, scaling), rounding)

    /** Retains the recipe unit so the result is comparable to Grocy's converted stock. */
    fun exact(quantity: Quantity, baseServings: Int, targetServings: Int, scaling: Scaling): Quantity {
        require(baseServings in 1..24 && targetServings > 0)
        val amount = quantity.amount
        if (scaling == Scaling.TO_TASTE) {
            require(amount == null)
            return quantity
        }
        require(amount != null && amount.isFinite() && amount > 0)
        if (scaling == Scaling.FIXED) return quantity
        val product = amount * targetServings
        val exact = if (product.isFinite()) product / baseServings else amount * (targetServings.toDouble() / baseServings)
        require(exact.isFinite()) { "Scaled quantity is out of range" }
        return quantity.copy(amount = exact)
    }

    private fun display(quantity: Quantity, rounding: Rounding?): ScaledQuantity {
        val amount = quantity.amount ?: return ScaledQuantity(null, null, quantity.unit, null)
        val (exact, unit) = when {
            amount < 0.1 && quantity.unit == UnitCode.KG -> Quantity(amount * 1000, UnitCode.G)
            amount < 0.1 && quantity.unit == UnitCode.L -> Quantity(amount * 1000, UnitCode.ML)
            else -> quantity
        }
        checkNotNull(exact)
        val step = when (unit) {
            UnitCode.G, UnitCode.ML -> when {
                exact < 5 -> 0.5
                exact < 50 -> 1.0
                exact < 250 -> 5.0
                else -> 10.0
            }
            UnitCode.MG, UnitCode.CL, UnitCode.PINCH -> 1.0
            UnitCode.KG, UnitCode.L -> 0.05
            UnitCode.TSP, UnitCode.TBSP, UnitCode.CUP -> 0.25
            else -> if (rounding == Rounding.HALF) 0.5 else 1.0
        }
        val steps = if (step < 1) exact * (1 / step) else exact / step
        val nearest = if (steps.isFinite()) {
            val whole = floor(steps)
            val rounded = if (steps - whole >= 0.5) whole + 1 else whole
            if (step < 1) rounded / (1 / step) else rounded * step
        } else exact
        val displayed = if (exact > 0) maxOf(step, nearest) else 0.0
        val note = if (displayed * 100 > exact * 115 || displayed * 100 < exact * 85) ScalingNote.ROUNDED else null
        return ScaledQuantity(exact, displayed, unit, note)
    }

    fun formatFr(quantity: Quantity, omitPieceUnit: Boolean = false): String {
        val (amount, unit) = quantity
        if (amount == null) return "selon le goût"
        val whole = floor(amount)
        val quarter = (amount - whole) * 4
        val number = if (unit in listOf(UnitCode.TSP, UnitCode.TBSP, UnitCode.CUP) &&
            (quarter == 1.0 || quarter == 2.0 || quarter == 3.0)
        ) {
            val glyph = quarterGlyphs[quarter.toInt()]
            if (whole == 0.0) glyph else "${decimalFr(whole)} $glyph"
        } else decimalFr(amount)
        if (omitPieceUnit && unit == UnitCode.PIECE) return number
        val label = when (unit) {
            UnitCode.TSP -> "c. à café"
            UnitCode.TBSP -> "c. à soupe"
            UnitCode.CUP -> "tasse"
            UnitCode.PIECE -> "pièce"
            UnitCode.CLOVE -> "gousse"
            UnitCode.SLICE -> "tranche"
            UnitCode.CAN -> "boîte"
            UnitCode.PACK -> "paquet"
            UnitCode.BUNCH -> "botte"
            UnitCode.PINCH -> "pincée"
            else -> return "$number ${unit.name.lowercase()}"
        }
        return if (amount < 2 || unit == UnitCode.TSP || unit == UnitCode.TBSP) "$number $label" else "$number ${label}s"
    }

    private fun decimalFr(amount: Double): String = BigDecimal.valueOf(amount)
        .setScale(20, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    fun renderStep(
        instruction: String,
        ingredients: List<ScalableIngredient>,
        ingredientRefs: List<IngredientRef>,
        baseServings: Int,
        targetServings: Int,
        overrides: Map<String, Quantity> = emptyMap(),
        onQuantity: ((IntRange) -> Unit)? = null,
    ): String {
        val byId = ingredients.associateBy { it.id }
        val fractions = ingredientRefs.associate { it.ingredientId to it.fraction }
        val rendered = buildString {
            var cursor = 0
            for (match in marker.findAll(instruction)) {
                append(instruction, cursor, match.range.first)
                val start = length
                val id = match.groupValues[1]
                val ingredient = requireNotNull(byId[id]) { "Unknown ingredient marker: $id" }
                val fraction = requireNotNull(fractions[id]) { "Ingredient marker without reference: $id" }
                require(fraction > 0 && fraction <= 1)
                val exact = overrides[id] ?: exact(ingredient.quantity, baseServings, targetServings, ingredient.scaling)
                val scaled = display(exact.copy(amount = exact.amount?.times(fraction)), ingredient.rounding)
                append(formatFr(Quantity(scaled.displayAmount, scaled.unit), omitPieceUnit = true))
                onQuantity?.invoke(start until length)
                cursor = match.range.last + 1
            }
            append(instruction, cursor, instruction.length)
        }
        require("{{ing:" !in rendered) { "Malformed ingredient marker" }
        return rendered
    }

    fun renderStep(session: CookingSession, step: RecipeStep): String {
        val overrides = session.ingredientOverrides.associate { it.ingredientId to it.quantity }
        val recipe = session.recipeSnapshot
        val ingredients = recipe.ingredients.map { ScalableIngredient(it.id, it.quantity, it.scaling, it.rounding) }
        return renderStep(step.instruction, ingredients, step.ingredientRefs, recipe.servings, session.servings, overrides)
    }
}

private val untrackedStaples = setOf("eau", "sel", "sel fin", "poivre")
private val foodNameArticles = setOf("le", "la", "les", "l", "un", "une", "des", "du", "de", "d")
private val foodNameMarks = Regex("""\p{M}""")
private val foodNameSeparators = Regex("""[^\p{L}\p{N}]+""")

private fun isUntrackedStaple(name: String): Boolean {
    val normalized = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
        .replace(foodNameMarks, "")
        .replace("œ", "oe").replace("æ", "ae")
        .replace(foodNameSeparators, " ").trim()
    val tokens = normalized.split(' ').filter { it.isNotEmpty() && it !in foodNameArticles }
        .map { if (it.length > 3 && (it.endsWith('s') || it.endsWith('x'))) it.dropLast(1) else it }
    return tokens.joinToString(" ") in untrackedStaples
}

/** Reuses only stock from the same recipe version, in its original ingredient units. */
internal fun Availability.recompute(recipe: RecipeDefinition, servings: Int): Availability {
    require(recipe.id == recipeId && recipe.version == recipeVersion && servings > 0)
    val cached = items.associateBy { it.ingredientId }
    val recalculated = recipe.ingredients.map { ingredient ->
        val row = cached[ingredient.id]
        val required = FoodQuantities.exact(ingredient.quantity, recipe.servings, servings, ingredient.scaling)
        if (row?.status == AvailabilityStatus.NOT_TRACKED || row?.status == AvailabilityStatus.UNKNOWN) {
            return@map row.copy(required = required)
        }
        if (row == null && ingredient.grocyProductId == null) {
            val status = if (isUntrackedStaple(ingredient.name)) AvailabilityStatus.NOT_TRACKED else AvailabilityStatus.UNKNOWN
            return@map AvailabilityItem(ingredient.id, status, required, null, null, "NO_PRODUCT_LINK", null)
        }
        val stock = row?.inStock?.takeIf { it.unit == required.unit }
        if (stock == null) {
            AvailabilityItem(ingredient.id, AvailabilityStatus.UNKNOWN, required, null, null, row?.reason, row?.substitutionAvailable)
        } else {
            val need = required.amount ?: 0.0
            val shortfall = need - stock.amount
            val missing = if (shortfall <= maxOf(need, stock.amount) * 1e-12) 0.0 else shortfall
            val enough = if (required.amount == null) stock.amount > 0 else missing == 0.0
            val status = when {
                enough -> AvailabilityStatus.AVAILABLE
                ingredient.optional -> AvailabilityStatus.OPTIONAL_MISSING
                stock.amount > 0 -> AvailabilityStatus.PARTIAL
                else -> AvailabilityStatus.MISSING
            }
            AvailabilityItem(
                ingredient.id, status, required, stock,
                missing.takeIf { it > 0 }?.let { MeasuredQuantity(it, required.unit) },
                if (missing > 0) row.reason else null, row.substitutionAvailable,
            )
        }
    }
    val required = recalculated.filterIndexed { index, item ->
        !recipe.ingredients[index].optional && item.status != AvailabilityStatus.NOT_TRACKED
    }
    val availableCount = required.count { it.status == AvailabilityStatus.AVAILABLE }
    val missingCount = required.size - availableCount
    val purchases = required.count { it.status != AvailabilityStatus.AVAILABLE && it.substitutionAvailable == null }
    val category = when {
        purchases >= 3 -> AvailabilityCategory.MANY_PURCHASES
        purchases > 0 -> AvailabilityCategory.FEW_PURCHASES
        availableCount < required.size -> AvailabilityCategory.READY_WITH_SUBSTITUTIONS
        else -> AvailabilityCategory.READY
    }
    val ratio = if (required.isEmpty()) 1.0 else availableCount.toDouble() / required.size
    return copy(servings = servings, items = recalculated, summary = AvailabilitySummary(ratio, category, missingCount))
}
