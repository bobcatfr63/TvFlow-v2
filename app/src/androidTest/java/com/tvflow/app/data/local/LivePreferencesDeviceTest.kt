package com.tvflow.app.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LivePreferencesDeviceTest {
    @Test fun refreshPreservesFavoritesAndCustomOrderWithoutCrossingProfiles() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TVFlowDatabase::class.java).build()
        try {
            val dao = db.dao()
            val cats = listOf(LiveCategoryEntity("audit", "a", "Alpha", 0), LiveCategoryEntity("audit", "z", "Zulu", 1))
            val rows = listOf(
                LiveChannelEntity("audit", 1, "Alpha", "a", null, null, "live"),
                LiveChannelEntity("audit", 2, "Zulu", "a", null, null, "live")
            )
            dao.replaceCatalog("audit", cats, rows)
            dao.upsertChannels(listOf(rows.first().copy(profileId = "other")))
            dao.toggleFavorite("audit", 2)
            dao.setChannelOrder("audit", 2, 0)
            dao.setCategoryOrder("audit", "z", 0)
            dao.replaceCatalog("audit", cats, rows)
            assertEquals(listOf(2), dao.observeFavorites("audit").first().map { it.streamId })
            assertEquals(listOf(2, 1), dao.observeChannels("audit", "a").first().map { it.streamId })
            assertEquals(listOf("z", "a"), dao.observeCategories("audit").first().map { it.id })
            assertTrue(dao.observeFavorites("other").first().isEmpty())
        } finally { db.close() }
    }
}
