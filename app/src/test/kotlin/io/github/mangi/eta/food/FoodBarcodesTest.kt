package io.github.mangi.eta.food

import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test

class FoodBarcodesTest {
    @Test
    fun validatesManualContractAndPreservesLeadingZeroes() {
        listOf("00123456", "00012345678901", "001234567").forEach { assertTrue(it.isFoodBarcode()) }
        listOf("", "1234567", "123456789012345", " 00123456", "0012345a", "００１２３４５６").forEach {
            assertFalse(it.isFoodBarcode())
        }
    }

    @Test
    fun validatesCameraFormatsAndCheckDigits() {
        val examples = mapOf(
            RetailBarcodeFormat.EAN_8 to "20012342",
            RetailBarcodeFormat.EAN_13 to "2001234000017",
            RetailBarcodeFormat.UPC_A to "012345678905",
            RetailBarcodeFormat.ITF_14 to "12001234000014",
        )
        examples.forEach { (format, code) ->
            assertEquals(code, normalizeRetailBarcode(code, format))
            assertNull(normalizeRetailBarcode(code.dropLast(1) + "0", format))
            assertNull(normalizeRetailBarcode("0$code", format))
        }
        assertNull(normalizeRetailBarcode("20012342", RetailBarcodeFormat.ITF_14))
        assertNull(normalizeRetailBarcode("https://example.test", RetailBarcodeFormat.EAN_13))
    }

    @Test
    fun expandsEachUpcEPatternToUpcA() {
        val pairs = mapOf(
            "01234505" to "012000003455",
            "01234514" to "012100003454",
            "01234523" to "012200003453",
            "01234531" to "012300000451",
            "01234543" to "012340000053",
            "11234555" to "112345000055",
        )
        pairs.forEach { (upcE, upcA) -> assertEquals(upcA, normalizeRetailBarcode(upcE, RetailBarcodeFormat.UPC_E)) }
        assertNull(normalizeRetailBarcode("01234556", RetailBarcodeFormat.UPC_E))
        assertNull(normalizeRetailBarcode("21234552", RetailBarcodeFormat.UPC_E))
    }

    @Test
    fun confirmsOneIdenticalCodeInConsecutiveUnambiguousFramesOnlyOnce() {
        val confirmation = BarcodeConfirmation()
        val first = "20012342"
        val second = "012345678905"
        assertNull(confirmation.onFrame(listOf(first)))
        assertNull(confirmation.onFrame(listOf(second)))
        assertNull(confirmation.onFrame(emptyList()))
        assertNull(confirmation.onFrame(listOf(second)))
        assertNull(confirmation.onFrame(listOf(first, second)))
        assertNull(confirmation.onFrame(listOf(second)))
        confirmation.resetFrame()
        assertNull(confirmation.onFrame(listOf(second)))
        assertEquals(second, confirmation.onFrame(listOf(second, second)))
        assertNull(confirmation.onFrame(listOf(second)))
        assertNull(confirmation.onFrame(listOf(first)))
    }

    @Test
    fun decodesCatalogProductSuggestionAndUnknownCode() {
        val product = """{"id":31,"name":"Farine de sarrasin","stockUnit":{"grocyQuId":2,"name":"g","unitCode":"g"},"locationId":3,"minStockAmount":0.0}"""
        val known = lookup(product = product)
        assertEquals(CatalogProduct(31, "Farine de sarrasin", StockUnit(2, "g", UnitCode.G), 3, 0.0), known.product)
        assertNull(known.suggestion)
        val suggested = lookup(suggestion = suggestion)
        assertEquals(BarcodeSuggestion("Purée de poires", "Verger Démo", "500 g", UnitCode.G, null,
            BarcodeSuggestionSource.OPEN_FOOD_FACTS), suggested.suggestion)
        assertNull(suggested.product)
        assertEquals(BarcodeLookupResponse(1, "2001234000017", null, null), lookup())
        assertNull(lookup(suggestion = suggestion.replace("\"g\"", "null")).suggestion?.unit)
    }

    @Test
    fun rejectsUnknownSchemaSourceAndUnits() {
        assertThrows(SerializationException::class.java) { lookup(schemaVersion = 2) }
        assertThrows(SerializationException::class.java) {
            lookup(suggestion = suggestion.replace("OPEN_FOOD_FACTS", "NEW_SOURCE"))
        }
        assertThrows(SerializationException::class.java) {
            lookup(suggestion = suggestion.replace("\"g\"", "\"new-unit\""))
        }
    }

    @Test
    fun addsOptionalBarcodeToBothPantryPayloads() {
        val code = "00123456"
        val existing = PantryAddRequest("", 2.0, UnitCode.PIECE, grocyProductId = 31)
        val new = PantryAddRequest("", 2.0, UnitCode.PIECE, newProduct = NewPantryProduct("Produit démo", UnitCode.PIECE, 3))
        for (request in listOf(existing, new)) {
            val json = FoodContract.json
            val initial = json.encodeToString(request)
            val withCode = json.encodeToString(request.copy(barcode = code))
            assertFalse(initial.contains("\"barcode\""))
            assertTrue(withCode.contains("\"barcode\":\"$code\""))
            assertNotEquals(initial, withCode)
            assertEquals(request, json.decodeFromString<PantryAddRequest>(initial))
            assertEquals(request.copy(barcode = code), json.decodeFromString<PantryAddRequest>(withCode))
        }
    }

    private fun lookup(product: String = "null", suggestion: String = "null", schemaVersion: Int = 1) =
        FoodContract.decode<BarcodeLookupResponse>(
            """{"schemaVersion":$schemaVersion,"barcode":"2001234000017","product":$product,"suggestion":$suggestion,"futureField":true}""",
        )

    private val suggestion =
        """{"name":"Purée de poires","brand":"Verger Démo","quantityLabel":"500 g","unit":"g","imageUrl":null,"source":"OPEN_FOOD_FACTS"}"""
}
