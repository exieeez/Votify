package app.votify.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme(val key: String) {
    OledBlack("oled"),
    Graphite("graphite"),
    System("system");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: OledBlack
    }
}

enum class ArtworkStyle(val key: String) {
    Vinyl("vinyl"),
    Square("square"),
    Blur("blur");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: Vinyl
    }
}

/** Server-side quality knob (routes/utils.js: AUDIO_QUALITY_FORMATS). */
enum class AudioQuality(val key: String) {
    Low("low"),
    Medium("medium"),
    High("high");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: Medium
    }
}

data class Settings(
    val theme: AppTheme = AppTheme.OledBlack,
    val artworkStyle: ArtworkStyle = ArtworkStyle.Vinyl,
    val audioQuality: AudioQuality = AudioQuality.Medium,
    val showLyricsOverArtwork: Boolean = false,
    val miniPlayerSwipeChangesTrack: Boolean = true,
    val volumeButtonsSkip: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val artwork = stringPreferencesKey("artwork_style")
        val quality = stringPreferencesKey("audio_quality")
        val lyricsOverArt = booleanPreferencesKey("lyrics_over_artwork")
        val swipeChangesTrack = booleanPreferencesKey("swipe_changes_track")
        val volumeSkip = booleanPreferencesKey("volume_buttons_skip")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            theme = AppTheme.fromKey(p[Keys.theme]),
            artworkStyle = ArtworkStyle.fromKey(p[Keys.artwork]),
            audioQuality = AudioQuality.fromKey(p[Keys.quality]),
            showLyricsOverArtwork = p[Keys.lyricsOverArt] ?: false,
            miniPlayerSwipeChangesTrack = p[Keys.swipeChangesTrack] ?: true,
            volumeButtonsSkip = p[Keys.volumeSkip] ?: false,
        )
    }

    suspend fun setTheme(v: AppTheme) = store.edit { it[Keys.theme] = v.key }
    suspend fun setArtworkStyle(v: ArtworkStyle) = store.edit { it[Keys.artwork] = v.key }
    suspend fun setAudioQuality(v: AudioQuality) = store.edit { it[Keys.quality] = v.key }
    suspend fun setShowLyricsOverArtwork(v: Boolean) = store.edit { it[Keys.lyricsOverArt] = v }
    suspend fun setMiniPlayerSwipeChangesTrack(v: Boolean) = store.edit { it[Keys.swipeChangesTrack] = v }
    suspend fun setVolumeButtonsSkip(v: Boolean) = store.edit { it[Keys.volumeSkip] = v }
}
