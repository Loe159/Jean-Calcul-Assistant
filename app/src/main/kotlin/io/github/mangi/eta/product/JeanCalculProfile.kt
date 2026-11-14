package io.github.mangi.eta.product

import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.ProviderTypes
import io.github.mangi.eta.data.model.runtimeProviderType
import io.github.mangi.eta.agent.model.AgentModelClient

/** Product selection approved on 2026-11-14. Not a user-editable permission setting. */
internal object JeanCalculProfile {
    val retainedFeatures = setOf(1,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,32,33,34,35,36,37,38,43,44)
    val excludedFeatures = setOf(2,30,31,39,40,41,42)
    private val excludedTools = setOf(
        "character_memory_get", "character_memory_write", "create_note", "read_note", "delete_note",
        "search_notes", "search_coloros_notes", "search_coloros_recordings", "search_recording_summaries",
        "search_system_memories", "search_coloros_memories", "search_saved_places", "search_bills",
        "summarize_bills", "search_todos", "search_calendar_todos", "search_memory_collections",
        "search_daily_events", "search_flights", "search_hotels", "search_trains", "read_personal_item",
    )
    fun allowsTool(name: String): Boolean = name !in excludedTools
    fun allowsEnvironment(environment: String): Boolean = environment.isBlank() || environment.trim().equals("android", ignoreCase = true)
    fun allowsProvider(provider: ProviderSetting): Boolean = provider.runtimeProviderType == ProviderTypes.OPENAI_COMPATIBLE &&
        allowsConnection(provider.authMode, provider.sourceType)
    fun allowsConnection(authMode: String, sourceType: String): Boolean =
        authMode == ProviderAuthModes.OPENAI_CHATGPT ||
            (authMode == ProviderAuthModes.API_KEY && sourceType in setOf("", ProviderSourceTypes.CUSTOM))
    fun allowsModel(config: AgentModelClient.ModelConfig): Boolean =
        config.providerType == ProviderTypes.OPENAI_COMPATIBLE && allowsConnection(config.authMode, config.providerSourceType)
}
