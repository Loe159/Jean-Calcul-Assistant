package fr.loevan.jeancalcul.feature.tasks

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.UUID

@Serializable
data class LocalTask(
    val id: String,
    val title: String,
    val notes: String? = null,
    val dueAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
)

interface LocalTaskStore {
    fun create(
        actionId: String,
        title: String,
        notes: String?,
        dueAtEpochMillis: Long?,
        createdAtEpochMillis: Long,
    ): LocalTask

    fun list(): List<LocalTask>
}

/** Minimal local-only persistence used by the phase-1 task creation tool. */
class SharedPreferencesLocalTaskStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = false },
) : LocalTaskStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun create(
        actionId: String,
        title: String,
        notes: String?,
        dueAtEpochMillis: Long?,
        createdAtEpochMillis: Long,
    ): LocalTask {
        require(actionId.isNotBlank())
        require(title.isNotBlank())
        require(createdAtEpochMillis >= 0)
        require(dueAtEpochMillis == null || dueAtEpochMillis >= 0)
        val id = stableTaskId(actionId)
        val current = list()
        current.firstOrNull { it.id == id }?.let { return it }
        val task = LocalTask(id, title.trim(), notes?.trim()?.ifBlank { null }, dueAtEpochMillis, createdAtEpochMillis)
        val stored = json.encodeToString(ListSerializer(LocalTask.serializer()), current + task)
        check(preferences.edit().putString(TASKS_KEY, stored).commit()) {
            "The local task could not be persisted."
        }
        return task
    }

    @Synchronized
    override fun list(): List<LocalTask> {
        val stored = preferences.getString(TASKS_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(LocalTask.serializer()), stored)
        }.getOrDefault(emptyList())
    }

    private fun stableTaskId(actionId: String): String =
        UUID.nameUUIDFromBytes("jean-calcul-task:$actionId".toByteArray(StandardCharsets.UTF_8)).toString()

    private companion object {
        const val PREFERENCES_NAME = "local_tasks"
        const val TASKS_KEY = "tasks"
    }
}
