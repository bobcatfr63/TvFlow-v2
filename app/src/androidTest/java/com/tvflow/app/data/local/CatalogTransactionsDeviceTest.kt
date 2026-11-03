package com.tvflow.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogTransactionsDeviceTest {
    @Test fun filmRefreshDoesNotClearSeriesAndPreservesFilmMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TVFlowDatabase::class.java).build()
        try {
            val dao = database.dao()
            dao.upsertVod(listOf(VodItemEntity("test", 1, "Film ancien", "a", null, null, null,
                synopsis = "Synopsis local", containerExtension = "mp4", tmdbGenreIds = "18",
                releaseDateIso = "2020-02-03", releaseDateSource = "TMDB")))
            dao.upsertSeries(listOf(SeriesItemEntity("test", 2, "Série conservée", "s", null, null, "Intrigue")))
            dao.replaceVodCatalog(
                "test",
                listOf(MediaCategoryEntity("test", "a", "Films", "VOD", 0)),
                listOf(VodItemEntity("test", 1, "Film actualisé", "a", null, null, null,
                    containerExtension = "mp4"))
            )
            val film = dao.getAllVod("test").single()
            assertEquals("Film actualisé", film.name)
            assertEquals("Synopsis local", film.synopsis)
            assertEquals("18", film.tmdbGenreIds)
            assertEquals("2020-02-03", film.releaseDateIso)
            assertEquals("TMDB", film.releaseDateSource)
            assertEquals("Série conservée", dao.getAllSeries("test").single().name)
        } finally {
            database.close()
        }
    }

    @Test fun seriesRefreshDoesNotClearFilmsAndPreservesSeriesMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TVFlowDatabase::class.java).build()
        try {
            val dao = database.dao()
            dao.upsertVod(listOf(VodItemEntity("test", 1, "Film conservé", "a", null, null, null,
                containerExtension = "mkv")))
            dao.upsertSeries(listOf(SeriesItemEntity("test", 2, "Ancienne série", "s", null, null,
                "Intrigue locale", tmdbGenreIds = "35", releaseDateIso = "2021-05-06", releaseDateSource = "TMDB")))
            dao.replaceSeriesCatalog(
                "test",
                listOf(MediaCategoryEntity("test", "s", "Séries", "SERIES", 0)),
                listOf(SeriesItemEntity("test", 2, "Série actualisée", "s", null, null, null))
            )
            val series = dao.getAllSeries("test").single()
            assertEquals("Série actualisée", series.name)
            assertEquals("Intrigue locale", series.plot)
            assertEquals("35", series.tmdbGenreIds)
            assertEquals("2021-05-06", series.releaseDateIso)
            assertEquals("TMDB", series.releaseDateSource)
            assertEquals("Film conservé", dao.getAllVod("test").single().name)
        } finally {
            database.close()
        }
    }
}
