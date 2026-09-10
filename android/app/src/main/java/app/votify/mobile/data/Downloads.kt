package app.votify.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live progress of one offline download. */
data class DownloadProgress(
    val trackId: String,
    val received: Long = 0L,
    val total: Long = -1L,
) {
    /** 0f..1f, or 0f while the total size is still unknown. */
    val fraction: Float
        get() = if (total > 0) (received.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) else 0f

    /** True until the received size reaches the total (indeterminate when total is unknown). */
    val active: Boolean
        get() = total <= 0L || received < total
}

/**
 * App-wide offline-download tracker. Track rows subscribe to [progress] to draw
 * live progress bars, and to [downloaded] to show the «скачано» badge.
 */
object DownloadTracker {

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    /** trackId → live progress of the downloads running right now. */
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())

    /** Ids with a stored offline copy (as far as the app has seen them). */
    val downloaded: StateFlow<Set<String>> = _downloaded.asStateFlow()

    fun start(trackId: String) {
        _progress.value = _progress.value + (trackId to DownloadProgress(trackId))
    }

    fun update(trackId: String, received: Long, total: Long) {
        _progress.value = _progress.value + (trackId to DownloadProgress(trackId, received, total))
    }

    fun finish(trackId: String, success: Boolean) {
        _progress.value = _progress.value - trackId
        if (success) _downloaded.value = _downloaded.value + trackId
    }

    fun removeDownloaded(trackId: String) {
        _downloaded.value = _downloaded.value - trackId
    }

    /** Merge a batch of known-offline ids (initial scan of the downloads dir). */
    fun seedDownloaded(ids: Set<String>) {
        if (ids.isNotEmpty()) _downloaded.value = _downloaded.value + ids
    }
}
