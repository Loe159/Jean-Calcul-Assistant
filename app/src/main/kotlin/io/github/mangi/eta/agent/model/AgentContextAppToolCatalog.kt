package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** 上下文、应用入口与屏幕观察工具 schema。 */
internal object AgentContextAppToolCatalog {
    fun appendTo(tools: JSONArray) {
        tools
            .put(
                AgentToolSchema.function(
                    name = "get_current_context",
                    description = "Get current device time, timezone and last known location. Use for now, today, tomorrow or current-location questions.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "search_apps",
                    description = "Search installed Android apps, returning names and package IDs. Use before opening an app if its package is unknown.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "query",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "App name or package fragment, e.g. QQ, WeChat or com.tencent")
                                )
                                .put(
                                    "include_system",
                                    JSONObject()
                                        .put("type", "boolean")
                                        .put("description", "Include system apps; default false")
                                )
                                .put(
                                    "limit",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "Return 1–20 results; default 10")
                                )
                        )
                        .put("required", JSONArray().put("query"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "launch_app",
                    description = "Launch an installed Android app, preferring package_name. app_name allows fuzzy matches; multiple matches return candidates without launching. ok=true means the request was submitted; only launch_status=window_observed confirms a window on the target canvas. If unconfirmed, observe_screen before relaunching.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "package_name",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "Exact Android package, e.g. com.tencent.mobileqq")
                                )
                                .put(
                                    "app_name",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "App display name, e.g. QQ")
                                )
                                .put(
                                    "display",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("enum", JSONArray().put("auto").put("virtual").put("main"))
                                        .put("default", "auto")
                                        .put(
                                            "description",
                                            "Target canvas: auto (default; cross-app tasks prefer the background virtual display without interrupting the main screen), virtual (explicit background display), or main (visible physical foreground)."
                                        )
                                )
                        )
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "present_to_user",
                    description = "Seamlessly present a prepared virtual-display app on the physical main screen for viewing, verification or payment confirmation. Use after preparation reaches checkout, QR or results, or a secure window requires the user.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "reason",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "Optional short reason for presenting, e.g. payment confirmation is ready")
                                )
                        )
                        .put("additionalProperties", false),
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "open_uri",
                    description = "Hand a known valid URI to an external Android app, such as https, tel, geo or an app deep link. Not for reading or interacting with web pages. Never invent URIs.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "uri",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "A known valid URI the system can handle")
                                )
                        )
                        .put("required", JSONArray().put("uri"))
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "observe_screen",
                    description = "Acquire phone control and observe the screen; the runtime waits if another conversation owns it. Default returns foreground app, dimensions, observation_id and visible nodes without a screenshot. Set include_screenshot=true only for insufficient nodes, ambiguous targets or visual/canvas/map/image/QR tasks; keep include_ui_tree=true so new nodes and screenshot share observation_id. Never mix new screenshots with old nodes. Send node actions with the same observation_id. For useful truncated nodes, first raise max_nodes to 120. Call release_gui when finished; observe again after release or user takeover.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "include_screenshot",
                                    JSONObject()
                                        .put("type", "boolean")
                                        .put("default", AgentScreenObservationContract.DEFAULT_INCLUDE_SCREENSHOT)
                                        .put("description", "Attach a screenshot for model vision; default false. Enable only when UI nodes are insufficient.")
                                )
                                .put(
                                    "include_ui_tree",
                                    JSONObject()
                                        .put("type", "boolean")
                                        .put("default", AgentScreenObservationContract.DEFAULT_INCLUDE_UI_TREE)
                                        .put("description", "Return UI nodes; default true")
                                )
                                .put(
                                    "max_nodes",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("minimum", AgentScreenObservationContract.MIN_MAX_NODES)
                                        .put("maximum", AgentScreenObservationContract.MAX_MAX_NODES)
                                        .put("default", AgentScreenObservationContract.DEFAULT_MAX_NODES)
                                        .put("description", "Return 1–120 UI nodes; default 60")
                                )
                        )
                )
            )
            .put(
                AgentToolSchema.function(
                    name = "release_gui",
                    description = "End this GUI phase and release phone control for other conversations. Reasoning, web, shell and file work need no phone ownership. Old observations/nodes/coordinates expire; call observe_screen before acting again. Succeeds even without ownership.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject())
                        .put("additionalProperties", false),
                )
            )
    }
}
