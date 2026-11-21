package io.github.mangi.eta.data.car

import android.content.Context
import io.github.mangi.eta.agent.device.DeviceLocationProvider
import io.github.mangi.eta.data.repository.KeystoreSecretStore
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class MyPeugeotCarSource private constructor(private val context: Context) : CarSource {
    private val http = CarHttp()
    private val session = PeugeotSession(http, EncryptedPeugeotSession(context))
    private val stations = FuelStations(http)
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(CarSettings())
    override val settings = mutableSettings.asStateFlow()

    private var fleet: Cached<List<PeugeotVehicle>>? = null
    private var readings: Readings? = null
    private var nearby: Nearby? = null

    override suspend fun load(): BriefCarState = transaction {
        try {
            val parameters = syncConnection()
            when (settings.value.connection) {
                CarConnection.MyPeugeotMissing -> BriefCarState.MyPeugeotMissing
                CarConnection.Disconnected -> BriefCarState.Disconnected
                CarConnection.NeedsReauth -> BriefCarState.NeedsReauth
                CarConnection.Checking -> BriefCarState.Loading
                CarConnection.Connected -> loadVehicle(requireNotNull(parameters))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PeugeotReauthRequired) {
            clearReadings()
            syncConnection()
            BriefCarState.NeedsReauth
        } catch (error: Exception) {
            BriefCarState.Error(carFetchMessage(error))
        }
    }

    suspend fun checkConnection(): Unit = transaction { syncConnection() }

    suspend fun refreshSettings(): Unit = transaction {
        val parameters = syncConnection()
        if (parameters == null || !settings.value.hasSession) return@transaction
        try {
            loadFleet(parameters)
        } catch (error: PeugeotReauthRequired) {
            clearReadings()
            syncConnection()
            throw error
        }
    }

    suspend fun beginLogin(): PeugeotLogin? = transaction { syncConnection()?.let { PeugeotLogin.create(it) } }

    suspend fun finishLogin(attempt: PeugeotLogin, state: String?, code: String): Unit = transaction {
        session.connect(attempt, state, code)
        clearReadings()
        syncConnection()
    }

    suspend fun chooseVehicle(id: String) = transaction {
        val current = settings.value
        if (current.vehicles.none { it.id == id }) return@transaction
        session.select(id, current.fuel)
        mutableSettings.value = current.copy(vehicleId = id)
    }

    suspend fun chooseFuel(fuel: CarFuel) = transaction {
        val current = session.snapshot()
        session.select(current.vehicleId, fuel)
        mutableSettings.value = settings.value.copy(fuel = fuel)
    }

    suspend fun disconnect(): Unit = transaction {
        session.disconnect()
        clearReadings()
        syncConnection()
    }

    private suspend fun <T> transaction(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private suspend fun syncConnection(): MyPeugeotParameters? {
        val record = session.snapshot()
        val parameters = readMyPeugeotParameters(context)
        val connection = when {
            parameters == null -> CarConnection.MyPeugeotMissing
            record.needsReauth -> CarConnection.NeedsReauth
            record.refreshToken == null -> CarConnection.Disconnected
            else -> CarConnection.Connected
        }
        mutableSettings.value = CarSettings(
            connection = connection,
            hasSession = record.refreshToken != null,
            vehicles = fleet?.value.orEmpty().mapNotNull(PeugeotVehicle::toVehicle),
            vehicleId = record.vehicleId,
            fuel = record.fuel,
        )
        return parameters
    }

    private fun clearReadings() {
        fleet = null
        readings = null
        nearby = null
    }

    private suspend fun loadFleet(parameters: MyPeugeotParameters): List<PeugeotVehicle> {
        val cached = fleet?.takeIf { it.isFresh(DAY) }
        val vehicles = cached?.value ?: try {
            collection(parameters, api.newBuilder().addPathSegment("vehicles").addQueryParameter("extension", "branding").build())
                .flatMap { it.vehicles.orEmpty() }.distinctBy { it.id }
                .filter { !it.id.isNullOrBlank() }.also { fleet = Cached(it) }
        } catch (error: Exception) {
            if (error is CancellationException || error is PeugeotReauthRequired) throw error
            fleet?.value ?: throw error
        }
        val current = settings.value
        val selected = current.vehicleId?.takeIf { id -> vehicles.any { it.id == id } }
            ?: vehicles.singleOrNull()?.id
        if (selected != current.vehicleId) session.select(selected, current.fuel)
        mutableSettings.value = current.copy(vehicles = vehicles.mapNotNull(PeugeotVehicle::toVehicle), vehicleId = selected)
        return vehicles
    }

    private suspend fun loadVehicle(parameters: MyPeugeotParameters): BriefCarState {
        val vehicles = loadFleet(parameters)
        val selected = settings.value.vehicleId
        val vehicle = vehicles.firstOrNull { it.id == selected }
        if (vehicle == null) return BriefCarState.Error(
            if (vehicles.isEmpty()) "Aucun véhicule associé à ce compte MyPeugeot."
            else "Choisis ton véhicule dans les réglages Voiture.",
        )
        val id = requireNotNull(vehicle.id)
        val current = readings?.takeIf { it.vehicleId == id } ?: Readings(id).also { readings = it }
        val root = api.newBuilder().addPathSegment("vehicles").addPathSegment(id).build()
        fun endpoint(path: String): HttpUrl = root.newBuilder().addPathSegment(path).build()

        val status = current.status.read(QUARTER_HOUR) {
            carJson.decodeFromString<PeugeotStatus>(session.get(parameters, endpoint("status"))).toStatus()
        }
        val maintenance = current.maintenance.read(DAY) {
            val url = peugeotLink(root, vehicle.links?.maintenance?.href) ?: endpoint("maintenance")
            carJson.decodeFromString<PeugeotMaintenance>(session.get(parameters, url)).toMaintenance(ZoneId.systemDefault())
        }
        val alerts = current.alerts.read(QUARTER_HOUR) {
            collection(parameters, endpoint("alerts")).flatMap { it.alerts.orEmpty() }.mapNotNull(PeugeotAlert::toActiveAlert)
        }
        if (status.first == null && maintenance.first == null && alerts.first == null) {
            return BriefCarState.Error(carFetchMessage(status.second ?: maintenance.second ?: alerts.second))
        }
        val vehicleStatus = status.first
        val vehiclePoint = vehicleStatus?.position?.takeIf { it.quality(Instant.now()) == CarPositionQuality.Available }?.point
        val point = vehiclePoint ?: (DeviceLocationProvider.latest(context, requireBackground = false) as? DeviceLocationProvider.Result.Available)
            ?.takeIf { it.ageMillis < DAY.toMillis() }?.let { CarPoint(it.latitude, it.longitude).takeIf(CarPoint::isValid) }
        val station = point?.let {
            val fuel = settings.value.fuel
            val currentNearby = nearby?.takeIf { it.point == point && it.fuel == fuel }
                ?: Nearby(point, fuel).also { nearby = it }
            currentNearby.station.read(Duration.ofMinutes(20)) { stations.nearest(point, fuel) }
        }
        val data = BriefCarData(
            label = requireNotNull(vehicle.toVehicle()).label,
            status = vehicleStatus,
            maintenance = maintenance.first,
            alerts = alerts.first,
            station = station?.first,
            stationFromPhone = vehiclePoint == null,
        )
        val incomplete = vehicleStatus?.rangeKm == null || vehicleStatus.fuelPercent == null || vehicleStatus.odometerKm == null ||
            maintenance.first == null || alerts.first == null || listOf(status.second, maintenance.second, alerts.second, station?.second).any { it != null }
        return BriefCarState.Ready(data, partial = incomplete)
    }

    private suspend fun collection(parameters: MyPeugeotParameters, first: HttpUrl): List<CarCollectionItems> = buildList {
        val seen = mutableSetOf<HttpUrl>()
        var url: HttpUrl? = first
        while (url != null && seen.size < 20 && seen.add(url)) {
            val page = carJson.decodeFromString<CarCollection>(session.get(parameters, url))
            page.embedded?.let(::add)
            url = peugeotLink(url, page.links?.next?.href)
        }
        if (url != null) throw IOException("Incomplete Peugeot collection")
    }

    private class Readings(val vehicleId: String) {
        val status = CarCache<CarStatus>()
        val maintenance = CarCache<CarMaintenance?>()
        val alerts = CarCache<List<CarAlert>>()
    }

    private class Nearby(val point: CarPoint, val fuel: CarFuel) {
        val station = CarCache<FuelStation?>()
    }

    companion object {
        @Volatile private var instance: MyPeugeotCarSource? = null
        fun get(context: Context): MyPeugeotCarSource = instance ?: synchronized(this) {
            instance ?: MyPeugeotCarSource(context.applicationContext).also { instance = it }
        }

        private val api = "https://api.groupe-psa.com/connectedcar/v4/user".toHttpUrl()
        private val DAY = Duration.ofHours(24)
        private val QUARTER_HOUR = Duration.ofMinutes(15)
    }
}

internal fun peugeotLink(base: HttpUrl, href: String?): HttpUrl? =
    href?.let(base::resolve)?.takeIf {
        it.scheme == base.scheme && it.host == base.host && it.port == base.port &&
            it.encodedUsername.isEmpty() && it.encodedPassword.isEmpty()
    }

private class Cached<T>(val value: T) {
    private val fetchedAt = Instant.now()
    fun isFresh(ttl: Duration): Boolean = Duration.between(fetchedAt, Instant.now()) in Duration.ZERO..<ttl
}

private class CarCache<T> {
    private var success: Cached<T>? = null
    private var failure: Cached<Exception>? = null

    suspend fun read(ttl: Duration, fetch: suspend () -> T): Pair<T?, Exception?> {
        success?.takeIf { it.isFresh(ttl) }?.let { return it.value to null }
        failure?.takeIf { it.isFresh(Duration.ofMinutes(1)) }?.let { return success?.value to it.value }
        return try {
            val result = fetch()
            success = Cached(result)
            failure = null
            result to null
        } catch (error: Exception) {
            if (error is CancellationException || error is PeugeotReauthRequired) throw error
            failure = Cached(error)
            success?.value to error
        }
    }
}

private class EncryptedPeugeotSession(context: Context) : PeugeotSessionStorage {
    private val store by lazy { KeystoreSecretStore(context, "eta_peugeot_session", "eta_peugeot_session_v1") }
    override fun read(): PeugeotSessionRecord = store.read("session")?.let {
        runCatching { carJson.decodeFromString<PeugeotSessionRecord>(it) }.getOrNull()
    } ?: PeugeotSessionRecord()

    override fun save(record: PeugeotSessionRecord) = store.write("session", carJson.encodeToString(record))
    override fun clear() = store.clear("session")
}

internal fun carFetchMessage(error: Exception?): String = when (error) {
    is PeugeotExchangeRejected -> "MyPeugeot a refusé la connexion avec PKCE (HTTP 400)."
    is PeugeotReauthRequired -> carReauthMessage
    is CarHttpFailure -> when (error.status) {
        429 -> "MyPeugeot limite les requêtes. Réessaie plus tard."
        401, 403 -> "Vérifie l’accès aux données dans MyPeugeot."
        else -> "Service Voiture indisponible."
    }
    else -> "Service Voiture indisponible."
}
