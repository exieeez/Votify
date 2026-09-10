package app.votify.mobile.data

import kotlinx.serialization.Serializable

/**
 * Track as returned by the Votify backend (`makeTrack` in routes/utils.js).
 * `id` is an 11-char YouTube id or `sc_<id>` for SoundCloud.
 */
@Serializable
data class Track(
    val id: String,
    val title: String = "Unknown",
    val artist: String = "Unknown",
    val cover: String = "",
    val url: String = "",
    val duration: Int = 0,
)

@Serializable
data class TracksResponse(val tracks: List<Track> = emptyList())

@Serializable
data class ArtistResponse(val artist: String = "", val tracks: List<Track> = emptyList())

@Serializable
data class LyricsResponse(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val track: String = "",
    val artist: String = "",
)

@Serializable
data class NetworkSettings(
    val streamSource: String = "yt-dlp",
    val audioQuality: String = "medium",
)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val name: String = "",
    val version: String = "",
)

@Serializable
data class ApiError(val error: String = "")

// ---- Account (routes/auth.js) ----

@Serializable
data class AuthUser(val id: String = "", val email: String = "", val username: String = "")

@Serializable
data class AuthResponse(val token: String = "", val user: AuthUser = AuthUser())

/** /api/sync/push — the server's ack for a settings upload. */
@Serializable
data class SyncPushResponse(val ok: Boolean = false, val savedAt: Long = 0)

/** /api/sync/get — the user's saved settings blob (raw CustomPrefs JSON). */
@Serializable
data class SyncData(val settings: String = "", val savedAt: Long = 0)

/** /api/auth/forgot-password returns the code inline when SMTP is not configured. */
@Serializable
data class ForgotResponse(val message: String = "", val code: String? = null)

// ---- Playlist import (routes/music.js) ----

/** /api/playlist (YouTube/Spotify) and /api/soundcloud/import. */
@Serializable
data class ImportedTracks(val name: String? = null, val tracks: List<Track> = emptyList())
