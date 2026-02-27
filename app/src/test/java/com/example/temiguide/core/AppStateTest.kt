package com.example.temiguide.core

import org.junit.Assert.*
import org.junit.Test
import com.example.temiguide.ui.ScreenManager

class AppStateTest {

    @Test
    fun `Idle can transition to expected states`() {
        val idle = AppState.Idle
        assertTrue(idle.canTransitionTo(AppState.Greeting))
        assertTrue(idle.canTransitionTo(AppState.Listening()))
        assertTrue(idle.canTransitionTo(AppState.Autonomous("test")))
        assertTrue(idle.canTransitionTo(AppState.Navigating("A", "A")))
        assertTrue(idle.canTransitionTo(AppState.Error("e", true)))
    }

    @Test
    fun `same state transition is blocked`() {
        assertFalse(AppState.Idle.canTransitionTo(AppState.Idle))
        assertFalse(AppState.Greeting.canTransitionTo(AppState.Greeting))
    }

    @Test
    fun `any state can go to Idle or Error`() {
        val states = listOf(
            AppState.Greeting, AppState.Listening(), AppState.Thinking,
            AppState.Speaking("t"), AppState.Navigating("a", "a"),
            AppState.Arrival("a", "a"), AppState.Autonomous("t")
        )
        for (s in states) {
            assertTrue("${s::class.simpleName} → Idle should be allowed", s.canTransitionTo(AppState.Idle))
            assertTrue("${s::class.simpleName} → Error should be allowed", s.canTransitionTo(AppState.Error("e", true)))
        }
    }

    @Test
    fun `StaffCall can only transition to Idle or Error`() {
        val sc = AppState.StaffCall("reason")
        assertTrue(sc.canTransitionTo(AppState.Idle))
        assertTrue(sc.canTransitionTo(AppState.Error("e", true)))
        assertFalse(sc.canTransitionTo(AppState.Greeting))
        assertFalse(sc.canTransitionTo(AppState.Navigating("a", "a")))
    }

    @Test
    fun `screenId returns correct values`() {
        assertEquals(ScreenManager.SCREEN_IDLE, AppState.Idle.screenId())
        assertEquals(ScreenManager.SCREEN_GREETING, AppState.Greeting.screenId())
    }
}
