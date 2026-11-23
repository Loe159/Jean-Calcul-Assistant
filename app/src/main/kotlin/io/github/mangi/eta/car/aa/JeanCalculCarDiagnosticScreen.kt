package io.github.mangi.eta.car.aa

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.HostException
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Speed
import androidx.car.app.hardware.info.TollCard
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.github.mangi.eta.R

// Cet écran repose sur les API hardware CarInfo marquées @ExperimentalCarApi.
// L'opt-in au niveau classe est reconnu par Android Lint jusque dans les lambdas
// des listeners, là où un @OptIn porté sur une propriété ne l'est pas (UnsafeOptInUsageError).
@OptIn(ExperimentalCarApi::class)
internal class JeanCalculCarDiagnosticScreen(carContext: CarContext) : Screen(carContext) {
    private val executor = ContextCompat.getMainExecutor(carContext)
    private val hardware = carContext.getCarService(CarHardwareManager::class.java)
    private val info = hardware.carInfo

    // The requested vehicle properties come from CarInfo. Motion and location are out of scope.
    @Suppress("unused")
    private val sensors = hardware.carSensors

    private val snapshot = carContext.getSharedPreferences("android_auto_diagnostic", Context.MODE_PRIVATE)
    private val readings = mutableMapOf<Field, String>()
    private val rejectedPermissions = mutableSetOf<String>()
    private val startedReads = mutableMapOf<Source, (() -> Unit)?>()
    private val readErrors = mutableMapOf<Source, Int>()

    private val energy = OnCarDataAvailableListener<EnergyLevel> {
        record(
            Field.FUEL_PERCENT.reading(it.fuelPercent),
            Field.RANGE_REMAINING.reading(it.rangeRemainingMeters),
            Field.BATTERY_PERCENT.reading(it.batteryPercent),
            Field.ENERGY_LOW.reading(it.energyIsLow),
            Field.RANGE_DISPLAY_UNIT.reading(it.distanceDisplayUnit),
            Field.FUEL_VOLUME_DISPLAY_UNIT.reading(it.fuelVolumeDisplayUnit),
        )
    }
    private val mileage = OnCarDataAvailableListener<Mileage> {
        record(
            Field.ODOMETER.reading(it.odometerMeters),
            Field.ODOMETER_DISPLAY_UNIT.reading(it.distanceDisplayUnit),
        )
    }
    private val speed = OnCarDataAvailableListener<Speed> {
        record(
            Field.RAW_SPEED.reading(it.rawSpeedMetersPerSecond),
            Field.DISPLAY_SPEED.reading(it.displaySpeedMetersPerSecond),
            Field.SPEED_DISPLAY_UNIT.reading(it.speedDisplayUnit),
        )
    }
    private val toll = OnCarDataAvailableListener<TollCard> {
        record(Field.TOLL_CARD_STATE.reading(it.cardState))
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                startGrantedReads()
            }

            override fun onResume(owner: LifecycleOwner) {
                startGrantedReads()
                invalidate()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                for ((source, stop) in startedReads) {
                    if (stop != null) callHardware(source, stop)
                }
                startedReads.clear()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
        listOf(Field.FUEL_PERCENT, Field.RANGE_REMAINING, Field.ODOMETER).forEach {
            pane.addRow(row(it))
        }
        if (missingPermissions().isNotEmpty()) {
            pane.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.aa_permissions))
                    .setOnClickListener(ParkedOnlyOnClickListener.create(::requestMissingPermissions))
                    .build(),
            )
        }
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.aa_more_readings))
                .setOnClickListener { screenManager.push(ReadingGroupsScreen()) }
                .build(),
        )
        return paneTemplate(R.string.aa_diagnostic_title, pane, Action.APP_ICON)
    }

    private fun startGrantedReads() {
        startRead(Source.MODEL) {
            info.fetchModel(executor) {
                record(
                    Field.MANUFACTURER.reading(it.manufacturer),
                    Field.MODEL.reading(it.name),
                    Field.MODEL_YEAR.reading(it.year),
                )
            }
        }
        startRead(Source.TOLL, { info.removeTollListener(toll) }) {
            info.addTollListener(executor, toll)
        }
        startRead(Source.PROFILE) {
            info.fetchEnergyProfile(executor) {
                record(
                    Field.FUEL_TYPES.reading(it.fuelTypes),
                    Field.EV_CONNECTOR_TYPES.reading(it.evConnectorTypes),
                )
            }
        }
        startRead(Source.ENERGY, { info.removeEnergyLevelListener(energy) }) {
            info.addEnergyLevelListener(executor, energy)
        }
        startRead(Source.MILEAGE, { info.removeMileageListener(mileage) }) {
            info.addMileageListener(executor, mileage)
        }
        startRead(Source.SPEED, { info.removeSpeedListener(speed) }) {
            info.addSpeedListener(executor, speed)
        }
    }

    private fun startRead(source: Source, stop: (() -> Unit)? = null, start: () -> Unit) {
        val permission = source.permission
        if (source in startedReads || (permission != null && !hasPermission(permission))) return

        // The library can register its local listener before the remote call fails.
        startedReads[source] = stop
        callHardware(source, start)
    }

    private fun callHardware(source: Source, call: () -> Unit) {
        try {
            call()
        } catch (error: SecurityException) {
            readErrors[source] = R.string.aa_permission_denied
            Log.w("JeanCalculAA", "${source.name}: accès refusé", error)
        } catch (error: HostException) {
            readErrors[source] = R.string.aa_read_error
            Log.w("JeanCalculAA", "${source.name}: erreur de lecture", error)
        }
    }

    private fun hasPermission(permission: Permission): Boolean =
        ContextCompat.checkSelfPermission(carContext, permission.manifestName) == PackageManager.PERMISSION_GRANTED

    private fun missingPermissions(): List<String> =
        Permission.entries.filterNot(::hasPermission).map { it.manifestName }

    private fun requestMissingPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            startGrantedReads()
            invalidate()
            return
        }
        carContext.requestPermissions(missing, executor) { granted, rejected ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                rejectedPermissions.removeAll(granted)
                rejectedPermissions.addAll(rejected)
                startGrantedReads()
                refreshReadings()
            }
        }
    }

    private fun record(vararg updates: Pair<Field, String>) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return

        // The first callback starts a new snapshot. Until then the previous drive stays readable.
        snapshot.edit {
            if (readings.isEmpty()) clear()
            for ((field, reading) in updates) {
                val key = field.name.lowercase()
                Log.i("JeanCalculAA", "$key=$reading")
                readings[field] = reading
                putString(key, reading)
            }
            putLong("timestamp_ms", System.currentTimeMillis())
        }
        refreshReadings()
    }

    private fun refreshReadings() {
        invalidate()
        (screenManager.top as? GroupReadingsScreen)?.invalidate()
    }

    private fun row(field: Field): Row {
        val permission = field.source.permission
        val text = when {
            permission != null && !hasPermission(permission) -> carContext.getString(
                if (permission.manifestName in rejectedPermissions) R.string.aa_permission_denied
                else R.string.aa_permission_required,
            )
            else -> readings[field] ?: carContext.getString(readErrors[field.source] ?: R.string.aa_waiting)
        }
        return Row.Builder().setTitle(carContext.getString(field.label)).addText(text).build()
    }

    // The original title and header methods also work with hosts at Car App API level 3.
    @Suppress("DEPRECATION")
    private fun paneTemplate(@StringRes title: Int, pane: Pane.Builder, headerAction: Action): Template =
        PaneTemplate.Builder(pane.build())
            .setTitle(carContext.getString(title))
            .setHeaderAction(headerAction)
            .build()

    private inner class ReadingGroupsScreen : Screen(carContext) {
        @Suppress("DEPRECATION")
        override fun onGetTemplate(): Template {
            val groups = ItemList.Builder()
            for (group in Group.entries) {
                groups.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(group.label))
                        .setOnClickListener { screenManager.push(GroupReadingsScreen(group)) }
                        .build(),
                )
            }
            return ListTemplate.Builder()
                .setTitle(carContext.getString(R.string.aa_more_readings))
                .setHeaderAction(Action.BACK)
                .setSingleList(groups.build())
                .build()
        }
    }

    private inner class GroupReadingsScreen(private val group: Group) : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val pane = Pane.Builder()
            group.fields.forEach { pane.addRow(row(it)) }
            return paneTemplate(group.label, pane, Action.BACK)
        }
    }

    private enum class Permission(val manifestName: String) {
        FUEL("com.google.android.gms.permission.CAR_FUEL"),
        MILEAGE("com.google.android.gms.permission.CAR_MILEAGE"),
        SPEED("com.google.android.gms.permission.CAR_SPEED"),
    }

    private enum class Source(val permission: Permission? = null) {
        MODEL,
        TOLL,
        PROFILE(Permission.FUEL),
        ENERGY(Permission.FUEL),
        MILEAGE(Permission.MILEAGE),
        SPEED(Permission.SPEED),
    }

    private enum class Field(
        @StringRes val label: Int,
        val source: Source,
        val unit: String = "",
    ) {
        FUEL_PERCENT(R.string.aa_fuel_percent, Source.ENERGY, "%"),
        RANGE_REMAINING(R.string.aa_range, Source.ENERGY, "m"),
        ODOMETER(R.string.aa_odometer, Source.MILEAGE, "m"),
        MANUFACTURER(R.string.aa_manufacturer, Source.MODEL),
        MODEL(R.string.aa_model, Source.MODEL),
        MODEL_YEAR(R.string.aa_model_year, Source.MODEL),
        FUEL_TYPES(R.string.aa_fuel_types, Source.PROFILE),
        EV_CONNECTOR_TYPES(R.string.aa_ev_connectors, Source.PROFILE),
        BATTERY_PERCENT(R.string.aa_battery_percent, Source.ENERGY, "%"),
        ENERGY_LOW(R.string.aa_energy_low, Source.ENERGY),
        RANGE_DISPLAY_UNIT(R.string.aa_range_unit, Source.ENERGY),
        FUEL_VOLUME_DISPLAY_UNIT(R.string.aa_fuel_volume_unit, Source.ENERGY),
        RAW_SPEED(R.string.aa_raw_speed, Source.SPEED, "m/s"),
        DISPLAY_SPEED(R.string.aa_display_speed, Source.SPEED, "m/s"),
        SPEED_DISPLAY_UNIT(R.string.aa_speed_unit, Source.SPEED),
        ODOMETER_DISPLAY_UNIT(R.string.aa_odometer_unit, Source.MILEAGE),
        TOLL_CARD_STATE(R.string.aa_toll_state, Source.TOLL);

        fun reading(value: CarValue<*>): Pair<Field, String> = this to formatCarValue(value, unit)
    }

    private enum class Group(@StringRes val label: Int, vararg val fields: Field) {
        MODEL(R.string.aa_model_group, Field.MANUFACTURER, Field.MODEL, Field.MODEL_YEAR),
        PROFILE(R.string.aa_profile_group, Field.FUEL_TYPES, Field.EV_CONNECTOR_TYPES),
        ENERGY(R.string.aa_energy_group, Field.BATTERY_PERCENT, Field.ENERGY_LOW,
            Field.RANGE_DISPLAY_UNIT, Field.FUEL_VOLUME_DISPLAY_UNIT),
        SPEED(R.string.aa_speed_group, Field.RAW_SPEED, Field.DISPLAY_SPEED, Field.SPEED_DISPLAY_UNIT),
        MILEAGE_TOLL(R.string.aa_mileage_toll_group, Field.ODOMETER_DISPLAY_UNIT, Field.TOLL_CARD_STATE),
    }
}
