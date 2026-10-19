package com.tvflow.app.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbTitleCleanerTest {
    @Test fun removesIpTvQualityAndYearWithoutLosingFilmName() {
        assertEquals("Interstellar", TmdbTitleCleaner.clean("|FR| Interstellar (2014) [4K]"))
        assertEquals("Dune Part Two", TmdbTitleCleaner.clean("FR: Dune.Part.Two.2024.FHD"))
    }
    @Test fun preservesSubtitlesInParentheses() {
        assertEquals("Le Seigneur des anneaux La Communaute de l anneau",
            TmdbTitleCleaner.clean("Le Seigneur des anneaux (La Communaute de l anneau)"))
    }
    @Test fun stripsLeadingIptvLanguageTags() {
        assertEquals("Le Comte de Monte Cristo", TmdbTitleCleaner.clean("[FR] [4K] Le Comte de Monte Cristo (2024)"))
        assertEquals("Dark", TmdbTitleCleaner.clean("(VOSTFR) |FR| Dark [HD]"))
    }
    @Test fun doesNotDropFilmTitleWhenNoPackagingMarkers() {
        assertEquals("Un film comme les autres", TmdbTitleCleaner.clean("Un film comme les autres"))
    }
}
