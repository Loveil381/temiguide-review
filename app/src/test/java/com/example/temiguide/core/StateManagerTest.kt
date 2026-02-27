package com.example.temiguide.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StateManagerTest {

    private lateinit var sm: StateManager

    @Before
    fun setup() {
        sm = StateManager()
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(sm.state.value is AppState.Idle)
    }

    @Test
    fun `valid transition Idle to Greeting succeeds`() {
        val ok = sm.transition(AppState.Greeting)
        assertTrue(ok)
        assertTrue(sm.state.value is AppState.Greeting)
    }

    @Test
    fun `same state transition is denied`() {
        val ok = sm.transition(AppState.Idle)
        assertFalse(ok)
    }

    @Test
    fun `any state can transition to Error`() {
        sm.transition(AppState.Greeting)
        val ok = sm.transition(AppState.Error("test", true))
        assertTrue(ok)
        assertTrue(sm.state.value is AppState.Error)
    }

    @Test
    fun `any state can transition to Idle`() {
        sm.transition(AppState.Greeting)
        sm.transition(AppState.Listening())
        val ok = sm.transition(AppState.Idle)
        assertTrue(ok)
        assertTrue(sm.state.value is AppState.Idle)
    }

    @Test
    fun `invalid transition is denied`() {
        // StaffCall can only go to Idle or Error
        sm.forceTransition(AppState.StaffCall("test"))
        val ok = sm.transition(AppState.Greeting)
        assertFalse(ok)
        assertTrue(sm.state.value is AppState.StaffCall)
    }

    @Test
    fun `forceTransition bypasses matrix`() {
        sm.forceTransition(AppState.StaffCall("test"))
        sm.forceTransition(AppState.Greeting)
        assertTrue(sm.state.value is AppState.Greeting)
    }

    @Test
    fun `reset returns to Idle`() {
        sm.transition(AppState.Greeting)
        sm.reset()
        assertTrue(sm.state.value is AppState.Idle)
    }

    @Test
    fun `previousState tracks last state`() {
        sm.transition(AppState.Greeting)
        sm.transition(AppState.Listening())
        assertTrue(sm.previousState is AppState.Greeting)
    }

    @Test
    fun `isAutonomous returns true for Idle and Autonomous`() {
        assertTrue(sm.isAutonomous())
        sm.forceTransition(AppState.Autonomous("test"))
        assertTrue(sm.isAutonomous())
        sm.forceTransition(AppState.Navigating("A", "A"))
        assertFalse(sm.isAutonomous())
    }

    @Test
    fun `isGuiding returns true for Navigating and Arrival`() {
        sm.forceTransition(AppState.Navigating("A", "A"))
        assertTrue(sm.isGuiding())
        sm.forceTransition(AppState.Arrival("A", "A"))
        assertTrue(sm.isGuiding())
        sm.forceTransition(AppState.Idle)
        assertFalse(sm.isGuiding())
    }
}
