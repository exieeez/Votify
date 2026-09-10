package app.votify.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VotifyDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun playlists(): PlaylistDao

    companion object {
        fun create(context: Context): VotifyDatabase =
            Room.databaseBuilder(context.applicationContext, VotifyDatabase::class.java, "votify.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
