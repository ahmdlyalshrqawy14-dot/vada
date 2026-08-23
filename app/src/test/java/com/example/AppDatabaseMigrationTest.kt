package com.example

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.HistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @Test
    fun testMigration1To2PreservesExistingRecords() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val dbName = "migration_test_db.db"
            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists()) {
                dbFile.delete()
            }

            // Step 1: Create version 1 database schema manually
            val factory = FrameworkSQLiteOpenHelperFactory()
            val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS history_items (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                fileName TEXT NOT NULL,
                                fileType TEXT NOT NULL,
                                operationName TEXT NOT NULL,
                                originalSizeBytes INTEGER NOT NULL,
                                processedSizeBytes INTEGER NOT NULL,
                                outputPath TEXT NOT NULL,
                                timestamp INTEGER NOT NULL,
                                processorType TEXT NOT NULL,
                                status TEXT NOT NULL,
                                errorMessage TEXT
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val helper = factory.create(config)
            val v1Db = helper.writableDatabase

            // Step 2: Insert sample records into version 1 DB
            val values = ContentValues().apply {
                put("fileName", "test_video.mp4")
                put("fileType", "VIDEO")
                put("operationName", "COMPRESS")
                put("originalSizeBytes", 1024000L)
                put("processedSizeBytes", 512000L)
                put("outputPath", "/storage/test_video.mp4")
                put("timestamp", 123456789L)
                put("processorType", "HARDWARE")
                put("status", "COMPLETED")
                putNull("errorMessage")
            }
            val insertedId = v1Db.insert("history_items", SQLiteDatabase.CONFLICT_REPLACE, values)
            assertEquals(1L, insertedId)
            helper.close()

            // Step 3: Open database with Room using version 2 and MIGRATION_1_2
            val appDatabase = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                dbName
            )
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

            // Step 4: Verify that existing records are preserved intact
            val historyDao = appDatabase.historyDao()
            val items = historyDao.getAllHistory().first()

            assertEquals(1, items.size)
            val item = items[0]
            assertEquals(1L, item.id)
            assertEquals("test_video.mp4", item.fileName)
            assertEquals("VIDEO", item.fileType)
            assertEquals("COMPRESS", item.operationName)
            assertEquals(1024000L, item.originalSizeBytes)
            assertEquals(512000L, item.processedSizeBytes)
            assertEquals("/storage/test_video.mp4", item.outputPath)
            assertEquals(123456789L, item.timestamp)
            assertEquals("HARDWARE", item.processorType)
            assertEquals("COMPLETED", item.status)
            assertNull(item.errorMessage)
            assertNull(item.compressionOutcome)

            // Step 5: Insert a new version 2 record with compressionOutcome
            val newEntity = HistoryEntity(
                fileName = "new_image.jpg",
                fileType = "IMAGE",
                operationName = "COMPRESS",
                originalSizeBytes = 500000L,
                processedSizeBytes = 200000L,
                outputPath = "/storage/new_image.jpg",
                processorType = "SOFTWARE",
                status = "COMPLETED",
                compressionOutcome = "SUCCESS"
            )
            val newId = historyDao.insertHistory(newEntity)

            val updatedItems = historyDao.getAllHistory().first()
            assertEquals(2, updatedItems.size)
            val newItem = updatedItems.first { it.id == newId }
            assertEquals("SUCCESS", newItem.compressionOutcome)

            appDatabase.close()
            dbFile.delete()
        }
    }
}
