package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.Flow

/**
 * Direct model access without backend-owned sessions, skills, jobs, or memory.
 *
 * Cancelling collection of [stream] must cancel upstream transport work. [cancel] provides an
 * explicit cancellation path for callers that only retain a request identifier.
 */
interface ModelProvider {
    val id: String

    suspend fun capabilities(profile: ModelProfile): ModelCapabilities

    suspend fun listModels(profile: ModelProfile): List<ModelDescriptor>

    fun stream(request: ChatRequest): Flow<ModelStreamEvent>

    suspend fun cancel(requestId: String)
}
