package io.github.mangi.eta.food

internal const val BarcodeConflictCode = "BARCODE_CONFLICT"

private val barcodePattern = Regex("[0-9]{8,14}")

internal fun String.isFoodBarcode(): Boolean = barcodePattern.matches(this)

internal enum class RetailBarcodeFormat(val length: Int) {
    EAN_8(8), EAN_13(13), UPC_A(12), UPC_E(8), ITF_14(14),
}

internal fun normalizeRetailBarcode(raw: String, format: RetailBarcodeFormat): String? {
    if (raw.length != format.length || !raw.isFoodBarcode()) return null
    val code = if (format == RetailBarcodeFormat.UPC_E) expandUpcE(raw) ?: return null else raw
    val sum = code.dropLast(1).reversed().mapIndexed { index, digit ->
        digit.digitToInt() * if (index % 2 == 0) 3 else 1
    }.sum()
    return code.takeIf { (sum + code.last().digitToInt()) % 10 == 0 }
}

private fun expandUpcE(code: String): String? {
    val numberSystem = code.first()
    if (numberSystem != '0' && numberSystem != '1') return null
    val payload = code.substring(1, 7)
    val last = payload.last()
    val expanded = when (last) {
        '0', '1', '2' -> "${payload.take(2)}${last}0000${payload.substring(2, 5)}"
        '3' -> "${payload.take(3)}00000${payload.substring(3, 5)}"
        '4' -> "${payload.take(4)}00000${payload[4]}"
        else -> "${payload.take(5)}0000$last"
    }
    return "$numberSystem$expanded${code.last()}"
}

/** Accept a single unambiguous code in two consecutive analysed frames. */
internal class BarcodeConfirmation {
    private var previous: String? = null
    private var accepted = false

    fun onFrame(codes: Iterable<String>): String? {
        if (accepted) return null
        val current = codes.toSet().singleOrNull()
        val confirmed = current != null && previous == current
        previous = current
        if (!confirmed) return null
        accepted = true
        return current
    }

    fun resetFrame() {
        previous = null
    }
}
