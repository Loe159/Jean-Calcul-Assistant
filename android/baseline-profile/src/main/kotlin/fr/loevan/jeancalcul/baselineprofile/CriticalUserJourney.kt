package fr.loevan.jeancalcul.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal fun MacrobenchmarkScope.openMainCriticalUserJourney() {
    startActivityAndWait()
    device.wait(Until.hasObject(By.text("Conversations")), UI_TIMEOUT_MILLIS)
    device.findObject(By.text("Reglages"))?.click()
    device.waitForIdle()
}

private const val UI_TIMEOUT_MILLIS = 5_000L
