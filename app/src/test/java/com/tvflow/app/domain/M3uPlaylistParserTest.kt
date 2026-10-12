package com.tvflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class M3uPlaylistParserTest {
    @Test fun parsesFrenchPlaylist() {
        val source = """#EXTM3U
#EXTINF:-1 tvg-id="france2.fr" tvg-logo="https://img.example/fr2.png" group-title="France",France 2
https://streams.example/france2.m3u8
#EXTINF:-1 group-title="Infos",BFM TV
http://streams.example/bfmtv.ts
"""
        val stations = M3uPlaylistParser.parse(source)
        assertEquals(2, stations.size)
        assertEquals("France 2", stations[0].name)
        assertEquals("France", stations[0].group)
        assertEquals("france2.fr", stations[0].tvgId)
        assertEquals("https://img.example/fr2.png", stations[0].logo)
        assertEquals("Infos", stations[1].group)
    }

    @Test fun rejectsInvalidPlaylist() {
        assertThrows(IllegalArgumentException::class.java) {
            M3uPlaylistParser.parse("#EXTINF:-1,Invalid\nhttps://streams.example/test")
        }
    }

    @Test fun skipsNonHttpStreams() {
        val source = "#EXTM3U\n#EXTINF:-1,Unsafe\nfile:///private.mp4\n#EXTINF:-1,Good\nhttps://example.com/live.m3u8"
        assertEquals("Good", M3uPlaylistParser.parse(source).single().name)
    }
}
