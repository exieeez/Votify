package app.votify.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    /**
     * Insert-or-update. NOT `@Insert(onConflict = REPLACE)`: REPLACE deletes the old row first,
     * which would cascade-delete favorites / history / playlist links pointing at it.
     */
    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: String): TrackEntity?

    /** Tracks referenced by favorites or playlists — the sync export set. */
    @Query(
        """SELECT * FROM tracks
           WHERE id IN (SELECT trackId FROM favorites)
              OR id IN (SELECT trackId FROM playlist_tracks)""",
    )
    suspend fun syncExport(): List<TrackEntity>
}

@Dao
interface FavoriteDao {
    @Query(
        """SELECT t.* FROM tracks t INNER JOIN favorites f ON f.trackId = t.id
           ORDER BY f.addedAt DESC""",
    )
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT trackId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(favs: List<FavoriteEntity>)

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}

@Dao
interface HistoryDao {
    /** Distinct tracks, most recently played first. */
    @Query(
        """SELECT t.* FROM tracks t
           INNER JOIN (SELECT trackId, MAX(playedAt) AS lastPlayed FROM history GROUP BY trackId) h
             ON h.trackId = t.id
           ORDER BY h.lastPlayed DESC LIMIT :limit""",
    )
    fun observeRecent(limit: Int): Flow<List<TrackEntity>>

    /** Artists weighted by how often they were played — seeds for "Моя волна". */
    @Query(
        """SELECT t.artist FROM history h INNER JOIN tracks t ON t.id = h.trackId
           WHERE t.artist != '' AND t.artist != 'Unknown'
           GROUP BY t.artist ORDER BY COUNT(*) DESC, MAX(h.playedAt) DESC LIMIT :limit""",
    )
    suspend fun topArtists(limit: Int): List<String>

    @Query("SELECT COUNT(DISTINCT trackId) FROM history")
    fun observeDistinctCount(): Flow<Int>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query(
        """SELECT p.id, p.name, p.createdAt, p.updatedAt,
                  (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
                  (SELECT t.cover FROM playlist_tracks pt INNER JOIN tracks t ON t.id = pt.trackId
                     WHERE pt.playlistId = p.id ORDER BY pt.position ASC LIMIT 1) AS cover
           FROM playlists p ORDER BY p.updatedAt DESC""",
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query(
        """SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.trackId = t.id
           WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC""",
    )
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun playlistIdsContaining(trackId: String): List<Long>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists")
    suspend fun clearAll()

    @Query("DELETE FROM playlist_tracks")
    suspend fun clearAllTracks()

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracksData()

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(link: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query(
        """SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.trackId = t.id
           WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC""",
    )
    suspend fun tracksOf(playlistId: Long): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int
}
