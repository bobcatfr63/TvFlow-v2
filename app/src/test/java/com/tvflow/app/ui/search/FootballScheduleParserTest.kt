package com.tvflow.app.ui.search

import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballScheduleParserTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val today = OffsetDateTime.parse("2026-10-13T10:00:00+02:00")
    private val source = "https://www.thesportsdb.com/"
    private fun schedule(day: String, time: String): String =
        """{"events":[{"strEvent":"Laval vs Clermont Foot","dateEvent":"$day","strTime":"$time"}]}"""

    @Test fun upcomingGameOnAnotherDayIsNotTonight() {
        val result = FootballScheduleParser.parse(schedule("2026-10-16", "18:00:00"), today, zone, source)
        assertTrue(result.message.contains("Aucun match"))
        assertTrue(result.message.contains("16/10/2026"))
        assertTrue(result.eventName == null)
    }

    @Test fun matchTonightDoesNotPromiseAnIptvChannel() {
        val result = FootballScheduleParser.parse(schedule("2026-10-13", "18:00:00"), today, zone, source)
        assertTrue(result.message.contains("ce soir"))
        assertTrue(result.message.contains("EPG"))
        assertTrue(result.eventName != null)
    }

    @Test fun missingProgramDoesNotClaimNoMatch() {
        val result = FootballScheduleParser.parse("""{"events":null}""", today, zone, source)
        assertTrue(result.message.contains("impossible"))
        assertFalse(result.message.contains("Aucun match"))
    }
}
