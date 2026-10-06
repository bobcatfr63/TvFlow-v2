package com.tvflow.app.data.repository

import com.tvflow.app.data.local.LiveCategoryEntity
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.MediaSection
import com.tvflow.app.domain.StreamHealth
import com.tvflow.app.domain.XtreamCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamLogicTest {
    private val credentials = XtreamCredentials(
        serverUrl = "example.test/player_api.php",
        username = "user",
        password = "pass",
        outputFormat = "m3u8"
    )

    @Test
    fun normalizeServer_removesXtreamEndpointAndAddsScheme() {
        assertEquals(
            "http://example.test",
            normalizeXtreamServer(" example.test/get.php?username=x&password=y ")
        )
        assertEquals(
            "https://example.test",
            normalizeXtreamServer("https://example.test/player_api.php")
        )
    }

    @Test
    fun streamUrls_useExpectedXtreamPathsAndExtensions() {
        assertEquals(
            "http://example.test/live/user/pass/42.m3u8",
            buildLiveStreamUrl(credentials, 42)
        )
        assertEquals(
            "http://example.test/movie/user/pass/7.mkv",
            buildVodStreamUrl(credentials, 7, "mkv")
        )
        assertEquals(
            "http://example.test/series/user/pass/ep-9.mp4",
            buildEpisodeStreamUrl(credentials, "ep-9", null)
        )
        assertEquals(
            "http://example.test/player_api.php",
            buildPlayerApiUrl(credentials.serverUrl)
        )
    }

    @Test
    fun catchupUrl_usesTimeshiftPathAndProgrammeDuration() {
        val url = buildCatchupStreamUrl(
            credentials = credentials,
            streamId = 42,
            startTimestamp = 1_760_000_000L,
            stopTimestamp = 1_760_003_600L
        )
        assertTrue(url.startsWith("http://example.test/timeshift/user/pass/60/"))
        assertTrue(url.endsWith("/42.ts"))
    }

    @Test
    fun liveUrl_invalidOutputFallsBackToTs() {
        val invalid = credentials.copy(outputFormat = "avi")
        assertEquals(
            "http://example.test/live/user/pass/5.ts",
            buildLiveStreamUrl(invalid, 5)
        )
    }

    @Test
    fun providerCategories_areGroupedIntoSubFilters() {
        val grouped = groupProviderCategories(
            listOf(
                category("1", "|EU| FRANCE HEVC", 0),
                category("2", "|EU| FRANCE GENERAL", 1),
                category("3", "|EU| ITALY", 2)
            )
        )

        assertEquals(2, grouped.size)
        assertEquals("FRANCE", grouped[0].name)
        assertEquals(listOf("HEVC", "GENERAL"), grouped[0].subFilters.map { it.name })
        assertEquals(listOf("1", "2"), decodeCategoryGroupId(grouped[0].id))
        assertEquals("ITALY", grouped[1].name)
    }

    @Test
    fun providerCategories_ignoreSectionMarkers() {
        val grouped = groupProviderCategories(
            listOf(
                category("section", "##### SPORTS #####", 0),
                category("1", "|EU| FRANCE", 1)
            )
        )
        assertEquals(1, grouped.size)
        assertFalse(grouped.any { it.id == "section" })
    }

    @Test
    fun groupedCategory_preservesLockedState() {
        val grouped = groupProviderCategories(
            listOf(
                category("1", "|EU| KIDS HD", 0, locked = false),
                category("2", "|EU| KIDS SD", 1, locked = true)
            )
        )
        assertTrue(grouped.single().isLocked)
    }

    @Test
    fun primaryCategories_splitFranceKidsAndOtherProviderContent() {
        val categories = buildPrimaryLiveCategories(
            listOf(
                category("fr-general", "|EU| FRANCE GÉNÉRAL", 0),
                category("fr-kids", "|EU| FRANCE ENFANTS", 1),
                category("uk-kids", "|UK| KIDS", 2),
                category("be", "|EU| BELGIQUE HD", 3),
                category("es", "|ES| GENERAL", 4)
            )
        )

        assertEquals(listOf("FRANCE", "ENFANTS", "AUTRES"), categories.map { it.name })
        assertEquals(listOf("fr-general"), decodeCategoryGroupId(categories[0].id))
        assertEquals(listOf("fr-kids", "uk-kids"), decodeCategoryGroupId(categories[1].id))
        assertEquals(listOf("be", "es"), decodeCategoryGroupId(categories[2].id))

        assertTrue(categories[0].subFilters.all { it.id.startsWith("__france__:") })
        assertTrue(categories[1].subFilters.all { it.id.startsWith("__kids__:") })
        assertTrue(categories[2].subFilters.all { it.id.startsWith("__other__:") })
    }


    @Test
    fun mediaCategories_classifyFranceKidsAndOther() {
        assertEquals(MediaSection.FRANCE, classifyMediaSection("|EU| FRANCE CINÉMA"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("Films FR"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("VOSTFR nouveautés"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("VF Films"))
        assertEquals(MediaSection.KIDS, classifyMediaSection("|EU| FRANCE ENFANTS"))
        assertEquals(MediaSection.KIDS, classifyMediaSection("KIDS MOVIES"))
        assertEquals(MediaSection.OTHER, classifyMediaSection("|ES| GENERAL"))
        assertEquals(MediaSection.OTHER, classifyMediaSection("AFRICA MOVIES"))
    }

    @Test
    fun frenchPresentation_filtersHighQualityAndDeduplicatesVariants() {
        val result = prepareFrenchChannels(
            listOf(
                live(1, "FR - TF1 FHD"),
                live(2, "FR - TF1 HD", epg = "tf1.fr"),
                live(3, "FR - TF1 HEVC"),
                live(4, "FR - M6 4K"),
                live(5, "FR - M6 HD"),
                live(6, "DE - DAS ERSTE HD")
            )
        )

        assertEquals(listOf("FR - TF1 HD", "FR - M6 HD"), result.map { it.name })
        assertEquals(listOf(1, 6), result.map { it.logicalNumber })
    }

    @Test
    fun frenchPresentation_keeps1080pButStillRejects4kAndFhd() {
        val result = prepareFrenchChannels(
            listOf(
                live(1, "FR - TF1 1080P", epg = "tf1.fr"),
                live(2, "FR - TF1 FHD"),
                live(3, "FR - M6 2160P"),
                live(4, "FR - M6 1080")
            )
        )

        assertEquals(listOf("FR - TF1 1080P", "FR - M6 1080"), result.map { it.name })
        assertEquals(listOf(1, 6), result.map { it.logicalNumber })
    }

    @Test
    fun frenchPresentation_usesCurrentTntOrderBeforeOtherFrenchChannels() {
        val result = prepareFrenchChannels(
            listOf(
                live(1, "FR - ZING HD"),
                live(2, "FR - CNEWS HD"),
                live(3, "FR - FRANCE 2 HD"),
                live(4, "FR - TF1 HD"),
                live(5, "FR - NOVO19 HD")
            )
        )

        assertEquals(
            listOf("FR - TF1 HD", "FR - FRANCE 2 HD", "FR - CNEWS HD", "FR - NOVO19 HD", "FR - ZING HD"),
            result.map { it.name }
        )
        assertEquals(listOf(1, 2, 14, 19, null), result.map { it.logicalNumber })
    }

    @Test
    fun frenchMediaCategory_recognizesProviderSuffixTag() {
        assertEquals(MediaSection.FRANCE, classifyMediaSection("VOD - RECEMMENT AJOUTER [FR]"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("SRS - NETFLIX [FR]"))
        assertEquals(MediaSection.OTHER, classifyMediaSection("VOD - NEW ADDED [EN]"))
    }

    @Test
    fun mediaCategory_classifier_supportsDifferentProviderConventions() {
        assertEquals(MediaSection.FRANCE, classifyMediaSection("|FR| GENERAL"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("FRA CINEMA"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("MULTI FR MOVIES"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("AUDIO FR SERIES"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("FRENCH PREMIUM"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("VOSTFR SERIES"))
        assertEquals(MediaSection.FRANCE, classifyMediaSection("VF FILMS"))
    }

    @Test
    fun mediaCategory_classifier_isConservativeForForeignOrAmbiguousNames() {
        assertEquals(MediaSection.OTHER, classifyMediaSection("AFRICA MOVIES"))
        assertEquals(MediaSection.OTHER, classifyMediaSection("ENGLISH MOVIES"))
        assertEquals(MediaSection.OTHER, classifyMediaSection("DE CINEMA"))
        assertEquals(MediaSection.OTHER, classifyMediaSection("EUROPE GENERAL"))
        assertEquals(MediaSection.OTHER, classifyMediaSection("PREMIUM MOVIES"))
    }

    @Test
    fun primaryLiveCategories_recognizeFrenchProviderCode() {
        val categories = buildPrimaryLiveCategories(
            listOf(
                category("fr", "|FR| GENERAL", 0),
                category("uk", "|UK| GENERAL", 1)
            )
        )

        assertEquals(listOf("FRANCE", "AUTRES"), categories.map { it.name })
        assertEquals(listOf("fr"), decodeCategoryGroupId(categories[0].id))
        assertEquals(listOf("uk"), decodeCategoryGroupId(categories[1].id))
    }

    @Test
    fun epgTimestamp_acceptsSecondsAndNormalizesMilliseconds() {
        assertEquals(1_760_000_000L, parseEpgTimestamp("1760000000"))
        assertEquals(1_760_000_000L, parseEpgTimestamp("1760000000000"))
        assertEquals(null, parseEpgTimestamp("0"))
        assertEquals(null, parseEpgTimestamp("invalid"))
    }

    @Test
    fun streamHealth_neverMarksRateLimitsOrForbiddenAsOffline() {
        assertEquals(StreamHealth.UNSTABLE, classifyStreamHealth(403, "Forbidden"))
        assertEquals(StreamHealth.UNSTABLE, classifyStreamHealth(429, "Too many requests"))
    }

    @Test
    fun streamHealth_marksOnlyDefinitiveMissingResponsesOffline() {
        assertEquals(StreamHealth.OFFLINE, classifyStreamHealth(404, "Not found"))
        assertEquals(StreamHealth.OFFLINE, classifyStreamHealth(410, "Gone"))
        assertEquals(StreamHealth.UNSTABLE, classifyStreamHealth(null, "Timeout"))
        assertEquals(StreamHealth.UNPLAYABLE, classifyStreamHealth(null, "ERROR_CODE_DECODING_FAILED"))
    }

    private fun live(
        id: Int,
        name: String,
        epg: String? = null
    ) = LiveChannel(
        streamId = id,
        name = name,
        categoryId = "fr",
        logoUrl = null,
        epgChannelId = epg
    )

    private fun category(
        id: String,
        name: String,
        order: Int,
        locked: Boolean = false
    ) = LiveCategoryEntity(
        profileId = "profile",
        id = id,
        name = name,
        sortOrder = order,
        isLocked = locked
    )
}
