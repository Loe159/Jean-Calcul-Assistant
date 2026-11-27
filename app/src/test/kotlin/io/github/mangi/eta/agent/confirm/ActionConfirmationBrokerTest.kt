package io.github.mangi.eta.agent.confirm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionConfirmationBrokerTest {
    private val request = ConfirmationRequest("Supprimer l'alarme 3", ActionRisk.HIGH)

    private fun answerWith(decision: ConfirmationDecision): ActionConfirmationBroker {
        lateinit var broker: ActionConfirmationBroker
        broker = ActionConfirmationBroker(timeoutMs = 2_000, newId = { "r1" }) { id ->
            Thread { broker.resolve(id, decision) }.start()
            true
        }
        return broker
    }

    @Test
    fun returnsTheUsersAnswer() {
        assertEquals(ConfirmationDecision.APPROVED, answerWith(ConfirmationDecision.APPROVED).confirm(request))
        assertEquals(ConfirmationDecision.DECLINED, answerWith(ConfirmationDecision.DECLINED).confirm(request))
    }

    @Test
    fun timesOutWithoutAnAnswer() {
        val broker = ActionConfirmationBroker(timeoutMs = 50) { true }
        assertEquals(ConfirmationDecision.TIMED_OUT, broker.confirm(request))
    }

    @Test
    fun unavailableWhenThePopupCannotOpen() {
        val broker = ActionConfirmationBroker(timeoutMs = 2_000) { false }
        assertEquals(ConfirmationDecision.UNAVAILABLE, broker.confirm(request))
    }

    @Test
    fun staleOrUnknownIdsAreIgnored() {
        val broker = ActionConfirmationBroker(timeoutMs = 2_000, newId = { "r1" }) { id ->
            assertEquals(request, brokerRef.requestFor(id))
            assertNull(brokerRef.requestFor("other"))
            assertFalse(brokerRef.resolve("other", ConfirmationDecision.APPROVED))
            Thread { brokerRef.resolve(id, ConfirmationDecision.DECLINED) }.start()
            true
        }
        brokerRef = broker
        assertEquals(ConfirmationDecision.DECLINED, broker.confirm(request))
        assertFalse(broker.resolve("r1", ConfirmationDecision.APPROVED))
        assertNull(broker.requestFor("r1"))
    }

    @Test
    fun onlyOneRequestIsShownAtATime() {
        val shown = CountDownLatch(1)
        val ids = mutableListOf<String>()
        var n = 0
        lateinit var broker: ActionConfirmationBroker
        broker = ActionConfirmationBroker(timeoutMs = 5_000, newId = { "r${++n}" }) { id ->
            synchronized(ids) { ids += id }
            shown.countDown()
            true
        }
        val first = Thread { broker.confirm(request) }.also { it.start() }
        assertTrue(shown.await(2, TimeUnit.SECONDS))
        val second = Thread { broker.confirm(request) }.also { it.start() }
        Thread.sleep(200)
        assertEquals(listOf("r1"), synchronized(ids) { ids.toList() })
        broker.resolve("r1", ConfirmationDecision.DECLINED)
        first.join(2_000)
        Thread.sleep(200)
        assertEquals(listOf("r1", "r2"), synchronized(ids) { ids.toList() })
        broker.resolve("r2", ConfirmationDecision.DECLINED)
        second.join(2_000)
    }

    private lateinit var brokerRef: ActionConfirmationBroker
}
