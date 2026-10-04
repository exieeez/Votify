package app.votify.mobile.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import app.votify.mobile.R
import app.votify.mobile.VotifyApp
import app.votify.mobile.data.Track
import coil.Coil
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PlaylistRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return PlaylistRemoteViewsFactory(applicationContext)
    }
}

class PlaylistRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var tracks: List<Track> = emptyList()
    private var currentPlayingId: String? = null

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val app = runCatching { VotifyApp.instance }.getOrNull() ?: return
        val playerState = app.player.state.value
        currentPlayingId = playerState.current?.id

        var list = playerState.queue
        if (list.isEmpty()) {
            // Fallback to favorite tracks if queue is not active
            list = runCatching {
                runBlocking { app.library.favorites.first() }
            }.getOrDefault(emptyList())
        }
        tracks = list
    }

    override fun onDestroy() {
        tracks = emptyList()
    }

    override fun getCount(): Int = tracks.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playlist_item)
        if (position !in tracks.indices) return views

        val track = tracks[position]
        views.setTextViewText(R.id.widget_item_title, track.title)
        views.setTextViewText(R.id.widget_item_artist, track.artist)

        val isCurrent = track.id == currentPlayingId
        views.setViewVisibility(
            R.id.widget_item_playing,
            if (isCurrent) View.VISIBLE else View.GONE
        )

        // Load small thumbnail
        val cover = loadSmallCover(track.cover)
        if (cover != null) {
            views.setImageViewBitmap(R.id.widget_item_cover, cover)
        } else {
            views.setImageViewResource(R.id.widget_item_cover, R.drawable.ic_votify_logo)
        }

        // FillInIntent with track position
        val fillInIntent = Intent().apply {
            putExtra(WidgetActions.EXTRA_INDEX, position)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun loadSmallCover(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(96)
                .transformations(RoundedCornersTransformation(16f))
                .allowHardware(false)
                .build()
            val drawable = Coil.imageLoader(context).execute(request).drawable
            drawable?.toBitmap(96, 96)
        }.getOrNull()
    }
}
