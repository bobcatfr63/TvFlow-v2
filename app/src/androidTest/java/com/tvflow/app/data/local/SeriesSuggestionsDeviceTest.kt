package com.tvflow.app.data.local

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesSuggestionsDeviceTest {
    @Test fun largeCategoryReturnsOnlyTenInStableOrderAndIsolatesProfile(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java
        ).build()
        try {
            val dao = db.dao()
            val items = (1..25_000).map { id ->
                SeriesItemEntity("audit", id, "Serie %05d".format(id), "category", null, null, null,
                    releaseYear = if (id % 7 == 0) null else 2000 + id % 27,
                    releaseMonth = if (id % 9 == 0) null else 1 + id % 12)
            }
            items.chunked(500).forEach { dao.upsertSeries(it) }
            dao.upsertSeries(listOf(
                SeriesItemEntity("other", 90_001, "Autre profil", "category", null, null, null, releaseYear = 2099),
                SeriesItemEntity("audit", 90_002, "Autre categorie", "other", null, null, null, releaseYear = 2099)
            ))
            val sorted = items.sortedWith(compareByDescending<SeriesItemEntity> { it.releaseYear ?: 0 }
                .thenByDescending { it.releaseMonth ?: 0 }.thenBy { it.name }.thenBy { it.seriesId })
            val excluded = sorted.first().seriesId
            val start = SystemClock.elapsedRealtime()
            val actual = dao.getSeriesSuggestions("audit", "category", excluded, 10)
            val elapsed = SystemClock.elapsedRealtime() - start
            assertEquals(10, actual.size)
            assertEquals(sorted.filter { it.seriesId != excluded }.take(10).map { it.seriesId },
                actual.map { it.seriesId })
            assertTrue(actual.all { it.profileId == "audit" && it.categoryId == "category" })
            assertEquals(actual, dao.getSeriesSuggestions("audit", "category", excluded, 10))
            assertEquals(25_001, dao.countSeriesItems("audit"))
            Log.i("NextSuggestionsTest", "categoryRows=25000 hydratedRows=${actual.size} queryMs=$elapsed")
        } finally { db.close() }
    }

    @Test fun emptyCategoryAndExcludedOnlyItemReturnEmpty(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java
        ).build()
        try {
            val dao = db.dao()
            dao.upsertSeries(listOf(SeriesItemEntity("audit", 1, "Unique", "one", null, null, null)))
            assertTrue(dao.getSeriesSuggestions("audit", "missing", 1, 10).isEmpty())
            assertTrue(dao.getSeriesSuggestions("audit", "one", 1, 10).isEmpty())
            assertTrue(dao.getSeriesSuggestions("missing", "one", 2, 10).isEmpty())
        } finally { db.close() }
    }
}
