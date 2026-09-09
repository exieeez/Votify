package app.votify.mobile.ui.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.AppTheme
import app.votify.mobile.data.WorkshopThemeDoc
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.VotifyTextField
import app.votify.mobile.ui.theme.VotifyColors

/**
 * «Мастерская» (community): публичный каталог тем — чипы-фильтры, поиск, сетка 2 колонки,
 * публикация своей темы (белая кнопка ↑). Тап по карточке открывает детальную настройку.
 */
@Composable
fun WorkshopScreen(
    viewModel: WorkshopViewModel,
    currentTheme: AppTheme,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPickBuiltIn: (AppTheme) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    var publishDialog by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selectedDoc by remember { mutableStateOf<WorkshopThemeDoc?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            // ---- Top bar: back | pill title | search + publish ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(onClick = onBack, size = 40.dp) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_back), tint = VotifyColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = CircleShape,
                    color = VotifyColors.SurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VotifyColors.BorderSubtle),
                ) {
                    Text(
                        stringResource(R.string.workshop_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = VotifyColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                    size = 40.dp,
                ) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.nav_search), tint = VotifyColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = { publishDialog = true },
                    shape = CircleShape,
                    color = VotifyColors.TextPrimary,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Outlined.Upload, stringResource(R.string.workshop_publish), tint = VotifyColors.PitchBlack, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (searchActive) {
                VotifyTextField(
                    searchQuery,
                    { searchQuery = it },
                    stringResource(R.string.workshop_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                )
            }

            // ---- Chip filters ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillChip(text = "▦ " + stringResource(R.string.workshop_filter_all), selected = filter == "all", onClick = { filter = "all" })
                PillChip(text = "🔥 " + stringResource(R.string.workshop_filter_popular), selected = filter == "pop", onClick = { filter = "pop" })
                PillChip(text = "✨ " + stringResource(R.string.workshop_filter_new), selected = filter == "new", onClick = { filter = "new" })
            }

            // ---- Grid ----
            if (!state.firebaseReady) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.workshop_connect_title), style = MaterialTheme.typography.titleSmall, color = VotifyColors.TextPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.workshop_connect_sub), style = MaterialTheme.typography.bodySmall, color = VotifyColors.TextMuted, textAlign = TextAlign.Center)
                }
            } else if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VotifyColors.Primary, strokeWidth = 2.dp)
                }
            } else {
                val visible = state.community
                    .filter { d ->
                        searchQuery.isBlank() ||
                            d.title.contains(searchQuery, ignoreCase = true) ||
                            d.authorName.contains(searchQuery, ignoreCase = true)
                    }
                    .let { list -> if (filter == "pop") list.sortedBy { d -> d.id.hashCode() } else list }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
                ) {
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.workshop_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            )
                        }
                    }
                    items(visible.chunked(2)) { rowDocs ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowDocs.forEach { doc ->
                                ThemeCard(doc = doc, modifier = Modifier.weight(1f)) { selectedDoc = doc }
                            }
                            if (rowDocs.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (state.error != null) {
                        item {
                            Text(
                                state.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = VotifyColors.Error,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // ---- Детальная настройка темы (поверх каталога) ----
        selectedDoc?.let { doc ->
            WorkshopThemeDetails(
                doc = doc,
                viewModel = viewModel,
                prefs = prefs,
                onClose = { selectedDoc = null },
            )
        }
    }

    if (publishDialog) {
        app.votify.mobile.ui.components.PlaylistNameDialog(
            title = stringResource(R.string.workshop_publish),
            confirm = stringResource(R.string.workshop_publish),
            onDismiss = { publishDialog = false },
            onConfirm = { name ->
                publishDialog = false
                viewModel.publish(name, "")
            },
        )
    }
}

/** Catalog card: square cover (image or placeholder) + #1A1A1A footer with title & author. */
@Composable
private fun ThemeCard(doc: WorkshopThemeDoc, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    runCatching { Color(android.graphics.Color.parseColor(doc.theme.background)) }.getOrDefault(Color(0xFF121212)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (doc.theme.backgroundUrl.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = doc.theme.backgroundUrl,
                    contentDescription = doc.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Outlined.Collections, null, tint = VotifyColors.TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                doc.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            runCatching { Color(android.graphics.Color.parseColor(doc.theme.primary)) }.getOrDefault(Color.White),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        doc.authorName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0A0A0A),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    doc.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    color = VotifyColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
