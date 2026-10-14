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

    @Test
    fun nameSort_ignoresProviderPrefixAndAccents() {
        val channels = listOf(
            channel(10, "FR - Équipe TV", false, null),
            channel(11, "FR - Arte HD", false, null),
            channel(12, "FR - BFM TV", false, null)
        )
        assertEquals(listOf(11, 12, 10), sortHomeChannels(channels, HomeSort.NAME).map { it.streamId })
    }

    @Test
    fun tntSort_placesKnownNumbersFirstAndKeepsOthersStable() {
        val channels = listOf(
            channel(20, "FR - Canal Cinema", false, null),
            channel(21, "FR - M6", false, null),
            channel(22, "FR - Arte HD", false, null),
            channel(23, "FR - TF1", false, null),
            channel(24, "FR - Radio Albi", false, null)
        )
        assertEquals(listOf(23, 21, 22, 20, 24), com.tvflow.app.data.repository.sortTntChannels(channels).map { it.streamId })
    }

    @Test
    fun tntCategoryRejectsArbitraryLogicalNumbersAndDeduplicates() {
        val channels = listOf(
            channel(1, "FR - TF1 SD", false, null).copy(logicalNumber = 1),
            channel(2, "FR - TF1 HD", false, "tf1.epg"),
            channel(3, "FR - M6", false, null),
            channel(4, "FR - Canal Cinema", false, null).copy(logicalNumber = 1),
            channel(5, "FR - Arte HD", false, null).copy(isHidden = true),
            channel(6, "FR - TF1 4K", false, null)
        )
        val result = com.tvflow.app.data.repository.selectTntChannels(channels)
        assertEquals(listOf(2, 3), result.map { it.streamId })
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
