package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.util.StorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class StorageManagerSecurityTest {

    @Test
    fun testStorageManagerMimeTypes() {
        assertEquals("video/mp4", StorageManager.getMimeType("mp4"))
        assertEquals("audio/mpeg", StorageManager.getMimeType("mp3"))
        assertEquals("image/png", StorageManager.getMimeType("png"))
        assertEquals("application/pdf", StorageManager.getMimeType("pdf"))
        assertEquals("application/zip", StorageManager.getMimeType("zip"))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", StorageManager.formatFileSize(0))
        assertEquals("500 B", StorageManager.formatFileSize(500))
        assertEquals("1 KB", StorageManager.formatFileSize(1024))
        assertEquals("1.0 MB", StorageManager.formatFileSize(1024 * 1024))
        assertEquals("1.00 GB", StorageManager.formatFileSize(1024L * 1024 * 1024))
    }

    @Test
    fun testCleanTempFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val temp1 = StorageManager.createTempFile(context, "vada_", "mp4")
        temp1.writeText("test content")
        assertTrue(temp1.exists())

        StorageManager.cleanTempFiles(context)
        assertTrue(!temp1.exists())
    }

    @Test
    fun testFileValidatorStorageSpace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheOk = com.example.data.util.FileValidator.checkAvailableStorageSpace(context, 1024L)
        assertTrue(cacheOk)

        val destOk = com.example.data.util.FileValidator.checkDestinationStorageSpace(context, 1024L, null)
        assertTrue(destOk)
    }

    @Test
    fun testSaveFinalOutputLocal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val temp = StorageManager.createTempFile(context, "test_", "mp4")
        temp.writeText("hello world")
        val result = StorageManager.saveFinalOutput(context, temp, "my_video", "mp4", null)
        assertEquals("my_video.mp4", result.name)
        assertTrue(result.length > 0)
        assertTrue(result.pathOrUri.isNotEmpty())
    }
}

