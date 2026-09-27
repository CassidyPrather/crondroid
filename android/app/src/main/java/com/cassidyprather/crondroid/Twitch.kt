package com.cassidyprather.crondroid

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Finds the HLS stream behind a Twitch channel so Crondroid can play its audio without the Twitch app.
 *
 * Twitch has no public API for this. It uses the same unofficial steps as its web player (and tools like
 * Streamlink): ask the GraphQL API for a playback token, then ask the "usher" service for the playlist.
 * If Twitch changes either, this stops working until it is updated.
 */
object Twitch {
    /** The public client ID of Twitch's own web player. */
    private const val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
    private const val TIMEOUT_MS = 15_000

    private val HOSTS = setOf("twitch.tv", "www.twitch.tv", "m.twitch.tv")
    private val LOGIN = Regex("^[a-z0-9_]{1,25}$")
    private val NOT_CHANNELS = setOf(
        "directory", "downloads", "drops", "friends", "inventory", "jobs", "login", "messages", "p",
        "popout", "search", "settings", "signup", "subscriptions", "turbo", "videos", "wallet",
    )

    /** The channel name in a link like `https://www.twitch.tv/somebody`, or null if it isn't one. */
    fun channelFromUrl(url: String): String? {
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.host?.lowercase() !in HOSTS) return null
        val name = uri.path.orEmpty().trim('/').substringBefore('/').lowercase()
        return name.takeIf { LOGIN.matches(it) && it !in NOT_CHANNELS }
    }

    sealed interface Lookup {
        /** [url] is a media playlist; [audioOnly] is false when only video renditions were offered. */
        data class Live(val url: String, val audioOnly: Boolean) : Lookup
        data object Offline : Lookup
        data class Failed(val reason: String) : Lookup
    }

    /** Looks up [channel]'s live stream. Does network I/O: never call on the main thread. */
    fun findStream(channel: String): Lookup = try {
        lookUp(channel)
    } catch (e: IOException) {
        Lookup.Failed(e.message ?: e.javaClass.simpleName)
    } catch (e: org.json.JSONException) {
        Lookup.Failed("unexpected reply from Twitch (${e.message})")
    }

    private fun lookUp(channel: String): Lookup {
        val token = accessToken(channel) ?: return Lookup.Failed("no Twitch channel called \"$channel\"")
        val query = mapOf(
            "sig" to token.second,
            "token" to token.first,
            "allow_source" to "true",
            "allow_audio_only" to "true",
            "fast_bread" to "true",
            "player" to "twitchweb",
            "type" to "any",
            "p" to Random.nextInt(1_000_000, 10_000_000).toString(),
        ).entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = open("https://usher.ttvnw.net/api/channel/hls/$channel.m3u8?$query")
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {}
                HttpURLConnection.HTTP_NOT_FOUND -> return Lookup.Offline
                else -> return Lookup.Failed("Twitch answered HTTP $code")
            }
            val variants = parseMasterPlaylist(connection.inputStream.bufferedReader().use { it.readText() })
            variants.firstOrNull { it.audioOnly }?.let { return Lookup.Live(it.url, audioOnly = true) }
            val smallest = variants.minByOrNull { it.bandwidth } ?: return Lookup.Offline
            return Lookup.Live(smallest.url, audioOnly = false)
        } finally {
            connection.disconnect()
        }
    }

    /** Returns (token, signature), or null if the channel doesn't exist. */
    private fun accessToken(channel: String): Pair<String, String>? {
        val body = JSONObject()
            .put("operationName", "PlaybackAccessToken_Template")
            .put(
                "query",
                "query PlaybackAccessToken_Template(\$login: String!, \$playerType: String!) {" +
                    " streamPlaybackAccessToken(channelName: \$login," +
                    " params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: \$playerType})" +
                    " { value signature } }",
            )
            .put("variables", JSONObject().put("login", channel).put("playerType", "site"))
        val connection = open("https://gql.twitch.tv/gql")
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Client-ID", CLIENT_ID)
            connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Twitch answered HTTP $code")
            val reply = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val token = reply.optJSONObject("data")?.optJSONObject("streamPlaybackAccessToken") ?: return null
            return token.getString("value") to token.getString("signature")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }

    data class Variant(val url: String, val bandwidth: Long, val audioOnly: Boolean)

    /** Reads the renditions out of an HLS master playlist. */
    fun parseMasterPlaylist(text: String): List<Variant> {
        val variants = mutableListOf<Variant>()
        var attributes: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> attributes = line.substringAfter(':')
                line.isEmpty() || line.startsWith("#") -> {}
                attributes != null -> {
                    val codecs = Regex("CODECS=\"([^\"]*)\"").find(attributes)?.groupValues?.get(1).orEmpty()
                    variants += Variant(
                        url = line,
                        bandwidth = Regex("(?:^|,)BANDWIDTH=(\\d+)").find(attributes)?.groupValues?.get(1)?.toLong() ?: 0,
                        audioOnly = "VIDEO=\"audio_only\"" in attributes || codecs.startsWith("mp4a") && ',' !in codecs,
                    )
                    attributes = null
                }
            }
        }
        return variants
    }
}
