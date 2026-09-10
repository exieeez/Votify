#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Votify Android — восстановление фич «прогресс скачивания + плашки "скачано" +
настройки фона (затемнение/размытие)» и починки CI-триггеров.

КАК ПОЛЬЗОВАТЬСЯ (агент в новом чате):
  1. git fetch origin android
  2. git checkout origin/android -- android .github/workflows/release-apk.yml .github/workflows/android.yml
     (это кладёт каталог android/ + workflows с ветки android в вашу рабочую ветку)
  3. python3 restore-android-features.py      # из корня репозитория
  4. git add -A && git commit -m "feat(android): download progress UI, «скачано» badges, background dim/blur settings + ci triggers"
  5. git push origin HEAD   # ваша сессионная ветка
  6. gh workflow run release-apk.yml --ref <ваша-ветка>
  APK появится по постоянной ссылке:
     https://github.com/exieeez/Votify/releases/download/android-debug/Votify-debug.apk

Скрипт идемпотентен: каждый шаг проверяет, применена ли правка уже.
Любое несовпадение «старого» текста — громкая ошибка (assert), молча ничего не ломается.
"""
import io, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'

def P(rel): return os.path.join(ROOT, rel)

def read(rel):
    return io.open(P(rel), encoding='utf-8').read()

def write(rel, s):
    io.open(P(rel), 'w', encoding='utf-8').write(s)

def replace(rel, old, new, label=''):
    s = read(rel)
    if new in s:
        print(f'  = {rel}: уже применено ({label or "skip"})')
        return
    assert old in s, f'НЕ НАЙДЕН якорь в {rel} ({label}):\n---\n{old[:300]}\n---'
    s = s.replace(old, new, 1)
    write(rel, s)
    print(f'  + {rel}: {label or "patched"}')

def inline_trailing_header(rel):
    """Kotlin запрещает смешивать именованные аргументы с trailing-lambda:
    `TrackList(..., downloadedIds = ...) { ... }` не компилируется. Превращаем
    хвостовую лямбду в именованный аргумент `header = { ... }` (и переотступаем тело).
    Идемпотентно: тело уже с отступом в 12 пробелов — значит правка применена.
    """
    lines = read(rel).split('\n')
    out, i, changed = [], 0, 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        if line == '        header = {':
            j = i + 1
            body = []
            while j < len(lines) and lines[j] != '    }':
                body.append(lines[j]); j += 1
            already = i + 1 < len(lines) and lines[i + 1].startswith('            ')
            if j < len(lines) and not already:
                out.extend(('    ' + l if l.strip() else l) for l in body)
                out.append('        },')
                out.append('    )')
                i = j + 1
                changed += 1
                continue
        i += 1
    if changed:
        write(rel, '\n'.join(out))
    print(f'  + {rel}: {changed} вызов(ов) TrackList → header = {{')

# ---------------------------------------------------------------------------
print('1/9 Downloads.kt (новый файл — трекер прогресса)')
DL = 'android/app/src/main/java/app/votify/mobile/data/Downloads.kt'
if os.path.exists(P(DL)):
    print('  = Downloads.kt уже существует')
else:
    write(DL, '''package app.votify.mobile.data

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
''')
    print('  + Downloads.kt создан')

# ---------------------------------------------------------------------------
print('2/9 EmbeddedMusicSource: downloadTrack с прогрессом')
replace(
    'android/app/src/main/java/app/votify/mobile/data/EmbeddedMusicSource.kt',
    """    /**
     * Download a track for offline listening: resolve the stream URL, fetch the bytes into
     * a temp file, then atomically move it into place. Existing copies are reused as-is.
     */
    fun downloadTrack(trackId: String, quality: AudioQuality): java.io.File {
        ensureInit()
        val dir = downloadsDir ?: throw IOException("Хранилище недоступно")
        dir.mkdirs()
        val target = java.io.File(dir, downloadName(trackId) + ".audio")
        if (target.exists() && target.length() > 0) return target
        val url = resolveAudioUrl(trackId, quality)
        val request = OkHttpRequest.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val tmp = java.io.File(dir, downloadName(trackId) + ".tmp")
            runCatching {
                resp.body?.byteStream()?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Пустой ответ")
            }.onFailure { tmp.delete(); throw IOException("Не удалось сохранить файл") }""",
    """    /**
     * Download a track for offline listening: resolve the stream URL, fetch the bytes into
     * a temp file, then atomically move it into place. Existing copies are reused as-is.
     * [onProgress] reports (receivedBytes, totalBytes); total is -1 when the server does
     * not send Content-Length.
     */
    fun downloadTrack(
        trackId: String,
        quality: AudioQuality,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): java.io.File {
        ensureInit()
        val dir = downloadsDir ?: throw IOException("Хранилище недоступно")
        dir.mkdirs()
        val target = java.io.File(dir, downloadName(trackId) + ".audio")
        if (target.exists() && target.length() > 0) return target
        val url = resolveAudioUrl(trackId, quality)
        val request = OkHttpRequest.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val tmp = java.io.File(dir, downloadName(trackId) + ".tmp")
            runCatching {
                val body = resp.body ?: throw IOException("Пустой ответ")
                val total = body.contentLength()
                onProgress(0L, total)
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        var reported = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            received += read
                            // Throttle UI updates: report at most every 256 KiB.
                            if (received - reported >= 256 * 1024) {
                                reported = received
                                onProgress(received, total)
                            }
                        }
                        onProgress(received, if (total > 0) total else received)
                    }
                }
            }.onFailure { tmp.delete(); throw IOException("Не удалось сохранить файл") }""",
    'downloadTrack + прогресс',
)

# ---------------------------------------------------------------------------
print('3/9 MusicRepository: флоу + downloadTrackTracked')
replace(
    'android/app/src/main/java/app/votify/mobile/data/MusicRepository.kt',
    """import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first""",
    """import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first""",
    'import StateFlow',
)
replace(
    'android/app/src/main/java/app/votify/mobile/data/MusicRepository.kt',
    """    // ---- offline downloads ----

    fun isDownloaded(trackId: String): Boolean =
        !isServerMode && EmbeddedMusicSource.isDownloaded(trackId)

    fun downloadStats(): Pair<Int, Long> =
        if (isServerMode) 0 to 0L else EmbeddedMusicSource.downloadStats()

    fun deleteDownload(trackId: String) {
        if (!isServerMode) EmbeddedMusicSource.deleteDownload(trackId)
    }

    /** Blocking download of one track (true = stored / already present). */
    fun downloadTrackSync(trackId: String): Boolean =
        runCatching { !isServerMode && EmbeddedMusicSource.downloadTrack(trackId, quality).length() > 0 }
            .getOrDefault(false)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}""",
    """    // ---- offline downloads ----

    fun isDownloaded(trackId: String): Boolean =
        !isServerMode && EmbeddedMusicSource.isDownloaded(trackId)

    /** Live download progress (trackId → progress) for the UI progress bars. */
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = DownloadTracker.progress

    /** Ids with a stored offline copy, as observed by the app so far. */
    val downloadedIds: StateFlow<Set<String>> = DownloadTracker.downloaded

    fun downloadStats(): Pair<Int, Long> =
        if (isServerMode) 0 to 0L else EmbeddedMusicSource.downloadStats()

    fun deleteDownload(trackId: String) {
        if (!isServerMode) {
            EmbeddedMusicSource.deleteDownload(trackId)
            DownloadTracker.removeDownloaded(trackId)
        }
    }

    /** Blocking download of one track (true = stored / already present). */
    fun downloadTrackSync(trackId: String): Boolean =
        runCatching { !isServerMode && EmbeddedMusicSource.downloadTrack(trackId, quality).length() > 0 }
            .getOrDefault(false)

    /**
     * Blocking download with live progress reporting into [DownloadTracker] (and via
     * [onProgress] for callers that want the raw numbers). Safe on any thread.
     */
    fun downloadTrackTracked(trackId: String, onProgress: (Long, Long) -> Unit = { _, _ -> }): Boolean =
        runCatching {
            if (isServerMode) return@runCatching false
            DownloadTracker.start(trackId)
            val ok = EmbeddedMusicSource.downloadTrack(trackId, quality) { received, total ->
                DownloadTracker.update(trackId, received, total)
                onProgress(received, total)
            }.length() > 0
            DownloadTracker.finish(trackId, ok)
            ok
        }.getOrElse {
            DownloadTracker.finish(trackId, false)
            false
        }

    /** Mark ids as downloaded after scanning the offline storage. */
    fun seedDownloaded(ids: Set<String>) = DownloadTracker.seedDownloaded(ids)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}""",
    'флоу + tracked download',
)

# ---------------------------------------------------------------------------
print('4/9 CustomPrefs: bgDim / bgBlur')
replace(
    'android/app/src/main/java/app/votify/mobile/data/CustomPrefs.kt',
    """    // ---- Библиотека (фоны) ----
    val backgrounds: List<String> = emptyList(),""",
    """    // ---- Библиотека (фоны) ----
    val backgrounds: List<String> = emptyList(),
    val bgDim: Int = 35, // затемнение фоновой картинки, % (0..92)
    val bgBlur: Int = 0, // размытие фоновой картинки, dp (0..60)""",
    'bgDim/bgBlur',
)

# ---------------------------------------------------------------------------
print('5/9 LibraryViewModel: флоу, init-скан, tracked скачивание')
LVM = 'android/app/src/main/java/app/votify/mobile/ui/library/LibraryViewModel.kt'
replace(
    LVM,
    """import app.votify.mobile.data.LibraryRepository""",
    """import app.votify.mobile.data.DownloadProgress
import app.votify.mobile.data.LibraryRepository""",
    'import DownloadProgress',
)
replace(
    LVM,
    """    val playlists: StateFlow<List<PlaylistSummary>> = stream(library.playlists, emptyList())

    // ---- Playlist detail ----""",
    """    val playlists: StateFlow<List<PlaylistSummary>> = stream(library.playlists, emptyList())

    // ---- Offline downloads ----

    /** Live download progress (trackId → progress) for per-row progress bars. */
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = music.downloadProgress

    /** Ids with an offline copy — drives the «скачано» badge on track rows. */
    val downloadedIds: StateFlow<Set<String>> = music.downloadedIds

    init {
        // Seed the «скачано» badges: scan the offline storage for everything the
        // library currently knows about (favorites + history + all playlists).
        viewModelScope.launch(Dispatchers.IO) {
            if (!music.isServerMode) {
                runCatching {
                    val ids = buildSet {
                        library.favorites.first().let { addAll(it.map { t -> t.id }) }
                        library.recent(limit = 300).first().let { addAll(it.map { t -> t.id }) }
                        library.playlists.first().forEach { p ->
                            library.playlistTracks(p.id).first().let { addAll(it.map { t -> t.id }) }
                        }
                    }
                    music.seedDownloaded(ids.filter { music.isDownloaded(it) }.toSet())
                }
            }
        }
    }

    // ---- Playlist detail ----""",
    'флоу + init',
)
replace(LVM, '/** Download one track for offline listening (menu action). */',
        '/** Download one track for offline listening (menu action), reporting live progress. */',
        'комментарий')
replace(LVM, '            val ok = music.downloadTrackSync(track.id)',
        '            val ok = music.downloadTrackTracked(track.id)', 'tracked одиночное')
replace(LVM, '                if (music.downloadTrackSync(t.id)) ok++',
        '                if (music.downloadTrackTracked(t.id)) ok++', 'tracked плейлист')

# ---------------------------------------------------------------------------
print('6/9 TrackRow: плашка «скачано» + полоса прогресса')
COMMON = 'android/app/src/main/java/app/votify/mobile/ui/components/Common.kt'
replace(COMMON,
    'import androidx.compose.material.icons.outlined.MoreVert',
    'import androidx.compose.material.icons.outlined.DownloadDone\nimport androidx.compose.material.icons.outlined.MoreVert',
    'import DownloadDone')
replace(COMMON,
    'import app.votify.mobile.R',
    'import androidx.compose.ui.res.stringResource\nimport app.votify.mobile.R',
    'import stringResource')
replace(COMMON,
    """fun TrackRow(
    track: Track,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {""",
    """fun TrackRow(
    track: Track,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    downloadFraction: Float? = null,
) {""",
    'сигнатура')
replace(COMMON,
    """    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {""",
    """    Column(modifier.fillMaxWidth()) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {""",
    'Column-обёртка')
replace(COMMON,
    """        Box(contentAlignment = Alignment.Center) {
            Artwork(track.cover, size = 44.dp)
            if (isCurrent) {""",
    """        Box(contentAlignment = Alignment.Center) {
            Artwork(track.cover, size = 44.dp)
            if (downloaded && downloadFraction == null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DownloadDone,
                        contentDescription = stringResource(R.string.track_badge_downloaded),
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            if (isCurrent) {""",
    'бейдж на обложке')
replace(COMMON,
    """            Text(
                buildString {
                    append(track.artist)
                    if (track.duration > 0) append(" • ").append(formatDuration(track.duration * 1000L))
                },""",
    """            Text(
                buildString {
                    append(track.artist)
                    if (track.duration > 0) append(" • ").append(formatDuration(track.duration * 1000L))
                    if (downloadFraction != null) append(" • ").append((downloadFraction * 100).toInt()).append('%')
                },""",
    'проценты в подзаголовке')
replace(COMMON,
    """        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, null, tint = VotifyColors.TextMuted)
            }
        }
    }
}

/** Pill filter chip: white fill when selected, #1E1E1E + hairline border otherwise. */""",
    """        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, null, tint = VotifyColors.TextMuted)
            }
        }
    }
    if (downloadFraction != null) {
        // Thin hand-rolled progress bar (no deprecated progress APIs).
        Box(
            Modifier
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(VotifyColors.SurfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(downloadFraction.coerceIn(0.02f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VotifyColors.Primary),
            )
        }
    }
}
}

/** Pill filter chip: white fill when selected, #1E1E1E + hairline border otherwise. */""",
    'полоса прогресса')

# ---------------------------------------------------------------------------
print('7/9 Списки: Library / TrackList (Favorites, History, Playlist)')
TLS = 'android/app/src/main/java/app/votify/mobile/ui/library/TrackListScreen.kt'
replace(TLS,
    'import app.votify.mobile.data.Track',
    'import app.votify.mobile.data.DownloadProgress\nimport app.votify.mobile.data.Track',
    'import DownloadProgress')
replace(TLS,
    """private fun TrackList(
    tracks: List<Track>,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    emptyText: String,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    header: @Composable () -> Unit,
) {""",
    """private fun TrackList(
    tracks: List<Track>,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    emptyText: String,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    header: @Composable () -> Unit,
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, DownloadProgress> = emptyMap(),
) {""",
    'параметры TrackList')
replace(TLS,
    """        itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
            TrackRow(
                track = t,
                isCurrent = t.id == currentTrackId,
                onClick = { onPlay(tracks, i) },
                onMore = { onMore(t) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }""",
    """        itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
            TrackRow(
                track = t,
                isCurrent = t.id == currentTrackId,
                onClick = { onPlay(tracks, i) },
                onMore = { onMore(t) },
                modifier = Modifier.padding(horizontal = 4.dp),
                downloaded = t.id in downloadedIds,
                downloadFraction = downloadProgress[t.id]?.fraction,
            )
        }""",
    'строка списка')
replace(TLS,
    """    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    TrackList(
        tracks = favorites,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_favorites_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
    ) {""",
    """    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    TrackList(
        tracks = favorites,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_favorites_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
        downloadedIds = downloadedIds,
        downloadProgress = downloadProgress,
        header = {""",
    'Favorites wiring')
replace(TLS,
    """    val recent by viewModel.recent.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    TrackList(
        tracks = recent,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_history_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
    ) {""",
    """    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    TrackList(
        tracks = recent,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_history_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it) },
        downloadedIds = downloadedIds,
        downloadProgress = downloadProgress,
        header = {""",
    'History wiring')
replace(TLS,
    """    val playlist by viewModel.openedPlaylist.collectAsStateWithLifecycle()
    val tracks by viewModel.openedPlaylistTracks.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val name = playlist?.name ?: ""

    TrackList(
        tracks = tracks,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_playlist_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it, playlistId = playlistId) },
    ) {""",
    """    val playlist by viewModel.openedPlaylist.collectAsStateWithLifecycle()
    val tracks by viewModel.openedPlaylistTracks.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val name = playlist?.name ?: ""

    TrackList(
        tracks = tracks,
        currentTrackId = currentTrackId,
        contentPadding = contentPadding,
        emptyText = stringResource(R.string.library_playlist_empty),
        onPlay = onPlay,
        onMore = { viewModel.openMenu(it, playlistId = playlistId) },
        downloadedIds = downloadedIds,
        downloadProgress = downloadProgress,
        header = {""",
    'Playlist wiring')

inline_trailing_header(TLS)

LS = 'android/app/src/main/java/app/votify/mobile/ui/library/LibraryScreen.kt'
replace(LS,
    """    val recent by viewModel.recent.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }""",
    """    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }""",
    'state wiring')
replace(LS,
    """                val shown = if (filter == LibraryFilter.History) recent else recent.take(6)
                items(shown, key = { it.id }) { t ->
                    TrackRow(
                        track = t,
                        isCurrent = t.id == currentTrackId,
                        onClick = { onPlay(shown, shown.indexOf(t)) },
                        onMore = { viewModel.openMenu(t) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }""",
    """                val shown = if (filter == LibraryFilter.History) recent else recent.take(6)
                items(shown, key = { it.id }) { t ->
                    TrackRow(
                        track = t,
                        isCurrent = t.id == currentTrackId,
                        onClick = { onPlay(shown, shown.indexOf(t)) },
                        onMore = { viewModel.openMenu(t) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        downloaded = t.id in downloadedIds,
                        downloadFraction = downloadProgress[t.id]?.fraction,
                    )
                }""",
    'строки истории')


# ---------------------------------------------------------------------------
print('8/9 Настройки фона: VM + экран «Фон» + VotifyRoot')
replace(
    'android/app/src/main/java/app/votify/mobile/ui/settings/SettingsViewModel.kt',
    """    fun removeBackground(url: String) = updatePrefs { cur -> cur.copy(backgrounds = cur.backgrounds - url) }""",
    """    fun removeBackground(url: String) = updatePrefs { cur -> cur.copy(backgrounds = cur.backgrounds - url) }

    /** Затемнение фоновой картинки, % (0..92). */
    fun setBackgroundDim(v: Int) = updatePrefs { cur -> cur.copy(bgDim = v.coerceIn(0, 92)) }

    /** Размытие фоновой картинки, dp (0..60). */
    fun setBackgroundBlur(v: Int) = updatePrefs { cur -> cur.copy(bgBlur = v.coerceIn(0, 60)) }""",
    'VM-сеттеры')

BG = 'android/app/src/main/java/app/votify/mobile/ui/settings/BackgroundsScreen.kt'
replace(BG,
    'import androidx.compose.material3.Surface',
    'import androidx.compose.material3.Slider\nimport androidx.compose.material3.Surface',
    'import Slider')
replace(BG,
    'import androidx.compose.runtime.mutableStateOf',
    'import androidx.compose.runtime.mutableFloatStateOf\nimport androidx.compose.runtime.mutableStateOf',
    'import mutableFloatStateOf')
replace(BG,
    """            // Bottom fixed buttons per the spec
            Surface(
                onClick = { urlDialog = true },""",
    """            // ---- Настройки фона: затемнение / размытие ----
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_bg_tune),
                style = MaterialTheme.typography.titleSmall,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            BgTuneSlider(
                label = stringResource(R.string.settings_bg_dim),
                value = prefs.bgDim,
                range = 0f..92f,
                suffix = '%',
                onApply = { viewModel.setBackgroundDim(it) },
            )
            BgTuneSlider(
                label = stringResource(R.string.settings_bg_blur),
                value = prefs.bgBlur,
                range = 0f..60f,
                suffix = 'd',
                onApply = { viewModel.setBackgroundBlur(it) },
            )
            Text(
                stringResource(R.string.settings_bg_tune_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            // Bottom fixed buttons per the spec
            Surface(
                onClick = { urlDialog = true },""",
    'секция настроек фона')
_bg_tail = read(BG).rstrip()
if 'private fun BgTuneSlider' not in _bg_tail:
    write(BG, _bg_tail + '''

/** Label + value row and a slider that commits on release (same feel as the workshop sliders). */
@Composable
private fun BgTuneSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    suffix: Char,
    onApply: (Int) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.coerceIn(range.start.toInt(), range.endInclusive.toInt()).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = VotifyColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                current.toInt().toString() + if (suffix == '%') "%" else " dp",
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
            )
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onApply(current.toInt()) },
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = VotifyColors.Primary,
                activeTrackColor = VotifyColors.Primary,
                inactiveTrackColor = VotifyColors.SurfaceContainerHigh,
            ),
        )
    }
}
''')
    print('  + BackgroundsScreen: BgTuneSlider добавлен')
else:
    print('  = BackgroundsScreen: BgTuneSlider уже есть')

replace(
    'android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt',
    """    // Fine-tuning from the theme details screen: blur (dp) + extra dim over the background.
    val bgBlurDp = bgSpec.backgroundBlur.coerceIn(0, 60)
    val bgDimExtra = ((100 - bgSpec.uiTransparency).coerceIn(0, 90)) / 100f""",
    """    // Fine-tuning: Настройки → Фон («затемнение»/«размытие», prefs.bgDim/bgBlur).
    // Defaults (dim 35, blur 0) match the previous hard-coded look; the workshop
    // spec sliders were the only tuning before and are now overridden here.
    val bgPrefs = parseCustomPrefs(settings.customPrefs)
    val bgBlurDp = bgPrefs.bgBlur.coerceIn(0, 60)
    val bgDimAlpha = bgPrefs.bgDim.coerceIn(0, 92) / 100f""",
    'VotifyRoot: источник настроек')
replace(
    'android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt',
    """            // Dim the artwork so text on cards stays readable (0.35 base + the slider's extra).
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = (0.35f + bgDimExtra * 0.6f).coerceAtMost(0.92f)),
                ),
            )""",
    """            // Dim the background so text on cards stays readable (Настройки → Фон → Затемнение).
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = bgDimAlpha),
                ),
            )""",
    'VotifyRoot: dim по prefs')

# ---------------------------------------------------------------------------
print('9/9 strings.xml + CI-триггеры')
replace(
    'android/app/src/main/res/values/strings.xml',
    """    <string name="toast_download_server">Офлайн-скачивание доступно в автономном режиме (без сервера)</string>""",
    """    <string name="toast_download_server">Офлайн-скачивание доступно в автономном режиме (без сервера)</string>
    <string name="track_badge_downloaded">Скачано</string>
    <string name="settings_bg_tune">Настройки фона</string>
    <string name="settings_bg_dim">Затемнение фона</string>
    <string name="settings_bg_blur">Размытие фона</string>
    <string name="settings_bg_tune_hint">Применяется к фоновой картинке сразу. Затемнение помогает тексту оставаться читаемым на ярких обоях.</string>""",
    'строки')

for wf in ('.github/workflows/release-apk.yml', '.github/workflows/android.yml'):
    if not os.path.exists(P(wf)):
        print(f'  ! {wf} не найден — пропускаю')
        continue
    s = read(wf)
    s2 = s.replace("branches: ['arena/01a0816e-votify']", "branches: ['android', 'main']")
    s2 = s2.replace("branches: ['arena/01a07e3f-votify', 'arena/01a0816e-votify']", "branches: ['android', 'main']")
    if s2 != s:
        write(wf, s2)
        print(f'  + {wf}: триггеры → android/main')
    else:
        print(f'  = {wf}: триггеры уже в порядке')

print()
print('ГОТОВО. Проверка сборки: cd android && ./gradlew assembleRelease (или CI соберёт сам).')
