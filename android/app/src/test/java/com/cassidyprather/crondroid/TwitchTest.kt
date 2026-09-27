package com.cassidyprather.crondroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwitchTest {
    @Test
    fun channelFromUrl() {
        assertEquals("somebody", Twitch.channelFromUrl("https://www.twitch.tv/somebody"))
        assertEquals("some_body42", Twitch.channelFromUrl("https://twitch.tv/Some_Body42/"))
        assertEquals("somebody", Twitch.channelFromUrl("https://m.twitch.tv/somebody/videos"))
        assertEquals("somebody", Twitch.channelFromUrl("https://www.twitch.tv/somebody?ref=home"))
        assertNull(Twitch.channelFromUrl("https://www.twitch.tv/"))
        assertNull(Twitch.channelFromUrl("https://www.twitch.tv/directory/gaming"))
        assertNull(Twitch.channelFromUrl("https://youtube.com/somebody"))
        assertNull(Twitch.channelFromUrl("https://nottwitch.tv/somebody"))
        assertNull(Twitch.channelFromUrl("not a url"))
    }

    @Test
    fun parseMasterPlaylistFindsAudioOnly() {
        val playlist = """
            #EXTM3U
            #EXT-X-TWITCH-INFO:NODE="video-edge",CLUSTER="sea02"
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="chunked",NAME="1080p60 (source)",AUTOSELECT=YES,DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",VIDEO="chunked",FRAME-RATE=60.000
            https://video-weaver.example/v1/playlist/source.m3u8
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="160p30",NAME="160p",AUTOSELECT=YES,DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=230000,RESOLUTION=284x160,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="160p30",FRAME-RATE=30.000
            https://video-weaver.example/v1/playlist/160p.m3u8
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="audio_only",NAME="audio_only",AUTOSELECT=NO,DEFAULT=NO
            #EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS="mp4a.40.2",VIDEO="audio_only"
            https://video-weaver.example/v1/playlist/audio.m3u8
        """.trimIndent()
        assertEquals(
            listOf(
                Twitch.Variant("https://video-weaver.example/v1/playlist/source.m3u8", 6_000_000, audioOnly = false),
                Twitch.Variant("https://video-weaver.example/v1/playlist/160p.m3u8", 230_000, audioOnly = false),
                Twitch.Variant("https://video-weaver.example/v1/playlist/audio.m3u8", 160_000, audioOnly = true),
            ),
            Twitch.parseMasterPlaylist(playlist),
        )
    }
}
