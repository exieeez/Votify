package app.votify.mobile.data

import app.votify.mobile.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** One charts-screen block: stable id, title, playable tracks and/or top artists. */
data class ChartSection(
    val id: String,
    val titleRes: Int,
    val tracks: List<Track> = emptyList(),
    val artists: List<ChartArtist> = emptyList(),
)

/** Top-chart artist: display name (opens the artist screen) + cover. */
data class ChartArtist(
    val name: String,
    val cover: String = "",
)

/**
 * YouTube Music charts via the public InnerTube API — the exact approach of
 * sigma67/ytmusicapi (github.com/sigma67/ytmusicapi: ChartsMixin.get_charts +
 * playlist parsing), ported to Kotlin:
 *
 * 1. browse {browseId: "FEmusic_charts", formData: {selectedValues: [country]}}
 *    → the country's «Top Music Videos» playlist id + top artists;
 * 2. browse {browseId: "VL" + playlistId} → tracks with real videoIds.
 *
 * Chart entries carry their own videoIds, so — unlike chart-title search —
 * every row is directly playable, with nothing to go stale.
 */
object InnertubeCharts {

    /** Display order of sections on the charts screen. */
    val SECTION_ORDER = listOf("chart_ua", "chart_world", "top_artists")

    private const val BROWSE_URL =
        "https://music.youtube.com/youtubei/v1/browse?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val SECTION_TIMEOUT_MS = 60_000L
    private const val PAGE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val TRACKS_TTL_MS = 2 * 60 * 60 * 1000L

    private data class ChartsPage(
        val playlistId: String,
        val artists: List<ChartArtist>,
    )

    private val pageCache = ConcurrentHashMap<String, Pair<Long, ChartsPage>>()
    private val tracksCache = ConcurrentHashMap<String, Pair<Long, List<Track>>>()

    /**
     * Loads every section concurrently, emitting each via [onSection] as soon as
     * it finishes. Returns after all sections were attempted (failed/empty ones
     * are skipped — the screen shows whatever loaded).
     */
    suspend fun loadAll(onSection: suspend (ChartSection) -> Unit) = coroutineScope {
        launch { trackSection("chart_ua", R.string.chart_ua, "UA", 15)?.let { onSection(it) } }
        launch { trackSection("chart_world", R.string.chart_world, "ZZ", 15)?.let { onSection(it) } }
        launch { artistsSection()?.let { onSection(it) } }
    }

    private suspend fun trackSection(id: String, titleRes: Int, country: String, limit: Int): ChartSection? {
        val tracks = withTimeoutOrNull(SECTION_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { runCatching { countryTop(country, limit) }.getOrDefault(emptyList()) }
        }.orEmpty()
        return if (tracks.isEmpty()) null else ChartSection(id, titleRes, tracks)
    }

    private suspend fun artistsSection(): ChartSection? {
        val artists = withTimeoutOrNull(SECTION_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching { chartsPage("UA")?.artists.orEmpty().take(10) }.getOrDefault(emptyList())
            }
        }.orEmpty()
        return if (artists.isEmpty()) null else ChartSection("top_artists", R.string.chart_artists, artists = artists)
    }

    // ------------------------------------------------------------------ charts page

    private fun countryTop(country: String, limit: Int): List<Track> {
        tracksCache[country]?.let { (at, tracks) ->
            if (System.currentTimeMillis() - at < TRACKS_TTL_MS) return tracks.take(limit)
        }
        val playlistId = chartsPage(country)?.playlistId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val tracks = playlistTracks(playlistId, limit)
        if (tracks.isNotEmpty()) tracksCache[country] = System.currentTimeMillis() to tracks
        return tracks
    }

    /** Charts page of a country: top-videos playlist + top artists (cached). */
    private fun chartsPage(country: String): ChartsPage? {
        pageCache[country]?.let { (at, page) ->
            if (System.currentTimeMillis() - at < PAGE_TTL_MS) return page
        }
        val response = EmbeddedMusicSource.httpPostJson(BROWSE_URL, browseBody("FEmusic_charts", country))
            ?: return null
        val sections = response.nav(
            "contents", "singleColumnBrowseResultsRenderer",
            "tabs", 0, "tabRenderer", "content", "sectionListRenderer", "contents",
        ) as? JsonArray ?: return null
        var playlistId = ""
        val artists = mutableListOf<ChartArtist>()
        sections.drop(1).forEach { section ->
            val carousel = section.nav("musicCarouselShelfRenderer", "contents") as? JsonArray ?: return@forEach
            val first = carousel.firstOrNull() as? JsonObject ?: return@forEach
            when {
                // Chart playlists are the only carousel items linking to a "VL" playlist.
                first.nav(
                    "musicTwoRowItemRenderer", "title", "runs", 0,
                    "navigationEndpoint", "browseEndpoint", "browseId",
                ).text()?.startsWith("VL") == true -> {
                    if (playlistId.isEmpty()) {
                        playlistId = first.nav(
                            "musicTwoRowItemRenderer", "title", "runs", 0,
                            "navigationEndpoint", "browseEndpoint", "browseId",
                        ).text()?.drop(2).orEmpty()
                    }
                }
                // Artists carousel: rows, not two-row tiles.
                "musicResponsiveListItemRenderer" in first -> {
                    if (artists.isEmpty()) {
                        carousel.forEach { item -> parseChartArtist(item)?.let { artists += it } }
                    }
                }
            }
        }
        if (playlistId.isEmpty() && artists.isEmpty()) return null
        val page = ChartsPage(playlistId, artists)
        pageCache[country] = System.currentTimeMillis() to page
        return page
    }

    private fun parseChartArtist(item: JsonElement): ChartArtist? {
        val data = (item as? JsonObject)?.get("musicResponsiveListItemRenderer") ?: return null
        val name = data.nav(
            "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs", 0, "text",
        ).text()?.takeIf { it.isNotBlank() } ?: return null
        return ChartArtist(name, cover = largestThumb(data))
    }

    // ------------------------------------------------------------------ playlist tracks

    private fun playlistTracks(playlistId: String, limit: Int): List<Track> {
        val response = EmbeddedMusicSource.httpPostJson(BROWSE_URL, browseBody("VL$playlistId", null))
            ?: return emptyList()
        val contents = response.nav(
            "contents", "twoColumnBrowseResultsRenderer",
            "secondaryContents", "sectionListRenderer", "contents",
        ) as? JsonArray ?: return emptyList()
        val shelf = contents
            .mapNotNull { (it as? JsonObject)?.get("musicPlaylistShelfRenderer") as? JsonObject }
            .firstOrNull() ?: return emptyList()
        return (shelf["contents"] as? JsonArray).orEmpty()
            .mapNotNull { parseTrack(it) }
            .distinctBy { it.id }
            .filter { it.duration in 30..1200 }
            .take(limit)
    }

    private fun parseTrack(item: JsonElement): Track? {
        val data = (item as? JsonObject)?.get("musicResponsiveListItemRenderer") as? JsonObject ?: return null
        val videoId = data.nav(
            "overlay", "musicItemThumbnailOverlayRenderer", "content",
            "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId",
        ).text() ?: data.nav("playlistItemData", "videoId").text()
        if (videoId.isNullOrBlank()) return null
        val title = data.nav(
            "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs", 0, "text",
        ).text()?.takeIf { it.isNotBlank() } ?: return null
        val artistRuns = data.nav(
            "flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text", "runs",
        ) as? JsonArray
        // Artist names sit on even runs; odd ones are separators (parse_artists_runs).
        val artists = artistRuns.orEmpty()
            .filterIndexed { i, _ -> i % 2 == 0 }
            .mapNotNull { (it as? JsonObject)?.get("text")?.text() }
            .filter { it.isNotBlank() }
            .take(3)
        return Track(
            id = videoId,
            title = title,
            artist = artists.joinToString(", ").ifEmpty { "Unknown" },
            cover = largestThumb(data),
            url = "https://www.youtube.com/watch?v=$videoId",
            duration = parseDuration(
                data.nav(
                    "fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer",
                    "text", "runs", 0, "text",
                ).text(),
            ),
        )
    }

    private fun largestThumb(data: JsonElement?): String {
        val thumbs = data.nav("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JsonArray
            ?: return ""
        return thumbs.mapNotNull { it as? JsonObject }
            .maxByOrNull { it["width"]?.text()?.toIntOrNull() ?: 0 }
            ?.get("url")?.text().orEmpty()
    }

    private fun parseDuration(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return runCatching {
            text.trim().split(":").map { it.toInt() }.fold(0) { acc, part -> acc * 60 + part }
        }.getOrDefault(0)
    }

    // ------------------------------------------------------------------ request

    private fun browseBody(browseId: String, country: String?): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.$date.01.00")
                })
                put("user", buildJsonObject {})
            })
            put("browseId", browseId)
            if (country != null) {
                put("formData", buildJsonObject {
                    put("selectedValues", buildJsonArray { add(country) })
                })
            }
        }.toString()
    }

    /** ytmusicapi-style path walk: strings descend objects, ints index arrays. */
    private fun JsonElement?.nav(vararg path: Any): JsonElement? {
        var cur: JsonElement? = this
        for (p in path) {
            cur = when (p) {
                is String -> (cur as? JsonObject)?.get(p)
                is Int -> (cur as? JsonArray)?.getOrNull(p)
                else -> null
            } ?: return null
        }
        return cur
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
}
