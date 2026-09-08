package app.votify.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.votify.mobile.R
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.theme.VotifyColors

/**
 * Library skeleton per the design: "Любимые" hero card, "Импортировать из сервиса" row and
 * filter chips. Favourites / playlists storage lands in the next iteration.
 */
@Composable
fun LibraryScreen(contentPadding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            color = VotifyColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Hero: Favourites
        VotifyCard(Modifier.fillMaxWidth(), onClick = {}, contentPadding = PaddingValues(20.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(listOf(VotifyColors.SurfaceContainerHigh.copy(alpha = 0.6f), VotifyColors.SurfaceContainer)),
                    ),
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VotifyColors.SurfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Favorite, null, tint = VotifyColors.TextPrimary) }

                CircleIconButton(onClick = {}, size = 48.dp, filled = true, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.PlayArrow, stringResource(R.string.player_play), modifier = Modifier.size(26.dp))
                }

                Column(Modifier.align(Alignment.BottomStart)) {
                    Text(stringResource(R.string.library_favorites), style = MaterialTheme.typography.headlineMedium, color = VotifyColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_tracks_count, 0), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Import row
        VotifyCard(Modifier.fillMaxWidth(), onClick = {}, contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VotifyColors.SurfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Download, null, tint = VotifyColors.TextPrimary) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.library_import), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.library_import_subtitle), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = VotifyColors.TextMuted)
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chips = listOf("Все", "Плейлисты", "Артисты", "Скачанные")
            items(chips.size) { i -> PillChip(text = chips[i], selected = i == 0, onClick = {}) }
        }

        Spacer(Modifier.height(48.dp))

        Text(
            stringResource(R.string.library_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = VotifyColors.TextMuted,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
        )
    }
}
