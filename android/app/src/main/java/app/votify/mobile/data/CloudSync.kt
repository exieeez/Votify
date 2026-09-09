package app.votify.mobile.data

import app.votify.mobile.data.local.FavoriteEntity
import app.votify.mobile.data.local.PlaylistEntity
import app.votify.mobile.data.local.PlaylistTrackEntity
import app.votify.mobile.data.local.TrackEntity
import app.votify.mobile.data.local.VotifyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Compact wire format for one track inside the sync blob. */
@Serializable
data class SyncTrack(val id: String, val t: String, val a: String, val c: String, val d: Int)

@Serializable
data class SyncFavorite(val addedAt: Long, val track: SyncTrack)

@Serializable
data class SyncPlaylist(val name: String, val createdAt: Long, val tracks: List<SyncTrack>)

/** Everything that travels to the account cloud: settings + favorites + playlists. */
@Serializable
data class SyncBlob(
    val v: Int = 1,
    val theme: String = "",
    val customTheme: String = "",
    val customPrefs: String = "",
    val backgroundUrl: String = "",
    val favorites: List<SyncFavorite> = emptyList(),
    val playlists: List<SyncPlaylist> = emptyList(),
)

/**
 * Account cloud sync for standalone (Firebase) mode: favorites, playlists and settings are
 * exported to one JSON blob stored in the user's private Firestore document (users/{uid}),
 * so a reinstall or a new phone restores everything after signing in.
 */
class CloudSync(
    private val db: VotifyDatabase,
    private val settingsRepo: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** True when the local library holds anything worth keeping (favorites / playlists). */
    suspend fun hasLocalData(): Boolean =
        db.favorites().count() > 0 || db.playlists().count() > 0

    suspend fun exportBlob(): SyncBlob {
        val s = settingsRepo.settings.first()
        val tracks = db.tracks().syncExport().associateBy { it.id }
        val favorites = db.favorites().getAll().mapNotNull { f ->
            tracks[f.trackId]?.let { SyncFavorite(f.addedAt, SyncTrack(it.id, it.title, it.artist, it.cover, it.duration)) }
        }
        val playlists = db.playlists().getAll().map { p ->
            SyncPlaylist(
                name = p.name,
                createdAt = p.createdAt,
                tracks = db.playlists().tracksOf(p.id).map { SyncTrack(it.id, it.title, it.artist, it.cover, it.duration) },
            )
        }
        return SyncBlob(
            theme = s.theme.key,
            customTheme = s.customTheme,
            customPrefs = s.customPrefs,
            backgroundUrl = s.backgroundUrl,
            favorites = favorites,
            playlists = playlists,
        )
    }

    suspend fun importBlob(blob: SyncBlob) {
        if (blob.theme.isNotBlank()) settingsRepo.setTheme(AppTheme.fromKey(blob.theme))
        settingsRepo.setBackgroundUrl(blob.backgroundUrl)
        if (blob.customTheme.isNotBlank()) settingsRepo.setCustomTheme(blob.customTheme)
        if (blob.customPrefs.isNotBlank()) settingsRepo.setCustomPrefs(blob.customPrefs)

        // Favorites: upsert the tracks, then the links (IGNORE keeps existing rows).
        val favTracks = blob.favorites.map { TrackEntity(it.track.id, it.track.t, it.track.a, it.track.c, it.track.d) }
        if (favTracks.isNotEmpty()) {
            db.tracks().upsertAll(favTracks)
            db.favorites().insertAll(blob.favorites.map { FavoriteEntity(it.track.id, it.addedAt) })
        }

        // Playlists: recreated with fresh ids; tracks land in the stored order.
        val now = System.currentTimeMillis()
        blob.playlists.forEach { p ->
            val pid = db.playlists().insert(PlaylistEntity(0, p.name, p.createdAt, now))
            p.tracks.forEachIndexed { i, t ->
                db.tracks().upsert(TrackEntity(t.id, t.t, t.a, t.c, t.d))
                db.playlists().insertTrack(PlaylistTrackEntity(pid, t.id, i, now))
            }
        }
    }

    fun toJson(blob: SyncBlob): String = json.encodeToString(SyncBlob.serializer(), blob)

    fun fromJson(raw: String): SyncBlob? =
        runCatching { json.decodeFromString<SyncBlob>(raw) }.getOrNull()
}
