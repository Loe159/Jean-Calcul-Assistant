package io.github.mangi.eta.agent.confirm

import org.json.JSONObject

/** How much protection the user's confirmation needs: [LOW] is a tap on Accepter, [HIGH] also asks for the fingerprint. */
internal enum class ActionRisk { LOW, HIGH }

internal data class ConfirmationRequest(val summary: String, val risk: ActionRisk)

/** Which agent actions need the user's confirmation popup, and what the popup says. Pure so it can be unit-tested. */
internal object SensitiveActionPolicy {
    private val lowRiskTools = setOf(
        "create_calendar_event",
        "create_calendar_events",
        "update_calendar_event",
        "update_alarm_time",
        "set_alarm_enabled",
        "create_note",
        "set_setting",
        "set_device_state",
        "set_hotspot",
    )

    /** Null when the tool call needs no confirmation (reads, reversible toggles, GUI and terminal tools). */
    fun requestFor(toolName: String, args: JSONObject): ConfirmationRequest? = when (toolName) {
        "delete_calendar_event" -> ConfirmationRequest(
            "Supprimer l'événement ${args.label("event_id")}", ActionRisk.HIGH,
        )
        "delete_alarm" -> ConfirmationRequest("Supprimer l'alarme ${args.label("alarm_id")}", ActionRisk.HIGH)
        "delete_note" -> ConfirmationRequest("Supprimer la note ${args.label("note_id")}", ActionRisk.HIGH)
        "app_state_control" -> appState(args)
        in lowRiskTools -> ConfirmationRequest(lowRiskSummary(toolName, args), ActionRisk.LOW)
        else -> null
    }

    /** A Majordome write waiting for approval: [serverRisk] is the homelab's optional `approval.risk`. */
    fun forMajordome(summary: String, serverRisk: String?): ConfirmationRequest {
        val text = summary.trim().ifEmpty { "Action demandée au Majordome" }
        val risk = when {
            serverRisk.equals("LOW", ignoreCase = true) -> ActionRisk.LOW
            serverRisk != null -> ActionRisk.HIGH
            summary.isBlank() -> ActionRisk.HIGH
            HIGH_WORDS.containsMatchIn(text) -> ActionRisk.HIGH
            else -> ActionRisk.LOW
        }
        return ConfirmationRequest(text, risk)
    }

    private val HIGH_WORDS = Regex(
        "supprim|effac|retir|delete|remove|envoy|send|message|sms|mail",
        RegexOption.IGNORE_CASE,
    )

    private fun appState(args: JSONObject): ConfirmationRequest {
        val pkg = args.label("package_name")
        return when (args.optString("action").lowercase()) {
            "force_stop" -> ConfirmationRequest("Forcer l'arrêt de $pkg", ActionRisk.HIGH)
            "freeze" -> ConfirmationRequest("Désactiver l'application $pkg", ActionRisk.HIGH)
            "unfreeze" -> ConfirmationRequest("Réactiver l'application $pkg", ActionRisk.LOW)
            else -> ConfirmationRequest("Modifier l'état de l'application $pkg", ActionRisk.HIGH)
        }
    }

    private fun lowRiskSummary(toolName: String, args: JSONObject): String = when (toolName) {
        "create_calendar_event" -> "Créer l'événement ${args.titleOr("title")}"
        "create_calendar_events" -> "Créer ${args.optJSONArray("events")?.length() ?: 0} événements dans l'agenda"
        "update_calendar_event" -> "Modifier l'événement ${args.label("event_id")}"
        "update_alarm_time" -> "Changer l'heure de l'alarme ${args.label("alarm_id")}"
        "set_alarm_enabled" ->
            (if (args.optBoolean("enabled", true)) "Activer" else "Désactiver") + " l'alarme ${args.label("alarm_id")}"
        "create_note" -> "Créer la note ${args.titleOr("title")}"
        "set_setting" -> "Modifier le réglage Android ${args.optString("key").take(60)}"
        "set_device_state" ->
            (if (args.optBoolean("enabled", true)) "Activer" else "Désactiver") + " ${args.optString("target").take(40)}"
        else -> (if (args.optBoolean("enabled", true)) "Activer" else "Désactiver") + " le point d'accès Wi-Fi"
    }

    private fun JSONObject.label(key: String): String =
        optString(key).trim().take(80).ifEmpty { "demandé" }

    private fun JSONObject.titleOr(key: String): String =
        optString(key).trim().take(80).takeIf { it.isNotEmpty() }?.let { "« $it »" } ?: ""
}
