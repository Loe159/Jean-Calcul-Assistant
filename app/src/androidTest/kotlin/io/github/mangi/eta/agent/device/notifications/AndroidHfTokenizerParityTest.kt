package io.github.mangi.eta.agent.device.notifications

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native BPE pair fidelity; only synthetic fixtures and app-private weights. */
@RunWith(AndroidJUnit4::class)
class AndroidHfTokenizerParityTest {
    @Test
    fun nativeHfBpeMatchesReferencePairs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val tokenizerFile = File(instrumentation.targetContext.filesDir, "notification-model/tokenizer.json")
        assertTrue("Tokenizer not installed in app-private storage", tokenizerFile.isFile)
        val tokenizer = AndroidHfTokenizer()
        val started = SystemClock.elapsedRealtimeNanos()
        assertTrue("Native tokenizers could not load pinned tokenizer.json", tokenizer.initialize(tokenizerFile.absolutePath))
        val fixtures = JSONObject(
            instrumentation.context.assets.open("notification_nli_reference.json").bufferedReader().use { it.readText() },
        )
        val cases = fixtures.getJSONArray("cases")
        val examples = listOf(
            "SHEIN : -70 % seulement aujourd'hui !",
            "Salut, tu peux me rappeler ce soir ?",
        )
        for ((caseIndex, example) in examples.withIndex()) {
            val fixture = cases.getJSONObject(caseIndex)
            val ids = fixture.getJSONArray("input_ids")
            val masks = fixture.getJSONArray("attention_mask")
            val labels = fixtures.getJSONArray("labels")
            for (i in 0 until labels.length()) {
                val attention = masks.getJSONArray(i)
                val activeCount = (0 until attention.length()).count { attention.getInt(it) == 1 }
                val reference = ids.getJSONArray(i)
                val expected = LongArray(activeCount) { reference.getLong(it) }
                val actual = tokenizer.encodePair(example, "This notification is ${labels.getString(i)}.", 128)
                assertTrue("Native tokenizer returned null for synthetic case $caseIndex/$i", actual != null)
                assertArrayEquals("BPE input mismatch at $caseIndex/$i", expected, actual)
            }
        }
        val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        instrumentation.sendStatus(0, Bundle().apply {
            putString("metrics", "HF_TOKENIZER_ANDROID_POC pairs=14 elapsed_ms=$elapsed")
        })
    }
}
