package app.votify.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class VotifyDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun playlists(): PlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN customCover TEXT DEFAULT NULL")
            }
        }

        fun create(context: Context): VotifyDatabase =
            Room.databaseBuilder(context.applicationContext, VotifyDatabase::class.java, "votify.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
