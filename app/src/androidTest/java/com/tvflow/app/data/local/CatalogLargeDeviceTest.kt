package com.tvflow.app.data.local

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated, synthetic 100k-film catalog: never uses real profiles or Xtream credentials. */
@RunWith(AndroidJUnit4::class)
class CatalogLargeDeviceTest {
    @Test fun refresh100kFilmsAndPreserveExistingMetadata() { runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TVFlowDatabase::class.java).build()
        val profile = "synthetic-100k"
        val count = 100_000
        try {
            val dao = db.dao()
            val t0 = SystemClock.elapsedRealtime()
            (1..count).asSequence().map { id ->
                VodItemEntity(profile, id, "Film $id", "c", null, null, null,
                    containerExtension = "mp4", synopsis = if (id == 1) "Local metadata" else null)
            }.chunked(500).forEach { dao.upsertVod(it) }
            val t1 = SystemClock.elapsedRealtime()
            assertEquals(count, dao.countVodItems(profile))
            val incoming = (1..count).map { id ->
                VodItemEntity(profile, id, "Updated film $id", "c", null, null, null,
                    containerExtension = "mp4")
            }
            dao.replaceVodCatalog(profile, listOf(MediaCategoryEntity(profile, "c", "Movies", "VOD", 0)), incoming)
            val t2 = SystemClock.elapsedRealtime()
            assertEquals(count, dao.countVodItems(profile))
            assertEquals("Local metadata", dao.getAllVod(profile).first { it.streamId == 1 }.synopsis)
            Log.i("CatalogStress", "100k insert=${t1-t0}ms replace=${t2-t1}ms heap=${Runtime.getRuntime().totalMemory()/1048576}MiB native=${Debug.getNativeHeapAllocatedSize()/1048576}MiB")
        } finally {
            db.close()
        }
    } }
}
