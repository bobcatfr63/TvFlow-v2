package com.tvflow.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchVisibilityDeviceTest {
    @Test fun mediaExclusionPrecedesLimitAndKeepsProfilesIsolated() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java).build()
        try {
            val dao = db.dao()
            fun vod(p: String, id: Int, cat: String, name: String) = VodItemEntity(p, id, name, cat, null, null, null, containerExtension = "mp4")
            fun series(p: String, id: Int, cat: String, name: String) = SeriesItemEntity(p, id, name, cat, null, null, null, null, null)
            dao.upsertVod((1..200).map { vod("audit", it, "hidden", "film a $it") } + vod("audit", 201, "visible", "film z") + vod("other", 999, "visible", "film aa"))
            dao.upsertSeries((1..200).map { series("audit", it, "hidden", "film a $it") } + series("audit", 201, "visible", "film z") + series("other", 999, "visible", "film aa"))
            assertEquals(listOf(201), dao.searchVod("audit", "film", 1, listOf("hidden")).first().map { it.streamId })
            assertEquals(listOf(201), dao.searchSeries("audit", "film", 1, listOf("hidden")).first().map { it.seriesId })
            assertEquals(201, dao.searchVod("audit", "film", 300).first().size)
            assertEquals(201, dao.searchSeries("audit", "film", 300).first().size)
            assertTrue(dao.getSearchVodItems("audit", listOf(1, 201, 999), listOf("hidden")).all { it.streamId == 201 })
            assertEquals(listOf(201), dao.getSearchSeriesItems("audit", listOf(1, 201, 999), listOf("hidden")).map { it.seriesId })
            assertEquals(2, dao.getSearchVodItems("audit", listOf(1, 201)).size)
            assertEquals(2, dao.getSearchSeriesItems("audit", listOf(1, 201)).size)
            assertEquals("hidden", dao.getVodSearchKeys("audit").first { it.id == 1 }.categoryId)
        } finally { db.close() }
    }

    @Test fun liveHydrationRechecksHiddenLockedAndIndividualFlagsAfterIndexBuild() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.upsertCategories(listOf(LiveCategoryEntity("audit", "same", "Live", 0), LiveCategoryEntity("other", "same", "Other", 0, isHidden = true)))
            dao.upsertChannels(listOf(LiveChannelEntity("audit", 1, "film channel", "same", null, null, "live")))
            val warmIndex = dao.getLiveSearchKeys("audit")
            assertEquals(1, warmIndex.size)
            assertEquals(1, dao.getSearchLiveItems("audit", listOf(1)).size)
            dao.setCategoryHidden("audit", "same", true)
            assertEquals(listOf("same"), dao.observeSearchExcludedLiveCategoryIds("audit").first())
            assertTrue(dao.getSearchLiveItems("audit", listOf(1)).isEmpty())
            assertTrue(dao.searchLive("audit", "film").first().isEmpty())
            dao.setCategoryHidden("audit", "same", false);dao.setCategoryLocked("audit", "same", true)
            assertTrue(dao.getSearchLiveItems("audit", listOf(1)).isEmpty())
            dao.setCategoryLocked("audit", "same", false);dao.setChannelHidden("audit", 1, true)
            assertEquals(listOf(1), dao.observeSearchHiddenLiveIds("audit").first())
            assertTrue(dao.getSearchLiveItems("audit", listOf(1)).isEmpty())
            // Hidden titles stay in the compact index so unhide works with a warm cache.
            assertEquals(warmIndex, dao.getLiveSearchKeys("audit"))
            dao.setChannelHidden("audit", 1, false)
            assertEquals(1, dao.getSearchLiveItems("audit", listOf(1)).size)
        } finally { db.close() }
    }
}
