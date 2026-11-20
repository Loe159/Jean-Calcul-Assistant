package io.github.mangi.eta.agent.device.notifications.remote

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.regex.PatternSyntaxException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Mirror of homelab-ai/src/mastra/brief-summary/contract.ts. */
internal object MastraBriefSummaryContract {
    const val PATH = "/mobile/v1/brief/summary"
    const val SCHEMA_VERSION = 1
    const val MAX_LENGTH = 220
    private val maxValidity = Duration.ofMinutes(30)
    private val uuid = Regex(
        "(?:[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}" +
            "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)",
        RegexOption.IGNORE_CASE,
    )
    private val name = Regex("[\\p{L}][\\p{L}\\p{M} '’-]*")
    private val timestamp = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(?::[0-9]{2}(?:\\.[0-9]+)?)?(?:Z|[+-][0-9]{2}:[0-9]{2})")
    private val unsafeText = run {
        val basic = "[*_\u0060~<>\\[\\]\\\\|#\\p{Cc}\\p{Cf}\\p{Cs}\\p{Zl}\\p{Zp}\\u20e3\\ufe0f"
        // Android uses ICU for regex. The JDK used by the JVM tests prefixes binary properties with Is.
        try {
            Regex(basic + "\\p{Extended_Pictographic}\\p{Emoji_Presentation}]")
        } catch (_: PatternSyntaxException) {
            Regex(basic + "\\p{IsExtended_Pictographic}\\p{IsEmoji_Presentation}]")
        }
    }

    val json = Json { encodeDefaults = true; explicitNulls = false }

    fun encodeRequest(request: MastraBriefSummaryRequest): String {
        validate(request)
        return json.encodeToString(request)
    }

    fun decodeRequest(text: String): MastraBriefSummaryRequest {
        val fields = json.parseToJsonElement(text).jsonObject
        require(fields["schema_version"] == JsonPrimitive(SCHEMA_VERSION))
        require(fields["user_first_name"] != JsonNull)
        val counts = requireNotNull(fields["notification_counts"]).jsonObject
        require(counts.values.all { it is JsonPrimitive && !it.isString && it.intOrNull != null })
        return json.decodeFromJsonElement(MastraBriefSummaryRequest.serializer(), fields).also(::validate)
    }

    fun decodeResponse(text: String): MastraBriefSummaryResponse {
        val fields = json.parseToJsonElement(text).jsonObject
        // The default serializers coerce quoted primitives. The wire contract requires JSON numbers and booleans.
        require(fields["schema_version"] == JsonPrimitive(SCHEMA_VERSION))
        require(isBoolean(fields["degraded"]))
        val segments = requireNotNull(fields["segments"]).jsonArray
        require(segments.size in 1..MAX_LENGTH && segments.all { isBoolean(it.jsonObject["emphasis"]) })
        return json.decodeFromJsonElement(MastraBriefSummaryResponse.serializer(), fields).also(::validate)
    }

    private fun isBoolean(value: JsonElement?): Boolean = value == JsonPrimitive(true) || value == JsonPrimitive(false)

    fun validate(request: MastraBriefSummaryRequest) {
        require(request.schemaVersion == SCHEMA_VERSION)
        parseTime(request.deviceTime)
        require(request.timeZone.length in 1..64 && request.timeZone in ZoneId.getAvailableZoneIds())
        require(request.locale.length in 1..64)
        Locale.Builder().setLanguageTag(request.locale).build()
        with(request.notificationCounts) { require(now >= 0 && today >= 0 && low >= 0) }
        request.userFirstName?.let { require(it.length in 1..48 && name.matches(it)) }
    }

    fun validate(response: MastraBriefSummaryResponse) {
        require(response.schemaVersion == SCHEMA_VERSION && uuid.matches(response.summaryId))
        val validity = Duration.between(parseTime(response.generatedAt), parseTime(response.validUntil))
        require(!validity.isZero && !validity.isNegative && validity <= maxValidity)
        require(response.sources.size in 1..2 && response.sources.distinct().size == response.sources.size)
        require(response.segments.size in 1..MAX_LENGTH)
        require(response.segments.all {
            it.text.isNotEmpty() && !unsafeText.containsMatchIn(it.text) && (!it.emphasis || it.text.isNotBlank())
        })
        val text = response.segments.joinToString("") { it.text }
        require(text.isNotBlank() && text.length <= MAX_LENGTH)
    }

    private fun parseTime(value: String): OffsetDateTime {
        require(timestamp.matches(value))
        return OffsetDateTime.parse(value)
    }
}

@Serializable
internal enum class MastraBriefMoment { MORNING, DAY, EVENING, NIGHT }

@Serializable
internal data class MastraBriefCounts(val now: Int, val today: Int, val low: Int)

@Serializable
internal data class MastraBriefSummaryRequest(
    @SerialName("schema_version") val schemaVersion: Int = MastraBriefSummaryContract.SCHEMA_VERSION,
    @SerialName("device_time") val deviceTime: String,
    @SerialName("time_zone") val timeZone: String,
    val locale: String,
    val moment: MastraBriefMoment,
    @SerialName("notification_counts") val notificationCounts: MastraBriefCounts,
    @SerialName("user_first_name") val userFirstName: String? = null,
)

@Serializable
internal data class MastraBriefSegment(val text: String, val emphasis: Boolean)

@Serializable
internal enum class MastraBriefSource {
    @SerialName("calendar") CALENDAR,
    @SerialName("notifications") NOTIFICATIONS,
}

@Serializable
internal data class MastraBriefSummaryResponse(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("summary_id") val summaryId: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("valid_until") val validUntil: String,
    val segments: List<MastraBriefSegment>,
    val sources: List<MastraBriefSource>,
    val degraded: Boolean,
)
