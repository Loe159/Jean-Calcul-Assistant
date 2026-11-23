package io.github.mangi.eta.car.aa

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import io.github.mangi.eta.BuildConfig

class JeanCalculCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this).build()
        }

    override fun onCreateSession(): Session = JeanCalculCarSession()
}

internal class JeanCalculCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = JeanCalculCarDiagnosticScreen(carContext)
}
