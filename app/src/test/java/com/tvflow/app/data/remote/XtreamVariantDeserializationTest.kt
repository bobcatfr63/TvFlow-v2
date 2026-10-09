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
}