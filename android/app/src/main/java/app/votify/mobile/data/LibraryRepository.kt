package app.votify.mobile.data

import app.votify.mobile.data.local.FavoriteEntity
import app.votify.mobile.data.local.HistoryEntity
import app.votify.mobile.data.local.PlaylistEntity
import app.votify.mobile.data.local.PlaylistSummary
import app.votify.mobile.data.local.PlaylistTrackEntity
import app.votify.mobile.data.local.TrackEntity
import app.votify.mobile.data.local.VotifyDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Favorites, listening history and user playlists — all local (Room). */
class LibraryRepository(private val db: VotifyDatabase) {

    // ---- Favorites ----

    val favorites: Flow<List<Track>> = db.favorites().observeAll().map { it.map(TrackEntity::toTrack) }
    val favoriteIds: Flow<Set<String>> = db.favorites().observeIds().map { it.toSet() }
    val favoriteCount: Flow<Int> = db.favorites().observeCount()

    /** @return true if the track is a favorite after the toggle. */
    suspend fun toggleFavorite(track: Track): Boolean = db.withTransaction {
        val dao = db.favorites()
        if (dao.isFavorite(track.id)) {
            dao.delete(track.id)
            false
        } else {
            db.tracks().upsert(TrackEntity.from(track))
            dao.insert(FavoriteEntity(track.id, now()))
            true
        }
    }

    // ---- History ----

    fun recent(limit: Int = 50): Flow<List<Track>> =
        db.history().observeRecent(limit).map { it.map(TrackEntity::toTrack) }

    val historyCount: Flow<Int> = db.history().observeDistinctCount()

    suspend fun recordPlay(track: Track) = db.withTransaction {
        db.tracks().upsert(TrackEntity.from(track))
        db.history().insert(HistoryEntity(trackId = track.id, playedAt = now()))
        db.history().trim(keep = 2000)
    }

    suspend fun topArtists(limit: Int = 6): List<String> = db.history().topArtists(limit)

    suspend fun trackById(id: String): Track? = db.tracks().byId(id)?.toTrack()

    /** Artists across ALL playlists, weighted by track count — wave seeds for collectors. */
    suspend fun playlistArtists(limit: Int = 8): List<String> {
        val counts = LinkedHashMap<String, Int>()
        db.playlists().getAll().forEach { pl ->
            db.playlists().tracksOf(pl.id).forEach { t ->
                val artist = t.artist.trim()
                if (artist.isNotBlank() && !artist.equals("Unknown", true)) {
                    counts[artist] = (counts[artist] ?: 0) + 1
                }
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
    }

    suspend fun clearHistory() = db.history().clear()

    /** «Очистить всё»: favorites, history, playlists and the cached track rows. */
    suspend fun clearAllData() = db.withTransaction {
        db.favorites().clear()
        db.history().clear()
        db.playlists().clearAllTracks()
        db.playlists().clearAll()
        db.playlists().clearAllTracksData()
    }

    // ---- Playlists ----

    val playlists: Flow<List<PlaylistSummary>> = db.playlists().observeSummaries()

    fun playlist(id: Long): Flow<PlaylistEntity?> = db.playlists().observe(id)

    fun playlistTracks(id: Long): Flow<List<Track>> =
        db.playlists().observeTracks(id).map { it.map(TrackEntity::toTrack) }

    suspend fun createPlaylist(name: String): Long {
        val t = now()
        return db.playlists().insert(PlaylistEntity(name = name.trim().ifEmpty { "Новый плейлист" }, createdAt = t, updatedAt = t))
    }

    suspend fun renamePlaylist(id: Long, name: String) = db.playlists().rename(id, name.trim(), now())

    suspend fun deletePlaylist(id: Long) = db.playlists().delete(id)

    /** @return false if the track was already in the playlist. */
    suspend fun addToPlaylist(playlistId: Long, track: Track): Boolean = db.withTransaction {
        db.tracks().upsert(TrackEntity.from(track))
        val dao = db.playlists()
        val t = now()
        val pos = dao.nextPosition(playlistId)
        val inserted = dao.insertTrack(PlaylistTrackEntity(playlistId, track.id, pos, t)) != -1L
        if (inserted) dao.touch(playlistId, t)
        inserted
    }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: String) {
        db.playlists().removeTrack(playlistId, trackId)
        db.playlists().touch(playlistId, now())
    }

    suspend fun playlistIdsContaining(trackId: String): Set<Long> =
        db.playlists().playlistIdsContaining(trackId).toSet()

    private fun now() = System.currentTimeMillis()
}
