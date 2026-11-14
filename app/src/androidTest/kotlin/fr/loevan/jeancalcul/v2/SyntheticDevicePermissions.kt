package fr.loevan.jeancalcul.v2

import android.Manifest
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Only runs in the account-free test APK on the runner-owned disposable device. */
internal fun grantSyntheticNotificationPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val command = "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .use { it.readBytes() }
    check(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        "The synthetic device must grant notification permission before the chat test"
    }
}
