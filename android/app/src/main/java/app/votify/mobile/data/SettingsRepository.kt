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

/** Built-in themes: two dark bases, five accent variants, system, and one from the Workshop. */
enum class AppTheme(val key: String) {
    OledBlack("oled"),
    Graphite("graphite"),
    Violet("violet"),
    /** Forced light mode (from the Interface settings). */
    Light("light"),
    Azure("azure"),
    Emerald("emerald"),
    Amber("amber"),
    Rose("rose"),
    System("system"),
    /** A community/custom theme from the Workshop, stored as JSON in [Settings.customTheme]. */
    Workshop("workshop");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: OledBlack
    }
}

enum class ArtworkStyle(val key: String) {
    Vinyl("vinyl"),
    Square("square"),
    Circle("circle"),
    Blur("blur");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: Vinyl
    }
}

/** Player background: flat app color or the dominant artwork color (PC-style). */
enum class PlayerBackground(val key: String) {
    Dark("dark"),
    Artwork("artwork"),
    Lava("lava");

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: Artwork
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

/** Signed-in account: Firebase (idToken + refreshToken) or the local server (JWT). */
data class Account(
    val email: String,
    val username: String,
    val token: String,
    val uid: String = "",
    val refreshToken: String = "",
) {
    val isFirebase: Boolean get() = refreshToken.isNotBlank() || uid.isNotBlank()
}

data class Settings(
    val theme: AppTheme = AppTheme.OledBlack,
    val artworkStyle: ArtworkStyle = ArtworkStyle.Vinyl,
    val audioQuality: AudioQuality = AudioQuality.Medium,
    val showLyricsOverArtwork: Boolean = false,
    val miniPlayerSwipeChangesTrack: Boolean = true,
    val volumeButtonsSkip: Boolean = false,
    /** Backend override; blank = the build-time default (10.0.2.2, emulator only). */
    val serverUrl: String = "",
    val playerBackground: PlayerBackground = PlayerBackground.Artwork,
    /** JSON of a Workshop theme applied via the Workshop screen (null/blank = none). */
    val customTheme: String = "",
    /** App background image (URL/path) — independent of the theme so settings changes never wipe it. */
    val backgroundUrl: String = "",
    val customPrefs: String = "",
    /** Cached Firebase Web Config (JSON) — powers accounts and the community Workshop. */
    val firebaseConfig: String = "",
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
        val serverUrl = stringPreferencesKey("server_url")
        val playerBackground = stringPreferencesKey("player_background")
        val customPrefs = stringPreferencesKey("custom_prefs")
    val customTheme = stringPreferencesKey("custom_theme")
        val backgroundUrl = stringPreferencesKey("background_url")
        val firebaseConfig = stringPreferencesKey("firebase_config")
        val accountUid = stringPreferencesKey("account_uid")
        val accountRefresh = stringPreferencesKey("account_refresh_token")
        val accountEmail = stringPreferencesKey("account_email")
        val accountUsername = stringPreferencesKey("account_username")
        val accountToken = stringPreferencesKey("account_token")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            theme = AppTheme.fromKey(p[Keys.theme]),
            artworkStyle = ArtworkStyle.fromKey(p[Keys.artwork]),
            audioQuality = AudioQuality.fromKey(p[Keys.quality]),
            showLyricsOverArtwork = p[Keys.lyricsOverArt] ?: false,
            miniPlayerSwipeChangesTrack = p[Keys.swipeChangesTrack] ?: true,
            volumeButtonsSkip = p[Keys.volumeSkip] ?: false,
            serverUrl = p[Keys.serverUrl] ?: "",
            playerBackground = PlayerBackground.fromKey(p[Keys.playerBackground]),
            customTheme = p[Keys.customTheme] ?: "",
            backgroundUrl = p[Keys.backgroundUrl] ?: "",
            customPrefs = p[Keys.customPrefs] ?: "",
            firebaseConfig = p[Keys.firebaseConfig] ?: "",
        )
    }

    /** The signed-in account, or null. */
    val account: Flow<Account?> = store.data.map { p ->
        val token = p[Keys.accountToken] ?: return@map null
        Account(
            email = p[Keys.accountEmail].orEmpty(),
            username = p[Keys.accountUsername].orEmpty(),
            token = token,
            uid = p[Keys.accountUid].orEmpty(),
            refreshToken = p[Keys.accountRefresh].orEmpty(),
        )
    }

    suspend fun setTheme(v: AppTheme) = store.edit { it[Keys.theme] = v.key }
    suspend fun setArtworkStyle(v: ArtworkStyle) = store.edit { it[Keys.artwork] = v.key }
    suspend fun setAudioQuality(v: AudioQuality) = store.edit { it[Keys.quality] = v.key }
    suspend fun setShowLyricsOverArtwork(v: Boolean) = store.edit { it[Keys.lyricsOverArt] = v }
    suspend fun setMiniPlayerSwipeChangesTrack(v: Boolean) = store.edit { it[Keys.swipeChangesTrack] = v }
    suspend fun setVolumeButtonsSkip(v: Boolean) = store.edit { it[Keys.volumeSkip] = v }
    suspend fun setServerUrl(v: String) = store.edit { it[Keys.serverUrl] = v.trim() }
    suspend fun setPlayerBackground(v: PlayerBackground) = store.edit { it[Keys.playerBackground] = v.key }
    suspend fun setCustomTheme(json: String) = store.edit { it[Keys.customTheme] = json.trim() }
    suspend fun setBackgroundUrl(url: String) = store.edit { it[Keys.backgroundUrl] = url.trim() }
    suspend fun setCustomPrefs(json: String) = store.edit { it[Keys.customPrefs] = json.trim() }
    suspend fun setFirebaseConfig(json: String) = store.edit { it[Keys.firebaseConfig] = json.trim() }

    suspend fun setAccount(
        email: String,
        username: String,
        token: String,
        uid: String = "",
        refreshToken: String = "",
    ) = store.edit {
        it[Keys.accountEmail] = email
        it[Keys.accountUsername] = username
        it[Keys.accountToken] = token
        it[Keys.accountUid] = uid
        it[Keys.accountRefresh] = refreshToken
    }

    suspend fun clearAccount() = store.edit {
        it.remove(Keys.accountEmail)
        it.remove(Keys.accountUsername)
        it.remove(Keys.accountToken)
        it.remove(Keys.accountUid)
        it.remove(Keys.accountRefresh)
    }
}
