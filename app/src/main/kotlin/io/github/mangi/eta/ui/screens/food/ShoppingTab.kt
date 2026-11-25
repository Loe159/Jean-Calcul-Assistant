package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Where a shopping row comes from, already resolved to something a person reads. */
internal sealed interface ShoppingOriginUi {
    data class Recipe(val title: String) : ShoppingOriginUi
    data class Meal(val date: LocalDate, val meal: io.github.mangi.eta.food.Meal, val title: String?) : ShoppingOriginUi
}

internal enum class ShoppingRowState { TODO, IN_BASKET, STOCKED }

internal data class ShoppingRowUi(
    val key: String,
    val row: ShoppingProductRow,
    val state: ShoppingRowState,
    val origin: ShoppingOriginUi?,
) {
    /** A free-text line, or a product without a known unit, cannot be stocked without help. */
    val canStock: Boolean get() = row.grocyProductId != null && row.quantity.grocyQuId != null
}

internal data class ShoppingGroupUi(val name: String?, val rows: List<ShoppingRowUi>)

internal data class ShoppingTabUi(
    val state: CuisineContentState = CuisineContentState.LOADING,
    val error: String? = null,
    val offline: Boolean = false,
    val groups: List<ShoppingGroupUi> = emptyList(),
    val stocked: List<ShoppingRowUi> = emptyList(),
    val inBasket: Int = 0,
    val total: Int = 0,
    val storeMode: Boolean = false,
    val query: String = "",
) {
    val basketRows: List<ShoppingRowUi> get() = groups.flatMap { it.rows }.filter { it.state == ShoppingRowState.IN_BASKET }
}

internal class ShoppingTabActions(
    val onQuery: (String) -> Unit,
    val onAdd: (String) -> Unit,
    val onCheck: (ShoppingRowUi, Boolean) -> Unit,
    val onEdit: (ShoppingRowUi) -> Unit,
    val onStoreMode: (Boolean) -> Unit,
    val onPutAway: () -> Unit,
    val onRetry: () -> Unit,
)

internal fun ShoppingProductRow.state(): ShoppingRowState = when {
    lines.any { it.state == ShoppingItemState.TODO } -> ShoppingRowState.TODO
    lines.any { it.state == ShoppingItemState.IN_BASKET } -> ShoppingRowState.IN_BASKET
    else -> ShoppingRowState.STOCKED
}

/** The most specific origin wins: a planned meal, then a recipe; manual rows have none. */
internal fun ShoppingProductRow.originUi(recipeTitles: Map<String, String>): ShoppingOriginUi? {
    origins.firstNotNullOfOrNull { it.origin as? ShoppingOrigin.MealSlot }?.let { slot ->
        return ShoppingOriginUi.Meal(LocalDate.parse(slot.slot.date), slot.slot.meal, recipeTitles[slot.recipeId])
    }
    return origins.firstNotNullOfOrNull { it.origin as? ShoppingOrigin.Recipe }
        ?.let { recipeTitles[it.recipeId] }?.let(ShoppingOriginUi::Recipe)
}

internal fun ShoppingProjection.toTabUi(
    recipeTitles: Map<String, String>,
    query: String,
    storeMode: Boolean,
): Pair<List<ShoppingGroupUi>, List<ShoppingRowUi>> {
    val words = query.foldForSearch().trim()
    val all = aisles.map { group ->
        group.name to group.rows.filter { words.isEmpty() || it.name.foldForSearch().contains(words) }.map { row ->
            ShoppingRowUi(listOf(row.grocyProductId, row.quantity.grocyQuId, row.lines.first().id).joinToString(":"),
                row, row.state(), row.originUi(recipeTitles))
        }
    }
    val stocked = all.flatMap { it.second }.filter { it.state == ShoppingRowState.STOCKED }
    val active = all.map { (name, rows) -> ShoppingGroupUi(name, rows.filter { it.state != ShoppingRowState.STOCKED }) }
    val groups = if (storeMode) {
        // Store mode: what is left to find, aisle by aisle, then the basket at the end.
        val todo = active.map { it.copy(rows = it.rows.filter { row -> row.state == ShoppingRowState.TODO }) }
        val basket = active.flatMap { it.rows }.filter { it.state == ShoppingRowState.IN_BASKET }
        todo + ShoppingGroupUi(STORE_BASKET, basket)
    } else active
    return groups.filter { it.rows.isNotEmpty() } to stocked
}

/** Sentinel group name for the store-mode basket, replaced by a localized title. */
internal const val STORE_BASKET = "\u0000basket"

@Composable
internal fun ColumnScope.ShoppingTabContent(state: ShoppingTabUi, actions: ShoppingTabActions) {
    val colors = COUITheme.colorScheme
    if (state.offline) CuisineBadge(stringResource(R.string.food_shopping_offline), icon = CuisineIcons.Offline)
    when {
        state.state == CuisineContentState.LOADING && state.total == 0 -> repeat(4) { CuisineLoading() }
        state.state == CuisineContentState.ERROR && state.total == 0 -> CuisineMessage(
            stringResource(R.string.food_shopping_unavailable), detail = state.error, icon = CuisineIcons.Warning,
            isError = true, onRetry = actions.onRetry,
        )
        else -> {
            ShoppingHeader(state, actions.onStoreMode)
            if (!state.storeMode) ShoppingAddField(state.query, actions)
            if (state.total == 0 && state.stocked.isEmpty() && state.query.isBlank()) {
                CuisineMessage(
                    stringResource(R.string.food_shopping_empty), detail = stringResource(R.string.food_shopping_empty_detail),
                    icon = CuisineIcons.Cart,
                )
            } else if (state.groups.isEmpty() && state.stocked.isEmpty()) {
                FoodSupporting(stringResource(R.string.food_no_results))
            }
            state.groups.forEach { group ->
                val title = when (group.name) {
                    STORE_BASKET -> stringResource(R.string.food_shopping_in_basket)
                    null -> stringResource(R.string.food_aisle_other)
                    else -> group.name
                }
                ShoppingGroupHeader(title, group.rows.size)
                ShoppingRows(group.rows, state.storeMode, actions)
            }
            if (!state.storeMode && state.stocked.isNotEmpty()) {
                ShoppingGroupHeader(stringResource(R.string.food_shopping_stocked_group), state.stocked.size)
                ShoppingRows(state.stocked, false, actions)
            }
        }
    }
}

@Composable
private fun ShoppingHeader(state: ShoppingTabUi, onStoreMode: (Boolean) -> Unit) {
    val colors = COUITheme.colorScheme
    val left = state.total - state.inBasket
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.inBasket}/${state.total}", style = COUITheme.textStyles.displayXS)
                FoodSupporting(stringResource(R.string.food_items_in_basket))
            }
            val progress = if (state.total == 0) 0f else state.inBasket.toFloat() / state.total
            val description = stringResource(R.string.food_basket_progress, state.inBasket, state.total)
            LinearProgressIndicator(
                Modifier.fillMaxWidth().semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    stateDescription = description
                },
                progress, height = 6.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = colors.green),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FoodSupporting(
                    if (left > 0) pluralStringResource(R.plurals.food_left_to_find, left, left)
                    else stringResource(R.string.food_all_in_basket),
                )
                Spacer(Modifier.weight(1f))
                Chip(
                    selected = state.storeMode, onClick = { onStoreMode(!state.storeMode) },
                    label = stringResource(R.string.food_store_mode),
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    icon = { Icon(CuisineIcons.Basket, null, Modifier.size(16.dp)) },
                )
            }
        }
    }
}

@Composable
private fun ShoppingAddField(query: String, actions: ShoppingTabActions) {
    CouiSearchBarBox(query, actions.onQuery, stringResource(R.string.food_shopping_add_hint))
    val trimmed = query.trim()
    if (trimmed.isNotEmpty()) {
        Card(Modifier.fillMaxWidth(), onClick = { actions.onAdd(trimmed) }) {
            BasicComponent(
                title = stringResource(R.string.food_shopping_add_named, trimmed),
                startAction = { Icon(COUIIcons.Add, null, tint = COUITheme.colorScheme.primary) },
                onClick = { actions.onAdd(trimmed) },
            )
        }
    }
}

@Composable
private fun ShoppingGroupHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = COUITheme.textStyles.description, color = COUITheme.colorScheme.labelSecondary)
        Text(count.toString(), style = COUITheme.textStyles.description, color = COUITheme.colorScheme.labelTertiary)
    }
}

@Composable
private fun ShoppingRows(rows: List<ShoppingRowUi>, storeMode: Boolean, actions: ShoppingTabActions) {
    Card(Modifier.fillMaxWidth()) {
        CardListGroup {
            rows.forEach { row ->
                item {
                    ShoppingItemRow(
                        row.toItemUi(), storeMode, { actions.onCheck(row, it) },
                        onEdit = { actions.onEdit(row) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ShoppingRowUi.toItemUi() = ShoppingItemUi(
    key, row.name, Quantity(row.quantity.amount, row.quantity.unitCode ?: UnitCode.PIECE),
    when (state) {
        ShoppingRowState.TODO -> ShoppingItemStatus.TODO
        ShoppingRowState.IN_BASKET -> ShoppingItemStatus.IN_BASKET
        ShoppingRowState.STOCKED -> ShoppingItemStatus.STOCKED
    },
    origin?.label(),
    quantityLabel = row.quantity.takeIf { it.unitCode == null }?.label(),
)

/** « 2 sachets » for a Grocy unit without a code. */
internal fun ShoppingLineQuantity.label(): String =
    if (unitCode != null) FoodQuantities.formatFr(Quantity(amount, unitCode))
    else listOfNotNull(java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString().replace('.', ','), unitName).joinToString(" ")

@Composable
internal fun ShoppingOriginUi.label(): String = when (this) {
    is ShoppingOriginUi.Recipe -> stringResource(R.string.food_origin_recipe, title)
    is ShoppingOriginUi.Meal -> stringResource(
        if (meal == io.github.mangi.eta.food.Meal.DINNER) R.string.food_origin_dinner else R.string.food_origin_lunch,
        date.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE)),
    )
}

@Composable
internal fun ShoppingBottomAction(state: ShoppingTabUi, onPutAway: () -> Unit) {
    Button(onPutAway, Modifier.fillMaxWidth(), minHeight = 56.dp, colors = ButtonDefaults.buttonColorsPrimary()) {
        Icon(CuisineIcons.Fridge, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (state.storeMode) stringResource(R.string.food_finish_shopping)
            else pluralStringResource(R.plurals.food_put_away_count, state.inBasket, state.inBasket),
            style = COUITheme.textStyles.buttonL,
        )
    }
}

/** Confirmation before any stock write: each basket row with its destination. */
@Composable
internal fun PutAwaySheet(
    rows: List<ShoppingRowUi>,
    locationName: (ShoppingRowUi) -> String?,
    busy: Boolean,
    error: String?,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    FoodSheet(stringResource(R.string.food_put_away_title), onClose) {
        val stockable = rows.filter { it.canStock }
        FoodSupporting(pluralStringResource(R.plurals.food_put_away_detail, stockable.size, stockable.size))
        Card(Modifier.fillMaxWidth()) {
            CardListGroup {
                rows.forEach { row ->
                    item {
                        BasicComponent(
                            title = row.row.name,
                            summary = if (row.canStock) locationName(row) else stringResource(R.string.food_put_away_needs_product),
                            endActions = {
                                Text(row.row.quantity.label(),
                                    style = COUITheme.textStyles.headlineXS)
                            },
                        )
                    }
                }
            }
        }
        error?.let { FoodErrorMessage(it) }
        FoodAction(R.string.food_later, onClick = onClose)
        FoodAction(R.string.food_stock_update, primary = true, enabled = !busy && stockable.isNotEmpty(), onClick = onConfirm)
    }
}

/** Edit quantity, or remove the row from the list. */
@Composable
internal fun ShoppingEditSheet(
    row: ShoppingRowUi,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var amount by rememberSaveable(row.key) { mutableStateOf(java.math.BigDecimal.valueOf(row.row.quantity.amount).stripTrailingZeros().toPlainString().replace('.', ',')) }
    val parsed = amount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    FoodSheet(stringResource(R.string.food_shopping_edit_title), onClose) {
        FoodHeading(row.row.name)
        row.origin?.let { FoodSupporting(it.label()) }
        FoodTextField(
            amount, { amount = it }, label = stringResource(R.string.food_quantity_in, row.row.quantity.unitName ?: ""),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            errorLabel = if (parsed == null) R.string.food_invalid_stock_quantity else null,
        )
        FoodAction(R.string.food_save, primary = true, enabled = !busy && parsed != null) { parsed?.let(onSave) }
        FoodButton(stringResource(R.string.food_shopping_remove), onDelete, enabled = !busy)
    }
}

internal class ShoppingTabHolder(
    val state: ShoppingTabUi,
    val actions: ShoppingTabActions,
    val overlay: @Composable () -> Unit,
)

@Composable
internal fun rememberShoppingTab(snackbars: SnackbarHostState): ShoppingTabHolder {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    val snapshot by remember(repo) { repo.shopping() }.collectAsState(initial = null)
    val library by remember(repo) { repo.recipes() }.collectAsState(initial = null)
    var storeMode by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<CatalogResponse?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var puttingAway by rememberSaveable { mutableStateOf(false) }
    var putAwayId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var putAwayFailure by remember { mutableStateOf<FoodResult.Failure?>(null) }

    LaunchedEffect(repo, refresh) {
        failure = repo.refreshShopping() as? FoodResult.Failure
        loaded = true
        catalog = (repo.catalog() as? FoodResult.Success)?.value
    }

    val response = snapshot?.cache?.value
    val titles = library?.value?.recipes.orEmpty().associate { it.id to it.title }
    val projection = response?.let { projectShopping(snapshot?.items.orEmpty(), it.aisles) }
    val (groups, stocked) = projection?.toTabUi(titles, query, storeMode) ?: (emptyList<ShoppingGroupUi>() to emptyList())
    val errorText = failure?.let { stringResource(it.error.label()) }
    val state = ShoppingTabUi(
        state = when {
            response != null -> CuisineContentState.READY
            failure != null -> CuisineContentState.ERROR
            loaded -> CuisineContentState.EMPTY
            else -> CuisineContentState.LOADING
        },
        error = errorText,
        offline = response != null && failure?.error == FoodError.OFFLINE,
        groups = groups,
        stocked = stocked,
        inBasket = projection?.inBasketCount ?: 0,
        total = (projection?.remainingCount ?: 0) + (projection?.inBasketCount ?: 0),
        storeMode = storeMode,
        query = query,
    )

    fun write(action: suspend () -> FoodResult<*>, done: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            when (val result = action()) {
                is FoodResult.Success -> done()
                is FoodResult.Failure -> failure = result
            }
            busy = false
        }
    }

    val notStockable = stringResource(R.string.food_put_away_partial)
    val stockedMessage = stringResource(R.string.food_put_away_done)
    val actions = ShoppingTabActions(
        onQuery = { query = it },
        onAdd = { name ->
            val product = catalog?.products?.firstOrNull { it.name.foldForSearch() == name.foldForSearch() }
            val unit = product?.stockUnit?.grocyQuId
                ?: catalog?.quantityUnits?.firstOrNull { it.unitCode == UnitCode.PIECE }?.id
                ?: catalog?.quantityUnits?.firstOrNull()?.id
            if (unit != null) {
                val item = if (product != null) ShoppingAddItem.Product(product.id, ShoppingQuantity(1.0, unit))
                else ShoppingAddItem.FreeText(name, ShoppingQuantity(1.0, unit))
                write({ repo.addShoppingItem(ShoppingAddRequest(UUID.randomUUID().toString(), item), product) }) { query = "" }
            }
        },
        onCheck = { row, checked ->
            val target = if (checked) ShoppingBasketState.IN_BASKET else ShoppingBasketState.TODO
            val from = if (checked) ShoppingItemState.TODO else ShoppingItemState.IN_BASKET
            scope.launch {
                row.row.lines.filter { it.state == from }.forEach { line ->
                    repo.setShoppingBasketState(line.id, UUID.randomUUID().toString(), line.revision, target)
                }
            }
        },
        onEdit = { editing = it.key },
        onStoreMode = { storeMode = it },
        onPutAway = {
            putAwayFailure = null
            putAwayId = UUID.randomUUID().toString()
            puttingAway = true
        },
        onRetry = { refresh++ },
    )

    val overlay: @Composable () -> Unit = {
        val editRow = (groups.flatMap { it.rows } + stocked).firstOrNull { it.key == editing }
        if (editRow != null) {
            ShoppingEditSheet(editRow, busy, onClose = { editing = null }, onSave = { amount ->
                // One product row may aggregate several contributions: the first line takes the new total.
                val first = editRow.row.lines.first()
                val unit = first.quantity.grocyQuId ?: return@ShoppingEditSheet
                write({
                    editRow.row.lines.drop(1).forEach {
                        repo.deleteShoppingItem(it.id, ShoppingDeleteRequest(UUID.randomUUID().toString(), it.revision))
                    }
                    repo.patchShoppingItem(first.id, ShoppingPatchRequest(UUID.randomUUID().toString(), first.revision,
                        ShoppingPatch(quantity = ShoppingQuantity(amount, unit))))
                }) { editing = null }
            }, onDelete = {
                write({
                    var last: FoodResult<String> = FoodResult.Success("")
                    editRow.row.lines.forEach {
                        last = repo.deleteShoppingItem(it.id, ShoppingDeleteRequest(UUID.randomUUID().toString(), it.revision))
                    }
                    last
                }) { editing = null }
            })
        }
        if (puttingAway) {
            val rows = state.basketRows
            val products = catalog?.products.orEmpty().associateBy { it.id }
            val locations = catalog?.locations.orEmpty().associate { it.id to it.name }
            PutAwaySheet(
                rows, locationName = { row -> row.row.grocyProductId?.let { products[it]?.locationId }?.let(locations::get) },
                busy = busy, error = putAwayFailure?.let { stringResource(it.error.label()) }, onClose = { puttingAway = false },
            ) {
                val lines = rows.filter { it.canStock }.flatMap { row ->
                    val location = products[row.row.grocyProductId]?.locationId ?: return@flatMap emptyList()
                    row.row.lines.filter { it.state == ShoppingItemState.IN_BASKET }.mapNotNull { line ->
                        val unit = line.quantity.grocyQuId ?: return@mapNotNull null
                        PutAwayLine(line.id, line.revision, ShoppingQuantity(line.quantity.amount, unit), location, null,
                            grocyProductId = line.grocyProductId)
                    }
                }
                val operationId = putAwayId ?: UUID.randomUUID().toString().also { putAwayId = it }
                if (busy) return@PutAwaySheet
                busy = true
                scope.launch {
                    // The same operation id is reused on retry, so a lost answer never stocks twice.
                    when (val result = repo.putAway(PutAwayRequest(operationId, lines))) {
                        is FoodResult.Success -> {
                            puttingAway = false
                            putAwayId = null
                            val applied = result.value.lines.count { it.state == ShoppingOperationLineState.APPLIED }
                            snackbars.showSnackbar(if (applied == result.value.lines.size) stockedMessage else notStockable)
                        }
                        is FoodResult.Failure -> putAwayFailure = result
                    }
                    busy = false
                }
            }
        }
    }
    return ShoppingTabHolder(state, actions, overlay)
}
