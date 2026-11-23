package io.github.mangi.eta.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.data.car.CarConnection
import io.github.mangi.eta.data.car.CarFuel
import io.github.mangi.eta.data.car.MyPeugeotCarSource
import io.github.mangi.eta.data.car.carFetchMessage
import io.github.mangi.eta.data.car.carInstallMessage
import io.github.mangi.eta.data.car.carReauthMessage
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CarSettingsSection(context: Context) {
    val source = remember(context) { MyPeugeotCarSource.get(context) }
    val settings by source.settings.collectAsState()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var showLogin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    suspend fun update(block: suspend () -> Unit) {
        message = null
        loading = true
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            message = carFetchMessage(error)
        } finally {
            loading = false
        }
    }
    val importMyp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            update {
                val text = withContext(Dispatchers.IO) { readMypText(context, uri) }
                val result = text?.let { source.importMyp(it) }
                message = if (result == null) {
                    "Import .myp impossible (fichier illisible ou vide)."
                } else {
                    "Recalibré : ${result.anchor.fuelPercent.toInt()} % · ${result.anchor.rangeKm.toInt()} km"
                }
            }
        }
    }
    LaunchedEffect(lifecycle, settings.hasSession, showLogin) {
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !showLogin) {
            update { source.refreshSettings() }
        }
    }
    PreferenceCategory(title = "Voiture")
    Card(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        CardListGroup {
            if (settings.connection != CarConnection.Connected) {
                item {
                    ArrowPreference(
                        title = "Connecter MyPeugeot",
                        summary = when (settings.connection) {
                            CarConnection.Checking -> "Vérification…"
                            CarConnection.MyPeugeotMissing -> carInstallMessage
                            CarConnection.Disconnected -> "Connecte ton compte pour afficher la voiture dans le Brief."
                            CarConnection.NeedsReauth -> carReauthMessage
                            CarConnection.Connected -> null
                        },
                        onClick = { showLogin = true },
                    )
                }
            }
            if (settings.vehicles.size > 1) {
                item {
                    val vehicles = settings.vehicles
                    val chosen = vehicles.indexOfFirst { it.id == settings.vehicleId }
                    val placeholder = chosen < 0
                    val labels = vehicles.mapIndexed { index, vehicle ->
                        if (vehicles.count { it.label == vehicle.label } == 1) vehicle.label
                        else "${vehicle.label} · véhicule ${index + 1}"
                    }
                    OverlayDropdownPreference(
                        title = "Véhicule",
                        items = if (placeholder) listOf("Choisir un véhicule") + labels else labels,
                        selectedIndex = chosen.coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            vehicles.getOrNull(index - if (placeholder) 1 else 0)?.let { vehicle ->
                                scope.launch { update { source.chooseVehicle(vehicle.id) } }
                            }
                        },
                    )
                }
            }
            item {
                OverlayDropdownPreference(
                    title = "Carburant",
                    items = CarFuel.entries.map(CarFuel::label),
                    selectedIndex = settings.fuel.ordinal,
                    onSelectedIndexChange = { index ->
                        CarFuel.entries.getOrNull(index)?.let { fuel ->
                            scope.launch { update { source.chooseFuel(fuel) } }
                        }
                    },
                )
            }
            if (settings.connection == CarConnection.Connected) {
                item {
                    ArrowPreference(
                        title = "J’ai fait le plein",
                        summary = "Repart de 100 % pour estimer carburant et autonomie.",
                        onClick = {
                            if (!loading) scope.launch {
                                update {
                                    message = if (source.recordFillUp()) {
                                        "Plein enregistré. Estimation remise à 100 %."
                                    } else {
                                        "Compteur indisponible : ouvre d’abord le Brief."
                                    }
                                }
                            }
                        },
                    )
                }
                item {
                    ArrowPreference(
                        title = "Importer un .myp",
                        summary = "Recalibre l’estimation sur le dernier relevé MyPeugeot.",
                        onClick = { if (!loading) importMyp.launch(arrayOf("*/*")) },
                    )
                }
            }
            if (settings.hasSession || settings.connection == CarConnection.NeedsReauth) {
                item {
                    ArrowPreference(
                        title = "Déconnecter",
                        onClick = {
                            if (!loading) scope.launch {
                                update {
                                    withContext(NonCancellable) {
                                        source.disconnect()
                                        clearPeugeotWebData(context)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    message?.let { PreferenceFooter(it) }
    if (showLogin) PeugeotLoginSheet(source) { showLogin = false }
}

private fun readMypText(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readBytes()
        if (bytes.size > 8 * 1024 * 1024) null else String(bytes, Charsets.UTF_8)
    }
} catch (_: Exception) {
    null
}
