package app.votify.mobile.data

import app.votify.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Thin client over the Votify HTTP API (see README.md → "API").
 * Base URL comes from BuildConfig.API_BASE_URL (gradle property `votifyApiBase`).
 */
class VotifyApi(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
) {
    val baseUrl: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    // ---- Music ----

    suspend fun search(query: String, limit: Int = 24): List<Track> =
        get<TracksResponse>("api/search", "q" to query, "limit" to limit.toString()).tracks

    suspend fun artist(name: String, limit: Int = 50): List<Track> =
        get<ArtistResponse>("api/artist", "name" to name, "limit" to limit.toString()).tracks

    suspend fun recommendations(limit: Int = 16): List<Track> =
        get<TracksResponse>("api/recommendations", "limit" to limit.toString()).tracks

    suspend fun customWave(
        artistSeeds: List<String>,
        trackSeeds: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
        limit: Int = 20,
    ): List<Track> = get<TracksResponse>(
        "api/custom-wave",
        "seeds" to artistSeeds.joinToString("|"),
        "trackSeeds" to trackSeeds.joinToString("|"),
        "exclude" to exclude.joinToString(","),
        "limit" to limit.toString(),
    ).tracks

    suspend fun lyrics(track: String, artist: String): LyricsResponse =
        get("api/lyrics", "track" to track, "artist" to artist)

    /** Fire-and-forget: warms the server-side stream URL cache for upcoming tracks. */
    suspend fun preload(ids: List<String>) {
        if (ids.isEmpty()) return
        runCatching { getRaw("api/preload", "ids" to ids.joinToString(",")) }
    }

    /** Proxied audio stream URL — feed this to ExoPlayer. Supports HTTP Range. */
    fun streamUrl(trackId: String): String =
        baseUrl.newBuilder().addPathSegments("api/stream").addQueryParameter("id", trackId).build().toString()

    // ---- Settings / misc ----

    suspend fun health(): HealthResponse = get("api/health")

    suspend fun networkSettings(): NetworkSettings = get("api/network/settings")

    suspend fun setAudioQuality(quality: String): NetworkSettings {
        val body = """{"audioQuality":"$quality"}""".toRequestBody(JSON_MEDIA)
        val req = Request.Builder().url(url("api/network/settings")).post(body).build()
        val text = execute(req)
        return json.decodeFromString<NetworkSettingsUpdate>(text).config
    }

    // ---- internals ----

    @kotlinx.serialization.Serializable
    private data class NetworkSettingsUpdate(val updated: Boolean = false, val config: NetworkSettings = NetworkSettings())

    private fun url(path: String, vararg params: Pair<String, String>): HttpUrl {
        val b = baseUrl.newBuilder().addPathSegments(path)
        params.forEach { (k, v) -> if (v.isNotEmpty()) b.addQueryParameter(k, v) }
        return b.build()
    }

    private suspend inline fun <reified T> get(path: String, vararg params: Pair<String, String>): T =
        json.decodeFromString(getRaw(path, *params))

    private suspend fun getRaw(path: String, vararg params: Pair<String, String>): String =
        execute(Request.Builder().url(url(path, *params)).get().build())

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { json.decodeFromString<ApiError>(text).error }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"
                throw ApiException(resp.code, msg)
            }
            text
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Search hits YouTube server-side and can be slow on a cold cache.
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
