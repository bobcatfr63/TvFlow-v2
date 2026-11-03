package com.tvflow.app.data.local

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodSuggestionsDeviceTest {
    private fun item(profile: String, id: Int, category: String, year: Int? = null) =
        VodItemEntity(profile, id, "Film %06d".format(id), category, null, null,
            added = id.toLong(), releaseYear = year, releaseMonth = if (id % 9 == 0) null else 1 + id % 12,
            containerExtension = "mp4")

    @Test fun largeCategoryHydratesOnlyTenAndPreservesOrderAndIsolation(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java).build()
        try {
            val dao = db.dao()
            val items = (1..25_000).map { id -> item("audit", id, "category", if (id % 7 == 0) null else 2000 + id % 27) }
            items.chunked(500).forEach { dao.upsertVod(it) }
            dao.upsertVod(listOf(item("other", 90_001, "category", 2099), item("audit", 90_002, "other", 2099)))
            val sorted = items.sortedWith(compareByDescending<VodItemEntity> { it.releaseYear ?: 0 }
                .thenByDescending { it.releaseMonth ?: 0 }.thenByDescending { it.added ?: 0 }
                .thenBy { it.name }.thenBy { it.streamId })
            val excluded = sorted.first().streamId
            val start = SystemClock.elapsedRealtime()
            val actual = dao.observeVodSuggestions("audit", "category", excluded, 10).first()
            val elapsed = SystemClock.elapsedRealtime() - start
            assertEquals(10, actual.size)
            assertEquals(sorted.filter { it.streamId != excluded }.take(10).map { it.streamId }, actual.map { it.streamId })
            assertTrue(actual.all { it.profileId == "audit" && it.categoryId == "category" })
            assertEquals(25_001, dao.countVodItems("audit"))
            Log.i("NextVodSuggestionsTest", "categoryRows=25000 hydratedRows=${actual.size} queryMs=$elapsed")
        } finally { db.close() }
    }

    @Test fun missingExcludedAndUpdatedMetadataAreHandled(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.upsertVod(listOf(item("audit", 1, "one", 2020), item("audit", 2, "one", 2021)))
            assertTrue(dao.observeVodSuggestions("audit", "missing", 0, 10).first().isEmpty())
            assertTrue(dao.observeVodSuggestions("missing", "one", 0, 10).first().isEmpty())
            assertEquals(listOf(2), dao.observeVodSuggestions("audit", "one", 1, 10).first().map { it.streamId })
            assertEquals(listOf(2, 1), dao.observeVodSuggestions("audit", "one", 0, 10).first().map { it.streamId })
            dao.upsertVod(listOf(item("audit", 1, "one", 2022)))
            assertEquals(listOf(1, 2), dao.observeVodSuggestions("audit", "one", 0, 10).first().map { it.streamId })
        } finally { db.close() }
    }
}
