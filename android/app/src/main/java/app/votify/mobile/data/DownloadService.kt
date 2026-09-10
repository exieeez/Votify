package app.votify.mobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.votify.mobile.MainActivity
import app.votify.mobile.R
import app.votify.mobile.VotifyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Offline downloads run in a foreground service: the process survives the screen being off
 * and the notification shows the live percentage, so it is obvious what is happening and
 * how much is left.
 *
 * Progress maths:
 *  * one track — bytes received / Content-Length;
 *  * a batch — (tracks done + the current track's fraction) / batch size.
 *
 * When the server sends no Content-Length the bar is indeterminate and only the
 * «трек N из M» counter moves.
 */
class DownloadService : Service() {

    private data class QueuedTrack(val id: String, val title: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<QueuedTrack>()

    private var worker: Job? = null
    private val done = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    private var cancelled = false
    private var lastPercent = -1
    private var lastPush = 0L

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            stopWorker()
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        val ids = intent?.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        if (ids.isEmpty()) {
            if (worker?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        val titles = intent?.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        ids.forEachIndexed { i, id -> queue.add(QueuedTrack(id, titles.getOrElse(i) { "" })) }

        // A fresh batch restarts the counters; extra tracks queued mid-run extend the total
        // (it is recomputed from done + failed + queue size on every track).
        push(done.get(), done.get() + failed.get() + queue.size, titles.firstOrNull().orEmpty(), 0, known = false)
        if (worker?.isActive != true) {
            cancelled = false
            worker = scope.launch { drain() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelled = true
        stopWorker()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopWorker() {
        worker?.cancel()
        worker = null
    }

    private suspend fun drain() {
        val music = VotifyApp.instance.music
        while (!cancelled && queue.isNotEmpty()) {
            // Пачками по PARALLEL: разбор страницы и получение ссылки занимают секунды,
            // из-за последовательной загрузки плейлист мог уходить в часы.
            val batch = ArrayList<QueuedTrack>(PARALLEL)
            repeat(PARALLEL) { if (queue.isNotEmpty()) batch += queue.removeFirst() }
            val base = done.get() + failed.get()
            val total = base + batch.size + queue.size
            coroutineScope {
                batch.forEachIndexed { i, item ->
                    launch {
                        val index = base + i
                        push(index, total, item.title, 0, known = false)
                        val ok = music.downloadTrackTracked(item.id) { received, bytes ->
                            if (cancelled) return@downloadTrackTracked
                            val fraction = if (bytes > 0) (received.toDouble() / bytes.toDouble()).coerceIn(0.0, 1.0) else 0.0
                            val percent = (((index + fraction) / total) * 100).toInt().coerceIn(0, 100)
                            throttledPush(index, total, item.title, percent, known = bytes > 0)
                        }
                        if (ok) done.incrementAndGet() else failed.incrementAndGet()
                    }
                }
            }
        }
        finish()
    }

    /** Notification posts are rate-limited: percent changes only, at most ~3 per second. */
    @Synchronized
    private fun throttledPush(index: Int, total: Int, title: String, percent: Int, known: Boolean) {
        if (percent == lastPercent) return
        val now = SystemClock.elapsedRealtime()
        if (percent != 100 && percent != 0 && now - lastPush < 330) return
        lastPush = now
        lastPercent = percent
        push(index, total, title, percent, known)
    }

    @Synchronized
    private fun push(index: Int, total: Int, title: String, percent: Int, known: Boolean) {
        val text = when {
            total > 1 && known -> getString(R.string.notif_download_queue, index + 1, total, percent)
            total > 1 -> getString(R.string.notif_download_queue_preparing, index + 1, total)
            known -> getString(R.string.notif_download_track, title, percent)
            else -> getString(R.string.notif_download_preparing)
        }
        val notification = buildNotification(text, percent, indeterminate = !known, ongoing = true)
        runCatching { startForegroundCompat(notification) }
    }

    /** API 29+ wants the foreground-service type explicitly (dataSync is declared in the manifest). */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun finish() {
        val ok = done.get()
        val bad = failed.get()
        val total = ok + bad
        val text = when {
            cancelled -> getString(R.string.notif_download_cancelled)
            bad == 0 && total == 1 -> getString(R.string.notif_download_done_one)
            bad == 0 -> getString(R.string.notif_download_done, ok, total)
            else -> getString(R.string.notif_download_failed, ok, total, bad)
        }
        val notification = buildNotification(text, 100, indeterminate = false, ongoing = false)
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            // Leave the result in the shade, but stop being a foreground service.
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        done.set(0)
        failed.set(0)
        lastPercent = -1
        cancelled = false
        stopSelf()
    }

    private fun buildNotification(text: String, percent: Int, indeterminate: Boolean, ongoing: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_votify_logo)
            .setContentTitle(getString(R.string.notif_download_title))
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(NotificationCompat.Action.Builder(0, getString(R.string.notif_download_cancel), cancel).build())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_downloads_desc)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {

        private const val ACTION_CANCEL = "app.votify.mobile.action.CANCEL_DOWNLOADS"
        private const val EXTRA_IDS = "ids"
        private const val EXTRA_TITLES = "titles"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4201
        /** Сколько треков качаем одновременно (плейлист перестал уходить в часы). */
        private const val PARALLEL = 3

        /**
         * Queue [ids] for offline download. [titles] is optional and only used for the
         * notification text (positional match with [ids]).
         */
        fun start(context: Context, ids: List<String>, titles: List<String> = emptyList()) {
            if (ids.isEmpty()) return
            val intent = Intent(context, DownloadService::class.java)
                .putStringArrayListExtra(EXTRA_IDS, ArrayList(ids))
                .putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { startBestEffort(context, intent) }
        }

        /** Last resort: no foreground service allowed right now — try a plain start. */
        private fun startBestEffort(context: Context, intent: Intent) {
            runCatching { context.startService(intent) }
        }
    }
}
