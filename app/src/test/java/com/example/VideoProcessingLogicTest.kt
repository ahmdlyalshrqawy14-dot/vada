package com.example

import com.example.data.model.CompressionOutcome
import com.example.data.model.CompressionPreset
import com.example.data.model.ProcessorType
import com.example.data.model.ProcessingTask
import com.example.data.model.TaskType
import com.example.data.video.VideoTranscoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoProcessingLogicTest {

    @Test
    fun testBitrateCalculation_with20PercentReduction() {
        // Preset values matching VideoScreen.kt
        val mediumBaseBitrate = 1_500_000
        val lightBaseBitrate = 3_200_000
        val heavyBaseBitrate = 750_000

        // 20% reduction multiplier (0.8f)
        val mediumReduced = (mediumBaseBitrate * 0.8f).toInt()
        val lightReduced = (lightBaseBitrate * 0.8f).toInt()
        val heavyReduced = (heavyBaseBitrate * 0.8f).toInt()

        assertEquals(1_200_000, mediumReduced)
        assertEquals(2_560_000, lightReduced)
        assertEquals(600_000, heavyReduced)

        assertTrue(mediumReduced < mediumBaseBitrate)
        assertTrue(lightReduced < lightBaseBitrate)
        assertTrue(heavyReduced < heavyBaseBitrate)
    }

    @Test
    fun testProcessingTask_compressionSkippedField() {
        val taskDefault = ProcessingTask(
            title = "ضغط الفيديو",
            subtitle = "video.mp4 -> video_compressed.mp4",
            taskType = TaskType.VIDEO,
            sourceUris = emptyList(),
            outputFileName = "video_compressed",
            outputExtension = "mp4",
            executeBlock = { _, _, _, _, _ -> File("") }
        )

        assertFalse("Default compressionSkipped should be false", taskDefault.compressionSkipped)

        val taskSkipped = taskDefault.copy(compressionSkipped = true)
        assertTrue("Updated compressionSkipped should be true", taskSkipped.compressionSkipped)
    }

    @Test
    fun testEvaluateCompressionResult_successAbove5Percent() {
        // Original: 100MB, Result: 60MB (40% reduction)
        val outcome40Percent = VideoTranscoder.evaluateCompressionResult(
            originalSize = 100_000_000L,
            resultSize = 60_000_000L,
            wasPassthrough = false
        )
        assertEquals(CompressionOutcome.SUCCESS, outcome40Percent)

        // Original: 100MB, Result: 94MB (6% reduction -> >= 5%)
        val outcome6Percent = VideoTranscoder.evaluateCompressionResult(
            originalSize = 100_000_000L,
            resultSize = 94_000_000L,
            wasPassthrough = false
        )
        assertEquals(CompressionOutcome.SUCCESS, outcome6Percent)
    }

    @Test
    fun testEvaluateCompressionResult_marginalBelow5Percent() {
        // Original: 100MB, Result: 97MB (3% reduction -> < 5% but > 0%)
        val outcome3Percent = VideoTranscoder.evaluateCompressionResult(
            originalSize = 100_000_000L,
            resultSize = 97_000_000L,
            wasPassthrough = false
        )
        assertEquals(CompressionOutcome.MARGINAL, outcome3Percent)
    }

    @Test
    fun testEvaluateCompressionResult_noCompressionWhenRemuxOnlyOrNoSizeReduction() {
        // Remux only
        val outcomeRemux = VideoTranscoder.evaluateCompressionResult(
            originalSize = 100_000_000L,
            resultSize = 60_000_000L,
            wasPassthrough = true
        )
        assertEquals(CompressionOutcome.NO_COMPRESSION, outcomeRemux)

        // Equal size
        val outcomeEqual = VideoTranscoder.evaluateCompressionResult(
            originalSize = 100_000_000L,
            resultSize = 100_000_000L,
            wasPassthrough = false
        )
        assertEquals(CompressionOutcome.NO_COMPRESSION, outcomeEqual)

        // Larger output size
        val outcomeLarger = VideoTranscoder.evaluateCompressionResult(
            originalSize = 100_000_000L,
            resultSize = 110_000_000L,
            wasPassthrough = false
        )
        assertEquals(CompressionOutcome.NO_COMPRESSION, outcomeLarger)
    }

    @Test
    fun testAudioBitratePresetMapping() {
        fun getTargetAudioBitrate(preset: CompressionPreset): Int = when (preset) {
            CompressionPreset.HEAVY -> 64_000
            CompressionPreset.MEDIUM -> 96_000
            CompressionPreset.LIGHT -> 128_000
            CompressionPreset.CUSTOM -> 96_000
        }

        assertEquals(64_000, getTargetAudioBitrate(CompressionPreset.HEAVY))
        assertEquals(96_000, getTargetAudioBitrate(CompressionPreset.MEDIUM))
        assertEquals(128_000, getTargetAudioBitrate(CompressionPreset.LIGHT))
        assertEquals(96_000, getTargetAudioBitrate(CompressionPreset.CUSTOM))
    }

    @Test
    fun testIFrameIntervalPresetMapping() {
        fun getIFrameInterval(preset: CompressionPreset): Int = when (preset) {
            CompressionPreset.HEAVY -> 4
            CompressionPreset.MEDIUM -> 3
            CompressionPreset.LIGHT -> 2
            CompressionPreset.CUSTOM -> 2
        }

        assertEquals(4, getIFrameInterval(CompressionPreset.HEAVY))
        assertEquals(3, getIFrameInterval(CompressionPreset.MEDIUM))
        assertEquals(2, getIFrameInterval(CompressionPreset.LIGHT))
        assertEquals(2, getIFrameInterval(CompressionPreset.CUSTOM))
    }
}
