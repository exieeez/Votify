package app.votify.mobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.SectionHeader
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.components.VotifyCard
import app.votify.mobile.ui.components.VotifyHaptics
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
                        title = stringResource(R.string.search_offline_title),
                        subtitle = stringResource(R.string.search_offline_sub),
                        action = stringResource(R.string.search_open_settings),
                        onAction = onOpenSettings,
                        secondaryAction = stringResource(R.string.search_retry),
                        onSecondaryAction = viewModel::retry,
                    )
                    state.offline -> StatusBlock(
                        title = stringResource(R.string.search_no_internet_title),
                        subtitle = stringResource(R.string.search_no_internet_sub),
                        action = stringResource(R.string.search_retry),
                        onAction = viewModel::retry,
                    )
                    else -> StatusBlock(
                        title = stringResource(R.string.search_error),
                        subtitle = state.error,
                        action = stringResource(R.string.search_retry),
                        onAction = viewModel::retry,
                    )
                }
            }

            state.searched && state.results.isEmpty() -> item {
                StatusBlock(
                    title = stringResource(R.string.search_empty),
                    subtitle = "«${state.query}» — " + stringResource(R.string.search_empty_hint),
                )
            }

            !state.searched -> {
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Быстрый выбор",
                            style = MaterialTheme.typography.titleSmall,
                            color = VotifyColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(EXPLORE_QUICK_TAGS) { tag ->
                                PillChip(
                                    text = tag.label,
                                    selected = false,
                                    onClick = { viewModel.pickRecent(tag.query) },
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SectionHeader(
                            title = "Жанры и настроения",
                            action = null,
                        )
                    }
                }

                items(EXPLORE_CATEGORIES.chunked(2)) { rowCategories ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (category in rowCategories) {
                            SearchCategoryCard(
                                category = category,
                                onClick = { viewModel.pickRecent(category.query) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowCategories.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
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

/** 48dp pill search field: #1E1E1E fill, #2A2A2A border, brightens to white when focused. */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(VotifyColors.SurfaceContainer, CircleShape)
            .border(1.dp, if (focused) VotifyColors.TextPrimary else VotifyColors.BorderSubtle, CircleShape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(20.dp))
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

@Composable
private fun StatusBlock(
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

@Composable
private fun SearchCategoryCard(
    category: SearchCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = category.gradientColors,
                    start = Offset(0f, 0f),
                    end = Offset(320f, 320f),
                )
            )
            .border(1.dp, VotifyColors.BorderSubtle.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable {
                VotifyHaptics.click(view, context)
                onClick()
            }
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 36.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleSmall,
                color = VotifyColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextSecondary.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = VotifyColors.TextPrimary.copy(alpha = 0.22f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(38.dp),
        )
    }
}

private data class QuickSearchTag(
    val label: String,
    val query: String,
)

private data class SearchCategory(
    val title: String,
    val subtitle: String,
    val query: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
)

private val EXPLORE_QUICK_TAGS = listOf(
    QuickSearchTag("🔥 Тренды", "хиты тренды"),
    QuickSearchTag("🌌 Slowed", "slowed reverb"),
    QuickSearchTag("⚡ Phonk", "phonk"),
    QuickSearchTag("🏎️ Nightcore", "nightcore"),
    QuickSearchTag("🌙 Lo-Fi", "lo-fi chill"),
    QuickSearchTag("🎧 Рэп", "хип хоп рэп"),
    QuickSearchTag("🎸 Рок", "рок"),
    QuickSearchTag("✨ Новинки", "новинки музыки"),
    QuickSearchTag("💫 Synthwave", "synthwave"),
    QuickSearchTag("🌧️ Меланхолия", "грустные треки"),
    QuickSearchTag("🎯 Тренировка", "workout music"),
)

private val EXPLORE_CATEGORIES = listOf(
    SearchCategory(
        title = "Slowed + Reverb",
        subtitle = "Атмосферный чилл",
        query = "slowed reverb",
        icon = Icons.Outlined.AutoAwesome,
        gradientColors = listOf(Color(0xFF2B1C47), Color(0xFF130E20)),
    ),
    SearchCategory(
        title = "Phonk & Drift",
        subtitle = "Басс и агрессия",
        query = "phonk",
        icon = Icons.Outlined.Equalizer,
        gradientColors = listOf(Color(0xFF451920), Color(0xFF1E0C0F)),
    ),
    SearchCategory(
        title = "Nightcore",
        subtitle = "Ускоренный темп",
        query = "nightcore",
        icon = Icons.Outlined.Speed,
        gradientColors = listOf(Color(0xFF142F46), Color(0xFF0B1927)),
    ),
    SearchCategory(
        title = "Тренды & Хиты",
        subtitle = "Главное в чартах",
        query = "хиты тренды",
        icon = Icons.Filled.TrendingUp,
        gradientColors = listOf(Color(0xFF3F2B12), Color(0xFF1E1408)),
    ),
    SearchCategory(
        title = "Lo-Fi & Ночь",
        subtitle = "Фокус и чилл",
        query = "lo-fi chill",
        icon = Icons.Outlined.DarkMode,
        gradientColors = listOf(Color(0xFF132F24), Color(0xFF0A1A14)),
    ),
    SearchCategory(
        title = "Хип-хоп & Рэп",
        subtitle = "Биты и рифмы",
        query = "хип хоп рэп",
        icon = Icons.Outlined.Headphones,
        gradientColors = listOf(Color(0xFF301B42), Color(0xFF160C1E)),
    ),
    SearchCategory(
        title = "Рок & Драйв",
        subtitle = "Гитары и энергия",
        query = "рок альтернатива",
        icon = Icons.Outlined.Album,
        gradientColors = listOf(Color(0xFF3A1919), Color(0xFF1C0C0C)),
    ),
    SearchCategory(
        title = "Электроника",
        subtitle = "Synthwave & EDM",
        query = "synthwave electronic",
        icon = Icons.Outlined.Speaker,
        gradientColors = listOf(Color(0xFF122240), Color(0xFF0A111F)),
    ),
    SearchCategory(
        title = "Меланхолия",
        subtitle = "Грустный вайб",
        query = "грустные треки",
        icon = Icons.Outlined.GraphicEq,
        gradientColors = listOf(Color(0xFF222434), Color(0xFF10121B)),
    ),
    SearchCategory(
        title = "В дорогу",
        subtitle = "Ритм для поездок",
        query = "music for driving",
        icon = Icons.Outlined.Tune,
        gradientColors = listOf(Color(0xFF302618), Color(0xFF18130B)),
    ),
)

