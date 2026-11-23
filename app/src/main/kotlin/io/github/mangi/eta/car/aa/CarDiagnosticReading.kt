package io.github.mangi.eta.car.aa

import androidx.car.app.hardware.common.CarValue

internal fun formatCarValue(reading: CarValue<*>, unit: String = ""): String =
    when (reading.status) {
        CarValue.STATUS_SUCCESS -> {
            val value = reading.value
            when {
                value == null -> "SUCCESS"
                unit.isEmpty() -> "SUCCESS:$value"
                else -> "SUCCESS:$value $unit"
            }
        }
        CarValue.STATUS_UNAVAILABLE -> "UNAVAILABLE"
        CarValue.STATUS_UNIMPLEMENTED -> "UNIMPLEMENTED"
        CarValue.STATUS_UNKNOWN -> "UNKNOWN"
        else -> "UNKNOWN(${reading.status})"
    }
