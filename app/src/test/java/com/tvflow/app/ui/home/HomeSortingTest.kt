package com.tvflow.app.ui.home

import com.tvflow.app.domain.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSortingTest {
    private val alpha = channel(1, "Alpha", favorite = false, epg = null)
    private val beta = channel(2, "Beta", favorite = true, epg = "beta.epg")
    private val gamma = channel(3, "Gamma", favorite = false, epg = "gamma.epg")
    private val section = channel(4, "##### SECTION #####", favorite = true, epg = "section")

    @Test
    fun nameSort_isAlphabeticalAndDropsSectionMarkers() {
        val result = sortHomeChannels(listOf(gamma, section, beta, alpha), HomeSort.NAME)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), result.map { it.name })
    }

    @Test
    fun favoritesSort_putsFavoritesFirstThenName() {
        val result = sortHomeChannels(listOf(gamma, alpha, beta), HomeSort.FAVORITES)
        assertEquals(listOf("Beta", "Alpha", "Gamma"), result.map { it.name })
    }

    @Test
    fun epgSort_putsChannelsWithEpgFirstThenName() {
        val result = sortHomeChannels(listOf(alpha, gamma, beta), HomeSort.EPG)
        assertEquals(listOf("Beta", "Gamma", "Alpha"), result.map { it.name })
    }

    private fun channel(
        id: Int,
        name: String,
        favorite: Boolean,
        epg: String?
    ) = LiveChannel(
        streamId = id,
        name = name,
        categoryId = "1",
        logoUrl = null,
        epgChannelId = epg,
        isFavorite = favorite
    )
}
