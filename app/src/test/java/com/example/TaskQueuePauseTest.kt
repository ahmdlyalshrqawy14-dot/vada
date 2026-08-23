package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskQueuePauseTest {

    @Test
    fun testPausedFlowStateTransitions() = runBlocking {
        val pausedFlow = MutableStateFlow(false)

        var resumed = false
        val job = launch {
            pausedFlow.first { !it }
            resumed = true
        }

        job.join()
        assertTrue("Should be resumed when initially false", resumed)

        // Test pausing
        pausedFlow.value = true
        var resumedAfterPause = false
        val pauseJob = launch {
            pausedFlow.first { !it }
            resumedAfterPause = true
        }

        // Ensure it stays paused while flow is true
        assertEquals(false, resumedAfterPause)

        // Resume
        pausedFlow.value = false
        pauseJob.join()
        assertTrue("Should resume once flow value becomes false", resumedAfterPause)
    }
}
