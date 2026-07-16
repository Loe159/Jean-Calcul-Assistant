package fr.loevan.jeancalcul.assistant.session

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLifecycleOwnerTest {
    @Test
    fun `showing a created session resumes its lifecycle`() {
        val owner = SessionLifecycleOwner()

        owner.create()
        owner.show()

        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
    }

    @Test
    fun `showing an already visible session keeps its lifecycle resumed`() {
        val owner = SessionLifecycleOwner()

        owner.show()
        owner.show()

        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
    }

    @Test
    fun `destroying a session destroys its lifecycle`() {
        val owner = SessionLifecycleOwner()

        owner.show()
        owner.destroy()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
}
