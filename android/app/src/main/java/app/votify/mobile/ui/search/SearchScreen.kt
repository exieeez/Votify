package app.votify.mobile.ui.search

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.SectionHeader
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.theme.VotifyColors

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            Text(
                stringResource(R.string.nav_search),
                style = MaterialTheme.typography.headlineMedium,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item {
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSubmit = { keyboard?.hide(); viewModel.submit() },
                onClear = viewModel::clear,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (state.recent.isNotEmpty() && state.results.isEmpty() && !state.isLoading) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(VotifyColors.Primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.History, null, tint = VotifyColors.Primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Недавние запросы",
                            style = MaterialTheme.typography.titleSmall,
                            color = VotifyColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::clearRecent) {
                            Text(stringResource(R.string.search_clear), color = VotifyColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.recent) { q ->
                            PillChip(
                                text = q,
                                selected = false,
                                onClick = { viewModel.pickRecent(q) },
                                leading = { Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp)) },
                            )
                        }
                    }
                }
            }
        }

        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VotifyColors.Primary, strokeWidth = 2.dp)
                }
            }

            state.error != null || state.offline -> item {
                when {
                    state.offline && state.serverMode -> StatusBlock(
                        icon = Icons.Default.CloudOff,
                        title = stringResource(R.string.search_offline_title),
                        subtitle = stringResource(R.string.search_offline_sub),
                        action = stringResource(R.string.search_open_settings),
                        onAction = onOpenSettings,
                        secondaryAction = stringResource(R.string.search_retry),
                        onSecondaryAction = viewModel::retry,
                    )
                    state.offline -> StatusBlock(
                        icon = Icons.Default.CloudOff,
                        title = stringResource(R.string.search_no_internet_title),
                        subtitle = stringResource(R.string.search_no_internet_sub),
                        action = stringResource(R.string.search_retry),
                        onAction = viewModel::retry,
                    )
                    else -> StatusBlock(
                        icon = Icons.Default.ErrorOutline,
                        title = stringResource(R.string.search_error),
                        subtitle = state.error,
                        action = stringResource(R.string.search_retry),
                        onAction = viewModel::retry,
                    )
                }
            }

            state.searched && state.results.isEmpty() -> item {
                StatusBlock(icon = Icons.Default.SearchOff, title = stringResource(R.string.search_empty), subtitle = "«${state.query}»")
            }

            !state.searched -> item {
                StatusBlock(icon = Icons.Default.Search, title = stringResource(R.string.search_start))
            }

            else -> {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.search_results),
                            action = pluralTracks(state.results.size),
                        )
                    }
                }
                item {
                    VotifyCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Column {
                            state.results.forEachIndexed { index, track ->
                                TrackRow(
                                    track = track,
                                    isCurrent = track.id == currentTrackId,
                                    onClick = { onPlay(state.results, index) },
                                    onMore = { onMore(track) },
                                )
                                if (index != state.results.lastIndex) {
                                    HorizontalDivider(color = VotifyColors.BorderSubtle, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 56dp pill search field: glows with an accent border + shadow when focused. */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || query.isNotEmpty()
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        color = VotifyColors.SurfaceContainer,
        border = BorderStroke(1.dp, if (active) VotifyColors.Primary else VotifyColors.BorderSubtle),
        shadowElevation = if (focused) 12.dp else 0.dp,
    ) {
    Row(
        Modifier.padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = if (active) VotifyColors.Primary else VotifyColors.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_hint), color = VotifyColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = VotifyColors.TextPrimary),
                cursorBrush = SolidColor(VotifyColors.TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, stringResource(R.string.search_clear), tint = VotifyColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
    }
}

@Composable
private fun StatusBlock(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(VotifyColors.Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = VotifyColors.Primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = VotifyColors.TextSecondary, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            if (secondaryAction != null && onSecondaryAction != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip(text = action, selected = true, onClick = onAction)
                    PillChip(text = secondaryAction, selected = false, onClick = onSecondaryAction)
                }
            } else {
                PillChip(text = action, selected = true, onClick = onAction)
            }
        }
    }
}
