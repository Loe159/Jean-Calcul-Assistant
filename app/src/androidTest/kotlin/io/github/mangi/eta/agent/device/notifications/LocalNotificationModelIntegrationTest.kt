package io.github.mangi.eta.agent.device.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Executed with pinned synthetic assets already installed by the POC CI. */
@RunWith(AndroidJUnit4::class)
class LocalNotificationModelIntegrationTest {
    @Test fun productionClassifierMatchesPinnedOnnxReference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Pinned model+tokenizer must be installed for this test", LocalNotificationModel.installed(context))
        val fixtures = JSONObject(instrumentation.context.assets.open("notification_nli_reference.json")
            .bufferedReader().use { it.readText() })
        val input = "SHEIN : -70 % seulement aujourd'hui !"
        val observed = LocalNotificationModel.classify(context, input)
        assertEquals("a promotional advertisement", observed.first().first)
        assertEquals(7, observed.size)
        assertTrue(abs(observed.sumOf { it.second } - 1.0) < 1e-5)
        val expected = fixtures.getJSONArray("cases").getJSONObject(0).getJSONArray("logits")
        val expectedScores = NotificationNliScores.scores((0 until expected.length()).map { i ->
            val pair = expected.getJSONArray(i)
            listOf(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat())
        }, multiLabel = false)
        NotificationNliScores.validatedLabels.forEachIndexed { i, label ->
            val actual = observed.first { it.first == label }.second
            assertTrue("Production score diverged for $label", abs(actual - expectedScores[i]) < 0.01)
        }
    }
}
