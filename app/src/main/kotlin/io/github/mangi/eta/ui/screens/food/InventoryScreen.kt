package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.launch

internal const val ExpiringInventoryFilter = "expiring"

@Composable
internal fun InventoryScreen(filter: String?, onBack: () -> Unit) {
    FoodPage(stringResource(R.string.food_inventory), onBack) {
        InventoryContent(filter == ExpiringInventoryFilter)
    }
}

@Composable
internal fun InventoryContent(expiringInitially: Boolean) {
    val repo = foodRuntime().repository
    val snapshot by remember(repo) { repo.pantry() }.collectAsState(initial = null)
    var expiring by rememberSaveable { mutableStateOf(expiringInitially) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var correcting by rememberSaveable { mutableStateOf<Long?>(null) }
    val pantry = snapshot?.value
    LaunchedEffect(repo, refresh) { error = repo.refreshPantry() as? FoodResult.Failure }

    FoodAction(R.string.food_add_stock, primary = true) { adding = true }
    CouiChipTabs(listOf(false to stringResource(R.string.food_filter_all), true to stringResource(R.string.food_filter_expiring)),
        expiring, { expiring = it })
    FoodErrorText(error)
    if (pantry != null || error != null) FoodAction(R.string.food_refresh) { refresh++ }
    if (pantry == null) {
        if (error == null) FoodSupporting(stringResource(R.string.food_loading))
    } else {
        FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(pantry.stockAsOf)))
        val items = pantry.items.filter { !expiring || it.status == PantryStatus.DUE_SOON || it.status == PantryStatus.EXPIRED }
        val groups = items.groupBy { it.locationId }
        if (groups.isEmpty()) FoodSupporting(stringResource(R.string.food_no_stock))
        val locations = pantry.locations.associateBy { it.id }
        groups.entries.sortedWith(compareBy({ locationRank(locations[it.key]?.name.orEmpty()) }, { locations[it.key]?.name.orEmpty() }))
            .forEach { (id, rows) ->
                FoodHeading(locations[id]?.let { locationName(it) } ?: stringResource(R.string.food_pantry_other))
                rows.sortedBy { it.name.lowercase() }.forEach { product ->
                    StockProductRow(product) { correcting = product.grocyProductId }
                }
            }
    }
    if (adding) StockEditor(null, pantry?.locations.orEmpty(), { adding = false }) { refresh++ }
    pantry?.items?.firstOrNull { it.grocyProductId == correcting }?.let { item ->
        key(item.grocyProductId) {
            StockEditor(item, pantry.locations, { correcting = null }) { refresh++ }
        }
    }
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
            if (product == null && newProduct == null) {
                CouiSearchBarBox(query, { query = it }, stringResource(R.string.food_catalog_search))
                catalog?.products.orEmpty().asSequence()
                    .filter { it.name.contains(query.trim(), ignoreCase = true) }.take(8).forEach { candidate ->
                        FoodButton(candidate.name, {
                            productId = candidate.id
                            unit = candidate.stockUnit.unitCode ?: UnitCode.PIECE
                            location = candidate.locationId
                        })
                    }
                if (query.isNotBlank() && catalog != null) {
                    FoodButton(stringResource(R.string.food_new_product, query.trim()), { newProduct = query.trim() })
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
            CouiTextField(date, { date = it }, label = stringResource(R.string.food_date))
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
