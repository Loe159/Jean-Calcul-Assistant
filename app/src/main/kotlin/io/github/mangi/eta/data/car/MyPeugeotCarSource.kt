package io.github.mangi.eta.data.car

import android.content.Context
import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.agent.device.DeviceLocationProvider
import io.github.mangi.eta.data.repository.KeystoreSecretStore
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl

internal class MyPeugeotCarSource private constructor(private val context: Context) : CarSource {
    private val session = PeugeotSession(EncryptedPeugeotSession(context))
    private val stations = FuelStations(CarHttp(logDetails = false))
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(CarSettings())
    override val settings = mutableSettings.asStateFlow()

    private var installed: InstalledMyPeugeot? = null
    private var profile = CarCache<MymGarage>()
    private var nearby: Nearby? = null
    private val estimates = FuelEstimateStore(context)
    private var estimate: FuelEstimateState? = null

    override suspend fun load(): BriefCarState = transaction {
        try {
            val client = syncConnection()
            when (settings.value.connection) {
                CarConnection.MyPeugeotMissing -> BriefCarState.MyPeugeotMissing
                CarConnection.Disconnected -> BriefCarState.Disconnected
                CarConnection.NeedsReauth -> BriefCarState.NeedsReauth
                CarConnection.Checking -> BriefCarState.Loading
                CarConnection.Connected -> loadVehicle(requireNotNull(client))
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
        val client = syncConnection()
        if (client == null || !settings.value.hasSession) return@transaction
        try {
            val (garage, error) = loadGarage(client)
            if (garage == null && error != null) throw error
        } catch (error: PeugeotReauthRequired) {
            clearReadings()
            syncConnection()
            throw error
        }
    }

    suspend fun beginLogin(): PeugeotLogin? = transaction {
        syncConnection()?.let { PeugeotLogin.create(it.parameters) }
    }

    suspend fun finishLogin(attempt: PeugeotLogin, state: String?, code: String): Unit = transaction {
        val client = syncConnection() ?: throw MyPeugeotUnavailable()
        session.connect(attempt, state, code, client.http)
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

    private suspend fun syncConnection(): InstalledMyPeugeot? {
        val record = session.snapshot()
        val client = installedClient()
        val connection = when {
            client == null -> CarConnection.MyPeugeotMissing
            record.needsReauth -> CarConnection.NeedsReauth
            record.refreshToken == null -> CarConnection.Disconnected
            else -> CarConnection.Connected
        }
        val garage = profile.lastValue
        mutableSettings.value = CarSettings(
            connection = connection,
            hasSession = record.refreshToken != null,
            vehicles = garage?.settingsVehicles().orEmpty(),
            vehicleId = garage?.choose(record.vehicleId)?.id,
            fuel = record.fuel,
        )
        return client
    }

    private fun installedClient(): InstalledMyPeugeot? {
        val info = runCatching { context.packageManager.getApplicationInfo(myPeugeotPackage, 0) }.getOrNull()
        val path = info?.sourceDir
        installed?.takeIf { it.path == path }?.let { return it }
        clearReadings()
        installed = if (info == null || path == null) null else runCatching {
            readMyPeugeotParameters(context, info)?.let { parameters ->
                InstalledMyPeugeot(path, parameters, myPeugeotHttp(path))
            }
        }.getOrNull()
        return installed
    }

    private fun clearReadings() {
        profile = CarCache()
        nearby = null
    }

    private suspend fun loadGarage(client: InstalledMyPeugeot): Pair<MymGarage?, Exception?> {
        val result = profile.read(Duration.ofMinutes(15)) { session.profile(client.parameters, client.http) }
        result.first?.let { garage ->
            val current = settings.value
            val selection = garage.choose(session.snapshot().vehicleId)
            mutableSettings.value = current.copy(vehicles = garage.settingsVehicles(), vehicleId = selection?.id)
        }
        return result
    }

    private suspend fun loadVehicle(client: InstalledMyPeugeot): BriefCarState {
        val (garage, error) = loadGarage(client)
        if (garage == null) return BriefCarState.Error(carFetchMessage(error))
        val vehicle = garage.choose(settings.value.vehicleId) ?: return BriefCarState.Error(
            if (garage.vehicles.isEmpty()) "Aucun véhicule associé à ce compte MyPeugeot."
            else "Choisis ton véhicule dans les réglages Voiture.",
        )
        val point = (DeviceLocationProvider.latest(context, requireBackground = false) as? DeviceLocationProvider.Result.Available)
            ?.takeIf { it.ageMillis < DAY.toMillis() }
            ?.let { CarPoint(it.latitude, it.longitude).takeIf(CarPoint::isValid) }
        val station = point?.let {
            val fuel = settings.value.fuel
            val cached = nearby?.takeIf { it.point == point && it.fuel == fuel }
                ?: Nearby(point, fuel).also { nearby = it }
            cached.station.read(Duration.ofMinutes(20)) { stations.nearest(point, fuel) }
        }
        val data = applyEstimate(vehicle.data.copy(station = station?.first, stationFromPhone = station?.first != null))
        val partial = error != null || data.status?.odometerKm == null || station?.second != null
        return BriefCarState.Ready(data, partial)
    }

    // Remplit jauge/autonomie par estimation quand le véhicule ne les expose pas (cas thermique
    // sans services connectés). Ne touche jamais à une vraie mesure si elle existe.
    private fun applyEstimate(data: BriefCarData): BriefCarData {
        val status = data.status ?: return data
        if (status.rangeKm != null || status.fuelPercent != null) return data
        val reading = estimateState().estimate(status.odometerKm) ?: return data
        return data.copy(
            status = status.copy(
                rangeKm = reading.rangeKm,
                fuelPercent = reading.fuelPercent,
                fuelMeasuredAt = reading.recordedAt,
                estimated = true,
            ),
        )
    }

    private fun estimateState(): FuelEstimateState = estimate ?: estimates.read().also { estimate = it }

    private fun saveEstimate(state: FuelEstimateState) {
        estimate = state
        estimates.write(state)
    }

    private suspend fun currentOdometerKm(): Double? {
        val client = installedClient() ?: return null
        if (profile.lastValue == null) runCatching { loadGarage(client) }
        val garage = profile.lastValue ?: return null
        return garage.choose(session.snapshot().vehicleId)?.data?.status?.odometerKm
    }

    // Signalé par l'utilisateur via un bouton : pose une ancre à 100 % sur le compteur courant.
    suspend fun recordFillUp(): Boolean = transaction {
        val odometer = currentOdometerKm() ?: return@transaction false
        saveEstimate(estimateState().withFillUp(odometer, Instant.now()))
        true
    }

    // Recalibre l'estimation sur le dernier relevé réel d'un export MyPeugeot (.myp).
    suspend fun importMyp(text: String): MypImportResult? = transaction {
        val result = runCatching { parseMypAnchor(text) }.getOrNull() ?: return@transaction null
        saveEstimate(estimateState().withAnchor(result.anchor))
        result
    }

    private fun MymGarage.settingsVehicles(): List<CarVehicle> =
        vehicles.mapNotNull { vehicle -> vehicle.id?.let { CarVehicle(it, vehicle.data.label) } }

    private class InstalledMyPeugeot(val path: String, val parameters: MyPeugeotParameters, val http: CarHttp)

    private class Nearby(val point: CarPoint, val fuel: CarFuel) {
        val station = CarCache<FuelStation?>()
    }

    companion object {
        @Volatile private var instance: MyPeugeotCarSource? = null
        fun get(context: Context): MyPeugeotCarSource = instance ?: synchronized(this) {
            instance ?: MyPeugeotCarSource(context.applicationContext).also { instance = it }
        }

        private val DAY = Duration.ofHours(24)
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
    val lastValue: T? get() = success?.value

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

private class MyPeugeotUnavailable : IOException(carInstallMessage)

internal fun carFetchMessage(error: Exception?, debug: Boolean = BuildConfig.DEBUG): String {
    val readFailure = error as? CarReadFailure
    val cause = readFailure?.error ?: error
    val status = readFailure?.status ?: (cause as? CarHttpFailure)?.status
    val message = when {
        cause is MyPeugeotUnavailable -> carInstallMessage
        cause is PeugeotExchangeRejected -> "MyPeugeot a refusé la connexion avec PKCE (HTTP 400)."
        cause is PeugeotReauthRequired -> carReauthMessage
        status == 429 -> "MyPeugeot limite les requêtes. Réessaie plus tard."
        status == 401 || status == 403 -> "Vérifie l’accès aux données dans MyPeugeot."
        status == 404 && readFailure?.stage == CarReadStage.Profile -> "Profil MyPeugeot introuvable."
        status == 404 -> "Véhicule introuvable côté Peugeot (services connectés ou partage des données désactivés ?)"
        status != null && status in 500..599 -> "Serveurs Peugeot indisponibles. Réessaie plus tard."
        cause is SerializationException -> "Réponse Peugeot inattendue."
        cause is IOException && cause !is CarHttpFailure -> "Pas de connexion au service Peugeot."
        else -> "Service Voiture indisponible."
    }
    if (!debug || readFailure == null) return message
    val http = status?.let { " · HTTP $it" }.orEmpty()
    return "$message (${readFailure.stage.label}$http)"
}
