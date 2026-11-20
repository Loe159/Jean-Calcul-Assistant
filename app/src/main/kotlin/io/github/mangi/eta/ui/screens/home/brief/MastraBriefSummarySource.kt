package io.github.mangi.eta.ui.screens.home.brief

import android.content.Context
import android.net.ConnectivityManager
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefMoment
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSession
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSummaryContract
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSummaryRequest
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSummaryResponse
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationClient
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

internal class MastraBriefSummarySource(
    private val loadSession: () -> MastraBriefSession?,
    private val writeCache: (Long, String?) -> Boolean,
    private val hasNetwork: () -> Boolean,
    private val fetch: suspend (MastraPairing, MastraBriefSummaryRequest) -> MastraBriefSummaryResponse =
        { pairing, request -> MastraNotificationClient(pairing).briefSummary(request) },
    private val clock: Clock = Clock.systemUTC(),
) : BriefSummarySource {
    constructor(context: Context) : this(
        loadSession = { MastraConnection.briefSession(context) },
        writeCache = { revision, value -> MastraConnection.writeBriefCache(context, revision, value) },
        hasNetwork = { context.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null },
    )

    override suspend fun summarize(input: BriefSummaryInput): BriefSummary = withContext(Dispatchers.IO) {
        val local = localBriefSummary(input)
        val counts = input.counts ?: return@withContext local
        try {
            val session = loadSession() ?: return@withContext local
            val currentInput = input.copy(now = clock.instant().atZone(input.now.zone))
            val stored = session.cache
            if (stored != null) {
                val cached = decodeCache(stored)
                if (cached?.remote?.accepts(currentInput, clock.instant()) == true) return@withContext cached
                if (!writeCache(session.revision, null)) return@withContext local
            }
            if (!hasNetwork()) return@withContext local

            val request = MastraBriefSummaryRequest(
                deviceTime = currentInput.now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                timeZone = currentInput.scope.timeZone,
                locale = currentInput.locale,
                moment = MastraBriefMoment.valueOf(currentInput.scope.moment.name),
                notificationCounts = counts,
            )
            ensureActive()
            val response = fetch(session.pairing, request)
            ensureActive()
            val entry = BriefSummaryCacheEntry(request, response)
            val summary = entry.toSummary()
            if (!clock.instant().isBefore(requireNotNull(summary.remote).until)) return@withContext local
            if (!writeCache(session.revision, MastraBriefSummaryContract.json.encodeToString(entry))) {
                return@withContext local
            }
            summary
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            local
        }
    }

    private fun decodeCache(value: String): BriefSummary? = try {
        MastraBriefSummaryContract.json.decodeFromString<BriefSummaryCacheEntry>(value).toSummary()
    } catch (_: Exception) {
        null
    }
}

@Serializable
private data class BriefSummaryCacheEntry(
    val request: MastraBriefSummaryRequest,
    val response: MastraBriefSummaryResponse,
) {
    fun toSummary(): BriefSummary {
        MastraBriefSummaryContract.validate(request)
        MastraBriefSummaryContract.validate(response)
        return BriefSummary(
            parts = response.segments.map { BriefSummaryPart(it.text, it.emphasis) },
            remote = RemoteBriefValidity(
                scope = BriefSummaryScope(
                    date = OffsetDateTime.parse(request.deviceTime).toLocalDate(),
                    timeZone = request.timeZone,
                    locale = request.locale,
                    moment = BriefMoment.valueOf(request.moment.name),
                    counts = request.notificationCounts,
                ),
                until = OffsetDateTime.parse(response.validUntil).toInstant(),
            ),
        )
    }
}
