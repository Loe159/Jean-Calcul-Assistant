package io.github.mangi.eta.data.car

import android.util.Log
import io.github.mangi.eta.BuildConfig
import java.io.IOException
import java.net.URLEncoder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl

internal enum class CarReadStage(val label: String) {
    Vehicles("vehicles"), Status("status"), Maintenance("maintenance"), Alerts("alerts"),
    Stations("stations"), TokenRefresh("token refresh"),
}

internal class CarReadFailure(val stage: CarReadStage, val status: Int?, val error: Exception) :
    IOException("Car read failed", error)

internal fun logCarWarning(message: String) {
    if (BuildConfig.DEBUG) Log.w("JeanCalculCar", message)
}

internal fun carDiagnosticPath(url: HttpUrl): String = url.encodedPathSegments.mapIndexed { index, segment ->
    if (url.isVehicleIdSegment(index)) "{id}" else segment
}.joinToString("/", prefix = "/")

private fun HttpUrl.isVehicleIdSegment(index: Int): Boolean =
    pathSegments[index].isNotEmpty() && pathSegments.getOrNull(index - 1).equals("vehicles", ignoreCase = true)

@OptIn(ExperimentalSerializationApi::class)
internal fun carReadDiagnostic(
    failure: CarReadFailure,
    url: HttpUrl,
    headers: Headers?,
    body: String?,
    sensitive: List<String?>,
): String = buildString {
    val redactor = CarLogRedactor(sensitive + url.pathSegments.mapIndexedNotNull { index, segment ->
        segment.takeIf { url.isVehicleIdSegment(index) }
    })
    append(failure.stage.label).append(' ').append(redactor.text(carDiagnosticPath(url)))
    append(failure.status?.let { " HTTP $it" } ?: " HTTP —")
    headers?.forEach { (name, value) ->
        if (rateHeader.matches(name)) {
            val safeValue = value.takeIf { it.length <= 120 && (rateValue.matches(it) || httpDate.matches(it)) } ?: MASKED
            append(' ').append(redactor.text(name)).append('=').append(redactor.text(safeValue, maskDecimals = false))
        }
    }
    val error = failure.error
    append(" · ").append(error.javaClass.simpleName)
    val message = error.message.orEmpty()
    if (error is SerializationException) {
        append(": ").append(redactor.serializationMessage(message))
        if (error is MissingFieldException) {
            append(" · missing=").append(error.missingFields.joinToString(prefix = "[", postfix = "]", transform = redactor::key))
        }
        val keys = (body?.let(::parseDiagnosticJson) as? JsonObject)?.keys.orEmpty()
        append(" · keys=").append(keys.joinToString(prefix = "[", postfix = "]", transform = redactor::key))
    } else if (message.isNotBlank()) {
        append(": ").append(redactor.text(message).take(300))
    }
    if (error !is SerializationException && body != null && failure.status != null && failure.status !in 200..299) {
        append(" · body=").append(redactor.errorBody(body))
    }
}

internal fun carErrorBodyPreview(body: String): String = CarLogRedactor(emptyList()).errorBody(body)

private fun parseDiagnosticJson(body: String): JsonElement? =
    runCatching { carJson.parseToJsonElement(body) }.getOrNull()

private class CarLogRedactor(values: List<String?>) {
    private val secrets = values.filterNotNull().filter(String::isNotBlank)
        .flatMap { listOf(it, URLEncoder.encode(it, Charsets.UTF_8)) }.distinct().sortedByDescending(String::length)

    fun text(value: String, maskDecimals: Boolean = true): String {
        var safe = value
        for (secret in secrets) safe = safe.replace(secret, MASKED)
        if (assignment.findAll(safe).any { normalizedKey(it.groupValues[1]) in sensitiveFields }) return MASKED
        safe = safe.replace(urlOrCredential, MASKED).replace(vin, MASKED)
        if (maskDecimals) safe = safe.replace(coordinatePair, MASKED).replace(decimal, MASKED)
        return safe.replace(controlCharacter, " ")
    }

    fun key(value: String): String = if (fieldName.matches(value)) text(value) else MASKED

    fun errorBody(body: String): String {
        if (body.isBlank()) return ""
        // A malformed or non-JSON body can contain credentials without reliable field boundaries.
        if (body.firstOrNull { !it.isWhitespace() } !in listOf('{', '[', '"')) return MASKED
        val json = parseDiagnosticJson(body) ?: return MASKED
        return redactJson(json, depth = 0).toString().take(300)
    }

    private fun redactJson(value: JsonElement, depth: Int): JsonElement {
        if (depth >= 16) return JsonPrimitive(MASKED)
        return when (value) {
            is JsonObject -> JsonObject(value.entries.associate { (name, field) ->
                key(name) to if (normalizedKey(name) in sensitiveFields) JsonPrimitive(MASKED) else redactJson(field, depth + 1)
            })
            is JsonArray -> JsonArray(value.map { redactJson(it, depth + 1) })
            is JsonPrimitive -> {
                val safe = if (value.isString || jsonScalar.matches(value.content)) text(value.content) else MASKED
                if (safe == value.content) value else JsonPrimitive(safe)
            }
        }
    }

    fun serializationMessage(message: String): String {
        val diagnostic = message.substringBefore("\nJSON input:")
        val detail = diagnostic.substringBeforeLast(" at path: ")
        val path = diagnostic.substringAfterLast(" at path: ", "").trim()
        // Kotlinx can quote raw response values and append a JSON excerpt to the message.
        val safeDetail = detail.replace(unquotedParseValue, MASKED).let {
            val firstQuote = it.indexOfFirst { char -> char == '\'' || char == '"' }
            if (firstQuote < 0) it else {
                val lastQuote = it.indexOfLast { char -> char == '\'' || char == '"' }
                it.take(firstQuote) + MASKED + it.drop(if (lastQuote > firstQuote) lastQuote + 1 else it.length)
            }
        }
        return text(safeDetail).take(300) + if (jsonPath.matches(path)) " at path: ${text(path)}" else ""
    }
}

private const val MASKED = "[masqué]"
private fun normalizedKey(key: String): String = key.lowercase().filter(Char::isLetterOrDigit)

private val sensitiveFields = setOf(
    "accesstoken", "refreshtoken", "idtoken", "clientid", "clientsecret", "vin", "code",
    "token", "authtoken", "authorization", "password", "secret", "apikey", "codeverifier", "authorizationcode",
    "vehicleid", "vehicleidentificationnumber", "chassisnumber",
    "coord", "coords", "coordinate", "coordinates", "lat", "lon", "lng", "long", "latitude", "longitude",
    "latlng", "latlon", "position", "lastposition", "location", "geolocation", "gps",
    "geom", "geometry", "geopoint", "geojson", "point", "address", "adresse", "city", "ville",
)
private val assignment = Regex("""(?i)\b([a-z][a-z0-9_-]*)["']?\s*[:=]""")
private val urlOrCredential = Regex(
    """(?i)(?:[a-z][a-z0-9+.-]*://|https?%3a%2f%2f)\S+|\b(?:bearer|basic)\s+\S+|\beyJ[\w-]+\.[\w-]+\.[\w-]+""",
)
private val vin = Regex("""(?i)\b[A-HJ-NPR-Z0-9]{17}\b""")
private val coordinatePair = Regex(
    """(?<![\w.])[+-]?\d+(?:\.\d+)?(?:\s*[,;/]\s*[+-]?\d+(?:\.\d+)?){1,3}(?![\w.])|(?i:\b(?:MULTI)?POINT\s*(?:ZM?\s*|M\s*)?\([^)]*\))""",
)
private val decimal = Regex("""(?<![\w.])[+-]?\d+\.\d+(?:[eE][+-]?\d+)?(?![\w.])""")
private val fieldName = Regex("""[\w@.-]{1,80}""")
private val jsonScalar = Regex("""null|true|false|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?""")
private val jsonPath = Regex("""\$(?:\.[\w-]+|\[\d+\]){0,20}""")
private val controlCharacter = Regex("""\p{Cntrl}""")
private val unquotedParseValue = Regex("""(?im)(?<=Can't convert ).*(?= to Long)|(?<=but got )[^'\n]+""")
private val rateHeader = Regex("""(?i)retry-after|(?:x-)?rate-?limit(?:-[a-z0-9-]{1,40})?""")
private val rateValue = Regex("""[\d,;= .-]+|\d+(?:\s*[;,]\s*(?:[qtrw]=)?\d+)*""")
private val httpDate = Regex("""[A-Za-z]{3}, \d{1,2} [A-Za-z]{3} \d{4} \d{2}:\d{2}:\d{2} GMT""")
