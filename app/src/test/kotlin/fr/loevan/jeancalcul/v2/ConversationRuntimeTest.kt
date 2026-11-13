package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConversationRuntimeTest {
    private class MemoryStore(var value: Session = Session()) : SessionStore {
        override fun load() = value
        override fun save(session: Session) { value = session }
    }
    private class Provider(val action: suspend (suspend (String) -> Unit) -> Unit) : ConversationProvider {
        var cancelled = false
        var requests = emptyList<ChatMessage>()
        override suspend fun stream(messages: List<ChatMessage>, onText: suspend (String) -> Unit) {
            requests = messages; action(onText)
        }
        override fun cancel() { cancelled = true }
    }
    @Test fun streamingPersistsAndReloads() = runTest {
        val store = MemoryStore(); val runtime = ConversationRuntime(store)
        runtime.send("Bonjour", Provider { emit -> emit("Bon"); emit("jour") })
        assertEquals(RunStatus.COMPLETED, runtime.session.value.status)
        assertEquals("Bonjour", ConversationRuntime(store).session.value.messages.last().text)
        assertEquals(listOf("started", "completed"), store.value.events.map { it.kind })
    }
    @Test fun stopRejectsLateChunksAndRetryDoesNotDuplicateUser() = runTest {
        val runtime = ConversationRuntime(MemoryStore())
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val provider = Provider { emit -> emit("Partiel"); started.complete(Unit); release.await(); emit(" tardif") }
        val job = launch { runtime.send("Question", provider) }
        started.await(); runtime.stop(); release.complete(Unit); job.join()
        assertTrue(provider.cancelled)
        assertEquals(RunStatus.STOPPED, runtime.session.value.status)
        assertEquals("Partiel", runtime.session.value.messages.last().text)
        val retry = Provider { it("Complet") }; runtime.retry(retry)
        assertEquals(listOf(ChatMessage("user", "Question")), retry.requests)
        assertEquals(2, runtime.session.value.messages.size)
        assertEquals("Complet", runtime.session.value.messages.last().text)
    }
    @Test fun processDeathIsInterruptedAndRetryable() = runTest {
        val store = MemoryStore(Session(messages = listOf(ChatMessage("user", "Question"), ChatMessage("assistant", "début")), status = RunStatus.RUNNING))
        val runtime = ConversationRuntime(store)
        assertEquals(RunStatus.INTERRUPTED, runtime.session.value.status)
        runtime.retry(Provider { it("Reprise") })
        assertEquals("Reprise", store.value.messages.last().text)
    }
    @Test fun duplicateSendWhileRunningIsIgnoredAndResetInvalidatesRun() = runTest {
        val runtime = ConversationRuntime(MemoryStore())
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val first = launch { runtime.send("Un", Provider { started.complete(Unit); release.await(); it("retard") }) }
        started.await()
        runtime.send("Deux", Provider { fail("Concurrent request") })
        runtime.reset(); release.complete(Unit); first.join()
        assertTrue(runtime.session.value.messages.isEmpty())
        assertEquals(RunStatus.IDLE, runtime.session.value.status)
    }
    @Test fun errorsAreTypedAndToolPolicyDenies() = runTest {
        val runtime = ConversationRuntime(MemoryStore())
        runtime.send("Q", Provider { throw RunFailure(RunError.AUTHENTICATION) })
        assertEquals(RunError.AUTHENTICATION, runtime.session.value.error)
        assertFalse(AndroidToolPolicy.permits("android.shell"))
        assertFalse(runtime.session.value.events.any { it.kind.contains("Q") })
    }
    @Test fun toolLoopRunsOnlyAllowlistedClockAndPersistsProtocol() = runTest {
        val store = MemoryStore(); val runtime = ConversationRuntime(store)
        var count = 0
        val provider = object : ConversationProvider {
            override suspend fun stream(messages: List<ChatMessage>, onText: suspend (String) -> Unit) = Unit
            override suspend fun turn(messages: List<ChatMessage>, onText: suspend (String) -> Unit): List<ToolCall> {
                if (count++ == 0) return listOf(ToolCall("clock1", "current_time", "{}"))
                val result = messages.last()
                assertEquals("tool", result.role)
                assertEquals("clock1", result.toolCallId)
                assertTrue(java.time.Instant.parse(result.text).isBefore(java.time.Instant.now().plusSeconds(1)))
                onText("Réponse synthétique du fournisseur de test")
                return emptyList()
            }
            override fun cancel() = Unit
        }
        runtime.send("Heure", provider)
        assertEquals(2, count)
        assertEquals(RunStatus.COMPLETED, store.value.status)
        assertEquals("tool_completed", store.value.events[2].kind)
        assertEquals(4, store.value.messages.size)
    }
    @Test fun unknownToolsAreNeverExecutedAndLoopsAreBounded() = runTest {
        for (name in listOf("android.shell", "current_time")) {
            val runtime = ConversationRuntime(MemoryStore())
            var count = 0
            val provider = object : ConversationProvider {
                override suspend fun stream(messages: List<ChatMessage>, onText: suspend (String) -> Unit) = Unit
                override suspend fun turn(messages: List<ChatMessage>, onText: suspend (String) -> Unit): List<ToolCall> {
                    count++
                    return listOf(ToolCall("call$count", name, "{}"))
                }
                override fun cancel() = Unit
            }
            runtime.send("Test", provider)
            assertEquals(RunError.TOOL_POLICY, runtime.session.value.error)
            assertEquals(if (name == "current_time") 4 else 1, count)
            if (name != "current_time") assertTrue(runtime.session.value.messages.none { it.role == "tool" })
        }
    }
    @Test fun storageFailureIsVisible() = runTest {
        val runtime = ConversationRuntime(object : SessionStore {
            override fun load() = Session()
            override fun save(session: Session) { throw java.io.IOException() }
        })
        runtime.send("Q", Provider { fail("No network before durable request") })
        assertEquals(RunError.STORAGE, runtime.session.value.error)
    }
}
