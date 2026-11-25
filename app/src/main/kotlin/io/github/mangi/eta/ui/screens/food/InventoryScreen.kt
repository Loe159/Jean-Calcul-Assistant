package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Timer
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.LocalDate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.launch

internal const val ExpiringInventoryFilter = "expiring"

@Composable
internal fun InventoryScreen(filter: String?, onBack: () -> Unit) {
    val slots = inventoryPageSlots(filter == ExpiringInventoryFilter)
    FoodPage(stringResource(R.string.food_inventory), onBack, overlay = slots.overlay) {
        slots.content()
    }
}

/** One row per product and place: its batches there summed, dated by the earliest best-before. */
internal data class PantryRowModel(
    val product: PantryItem,
    val kind: LocationKind,
    val locationName: String?,
    val amount: Double,
    val bestBefore: LocalDate?,
)

/** The configured Grocy location kind, or a guess from the location name when Mastra has none. */
internal fun locationKindOf(kind: LocationKind?, name: String?): LocationKind = kind?.takeIf { it != LocationKind.OTHER }
    ?: when (locationRank(name.orEmpty())) {
        0 -> LocationKind.FRIDGE
        1 -> LocationKind.FREEZER
        2 -> LocationKind.PANTRY
        else -> kind ?: LocationKind.OTHER
    }

internal fun PantryResponse.rows(): List<PantryRowModel> {
    val names = locations.associate { it.id to it.name }
    return items.filter { it.amount > 0 && it.status != PantryStatus.OUT_OF_STOCK }.flatMap { product ->
        val batches = product.batches.ifEmpty {
            listOf(PantryBatch("", product.amount, product.bestBeforeDate, product.locationId, false))
        }
        batches.groupBy { locationKindOf(it.locationKind, names[it.locationId]) }.map { (kind, lots) ->
            PantryRowModel(
                product, kind, names[lots.first().locationId], lots.sumOf { it.amount },
                lots.mapNotNull { it.bestBeforeDate?.let { date -> runCatching { LocalDate.parse(date) }.getOrNull() } }.minOrNull(),
            )
        }
    }
}

internal fun PantryRowModel.toRowUi(today: LocalDate) = FoodStockRowUi(
    "${product.grocyProductId}:$kind", product.name, amount, product.unit,
    when (kind) {
        LocationKind.FRIDGE -> FoodStockPlace.FRIDGE
        LocationKind.FREEZER -> FoodStockPlace.FREEZER
        LocationKind.PANTRY -> FoodStockPlace.PANTRY
        LocationKind.OTHER -> FoodStockPlace.OTHER
    },
    locationName, bestBefore, bestBefore?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) },
    product.status, product.confidence,
)

/** Soon = expired, today, or within two days (the prototype's « À consommer bientôt »). */
internal fun PantryRowModel.isSoon(today: LocalDate) = bestBefore != null && !bestBefore.isAfter(today.plusDays(2))

@Composable
internal fun inventoryPageSlots(expiringInitially: Boolean, onFindRecipe: (() -> Unit)? = null): FoodPageSlots {
    val repo = foodRuntime().repository
    val snapshot by remember(repo) { repo.pantry() }.collectAsState(initial = null)
    var place by rememberSaveable { mutableStateOf<LocationKind?>(null) }
    var soonOnly by rememberSaveable { mutableStateOf(expiringInitially) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var correcting by rememberSaveable { mutableStateOf<Long?>(null) }
    val pantry = snapshot?.value
    val today = remember { LocalDate.now() }
    LaunchedEffect(repo, refresh) { error = repo.refreshPantry() as? FoodResult.Failure }

    return FoodPageSlots(
        content = {
            InventoryContent(
                pantry = pantry, today = today, error = error, place = place, soonOnly = soonOnly, query = query,
                onQuery = { query = it }, onPlace = { place = it }, onSoonOnly = { soonOnly = it },
                onAdd = { adding = true }, onRow = { correcting = it.product.grocyProductId },
                onRetry = { refresh++ }, onFindRecipe = onFindRecipe,
            )
        },
        overlay = {
            if (adding) StockEditor(null, pantry?.locations.orEmpty(), { adding = false }) { refresh++ }
            pantry?.items?.firstOrNull { it.grocyProductId == correcting }?.let { item ->
                key(item.grocyProductId) {
                    StockEditor(item, pantry.locations, { correcting = null }) { refresh++ }
                }
            }
        },
    )
}

@Composable
internal fun InventoryContent(
    pantry: PantryResponse?,
    today: LocalDate,
    error: FoodResult.Failure?,
    place: LocationKind?,
    soonOnly: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onPlace: (LocationKind?) -> Unit,
    onSoonOnly: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onRow: (PantryRowModel) -> Unit,
    onRetry: () -> Unit,
    onFindRecipe: (() -> Unit)?,
) {
    val colors = COUITheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { CouiSearchBarBox(query, onQuery, stringResource(R.string.food_pantry_search)) }
        CuisineIconButton(COUIIcons.Add, stringResource(R.string.food_add_stock), onAdd, fill = colors.container8)
    }
    if (pantry == null) {
        if (error == null) repeat(4) { CuisineLoading() }
        else CuisineMessage(stringResource(R.string.food_pantry_unavailable), detail = stringResource(error.error.label()),
            icon = CuisineIcons.Warning, isError = true, onRetry = onRetry)
        return
    }
    val all = pantry.rows()
    if (all.isEmpty()) {
        CuisineMessage(stringResource(R.string.food_pantry_empty), detail = stringResource(R.string.food_pantry_empty_detail),
            icon = CuisineIcons.Fridge)
        FoodAction(R.string.food_add_stock, primary = true, onClick = onAdd)
        return
    }
    val words = query.trim().foldForSearch()
    val counts = all.groupingBy { it.kind }.eachCount()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FoodChoiceChip(stringResource(R.string.food_place_all, all.size), place == null && !soonOnly) { onSoonOnly(false); onPlace(null) }
        listOf(LocationKind.FRIDGE, LocationKind.FREEZER, LocationKind.PANTRY, LocationKind.OTHER).forEach { kind ->
            val count = counts[kind] ?: return@forEach
            FoodChoiceChip(stringResource(R.string.food_place_count, placeLabel(kind), count), place == kind && !soonOnly) {
                onSoonOnly(false); onPlace(kind)
            }
        }
    }
    val visible = all.filter { (place == null || it.kind == place) && (words.isEmpty() || it.product.name.foldForSearch().contains(words)) }
        .filter { !soonOnly || it.isSoon(today) }
    val soon = all.filter { it.isSoon(today) }.sortedBy { it.bestBefore }
    if (soon.isNotEmpty() && words.isEmpty() && !soonOnly) SoonCard(soon, today, onFindRecipe)
    if (error != null) FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(pantry.stockAsOf)) + " · " +
        stringResource(error.error.label()))
    if (visible.isEmpty()) {
        CuisineMessage(stringResource(R.string.food_no_results), icon = CuisineIcons.Fridge)
        return
    }
    visible.groupBy { it.kind }.toSortedMap().forEach { (kind, rows) ->
        Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(placeIcon(kind), null, Modifier.size(16.dp), tint = colors.labelSecondary)
            Text(stringResource(R.string.food_place_count, placeLabel(kind), rows.size), style = COUITheme.textStyles.description,
                color = colors.labelSecondary)
        }
        Card(Modifier.fillMaxWidth()) {
            CardListGroup {
                rows.sortedWith(compareBy<PantryRowModel, LocalDate?>(nullsLast()) { it.bestBefore }.thenBy { it.product.name.lowercase() })
                    .forEach { row -> item { FoodStockRow(row.toRowUi(today), { onRow(row) }) } }
            }
        }
    }
    FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(pantry.stockAsOf)))
}

@Composable
private fun SoonCard(rows: List<PantryRowModel>, today: LocalDate, onFindRecipe: (() -> Unit)?) {
    val colors = COUITheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = SquircleShape(24.dp), color = colors.containerOrange.copy(alpha = if (COUITheme.isDark) 1f else 0.7f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(COUIIcons.Timer, null, Modifier.size(18.dp), tint = cuisineTint(colors.orange))
                Text(stringResource(R.string.food_use_soon_title), style = COUITheme.textStyles.headlineXS)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.take(6).forEach { row ->
                    val days = row.bestBefore?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) } ?: 0
                    CuisineBadge("${row.product.name} · ${expiryWords(days)}", fill = colors.cardBackground)
                }
            }
            if (onFindRecipe != null) {
                Button(onFindRecipe, minHeight = 48.dp, minWidth = 48.dp, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Icon(CuisineIcons.Sparkle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.food_find_recipe), style = COUITheme.textStyles.buttonM)
                }
            }
        }
    }
}

@Composable
private fun expiryWords(days: Long): String = when {
    days < 0 -> stringResource(R.string.food_expired)
    days == 0L -> stringResource(R.string.food_today).lowercase()
    days == 1L -> stringResource(R.string.food_tomorrow).lowercase()
    else -> stringResource(R.string.food_in_days, days)
}

@Composable
internal fun placeLabel(kind: LocationKind): String = stringResource(when (kind) {
    LocationKind.FRIDGE -> R.string.food_place_fridge
    LocationKind.FREEZER -> R.string.food_place_freezer
    LocationKind.PANTRY -> R.string.food_place_pantry
    LocationKind.OTHER -> R.string.food_place_other
})

internal fun placeIcon(kind: LocationKind) = when (kind) {
    LocationKind.FREEZER -> CuisineIcons.Freezer
    LocationKind.PANTRY -> CuisineIcons.Pantry
    else -> CuisineIcons.Fridge
}


@Composable
private fun StockEditor(item: PantryItem?, locations: List<PantryLocation>, onClose: () -> Unit, onWrite: () -> Unit) {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<CatalogResponse?>(null) }
    var catalogError by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var result by remember { mutableStateOf<OperationResult?>(null) }
    var catalogRetry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var productId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newProduct by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(item?.unit?.unitCode ?: UnitCode.PIECE) }
    var location by rememberSaveable { mutableStateOf(item?.locationId) }
    var date by rememberSaveable { mutableStateOf("") }
    var estimated by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(item != null) }
    var spoiled by rememberSaveable { mutableStateOf(false) }
    var payload by rememberSaveable { mutableStateOf("") }
    var operation by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(repo, catalogRetry) {
        if (item != null) return@LaunchedEffect
        when (val fetched = repo.catalog()) {
            is FoodResult.Success -> { catalog = fetched.value; catalogError = null }
            is FoodResult.Failure -> catalogError = fetched
        }
    }
    val places = (catalog?.locations ?: locations).filter { it.id > 0 }
    val selectedLocation = location ?: places.firstOrNull()?.id
    val product = catalog?.products?.firstOrNull { it.id == productId }
    val title = if (item == null) R.string.food_add_stock else R.string.food_correct_stock

    FoodSheet(stringResource(title), { if (!busy) onClose() }) {
        FoodErrorText(error)
        result?.let { OperationResultLines(it) }
        if (item != null) {
            FoodHeading(item.name)
            FoodChoices(stringResource(title), listOf(true to stringResource(R.string.food_consume_stock),
                false to stringResource(R.string.food_add_stock)), removing) { removing = it }
        } else {
            FoodErrorText(catalogError)
            if (catalogError != null) FoodAction(R.string.food_retry) { catalogRetry++ }
            if (catalog == null && catalogError == null) FoodLoading()
            if (product == null && newProduct == null) {
                CouiSearchBarBox(query, { query = it }, stringResource(R.string.food_catalog_search))
                val search = query.trim()
                val candidates = catalog?.products.orEmpty().asSequence()
                    .filter { it.name.contains(search, ignoreCase = true) }.take(8).toList()
                val canCreate = search.isNotEmpty() && catalog != null
                if (candidates.isNotEmpty() || canCreate) CardListGroup {
                    candidates.forEach { candidate ->
                        item {
                            ArrowPreference(title = candidate.name, onClick = {
                                productId = candidate.id
                                unit = candidate.stockUnit.unitCode ?: UnitCode.PIECE
                                location = candidate.locationId
                            })
                        }
                    }
                    if (canCreate) item {
                        ArrowPreference(
                            title = stringResource(R.string.food_new_product, search),
                            onClick = { newProduct = search },
                        )
                    }
                }
            } else {
                FoodHeading(product?.name ?: newProduct.orEmpty())
                FoodAction(R.string.food_catalog_search) {
                    productId = null
                    newProduct = null
                }
            }
        }
        FoodNumberField(amount, { amount = it }, R.string.food_amount, decimal = true)
        FoodChoices(stringResource(R.string.food_unit), UnitCode.entries.map { it to unitName(it) }, unit) { unit = it }
        if (!removing) {
            FoodChoices(stringResource(R.string.food_location), places.map { it.id to locationName(it) }, selectedLocation) { location = it }
            FoodTextField(date, { date = it }, label = stringResource(R.string.food_date))
            SwitchPreference(title = stringResource(R.string.food_estimated), checked = estimated, onCheckedChange = { estimated = it })
        } else {
            SwitchPreference(title = stringResource(R.string.food_remove_spoiled), checked = spoiled, onCheckedChange = { spoiled = it })
        }
        val value = amount.amountOrNull()?.takeIf { it > 0 }
        val validDate = date.isBlank() || date.trim().dateOrNull() != null
        val selectedId = item?.grocyProductId ?: product?.id
        val canSave = value != null && (selectedId != null || !newProduct.isNullOrBlank()) &&
            (removing || (validDate && selectedLocation in places.map { it.id }))
        FoodAction(R.string.food_save, primary = true, enabled = canSave && !busy) {
            val quantity = requireNotNull(value)
            val add = if (removing) null else PantryAddRequest("", quantity, unit, selectedId,
                if (selectedId == null) NewPantryProduct(requireNotNull(newProduct), unit, requireNotNull(selectedLocation)) else null,
                selectedLocation, date.trim().dateOrNull(), estimated)
            val consume = PantryConsumeRequest("", quantity, unit, spoiled)
            val key = if (add != null) FoodContract.json.encodeToString(add)
                else "$selectedId:${FoodContract.json.encodeToString(consume)}"
            if (key != payload || operation.isEmpty()) {
                payload = key
                operation = operationId()
            }
            busy = true
            error = null
            scope.launch {
                val write = if (add != null) repo.addStock(add.copy(operationId = operation))
                else repo.consumeStock(requireNotNull(selectedId), consume.copy(operationId = operation))
                when (write) {
                    is FoodResult.Success -> {
                        result = write.value
                        onWrite()
                        if (write.value.state == OperationState.SUCCEEDED) onClose()
                    }
                    is FoodResult.Failure -> error = write
                }
                busy = false
            }
        }
    }
}

private fun locationRank(name: String): Int {
    val key = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    return when {
        key.contains("freezer") || key.contains("congel") -> 1
        key.contains("fridge") || key.contains("refriger") || key.contains("frigo") -> 0
        key.contains("placard") || key.contains("cupboard") || key.contains("pantry") -> 2
        else -> 3
    }
}

@Composable
private fun locationName(location: PantryLocation): String = when (location.name.lowercase()) {
    "fridge", "refrigerator", "frigo" -> stringResource(R.string.food_fridge)
    "freezer" -> stringResource(R.string.food_freezer)
    "cupboards", "pantry", "cupboard" -> stringResource(R.string.food_cupboards)
    else -> location.name
}
