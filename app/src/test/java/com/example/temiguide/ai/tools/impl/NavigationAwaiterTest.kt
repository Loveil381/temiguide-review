package com.example.temiguide.ai.tools.impl

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationAwaiterTest {

    @Before
    fun setup() {
        NavigationAwaiter.cancelAll()
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

    @Test
    fun `awaitArrival wakes exact location and any waiter without cross talk`() = runBlocking {
        val alpha = async { NavigationAwaiter.awaitArrival("alpha", 5000) }
        val beta = async { NavigationAwaiter.awaitArrival("beta", 200) }
        val any = async { NavigationAwaiter.awaitArrival(5000) }

        delay(100)
        NavigationAwaiter.onStatusChanged("alpha", "complete")

        assertTrue(alpha.await())
        assertFalse(beta.await())
        assertTrue(any.await())
    }
}
