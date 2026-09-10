package app.votify.mobile.data

import app.votify.mobile.R
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One charts-screen block: stable id, title and resolved playable tracks. */
data class ChartSection(
    val id: String,
    val titleRes: Int,
    val tracks: List<Track>,
)

/**
 * Charts & fresh releases without any API keys, CIS-first:
 * - Apple Music charts of the UA and RU storefronts (refreshed daily by Apple),
 * - Deezer global chart + per-artist tops (resolved at runtime, nothing hardcoded),
 * - iTunes «recent songs» per artist for the fresh-releases block.
 *
 * Every entry is an (artist, title) pair resolved to a playable YouTube Music
 * track via [EmbeddedMusicSource.searchFirst]; pairs that don't resolve are
 * skipped, so the screen only ever shows playable rows.
 *
 * Sections load concurrently and are emitted progressively as each finishes —
 * the screen fills in live instead of waiting for the slowest source.
 */
object DiscoverySource {

    /** Display order of sections on the charts screen. */
    val SECTION_ORDER = listOf("fresh", "chart_ua", "chart_ru", "chart_world", "pop", "hiphop")

    /** Artists feeding the fresh-releases block (names only — ids resolve at runtime). */
    private val FRESH_SEEDS = listOf(
        "ONDA ANDAR", "XOLIDAYBOY", "Nasty Babe", "Jakone", "ICEGERGERT",
        "ANNA ASTI", "Zivert", "JONY", "Три дня дождя", "Баста",
        "Мари Краймбрери", "Клава Кока", "Artik & Asti", "Toxi$",
    )

    private val POP_SEEDS = listOf(
        "ANNA ASTI", "Zivert", "JONY", "Клава Кока", "Мари Краймбрери", "Artik & Asti",
    )

    private val HIPHOP_SEEDS = listOf(
        "Баста", "Jakone", "ICEGERGERT", "XOLIDAYBOY", "Toxi$", "ONDA ANDAR",
    )

    private const val RESOLVE_BATCH = 6
    private const val SECTION_TIMEOUT_MS = 90_000L
    private const val CHART_TTL_MS = 2 * 60 * 60 * 1000L
    private const val ARTIST_TTL_MS = 12 * 60 * 60 * 1000L
    private const val RESOLVED_TTL_MS = 24 * 60 * 60 * 1000L
    private const val MISS_TTL_MS = 60 * 60 * 1000L

    /** Fresh block keeps releases from the last ~5 months. */
    private const val FRESH_DAYS = 150L

    private val metaCache = ConcurrentHashMap<String, Pair<Long, List<Pair<String, String>>>>()
    private val freshCache = ConcurrentHashMap<String, Pair<Long, List<FreshItem>>>()
    private val resolvedCache = ConcurrentHashMap<String, Pair<Long, Track>>()
    private val missCache = ConcurrentHashMap<String, Long>()

    private data class FreshItem(val artist: String, val title: String, val date: String)

    /**
     * Loads every section concurrently, emitting each via [onSection] as soon as
     * it finishes. Returns after all sections were attempted (failed/empty ones
     * are skipped silently — the screen shows whatever loaded).
     */
    suspend fun loadAll(onSection: suspend (ChartSection) -> Unit) = coroutineScope {
        sectionJobs().forEach { (id, titleRes, load) ->
            launch {
                val tracks = withTimeoutOrNull(SECTION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { runCatching { load() }.getOrDefault(emptyList()) }
                }.orEmpty()
                if (tracks.isNotEmpty()) onSection(ChartSection(id, titleRes, tracks))
            }
        }
    }

    private fun sectionJobs(): List<Triple<String, Int, () -> List<Track>>> = listOf(
        Triple("fresh", R.string.chart_fresh) { resolve(freshPairs(), 8) },
        Triple("chart_ua", R.string.chart_ua) { resolve(appleChart("ua"), 10) },
        Triple("chart_ru", R.string.chart_ru) { resolve(appleChart("ru"), 10) },
        Triple("chart_world", R.string.chart_world) { resolve(deezerGlobal(), 10) },
        Triple("pop", R.string.genre_pop) { resolve(genrePairs(POP_SEEDS), 8) },
        Triple("hiphop", R.string.genre_hiphop) { resolve(genrePairs(HIPHOP_SEEDS), 8) },
    )

    // ------------------------------------------------------------------ resolve

    /** (artist, title) pairs → playable tracks, order kept, unresolvable skipped. */
    private fun resolve(pairs: List<Pair<String, String>>, limit: Int): List<Track> = runBlocking {
        pairs.asSequence()
            .filter { it.second.isNotBlank() }
            .distinctBy { "${it.first}|${it.second}".lowercase() }
            .take(limit + 4)
            .toList()
            .chunked(RESOLVE_BATCH)
            .flatMap { batch ->
                coroutineScope {
                    batch.map { (artist, title) ->
                        async(Dispatchers.IO) { resolvePair(artist, title) }
                    }.awaitAll()
                }
            }
            .filterNotNull()
            .distinctBy { it.id }
            .distinctBy { "${it.artist}|${it.title}".lowercase() }
            .filter { it.duration in 30..600 }
            .take(limit)
    }

    private fun resolvePair(artist: String, title: String): Track? {
        val key = "${artist.lowercase()}|${title.lowercase()}"
        val now = System.currentTimeMillis()
        resolvedCache[key]?.let { (at, track) ->
            if (now - at < RESOLVED_TTL_MS) return track else resolvedCache.remove(key)
        }
        missCache[key]?.let { at ->
            if (now - at < MISS_TTL_MS) return null else missCache.remove(key)
        }
        val query = if (artist.isBlank()) title else "$artist $title"
        val track = EmbeddedMusicSource.searchFirst(query)
        if (track != null) resolvedCache[key] = now to track
        else missCache[key] = now
        return track
    }

    // ------------------------------------------------------------------ Apple charts

    /** Apple Music most-played songs of a storefront, newest feed host first. */
    private fun appleChart(region: String, limit: Int = 12): List<Pair<String, String>> {
        val key = "apple:$region"
        metaCache[key]?.let { (at, pairs) ->
            if (System.currentTimeMillis() - at < CHART_TTL_MS) return pairs.take(limit)
        }
        val feeds = listOf(
            "https://rss.applemarketingtools.com/api/v2/$region/music/most-played/50/songs.json",
            "https://rss.marketingtools.apple.com/api/v2/$region/music/most-played/50/songs.json",
        )
        for (url in feeds) {
            val pairs = runCatching {
                EmbeddedMusicSource.httpGetJson(url)
                    ?.jsonObject?.get("feed")?.jsonObject
                    ?.get("results")?.jsonArray
                    .orEmpty()
                    .mapNotNull { item ->
                        val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
                        val title = obj["name"]?.jsonPrimitive?.contentOrNull
                            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val artist = obj["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        artist to title
                    }
            }.getOrDefault(emptyList())
            if (pairs.isNotEmpty()) {
                metaCache[key] = System.currentTimeMillis() to pairs
                return pairs.take(limit)
            }
        }
        return emptyList()
    }

    // ------------------------------------------------------------------ Deezer

    /** Deezer worldwide top tracks. */
    private fun deezerGlobal(limit: Int = 15): List<Pair<String, String>> {
        metaCache["dz:global"]?.let { (at, pairs) ->
            if (System.currentTimeMillis() - at < CHART_TTL_MS) return pairs.take(limit)
        }
        val urls = listOf(
            "https://api.deezer.com/chart/0/tracks?limit=$limit",
            "https://api.deezer.com/chart",
        )
        for (url in urls) {
            val root = EmbeddedMusicSource.httpGetJson(url)?.jsonObject ?: continue
            // /chart/0/tracks → {data:[...]}; /chart → {tracks:{data:[...]}, ...}
            val items = runCatching {
                root["data"]?.jsonArray
                    ?: root["tracks"]?.jsonObject?.get("data")?.jsonArray
                    ?: emptyList()
            }.getOrDefault(emptyList())
            val pairs = deezerPairs(items)
            if (pairs.isNotEmpty()) {
                metaCache["dz:global"] = System.currentTimeMillis() to pairs
                return pairs.take(limit)
            }
        }
        return emptyList()
    }

    /** Round-robin across seed artists' tops so one name can't flood the block. */
    private fun genrePairs(seeds: List<String>): List<Pair<String, String>> = runBlocking {
        val perArtist = seeds.map { seed -> async(Dispatchers.IO) { deezerArtistTop(seed, 2) } }.awaitAll()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until 2) perArtist.forEach { list -> list.getOrNull(i)?.let { out += it } }
        out
    }

    private fun deezerArtistTop(name: String, limit: Int): List<Pair<String, String>> {
        val key = "dz:top:$name"
        metaCache[key]?.let { (at, pairs) ->
            if (System.currentTimeMillis() - at < ARTIST_TTL_MS) return pairs.take(limit)
        }
        val id = runCatching {
            EmbeddedMusicSource.httpGetJson("https://api.deezer.com/search/artist?q=${enc(name)}&limit=1")
                ?.jsonObject?.get("data")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
        }.getOrNull() ?: return emptyList()
        val pairs = runCatching {
            val items = EmbeddedMusicSource.httpGetJson("https://api.deezer.com/artist/$id/top?limit=${limit + 2}")
                ?.jsonObject?.get("data")?.jsonArray
                .orEmpty()
            deezerPairs(items)
        }.getOrDefault(emptyList()).take(limit)
        if (pairs.isNotEmpty()) metaCache[key] = System.currentTimeMillis() to pairs
        return pairs
    }

    private fun deezerPairs(items: List<JsonElement>): List<Pair<String, String>> =
        items.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = runCatching {
                obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            artist to title
        }

    // ------------------------------------------------------------------ iTunes fresh

    /** Latest songs across seed artists, newest first. */
    private fun freshPairs(): List<Pair<String, String>> = runBlocking {
        val cutoffDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis() - FRESH_DAYS * 24 * 60 * 60 * 1000L))
        val items = FRESH_SEEDS.map { seed ->
            async(Dispatchers.IO) { itunesRecent(seed, cutoffDay) }
        }.awaitAll().flatten()
        items.sortedByDescending { it.date }.take(12).map { it.artist to it.title }
    }

    private fun itunesRecent(name: String, cutoffDay: String): List<FreshItem> {
        freshCache["it:$name"]?.let { (at, items) ->
            if (System.currentTimeMillis() - at < ARTIST_TTL_MS) return items
        }
        val artistId = runCatching {
            EmbeddedMusicSource.httpGetJson(
                "https://itunes.apple.com/search?term=${enc(name)}&entity=musicArtist&limit=1",
            )?.jsonObject?.get("results")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("artistId")?.jsonPrimitive?.longOrNull
        }.getOrNull() ?: return emptyList()
        val items = runCatching {
            EmbeddedMusicSource.httpGetJson(
                "https://itunes.apple.com/lookup?id=$artistId&entity=song&limit=25&sort=recent",
            )?.jsonObject?.get("results")?.jsonArray
                .orEmpty()
                .mapNotNull { item ->
                    val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
                    if (obj["wrapperType"]?.jsonPrimitive?.contentOrNull != "track") return@mapNotNull null
                    val title = obj["trackName"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val artist = obj["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val date = obj["releaseDate"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (date.length < 10 || date.take(10) < cutoffDay) return@mapNotNull null
                    FreshItem(artist.ifEmpty { name }, title, date)
                }
                .sortedByDescending { it.date }
                .take(2)
        }.getOrDefault(emptyList())
        if (items.isNotEmpty()) freshCache["it:$name"] = System.currentTimeMillis() to items
        return items
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
