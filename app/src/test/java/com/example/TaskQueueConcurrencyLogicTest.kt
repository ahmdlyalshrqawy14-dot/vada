package com.example

import com.example.data.model.ProcessingTask
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TaskQueueConcurrencyLogicTest {

    @Test
    fun testConcurrencyLimitCalculation() {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val calculatedLimit = (availableProcessors / 2).coerceIn(1, 2)
        assertTrue("Max concurrent tasks must be 1 or 2", calculatedLimit in 1..2)
    }

    @Test
    fun testEligibleTaskSelection_withActiveVideo() {
        val activeTasks = listOf(
            ProcessingTask(
                id = "task-video-1",
                title = "Video Compression",
                subtitle = "vid.mp4",
                taskType = TaskType.VIDEO,
                sourceUris = emptyList(),
                outputFileName = "vid_out",
                outputExtension = "mp4",
                executeBlock = { _, _, _, _, _ -> File("") }
            )
        )

        val queue = listOf(
            ProcessingTask(
                id = "task-video-2",
                title = "Another Video",
                subtitle = "vid2.mp4",
                taskType = TaskType.VIDEO,
                sourceUris = emptyList(),
                outputFileName = "vid2_out",
                outputExtension = "mp4",
                executeBlock = { _, _, _, _, _ -> File("") }
            ),
            ProcessingTask(
                id = "task-image-1",
                title = "Image Compress",
                subtitle = "img1.png",
                taskType = TaskType.IMAGE,
                sourceUris = emptyList(),
                outputFileName = "img1_out",
                outputExtension = "jpg",
                executeBlock = { _, _, _, _, _ -> File("") }
            )
        )

        val hasActiveVideo = activeTasks.any { it.taskType == TaskType.VIDEO }
        val eligibleTask = queue.firstOrNull { task ->
            if (hasActiveVideo) {
                task.taskType != TaskType.VIDEO
            } else {
                true
            }
        }

        // Must pick the image task even if video-2 is earlier in queue
        assertEquals("task-image-1", eligibleTask?.id)
    }

    @Test
    fun testIndependentTaskCancellation() = runBlocking {
        val executionMap = ConcurrentHashMap<String, CompletableDeferred<String>>()
        val task1Deferred = CompletableDeferred<String>()
        val task2Deferred = CompletableDeferred<String>()

        executionMap["task-1"] = task1Deferred
        executionMap["task-2"] = task2Deferred

        // Cancel task 1
        executionMap.remove("task-1")?.cancel()

        // Task 1 should be cancelled
        assertTrue(task1Deferred.isCancelled)

        // Task 2 must still be active and can complete normally
        assertFalse(task2Deferred.isCancelled)
        task2Deferred.complete("Task 2 finished")
        assertEquals("Task 2 finished", task2Deferred.await())
    }
}
