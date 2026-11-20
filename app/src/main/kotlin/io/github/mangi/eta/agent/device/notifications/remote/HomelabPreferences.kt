package io.github.mangi.eta.agent.device.notifications.remote

import org.json.JSONObject

/** Read-only presentation of the homelab's explicit preferences (the homelab owns them). */
internal object HomelabPreferences {
    private val priorities = mapOf("NOW" to "Maintenant", "TODAY" to "Aujourd'hui", "LOW" to "Sans importance")

    fun list(pairing: MastraPairing): List<Pair<String, String>> = parse(MastraNotificationClient(pairing).preferences())

    fun delete(pairing: MastraPairing, id: String) = MastraNotificationClient(pairing).deletePreference(id)

    fun parse(raw: String): List<Pair<String, String>> {
        val array = JSONObject(raw).optJSONArray("preferences") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val p = array.optJSONObject(i) ?: return@mapNotNull null
            if (p.optString("origin") != "USER") return@mapNotNull null
            val scope = p.optJSONObject("scope") ?: JSONObject()
            fun field(o: JSONObject, k: String) = o.optString(k).takeIf { !o.isNull(k) && it.isNotBlank() }
            val target = listOfNotNull(field(scope, "package")?.substringAfterLast('.'), field(scope, "subtype")?.replace('_', ' '),
                field(scope, "channelId")?.let { "canal $it" }).joinToString(" · ").ifBlank { "Toutes" }
            val effect = listOfNotNull(field(p, "priority")?.let { priorities[it] ?: it }, field(p, "topic"),
                field(p, "interest")?.let { if (it == "UNWANTED") "indésirable" else if (it == "WANTED") "intéressante" else null },
                if (p.optBoolean("keepOnAndroid")) "garder sur Android" else null).joinToString(", ")
            field(p, "id")?.let { it to "$target → $effect" }
        }
    }
}
