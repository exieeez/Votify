package app.votify.mobile.data

import app.votify.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 *
 * The base URL can be changed at runtime (Settings → «Сервер») so a real phone can point at
 * the PC running `npm start` — the build-time default `10.0.2.2` only works inside the
 * emulator. When the user signs in, [authToken] is attached to every request as a Bearer
 * header.
 */
class VotifyApi(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Imports resolve playlists track-by-track server-side and can legitimately take minutes. */
    private val importClient: OkHttpClient = client.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var base: HttpUrl = normalizeBaseUrl(baseUrl)

    /** Bearer token of the signed-in user; null while logged out. */
    @Volatile
    var authToken: String? = null

    val baseUrl: HttpUrl get() = base

    /** Apply a new backend address. Throws [IllegalArgumentException] on malformed input. */
    fun setBaseUrl(url: String): HttpUrl {
        val parsed = normalizeBaseUrl(url)
        base = parsed
        return parsed
    }

    private fun normalizeBaseUrl(url: String): HttpUrl {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Address is empty")
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        return withScheme.trimEnd('/').toHttpUrl()
    }

    // ---- Music ----

    suspend fun search(query: String, limit: Int = 24): List<Track> =
        get<TracksResponse>("api/search", "q" to query, "limit" to limit.toString()).tracks

    suspend fun artist(name: String, limit: Int = 50): List<Track> =
        get<ArtistResponse>("api/artist", "name" to name, "limit" to limit.toString()).tracks

    suspend fun recommendations(limit: Int = 16): List<Track> =
        get<TracksResponse>("api/recommendations", "limit" to limit.toString()).tracks

    /** Живой чарт сервера (Настройки → Сервер): что популярно прямо сейчас. */
    suspend fun charts(limit: Int = 30, region: String = "ru"): List<Track> =
        get<TracksResponse>("api/charts", "limit" to limit.toString(), "region" to region).tracks

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

    // ---- Account ----

    suspend fun login(email: String, password: String): AuthResponse =
        post("api/auth/login", buildJsonObject {
            put("email", email)
            put("password", password)
        })

    suspend fun register(email: String, username: String, password: String): AuthResponse =
        post("api/auth/register", buildJsonObject {
            put("email", email)
            put("username", username)
            put("password", password)
        })

    suspend fun requestPasswordReset(email: String): ForgotResponse =
        post("api/auth/forgot-password", buildJsonObject {
            put("email", email)
        })

    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResponse =
        post("api/auth/reset-password", buildJsonObject {
            put("email", email)
            put("code", code)
            put("newPassword", newPassword)
        })

    // ---- Playlist import ----

    /** YouTube / Spotify playlist by URL → tracks resolved to YouTube ids. */
    suspend fun importPlaylist(url: String): ImportedTracks =
        get("api/playlist", "url" to url, httpClient = importClient)

    /** SoundCloud playlist/set by URL. */
    suspend fun importSoundcloud(url: String): ImportedTracks =
        get("api/soundcloud/import", "url" to url, httpClient = importClient)

    // ---- Settings / misc ----

    suspend fun health(): HealthResponse = get("api/health")

    /** The server's cached Firebase Web Config (503 when the server has none). */
    suspend fun firebaseConfig(): String = getRaw("api/firebase/config")

    suspend fun networkSettings(): NetworkSettings = get("api/network/settings")

    // ---- Settings sync (Основные → Синхронизация) ----

    suspend fun pushSync(settingsJson: String): SyncPushResponse =
        post("api/sync/push", buildJsonObject {
            put("settings", settingsJson)
        })

    suspend fun pullSync(): SyncData = get("api/sync/get")

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

    private suspend inline fun <reified T> get(path: String, vararg params: Pair<String, String>, httpClient: OkHttpClient = client): T =
        json.decodeFromString(getRaw(path, *params, httpClient = httpClient))

    private suspend fun getRaw(path: String, vararg params: Pair<String, String>, httpClient: OkHttpClient = client): String =
        execute(Request.Builder().url(url(path, *params)).get().build(), httpClient)

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T =
        json.decodeFromString(execute(Request.Builder().url(url(path)).post(body.toString().toRequestBody(JSON_MEDIA)).build()))

    private suspend fun execute(request: Request, httpClient: OkHttpClient = client): String = withContext(Dispatchers.IO) {
        val authorized = authToken?.takeIf { it.isNotBlank() }
            ?.let { token -> request.newBuilder().header("Authorization", "Bearer $token").build() }
            ?: request
        httpClient.newCall(authorized).execute().use { resp ->
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
