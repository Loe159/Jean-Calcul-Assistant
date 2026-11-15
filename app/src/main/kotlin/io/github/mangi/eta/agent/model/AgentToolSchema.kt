package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

internal object AgentToolSchema {
    fun coordinateSpace(): JSONObject =
        JSONObject()
            .put("type", "string")
            .put("enum", JSONArray().put("normalized").put("screen").put("screenshot"))
            .put(
                "description",
                "Required. normalized (recommended): x/y range 0–999 relative to the full screen; use for screenshots regardless of scaling." +
                    "screen: physical screen pixels, as used by ui_nodes center/bounds." +
                    "screenshot: original pixels of the latest screenshot, only when you are certain it is unscaled.",
            )

    fun function(
        name: String,
        description: String,
        parameters: JSONObject,
    ): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", parameters),
            )
}
