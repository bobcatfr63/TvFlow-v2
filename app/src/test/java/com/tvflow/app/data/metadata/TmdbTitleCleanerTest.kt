package com.tvflow.app.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbTitleCleanerTest {
    @Test fun keepsFilmNamesFromRealCatalogSearchable() {
        assertEquals("Masterplan", TmdbTitleCleaner.clean("Masterplan (2026)"))
        assertEquals("Animals", TmdbTitleCleaner.clean("Animals (2026)"))
        assertEquals("Crystal Lake", TmdbTitleCleaner.clean("Crystal Lake (2026) (US)"))
        assertEquals("El Círculo", TmdbTitleCleaner.clean("El Círculo (2026) (MX)"))
    }
    @Test fun removesProviderCountrySuffixButKeepsRealSubtitle() {
        assertEquals("Crystal Lake", TmdbTitleCleaner.clean("Crystal Lake (2026) (US)"))
        assertEquals("Le Seigneur des anneaux La Communaute de l anneau", TmdbTitleCleaner.clean("Le Seigneur des anneaux (La Communaute de l anneau) (FR)"))
    }

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
    @Test fun stripsEncodingAndEpisodeNoiseWithoutErasingTitle() {
        assertEquals("Le Bureau des legendes", TmdbTitleCleaner.clean("[FR] Le.Bureau.des.legendes.S02E03.1080p.x265"))
        assertEquals("Dune", TmdbTitleCleaner.clean("Dune.2021.2160p.HDR.DDP5.1.REPACK"))
    }
    @Test fun doesNotDropFilmTitleWhenNoPackagingMarkers() {
        assertEquals("Un film comme les autres", TmdbTitleCleaner.clean("Un film comme les autres"))
    }
}
