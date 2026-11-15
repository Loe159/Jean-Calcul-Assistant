package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** 屏幕手势与节点交互工具 schema。 */
internal object AgentGestureToolCatalog {
    fun appendTo(tools: JSONArray) {
        tools
            .put(
                AgentToolSchema.function(
                    name = "tap",
                    description = "Tap coordinates. Prefer tap_element when nodes are available. Use coordinate_space=normalized (0–999) for screenshots or screen for ui_nodes.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("x", JSONObject().put("type", "integer"))
                                .put("y", JSONObject().put("type", "integer"))
                                .put("coordinate_space", AgentToolSchema.coordinateSpace())
                        )
                        .put("required", JSONArray().put("x").put("y").put("coordinate_space"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "tap_area",
                    description = "Tap a rectangle's center, preferring this for large buttons, rows and text regions. Uses tap coordinate rules; coordinate_space is required.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("x1", JSONObject().put("type", "integer"))
                                .put("y1", JSONObject().put("type", "integer"))
                                .put("x2", JSONObject().put("type", "integer"))
                                .put("y2", JSONObject().put("type", "integer"))
                                .put("coordinate_space", AgentToolSchema.coordinateSpace())
                        )
                        .put("required", JSONArray().put("x1").put("y1").put("x2").put("y2").put("coordinate_space"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "tap_element",
                    description = "Tap a node from the latest observation. index and observation_id must come from the same observe_screen; observe again if expired. Runtime verifies accessibility before execution.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "index",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "UI node index from the same observe_screen")
                                )
                                .put(
                                    "observation_id",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "observation_id from the same latest observe_screen as index")
                                )
                        )
                        .put("required", JSONArray().put("index").put("observation_id"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "long_press",
                    description = "Long press coordinates, preferring long_press_element when nodes are available. Same coordinate rules as tap; coordinate_space is required.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("x", JSONObject().put("type", "integer"))
                                .put("y", JSONObject().put("type", "integer"))
                                .put(
                                    "duration_ms",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "Long-press duration, 300–3000 ms; default 800")
                                )
                                .put("coordinate_space", AgentToolSchema.coordinateSpace())
                        )
                        .put("required", JSONArray().put("x").put("y").put("coordinate_space"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "long_press_element",
                    description = "Long press a node from the latest observation. index and observation_id must come from the same observe_screen; observe again if expired. Runtime verifies accessibility before execution.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "index",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "UI node index from the same observe_screen")
                                )
                                .put(
                                    "observation_id",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "observation_id from the same latest observe_screen as index")
                                )
                                .put(
                                    "duration_ms",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "Long-press duration, 300–3000 ms; default 800")
                                )
                        )
                        .put("required", JSONArray().put("index").put("observation_id"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "swipe",
                    description = "Drag between coordinates in finger direction for sliders, carousels, maps and precise gestures. Use scroll for lists. Same coordinate rules as tap; coordinate_space is required.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("x1", JSONObject().put("type", "integer"))
                                .put("y1", JSONObject().put("type", "integer"))
                                .put("x2", JSONObject().put("type", "integer"))
                                .put("y2", JSONObject().put("type", "integer"))
                                .put(
                                    "duration_ms",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "Swipe duration, 100–2000 ms; default 500")
                                )
                                .put("coordinate_space", AgentToolSchema.coordinateSpace())
                        )
                        .put("required", JSONArray().put("x1").put("y1").put("x2").put("y2").put("coordinate_space"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "scroll",
                    description = "Scroll the main list in content direction: down reveals lower content, up reveals upper content, similarly left/right. moved=false with at_boundary=true means the boundary was reached. Change direction or stop; use scroll_element for multiple lists.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "direction",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("up").put("down").put("left").put("right"))
                                )
                                .put(
                                    "amount",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("page").put("small"))
                                        .put("description", "page scrolls roughly one screen (default); small reveals a little content for fine positioning."),
                                )
                        )
                        .put("required", JSONArray().put("direction"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "scroll_element",
                    description = "Scroll a node in content direction: down/lower, up/upper, left/left, right/right. index and observation_id must come from the same latest observe_screen. Observe again if expired. Runtime verifies accessibility before execution.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "index",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "Scrollable node index from the same observe_screen")
                                )
                                .put(
                                    "observation_id",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "observation_id from the same latest observe_screen as index")
                                )
                                .put(
                                    "direction",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("up").put("down").put("left").put("right"))
                                        .put("description", "Content direction: down reveals lower content, up reveals upper content")
                                )
                                .put(
                                    "amount",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("page").put("small"))
                                        .put("description", "page scrolls roughly one screen (default); small reveals a little content for fine positioning."),
                                )
                        )
                        .put("required", JSONArray().put("index").put("observation_id").put("direction"))
                )
            )
    }
}
