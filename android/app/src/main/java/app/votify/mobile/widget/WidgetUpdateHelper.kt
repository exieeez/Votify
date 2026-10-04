package app.votify.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import app.votify.mobile.MainActivity
import app.votify.mobile.R
import app.votify.mobile.VotifyApp
import coil.Coil
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetUpdateHelper {

    fun updateAll(context: Context) {
        val app = runCatching { VotifyApp.instance }.getOrNull() ?: return
        app.appScope.launch {
            updatePlayerWidgets(context)
            updateCompactWidgets(context)
            updatePlaylistWidgets(context)
            updateRemixWidgets(context)
        }
    }

    private suspend fun updatePlayerWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, PlayerWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val app = VotifyApp.instance
        val state = app.player.state.value
        val track = state.current
        val isPlaying = state.isPlaying
        val isFavorite = track?.let { app.library.isFavorite(it.id).first() } ?: false

        val badgeText = when {
            state.reverbPreset != "none" && state.speed < 1f -> "Slowed • Зал"
            state.pitchLinked && state.speed > 1.1f -> "Nightcore"
            Math.abs(state.speed - 1.0f) > 0.03f -> String.format(java.util.Locale.US, "%.2f×", state.speed)
            else -> "Votify"
        }

        // Load cover art
        val coverBitmap = loadCoverBitmap(context, track?.cover, sizePx = 256, radiusPx = 32f)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_player_3x2)

            // Track info
            views.setTextViewText(R.id.widget_player_title, track?.title ?: "Не воспроизводится")
            views.setTextViewText(R.id.widget_player_artist, track?.artist ?: "Votify Music")
            views.setTextViewText(R.id.widget_player_badge, badgeText)

            // Cover
            if (coverBitmap != null) {
                views.setImageViewBitmap(R.id.widget_player_cover, coverBitmap)
            } else {
                views.setImageViewResource(R.id.widget_player_cover, R.drawable.ic_votify_logo)
            }

            // Play/Pause button
            views.setImageViewResource(
                R.id.widget_player_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            // Favorite button
            views.setImageViewResource(
                R.id.widget_player_btn_favorite,
                if (isFavorite) R.drawable.ic_widget_favorite_filled else R.drawable.ic_widget_favorite_outline
            )

            // Intents
            views.setOnClickPendingIntent(R.id.widget_player_root, getOpenAppIntent(context))
            views.setOnClickPendingIntent(R.id.widget_player_btn_prev, getBroadcastIntent(context, WidgetActions.ACTION_PREV, 101))
            views.setOnClickPendingIntent(R.id.widget_player_btn_play_pause, getBroadcastIntent(context, WidgetActions.ACTION_PLAY_PAUSE, 102))
            views.setOnClickPendingIntent(R.id.widget_player_btn_next, getBroadcastIntent(context, WidgetActions.ACTION_NEXT, 103))
            views.setOnClickPendingIntent(R.id.widget_player_btn_favorite, getBroadcastIntent(context, WidgetActions.ACTION_TOGGLE_FAVORITE, 104))

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private suspend fun updateCompactWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CompactPlayerWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val app = VotifyApp.instance
        val state = app.player.state.value
        val track = state.current
        val isPlaying = state.isPlaying

        val coverBitmap = loadCoverBitmap(context, track?.cover, sizePx = 160, radiusPx = 80f)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_player_compact_4x1)

            views.setTextViewText(R.id.widget_compact_title, track?.title ?: "Не воспроизводится")
            views.setTextViewText(R.id.widget_compact_artist, track?.artist ?: "Votify")

            if (coverBitmap != null) {
                views.setImageViewBitmap(R.id.widget_compact_cover, coverBitmap)
            } else {
                views.setImageViewResource(R.id.widget_compact_cover, R.drawable.ic_votify_logo)
            }

            views.setImageViewResource(
                R.id.widget_compact_btn_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            views.setOnClickPendingIntent(R.id.widget_compact_root, getOpenAppIntent(context))
            views.setOnClickPendingIntent(R.id.widget_compact_btn_prev, getBroadcastIntent(context, WidgetActions.ACTION_PREV, 201))
            views.setOnClickPendingIntent(R.id.widget_compact_btn_play_pause, getBroadcastIntent(context, WidgetActions.ACTION_PLAY_PAUSE, 202))
            views.setOnClickPendingIntent(R.id.widget_compact_btn_next, getBroadcastIntent(context, WidgetActions.ACTION_NEXT, 203))

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun updatePlaylistWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, PlaylistWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val app = VotifyApp.instance
        val queue = app.player.state.value.queue

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_playlist_2x2)

            // Setup RemoteViewsService adapter
            val serviceIntent = Intent(context, PlaylistRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_playlist_list, serviceIntent)
            views.setEmptyView(R.id.widget_playlist_list, R.id.widget_playlist_empty)

            // Item click template
            val clickIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActions.ACTION_PLAY_INDEX
            }
            val clickPi = PendingIntent.getBroadcast(
                context,
                301,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_playlist_list, clickPi)

            // Shuffle button
            views.setOnClickPendingIntent(
                R.id.widget_playlist_btn_shuffle,
                getBroadcastIntent(context, WidgetActions.ACTION_SHUFFLE, 302)
            )

            // Empty view visibility
            views.setViewVisibility(R.id.widget_playlist_empty, if (queue.isEmpty()) View.VISIBLE else View.GONE)

            appWidgetManager.updateAppWidget(id, views)
        }

        // Notify ListView data changed
        appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_playlist_list)
    }

    private fun updateRemixWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, RemixWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_remix_launcher_2x1)

            views.setOnClickPendingIntent(
                R.id.widget_btn_preset_slowed,
                getBroadcastIntentWithExtra(context, WidgetActions.ACTION_SET_REMIX, WidgetActions.EXTRA_REMIX_PRESET, "slowed", 401)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_preset_default,
                getBroadcastIntentWithExtra(context, WidgetActions.ACTION_SET_REMIX, WidgetActions.EXTRA_REMIX_PRESET, "default", 402)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_preset_speedup,
                getBroadcastIntentWithExtra(context, WidgetActions.ACTION_SET_REMIX, WidgetActions.EXTRA_REMIX_PRESET, "speedup", 403)
            )

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private suspend fun loadCoverBitmap(context: Context, coverUrl: String?, sizePx: Int, radiusPx: Float): Bitmap? {
        if (coverUrl.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(sizePx)
                    .transformations(RoundedCornersTransformation(radiusPx))
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                result.drawable?.toBitmap(sizePx, sizePx)
            }.getOrNull()
        }
    }

    private fun getOpenAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player", true)
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getBroadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getBroadcastIntentWithExtra(context: Context, action: String, extraKey: String, extraVal: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
            putExtra(extraKey, extraVal)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
