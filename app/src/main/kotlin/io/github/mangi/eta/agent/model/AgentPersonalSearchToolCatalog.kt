package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.context.PersonalSearchTools
import org.json.JSONArray
import org.json.JSONObject

internal object AgentPersonalSearchToolCatalog {
    fun appendTo(tools: JSONArray) {
        PersonalSearchTools.searches.forEach { spec ->
            tools.put(
                function(
                    spec.name,
                    spec.description + "Results come from a historical index and may lag. Returned refs can retrieve details.",
                    searchProperties(),
                )
            )
        }
        tools.put(
            function(
                "read_personal_item",
                "Use ref from personal-data results to read indexed details. References cannot directly modify original records.",
                JSONObject().put("ref", string("ref from query results", 160)),
                "ref",
            )
        )
        val summary =
            searchProperties().also {
                it.remove("limit")
                it.remove("offset")
                it.remove("sort")
            }
        tools.put(
            function(
                "summarize_bills",
                "Precisely summarize matching bill indexes, distinguishing income, expenses, transfers and unknown types. At most 1000 records; fail rather than return partial totals when the range is too large.",
                summary,
            )
        )
    }

    fun searchProperties(): JSONObject =
        JSONObject()
            .put("query", string("Optional keyword", 200))
            .put("start_time", string("Inclusive start time, ISO 8601 with timezone offset", 64))
            .put("end_time", string("Exclusive end time, ISO 8601 with timezone offset", 64))
            .put("limit", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 30))
            .put(
                "offset",
                JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 10000),
            )
            .put(
                "sort",
                JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray(listOf("newest", "oldest"))),
            )

    fun function(
        name: String,
        description: String,
        properties: JSONObject,
        vararg required: String,
    ): JSONObject =
        AgentToolSchema.function(
            name,
            description,
            JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray(required.toList()))
                .put("additionalProperties", false),
        )

    private fun string(description: String, max: Int) =
        JSONObject().put("type", "string").put("description", description).put("maxLength", max)
}
