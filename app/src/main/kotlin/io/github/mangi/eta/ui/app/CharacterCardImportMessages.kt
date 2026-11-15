package io.github.mangi.eta.ui.app

/** 导入只公开受控分类，文件内容与底层异常文本不进入界面。 */
internal fun characterCardImportMessage(code: String?): String? = when (code) {
    "CARD_V3_INVALID" -> "The PNG's V3 character data is corrupt. No V2 fallback was used. Download the original card again."
    "CARD_METADATA_MISSING" -> "This PNG has no character card data. Select the original image rather than a screenshot or compressed copy."
    "CARD_INVALID_JSON" -> "Invalid character card JSON. Check that the file is complete."
    "CARD_TOO_LARGE" -> "Character card exceeds the size limit. Simplify it or select a smaller file."
    "CARD_INVALID_UTF8" -> "Invalid character card encoding. Use a UTF-8 JSON file."
    "CARD_UNSUPPORTED_SPEC" -> "Unsupported character card format. Export a V2 or V3 card and try again."
    "CARD_INVALID_PNG" -> "Corrupt or incomplete PNG. Download the original card again."
    "CARD_INVALID_DATA" -> "Invalid character card fields. Check its name, text, greetings and embedded worldbook."
    else -> null
}
