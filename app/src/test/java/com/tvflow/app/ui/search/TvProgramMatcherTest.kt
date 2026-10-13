package com.tvflow.app.ui.search

import com.tvflow.app.domain.EpgProgram
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TvProgramMatcherTest {
    @Test fun detectsEveningTelevisionIntent() {
        assertTrue(TvProgramMatcher.isTvQuestion("Est-ce qu'il y a un film d'action ce soir ?"))
        assertTrue(TvProgramMatcher.isTvQuestion("Du foot en direct maintenant ?"))
        assertFalse(TvProgramMatcher.isTvQuestion("Je veux un film avec DiCaprio"))
    }
    @Test fun matchesOnlyListedProgramData() {
        val program = EpgProgram(13, 1000, 2000, "Film d'action", "Aventure et combats")
        val unrelated = EpgProgram(14, 1000, 2000, "Journal télévisé", "Actualités")
        assertTrue(TvProgramMatcher.score("Un film d'action ce soir", program) > 0)
        assertEquals(0, TvProgramMatcher.score("Un film d'action ce soir", unrelated))
    }
    @Test fun teamQuestionDoesNotSuggestUnrelatedMatch() {
        val other = EpgProgram(15, 1000, 2000, "PSG - Monaco", "Football en direct")
        val clermont = EpgProgram(16, 1000, 2000, "Clermont Foot - Laval", "Match de Ligue 2")
        assertTrue(TvProgramMatcher.isTvQuestion("Est-ce que Clermont Foot joue ce soir ?"))
        assertEquals(0, TvProgramMatcher.score("Clermont Foot joue ce soir", other))
        assertTrue(TvProgramMatcher.score("Clermont Foot joue ce soir", clermont) > 0)
    }
    @Test fun eveningWindowIncludesLaterPrograms() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 0)
        }
        val (start, end) = TvProgramMatcher.window("film ce soir", calendar.timeInMillis)
        assertTrue(end > start)
        assertTrue(end - start <= 9 * 3600)
    }
}
