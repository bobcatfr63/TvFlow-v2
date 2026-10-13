package com.tvflow.app.ui.search

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonParser

data class FootballAnswer(val message: String, val eventName: String? = null, val sourceUrl: String)

/** Public sports data is independent of the IPTV guide and never proves a channel is available. */
class FootballScheduleClient @Inject constructor(private val client: OkHttpClient) {
    suspend fun clermontTonight(now: OffsetDateTime = OffsetDateTime.now()): FootballAnswer = withContext(Dispatchers.IO) {
        val url = "https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id=134713"
        val body = client.newCall(Request.Builder().url(url).get().build()).execute().use { result ->
            if (!result.isSuccessful) return@withContext FootballAnswer(
                "Calendrier sportif indisponible actuellement. Match non confirmé.", sourceUrl = url
            )
            result.body?.string().orEmpty()
        }
        FootballScheduleParser.parse(body, now, ZoneId.systemDefault(), url)
    }
}

internal object FootballScheduleParser {
    fun parse(body: String, now: OffsetDateTime, zone: ZoneId, url: String): FootballAnswer {
        val events = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonArray("events") }.getOrNull()
        if (events == null || events.size() == 0) return FootballAnswer(
            "Calendrier incomplet : impossible de confirmer si Clermont Foot joue ce soir.", sourceUrl = url
        )
        val next = (0 until events.size()).mapNotNull { index ->
            val event = runCatching { events[index].asJsonObject }.getOrNull() ?: return@mapNotNull null
            fun value(key: String) = runCatching { event.get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty() }.getOrDefault("")
            val day = runCatching { LocalDate.parse(value("dateEvent")) }.getOrNull() ?: return@mapNotNull null
            val time = runCatching { LocalTime.parse(value("strTime").take(8)) }.getOrNull()
                ?: return@mapNotNull null
            val name = value("strEvent").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Triple(name, day.atTime(time).atZone(ZoneOffset.UTC).withZoneSameInstant(zone), day)
        }.minByOrNull { it.second.toInstant() } ?: return FootballAnswer(
            "Le calendrier n'indique pas d'horaire fiable : match non confirmé.", sourceUrl = url
        )
        val today = now.atZoneSameInstant(zone).toLocalDate()
        val localDay = next.second.toLocalDate()
        val localTime = next.second.toLocalTime()
        val label = next.second.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm"))
        return when {
            localDay == today && localTime.hour >= 17 ->
                FootballAnswer(
                    "Oui, Clermont Foot joue ce soir : ${next.first} à ${localTime.format(DateTimeFormatter.ofPattern("HH:mm"))}. La chaîne reste à vérifier dans l'EPG.",
                    next.first, url
                )
            localDay == today ->
                FootballAnswer("Clermont Foot joue aujourd'hui (${next.first}, ${localTime.format(DateTimeFormatter.ofPattern("HH:mm"))}), mais ce match n'est pas programmé ce soir.", sourceUrl = url)
            localDay.isAfter(today) ->
                FootballAnswer("Aucun match de Clermont Foot annoncé ce soir dans ce calendrier. Prochain match : ${next.first}, le $label.", sourceUrl = url)
            else ->
                FootballAnswer("Données sportives potentiellement périmées (${next.first}, $label). Impossible de confirmer ce soir.", sourceUrl = url)
        }
    }
}
