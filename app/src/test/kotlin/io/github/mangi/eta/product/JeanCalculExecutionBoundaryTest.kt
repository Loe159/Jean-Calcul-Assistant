package io.github.mangi.eta.product

import android.app.Application
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.tool.AgentLocalTools
import io.github.mangi.eta.core.AgentLogger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class JeanCalculExecutionBoundaryTest {
    private val logger = object : AgentLogger {
        override fun debug(message: () -> String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
    @Test fun directOrStaleToolCallsCannotBypassTheSelection() {
        AgentLocalTools(RuntimeEnvironment.getApplication(), logger, rootAvailable = { true }, isToolEnabled = { true }).use { tools ->
            listOf("create_note", "search_system_memories", "character_memory_write").forEach { name ->
                val result = tools.execute(AgentModelClient.ToolCall("attempt-$name", name, "{}"))
                assertEquals("FEATURE_DISABLED", JSONObject(result.content).getString("code"))
            }
            val result = tools.execute(AgentModelClient.ToolCall("linux", "terminal", """{"environment":"linux","command":"echo must-not-run"}"""))
            assertEquals("FEATURE_DISABLED", JSONObject(result.content).getString("code"))
        }
    }
}
