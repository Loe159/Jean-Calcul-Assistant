package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

internal object AgentBrowserToolCatalog {
    fun appendTo(tools: JSONArray) {
        tools.put(
            AgentToolSchema.function(
                name = "browser_use",
                description = "Operate Jean-Calcul's shared offscreen agent browser without switching to an external app. Execute one action per call. Usually navigate first, then get_readable for content or find_elements before interaction. Use open_uri only for external handoff.",
                parameters = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put(
                                "action",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "The single browser action to execute")
                                    .put(
                                        "enum",
                                        JSONArray()
                                            .put("navigate")
                                            .put("get_readable")
                                            .put("get_text")
                                            .put("find_elements")
                                            .put("click")
                                            .put("type")
                                            .put("scroll")
                                            .put("screenshot")
                                            .put("get_page_info")
                                            .put("go_back")
                                            .put("go_forward")
                                            .put("reload")
                                            .put("wait_for_selector")
                                    )
                            )
                            .put(
                                "url",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "URL for navigate")
                            )
                            .put(
                                "selector",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "CSS selector for click, type, get_text, find_elements or wait_for_selector")
                            )
                            .put(
                                "text",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "Text for type; sent to the tool but omitted from the visible run summary")
                            )
                            .put(
                                "submit",
                                JSONObject()
                                    .put("type", "boolean")
                                    .put("description", "Submit the form after typing; default false")
                            )
                            .put(
                                "coordinate_x",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("description", "Viewport X coordinate for click/type; provide with coordinate_y")
                            )
                            .put(
                                "coordinate_y",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("description", "Viewport Y coordinate for click/type; provide with coordinate_x")
                            )
                            .put(
                                "amount",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("description", "Scroll distance in pixels")
                            )
                            .put(
                                "direction",
                                JSONObject()
                                    .put("type", "string")
                                    .put("enum", JSONArray().put("up").put("down"))
                                    .put("description", "Scroll direction")
                            )
                            .put(
                                "offset",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("description", "Starting text offset for get_readable/get_text; default 0")
                            )
                            .put(
                                "max_chars",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("description", "Maximum text characters for get_readable/get_text")
                            )
                            .put(
                                "read_image",
                                JSONObject()
                                    .put("type", "boolean")
                                    .put("description", "Attach screenshot directly to model vision; default true")
                            )
                            .put(
                                "timeout_ms",
                                JSONObject()
                                    .put("type", "integer")
                                    .put("description", "Timeout in milliseconds for navigate/wait_for_selector")
                            )
                    )
                    .put("required", JSONArray().put("action"))
            )
        )
    }

}
