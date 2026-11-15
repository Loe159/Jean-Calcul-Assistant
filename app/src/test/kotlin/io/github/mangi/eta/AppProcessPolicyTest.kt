package io.github.mangi.eta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessPolicyTest {
    @Test
    fun `仅主进程初始化完整 Runtime 依赖`() {
        assertTrue(AppProcessPolicy.shouldInitializeFullRuntime("fr.loevan.jeancalcul.v2", "fr.loevan.jeancalcul.v2"))
        assertFalse(AppProcessPolicy.shouldInitializeFullRuntime("io.github.mangi.eta:voice", "fr.loevan.jeancalcul.v2"))
        assertFalse(AppProcessPolicy.shouldInitializeFullRuntime("io.github.mangi.eta:voice_session", "fr.loevan.jeancalcul.v2"))
    }
}
