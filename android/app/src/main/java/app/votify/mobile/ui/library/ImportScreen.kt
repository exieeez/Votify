package app.votify.mobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.theme.VotifyColors

/** How many imported tracks to preview before the "+N more" line. */
private const val PREVIEW_LIMIT = 30

/**
 * «Импорт плейлиста»: paste a playlist URL (YouTube / Spotify / SoundCloud), the server
 * resolves the tracks, then the list is saved as a local playlist.
 */
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary)
            }
            Text(
                stringResource(R.string.import_title),
                style = MaterialTheme.typography.titleLarge,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // URL form
        VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column {
                VotifyTextField(state.url, viewModel::onUrlChange, stringResource(R.string.import_url_hint))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.import_supported),
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                ImportButton(
                    text = stringResource(R.string.import_button),
                    loading = state.loading,
                    enabled = state.url.isNotBlank(),
                    onClick = viewModel::import,
                )
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.Error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.loading) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.import_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Result: editable name + preview + "add to library"
        if (state.tracks.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.import_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = VotifyColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(pluralTracks(state.tracks.size), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
            }

            VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Column {
                    VotifyTextField(state.name, viewModel::onNameChange, stringResource(R.string.import_name))
                    Spacer(Modifier.height(8.dp))
                    state.tracks.take(PREVIEW_LIMIT).forEachIndexed { index, track ->
                        TrackRow(track = track, onClick = {})
                        if (index != state.tracks.take(PREVIEW_LIMIT).lastIndex) {
                            HorizontalDivider(color = VotifyColors.BorderSubtle, thickness = 1.dp)
                        }
                    }
                    if (state.tracks.size > PREVIEW_LIMIT) {
                        Text(
                            stringResource(R.string.import_more, state.tracks.size - PREVIEW_LIMIT),
                            style = MaterialTheme.typography.bodySmall,
                            color = VotifyColors.TextMuted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ImportButton(
                        text = stringResource(R.string.import_add),
                        loading = state.saving,
                        enabled = true,
                        onClick = viewModel::saveToLibrary,
                    )
                }
            }
        }
    }
}

/** Full-width pill CTA: spinner replaces the label while working. */
@Composable
private fun ImportButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        color = if (enabled) VotifyColors.Primary else VotifyColors.SurfaceContainerHigh,
        contentColor = if (enabled) VotifyColors.OnPrimary else VotifyColors.TextMuted,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = if (enabled) VotifyColors.OnPrimary else VotifyColors.TextMuted,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
