package io.github.mangi.eta.car.aa

import androidx.car.app.hardware.common.CarValue
import org.junit.Assert.assertEquals
import org.junit.Test

class CarDiagnosticReadingTest {
    @Test
    fun successIncludesValueAndOptionalUnit() {
        assertEquals("SUCCESS:48.5 %", formatCarValue(CarValue(48.5f, 0, CarValue.STATUS_SUCCESS), "%"))
        assertEquals("SUCCESS:0.0 m/s", formatCarValue(CarValue(0f, 0, CarValue.STATUS_SUCCESS), "m/s"))
        assertEquals("SUCCESS:208", formatCarValue(CarValue("208", 0, CarValue.STATUS_SUCCESS)))
        assertEquals("SUCCESS", formatCarValue(CarValue<Float>(null, 0, CarValue.STATUS_SUCCESS), "m"))
    }

    @Test
    fun otherStatusesOmitTheValueAndUnit() {
        assertEquals("UNAVAILABLE", formatCarValue(CarValue(100f, 0, CarValue.STATUS_UNAVAILABLE), "m"))
        assertEquals("UNIMPLEMENTED", formatCarValue(CarValue<Float>(null, 0, CarValue.STATUS_UNIMPLEMENTED), "m"))
        assertEquals("UNKNOWN", formatCarValue(CarValue<Float>(null, 0, CarValue.STATUS_UNKNOWN), "m"))
    }
}
