package com.tvflow.app.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamVariantDeserializationTest {
    private val gson = Gson()

    @Test fun vodInfoObject() {
        val response = gson.fromJson("""{"info":{"plot":"Synopsis","rating":"8.2"}}""", VodInfoResponse::class.java)
        assertEquals("Synopsis", response.info?.plot)
    }

    @Test fun vodInfoArray() {
        val response = gson.fromJson("""{"info":[{"plot":"Synopsis depuis tableau"}],"movie_data":{"stream_id":17}}""", VodInfoResponse::class.java)
        assertEquals("Synopsis depuis tableau", response.info?.plot)
        assertEquals(17, response.movieData?.streamId)
    }

    @Test fun vodInfoEmptyArray() {
        val response = gson.fromJson("""{"info":[],"movie_data":{}}""", VodInfoResponse::class.java)
        assertNull(response.info)
    }

    @Test fun vodInfoUnexpectedPrimitive() {
        val response = gson.fromJson("""{"info":"unknown","movie_data":{}}""", VodInfoResponse::class.java)
        assertNull(response.info)
    }

    @Test fun seriesNormalEpisodes() {
        val response = gson.fromJson("""{"info":{"name":"Test"},"episodes":{"1":[{"id":"42","episode_num":1,"title":"Pilot"}]}}""", SeriesInfoResponse::class.java)
        assertEquals("Test", response.info?.name)
        assertEquals("42", response.episodes["1"]?.first()?.id)
    }

    @Test fun seriesEmptyArrayVariants() {
        val response = gson.fromJson("""{"info":[],"episodes":[]}""", SeriesInfoResponse::class.java)
        assertNull(response.info)
        assertEquals(0, response.episodes.size)
    }

    @Test fun seriesMissingAndNullEpisodes() {
        val missing = gson.fromJson("""{"info":{}}""", SeriesInfoResponse::class.java)
        val explicitNull = gson.fromJson("""{"episodes":null}""", SeriesInfoResponse::class.java)
        assertEquals(0, missing.episodes.orEmpty().size)
        assertEquals(0, explicitNull.episodes.orEmpty().size)
    }

    @Test fun seriesSkipsMalformedEntries() {
        val response = gson.fromJson("""{"episodes":{"1":[null,5,{"id":"valid"},{"title":"no id"}],"2":false}}""", SeriesInfoResponse::class.java)
        assertEquals(listOf("valid"), response.episodes["1"]?.map { it.id })
        assertEquals(1, response.episodes.size)
    }
}