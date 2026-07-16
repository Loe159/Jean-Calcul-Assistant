package fr.loevan.jeancalcul.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantRoleControllerTest {
    @Test
    fun `reports unavailable when the device has no assistant role`() {
        val controller = AssistantRoleController(FakeAssistantRoleGateway(available = false, held = false))

        assertEquals(AssistantRoleStatus.Unavailable, controller.status())
    }

    @Test
    fun `reports active when this application holds the role`() {
        val controller = AssistantRoleController(FakeAssistantRoleGateway(available = true, held = true))

        assertEquals(AssistantRoleStatus.Active, controller.status())
    }

    @Test
    fun `reports available when the role can be requested`() {
        val controller = AssistantRoleController(FakeAssistantRoleGateway(available = true, held = false))

        assertEquals(AssistantRoleStatus.Available, controller.status())
    }
}

private class FakeAssistantRoleGateway(
    private val available: Boolean,
    private val held: Boolean,
) : AssistantRoleStatusGateway {
    override fun isAvailable(): Boolean = available

    override fun isHeld(): Boolean = held
}
