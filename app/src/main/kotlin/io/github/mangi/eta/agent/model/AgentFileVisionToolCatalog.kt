package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/** 文件路径到模型视觉输入的通用能力，不依赖任何个人数据 Provider。 */
internal object AgentFileVisionToolCatalog {
    fun appendTo(tools: JSONArray) {
        tools.put(
            AgentToolSchema.function(
                name = "read_image",
                description = "Read one image from a user-specified path or system media URI as model vision input. Call at most once per turn. Inspect the returned image before reading another next turn.",
                parameters = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject().put(
                            "path",
                            JSONObject()
                                .put("type", "string")
                                .put("maxLength", 1_024)
                                .put("description", "Absolute image path, file URI or system media content URI; local paths are read with root"),
                        ),
                    )
                    .put("required", JSONArray(listOf("path"))),
            ),
        )
    }
}
