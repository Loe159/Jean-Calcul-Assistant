package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.preview.CouiPhonePreviews
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Scan
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private enum class BarcodeNotice(val label: Int) {
    UNKNOWN(R.string.food_scan_unknown),
    OFFLINE(R.string.food_scan_offline),
    LOOKUP_ERROR(R.string.food_scan_lookup_error),
}

@Serializable
private data class StockEditorDraft(
    val query: String = "",
    val productId: Long? = null,
    val productName: String? = null,
    val newProduct: String? = null,
    val amount: String = "1",
    val unit: UnitCode = UnitCode.PIECE,
    val location: Long? = null,
    val date: String = "",
    val estimated: Boolean = false,
    val removing: Boolean = false,
    val spoiled: Boolean = false,
    val barcode: String? = null,
    val barcodeNotice: BarcodeNotice? = null,
) {
    fun select(product: CatalogProduct) = copy(
        productId = product.id,
        productName = product.name,
        newProduct = null,
        unit = product.stockUnit.unitCode ?: UnitCode.PIECE,
        location = product.locationId,
    )

    fun withBarcode(code: String, lookup: FoodResult<BarcodeLookupResponse>): StockEditorDraft {
        val cleared = copy(
            barcode = code, barcodeNotice = null, query = "", productId = null, productName = null,
            newProduct = null, unit = UnitCode.PIECE, location = null,
        )
        return when (lookup) {
            is FoodResult.Success -> {
                val product = lookup.value.product
                val suggestion = lookup.value.suggestion
                when {
                    product != null -> cleared.select(product)
                    suggestion != null -> cleared.copy(newProduct = suggestion.name, unit = suggestion.unit ?: UnitCode.PIECE)
                    else -> cleared.copy(barcodeNotice = BarcodeNotice.UNKNOWN)
                }
            }
            is FoodResult.Failure -> cleared.copy(
                barcodeNotice = if (lookup.error == FoodError.OFFLINE) BarcodeNotice.OFFLINE else BarcodeNotice.LOOKUP_ERROR,
            )
        }
    }
}

private val stockEditorDraftSaver = Saver<StockEditorDraft, String>(
    save = { FoodContract.json.encodeToString(it) },
    restore = { FoodContract.json.decodeFromString(it) },
)

@Composable
internal fun StockEditor(
    repo: FoodRepository,
    item: PantryItem?,
    pantry: PantryResponse?,
    onClose: () -> Unit,
    onWrite: () -> Unit,
    pendingBarcode: String? = null,
    visible: Boolean = true,
    onBarcodeHandled: () -> Unit = {},
    onScan: (() -> Unit)? = null,
    canScan: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<CatalogResponse?>(null) }
    var catalogError by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var result by remember { mutableStateOf<OperationResult?>(null) }
    var catalogRetry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = stockEditorDraftSaver) {
        mutableStateOf(StockEditorDraft(
            unit = item?.unit?.unitCode ?: UnitCode.PIECE,
            location = item?.locationId,
            removing = item != null,
        ))
    }
    var payload by rememberSaveable { mutableStateOf("") }
    var operation by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(repo, catalogRetry) {
        if (item != null) return@LaunchedEffect
        when (val fetched = repo.catalog()) {
            is FoodResult.Success -> { catalog = fetched.value; catalogError = null }
            is FoodResult.Failure -> catalogError = fetched
        }
    }
    LaunchedEffect(repo, pendingBarcode) {
        if (pendingBarcode == null) return@LaunchedEffect
        draft = draft.withBarcode(pendingBarcode, repo.barcode(pendingBarcode))
        result = null
        error = null
        onBarcodeHandled()
    }
    if (!visible) return

    val places = (catalog?.locations ?: pantry?.locations.orEmpty()).filter { it.id > 0 }
    val selectedLocation = draft.location ?: places.firstOrNull()?.id
    val selectedId = item?.grocyProductId ?: draft.productId
    val fallbackProducts = remember(pantry) {
        pantry?.items.orEmpty().map { CatalogProduct(it.grocyProductId, it.name, it.unit, it.locationId, it.minStockAmount) }
    }

    StockEditorForm(
        item = item,
        draft = draft,
        places = places,
        products = catalog?.products ?: if (draft.barcode != null) fallbackProducts else emptyList(),
        catalogReady = catalog != null,
        catalogError = catalogError,
        onCatalogRetry = { catalogRetry++ },
        lookingUp = pendingBarcode != null,
        busy = busy,
        error = error,
        result = result,
        onScan = onScan,
        canScan = canScan,
        onClose = { if (!busy) onClose() },
        onDraftChange = { draft = it },
        onSave = save@{ quantity ->
            if (busy) return@save
            val add = if (draft.removing) null else PantryAddRequest(
                operationId = "",
                amount = quantity,
                unit = draft.unit,
                grocyProductId = selectedId,
                newProduct = if (selectedId == null) {
                    NewPantryProduct(requireNotNull(draft.newProduct).trim(), draft.unit, requireNotNull(selectedLocation))
                } else null,
                locationId = selectedLocation,
                bestBeforeDate = draft.date.trim().dateOrNull(),
                estimated = draft.estimated,
                barcode = draft.barcode,
            )
            val consume = PantryConsumeRequest("", quantity, draft.unit, draft.spoiled)
            val key = if (add != null) FoodContract.json.encodeToString(add)
                else "$selectedId:${FoodContract.json.encodeToString(consume)}"
            if (key != payload || operation.isEmpty()) {
                payload = key
                operation = operationId()
            }
            val requestId = operation
            busy = true
            error = null
            result = null
            scope.launch {
                val write = if (add != null) repo.addStock(add.copy(operationId = requestId))
                    else repo.consumeStock(requireNotNull(selectedId), consume.copy(operationId = requestId))
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
        },
    )
}

@Composable
private fun StockEditorForm(
    item: PantryItem?,
    draft: StockEditorDraft,
    places: List<PantryLocation>,
    products: List<CatalogProduct>,
    catalogReady: Boolean,
    onClose: () -> Unit,
    onDraftChange: (StockEditorDraft) -> Unit,
    onSave: (Double) -> Unit,
    catalogError: FoodResult.Failure? = null,
    onCatalogRetry: () -> Unit = {},
    lookingUp: Boolean = false,
    busy: Boolean = false,
    error: FoodResult.Failure? = null,
    result: OperationResult? = null,
    onScan: (() -> Unit)? = null,
    canScan: Boolean = true,
) {
    val title = if (item == null) R.string.food_add_stock else R.string.food_correct_stock
    FoodSheet(stringResource(title), onClose) {
        if (lookingUp) {
            FoodLoading(R.string.food_scan_lookup)
            return@FoodSheet
        }
        if (error?.serverCode == BarcodeConflictCode) FoodErrorMessage(stringResource(R.string.food_scan_conflict))
        else FoodErrorText(error)
        result?.let { OperationResultLines(it) }
        if (item != null) {
            FoodHeading(item.name)
            FoodChoices(stringResource(title), listOf(true to stringResource(R.string.food_consume_stock),
                false to stringResource(R.string.food_add_stock)), draft.removing) { onDraftChange(draft.copy(removing = it)) }
        } else {
            if (onScan != null) {
                Card(Modifier.fillMaxWidth()) {
                    CardListGroup {
                        item {
                            ArrowPreference(
                                title = stringResource(if (canScan) R.string.food_scan_title else R.string.food_scan_type),
                                summary = draft.barcode,
                                enabled = !busy,
                                startAction = if (canScan) ({ Icon(COUIIcons.Scan, null, Modifier.size(24.dp)) }) else null,
                                onClick = onScan,
                            )
                        }
                    }
                }
            } else if (draft.barcode != null) {
                FoodSupporting(stringResource(R.string.food_scan_code) + " · " + draft.barcode)
            }
            draft.barcodeNotice?.let { FoodSupporting(stringResource(it.label)) }
            val choosingProduct = draft.productId == null && draft.newProduct == null
            if (choosingProduct || places.isEmpty()) {
                if (catalogError != null) {
                    if (catalogError.error != FoodError.OFFLINE || draft.barcodeNotice != BarcodeNotice.OFFLINE) {
                        FoodErrorText(catalogError)
                    }
                    FoodAction(R.string.food_retry, onClick = onCatalogRetry)
                } else if (!catalogReady && (draft.barcode == null || places.isEmpty())) {
                    FoodLoading()
                }
            }
            StockProductPicker(draft, products, catalogReady, onDraftChange)
        }

        val selectedLocation = draft.location ?: places.firstOrNull()?.id
        FoodNumberField(draft.amount, { onDraftChange(draft.copy(amount = it)) }, R.string.food_amount, decimal = true)
        OverlayDropdownPreference(
            title = stringResource(R.string.food_unit),
            items = UnitCode.entries.map { unitName(it) },
            selectedIndex = draft.unit.ordinal,
            modifier = Modifier.heightIn(min = 60.dp),
            onSelectedIndexChange = { index -> onDraftChange(draft.copy(unit = UnitCode.entries[index])) },
        )
        if (!draft.removing) {
            FoodChoices(stringResource(R.string.food_location), places.map { it.id to locationName(it) }, selectedLocation) {
                onDraftChange(draft.copy(location = it))
            }
            FoodTextField(draft.date, { onDraftChange(draft.copy(date = it)) }, label = stringResource(R.string.food_date))
            FoodSwitchRow(stringResource(R.string.food_estimated), draft.estimated) {
                onDraftChange(draft.copy(estimated = it))
            }
        } else {
            FoodSwitchRow(stringResource(R.string.food_remove_spoiled), draft.spoiled) {
                onDraftChange(draft.copy(spoiled = it))
            }
        }
        val value = draft.amount.amountOrNull()?.takeIf { it > 0 }
        val validDate = draft.date.isBlank() || draft.date.trim().dateOrNull() != null
        val canSave = value != null && (item != null || draft.productId != null || !draft.newProduct.isNullOrBlank()) &&
            (draft.removing || (validDate && places.any { it.id == selectedLocation }))
        FoodAction(R.string.food_save, primary = true, enabled = canSave && !busy) {
            if (canSave && !busy) onSave(requireNotNull(value))
        }
    }
}

@Composable
private fun StockProductPicker(
    draft: StockEditorDraft,
    products: List<CatalogProduct>,
    catalogReady: Boolean,
    onDraftChange: (StockEditorDraft) -> Unit,
) {
    if (draft.productId != null || draft.newProduct != null) {
        if (draft.newProduct != null) {
            FoodTextField(draft.newProduct, { onDraftChange(draft.copy(newProduct = it)) },
                label = stringResource(R.string.food_scan_suggested_name))
        } else {
            FoodHeading(draft.productName.orEmpty())
        }
        FoodAction(R.string.food_catalog_search) {
            onDraftChange(draft.copy(productId = null, productName = null, newProduct = null))
        }
        return
    }
    Box(Modifier.fillMaxWidth()) {
        CouiSearchBarBox(draft.query, { onDraftChange(draft.copy(query = it)) }, stringResource(R.string.food_catalog_search))
    }
    val search = draft.query.trim()
    if (search.isEmpty() && draft.barcode != null) return
    val candidates = products.asSequence().filter { it.name.contains(search, ignoreCase = true) }.take(8).toList()
    val canCreate = search.isNotEmpty() && (catalogReady || draft.barcode != null)
    if (candidates.isNotEmpty() || canCreate) CardListGroup {
        candidates.forEach { candidate ->
            item { ArrowPreference(title = candidate.name, onClick = { onDraftChange(draft.select(candidate)) }) }
        }
        if (canCreate) item {
            ArrowPreference(
                title = stringResource(R.string.food_new_product, search),
                onClick = { onDraftChange(draft.copy(newProduct = search)) },
            )
        }
    }
}

@Composable
private fun locationName(location: PantryLocation): String = when (location.name.lowercase()) {
    "fridge", "refrigerator", "frigo" -> stringResource(R.string.food_fridge)
    "freezer" -> stringResource(R.string.food_freezer)
    "cupboards", "pantry", "cupboard" -> stringResource(R.string.food_cupboards)
    else -> location.name
}

private const val PreviewBarcode = "2001234000017"
private val previewProduct = CatalogProduct(31, "Farine de sarrasin", StockUnit(2, "g", UnitCode.G), 3, 0.0)
private val previewSuggestion = BarcodeSuggestion("Purée de poires", null, null, UnitCode.G, null, BarcodeSuggestionSource.OPEN_FOOD_FACTS)

@Composable
private fun BarcodeEditorPreview(lookup: BarcodeLookupResponse) {
    CouiPreviewTheme {
        val places = listOf(PantryLocation(1, "Frigo"), PantryLocation(2, "Congélateur"), PantryLocation(3, "Placard"))
        CompositionLocalProvider(LocalFoodSheetPreview provides { title, close, body -> OpenFoodSheetPreview(title, close, body) }) {
            FoodPage(stringResource(R.string.food_inventory), {}, overlay = {
                var draft by remember(lookup) { mutableStateOf(StockEditorDraft().withBarcode(lookup.barcode, FoodResult.Success(lookup))) }
                StockEditorForm(null, draft, places, listOf(previewProduct), catalogReady = true,
                    onClose = {}, onDraftChange = { draft = it }, onSave = {}, onScan = {})
            }) {
                InventorySearchActions("", {}, {}, {})
            }
        }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun FoodBarcodeKnownProductPreview() = BarcodeEditorPreview(BarcodeLookupResponse(1, PreviewBarcode, previewProduct, null))

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun FoodBarcodeSuggestionPreview() = BarcodeEditorPreview(BarcodeLookupResponse(1, PreviewBarcode, null, previewSuggestion))

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun FoodBarcodeUnknownPreview() = BarcodeEditorPreview(BarcodeLookupResponse(1, PreviewBarcode, null, null))
