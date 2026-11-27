package io.github.mangi.eta.agent.confirm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionPolicyTest {
    private fun risk(tool: String, args: String = "{}") =
        SensitiveActionPolicy.requestFor(tool, JSONObject(args))?.risk

    @Test
    fun deletionsAreHighRisk() {
        assertEquals(ActionRisk.HIGH, risk("delete_calendar_event", """{"event_id":"12"}"""))
        assertEquals(ActionRisk.HIGH, risk("delete_alarm", """{"alarm_id":"3"}"""))
        assertEquals(ActionRisk.HIGH, risk("delete_note", """{"note_id":"n1"}"""))
    }

    @Test
    fun appStateDependsOnAction() {
        assertEquals(ActionRisk.HIGH, risk("app_state_control", """{"action":"force_stop","package_name":"a.b"}"""))
        assertEquals(ActionRisk.HIGH, risk("app_state_control", """{"action":"freeze","package_name":"a.b"}"""))
        assertEquals(ActionRisk.LOW, risk("app_state_control", """{"action":"unfreeze","package_name":"a.b"}"""))
        assertEquals(ActionRisk.HIGH, risk("app_state_control", """{"action":"weird"}"""))
    }

    @Test
    fun creationsAndSettingsAreLowRisk() {
        listOf(
            "create_calendar_event", "create_calendar_events", "update_calendar_event", "update_alarm_time",
            "set_alarm_enabled", "create_note", "set_setting", "set_device_state", "set_hotspot",
        ).forEach { assertEquals(it, ActionRisk.LOW, risk(it)) }
    }

    @Test
    fun reversibleTogglesReadsAndGuiToolsNeedNoConfirmation() {
        listOf(
            "set_flashlight", "set_brightness", "set_screen_timeout", "set_sound_mode", "set_do_not_disturb",
            "tap", "type_text", "terminal", "observe_screen", "read_note", "unknown_tool",
        ).forEach { assertNull(it, risk(it)) }
    }

    @Test
    fun summaryNamesTheTarget() {
        val request = SensitiveActionPolicy.requestFor("create_note", JSONObject("""{"title":"Courses"}"""))!!
        assertTrue(request.summary.contains("« Courses »"))
        val stop = SensitiveActionPolicy.requestFor("app_state_control", JSONObject("""{"action":"freeze","package_name":"x.y"}"""))!!
        assertTrue(stop.summary.contains("x.y"))
    }

    @Test
    fun majordomeRiskFailsSafe() {
        assertEquals(ActionRisk.LOW, SensitiveActionPolicy.forMajordome("Créer un rendez-vous", null).risk)
        assertEquals(ActionRisk.LOW, SensitiveActionPolicy.forMajordome("Créer un rendez-vous", "low").risk)
        assertEquals(ActionRisk.HIGH, SensitiveActionPolicy.forMajordome("Créer un rendez-vous", "HIGH").risk)
        assertEquals(ActionRisk.HIGH, SensitiveActionPolicy.forMajordome("Créer un rendez-vous", "???").risk)
        assertEquals(ActionRisk.HIGH, SensitiveActionPolicy.forMajordome("Supprimer la recette Tarte", null).risk)
        assertEquals(ActionRisk.HIGH, SensitiveActionPolicy.forMajordome("  ", null).risk)
    }
}
