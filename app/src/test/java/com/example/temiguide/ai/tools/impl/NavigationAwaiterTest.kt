package com.example.temiguide.ai.tools.impl

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NavigationAwaiterTest {

    @Before
    fun setup() {
        // Reset any pending continuations
        NavigationAwaiter.onStatusChanged("reset", "abort")
    }

    @Test
    fun `awaitArrival returns true on complete`() = runBlocking {
        launch {
            delay(100)
            NavigationAwaiter.onStatusChanged("testLocation", "complete")
        }
        val result = NavigationAwaiter.awaitArrival(5000)
        assertTrue(result)
    }

    @Test
    fun `awaitArrival returns false on abort`() = runBlocking {
        launch {
            delay(100)
            NavigationAwaiter.onStatusChanged("testLocation", "abort")
        }
        val result = NavigationAwaiter.awaitArrival(5000)
        assertFalse(result)
    }

    @Test
    fun `awaitArrival returns false on timeout`() = runBlocking {
        val result = NavigationAwaiter.awaitArrival(200)
        assertFalse(result)
    }
}
