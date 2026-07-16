package fr.loevan.jeancalcul.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

internal sealed interface AssistantRoleStatus {
    data object Active : AssistantRoleStatus

    data object Available : AssistantRoleStatus

    data object Unavailable : AssistantRoleStatus
}

internal interface AssistantRoleStatusGateway {
    fun isAvailable(): Boolean

    fun isHeld(): Boolean
}

internal interface AssistantRoleGateway : AssistantRoleStatusGateway {
    fun createRequestIntent(): Intent
}

internal class AssistantRoleController(
    private val gateway: AssistantRoleStatusGateway,
) {
    fun status(): AssistantRoleStatus =
        when {
            !gateway.isAvailable() -> AssistantRoleStatus.Unavailable
            gateway.isHeld() -> AssistantRoleStatus.Active
            else -> AssistantRoleStatus.Available
        }
}

internal fun createAssistantRoleGateway(context: Context): AssistantRoleGateway =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AndroidAssistantRoleGateway(context)
    } else {
        UnsupportedAssistantRoleGateway
    }

@Suppress("NewApi")
internal class AndroidAssistantRoleGateway(context: Context) : AssistantRoleGateway {
    private val roleManager = context.getSystemService(RoleManager::class.java)

    override fun isAvailable(): Boolean = roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)

    override fun isHeld(): Boolean = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)

    override fun createRequestIntent(): Intent =
        roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
}

private data object UnsupportedAssistantRoleGateway : AssistantRoleGateway {
    override fun isAvailable(): Boolean = false

    override fun isHeld(): Boolean = false

    override fun createRequestIntent(): Intent =
        error("The assistant role requires Android 10 or newer.")
}
