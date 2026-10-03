package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.PlayerBackground

/** «Плеер»: стиль, заголовок, слайдер, плашка, фон + секция мини-плеера (100% дизайн со скриншотов). */
@Composable
fun PlayerSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_player), contentPadding, onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {

            // СТИЛЬ ПЛЕЕРА
            SettingsSectionLabel("Стиль плеера")
            SettingsCard {
                SettingsValueRow(
                    title = "Стиль плеера",
                    subtitle = null,
                    value = when (settings.artworkStyle) {
                        ArtworkStyle.Square -> "Стандартный"
                        ArtworkStyle.Blur -> "Большой"
                        else -> "Пластинка"
                    },
                    trailing = when (settings.artworkStyle) {
                        ArtworkStyle.Square -> Icons.Outlined.MusicNote
                        ArtworkStyle.Blur -> Icons.Outlined.Tv
                        else -> Icons.Outlined.Album
                    },
                    choiceOptions = listOf(
                        ChoiceOption("square", "Стандартный", "Классический вид с обложкой", Icons.Outlined.MusicNote),
                        ChoiceOption("blur", "Большой", "Обложка на весь экран", Icons.Outlined.Tv),
                        ChoiceOption("vinyl", "Пластинка", "Виниловая пластинка с вращением", Icons.Outlined.Album),
                    ),
                    onPick = { key ->
                        when (key) {
                            "square" -> viewModel.setArtworkStyle(ArtworkStyle.Square)
                            "blur" -> viewModel.setArtworkStyle(ArtworkStyle.Blur)
                            else -> viewModel.setArtworkStyle(ArtworkStyle.Vinyl)
                        }
                        viewModel.updatePrefs { it.copy(playerStyle = key) }
                    },
                )
            }

            // ЗАГОЛОВОК
            SettingsSectionLabel("Заголовок")
            SettingsCard {
                SettingsValueRow(
                    title = "Выравнивание заголовка",
                    subtitle = null,
                    value = if (prefs.titleAlign == "left") "Слева" else "По центру",
                    trailing = if (prefs.titleAlign == "left") Icons.Outlined.FormatAlignLeft else Icons.Outlined.FormatAlignCenter,
                    choiceOptions = listOf(
                        ChoiceOption("center", "По центру", null, Icons.Outlined.FormatAlignCenter),
                        ChoiceOption("left", "Слева", null, Icons.Outlined.FormatAlignLeft),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(titleAlign = v) } },
                )
            }

            // СЛАЙДЕР
            SettingsSectionLabel("Слайдер")
            SettingsCard {
                SettingsValueRow(
                    title = "Тип слайдера",
                    subtitle = when (prefs.sliderStyle) {
                        "thin" -> "Тонкий"
                        "classic" -> "Стандартный"
                        "wave" -> "Волновой"
                        else -> "iOS"
                    },
                    value = when (prefs.sliderStyle) {
                        "thin" -> "Тонкий"
                        "classic" -> "Стандартный"
                        "wave" -> "Волновой"
                        else -> "iOS"
                    },
                    trailing = when (prefs.sliderStyle) {
                        "thin" -> Icons.Outlined.Remove
                        "classic" -> Icons.Outlined.RadioButtonUnchecked
                        "wave" -> Icons.Outlined.Equalizer
                        else -> Icons.Outlined.ToggleOn
                    },
                    choiceOptions = listOf(
                        ChoiceOption("classic", "Стандартный", null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("thin", "Тонкий", null, Icons.Outlined.Remove),
                        ChoiceOption("ios", "iOS", null, Icons.Outlined.ToggleOn),
                        ChoiceOption("wave", "Волновой", null, Icons.Outlined.Equalizer),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(sliderStyle = v) } },
                )
            }

            // ПЛАШКА ИНФОРМАЦИИ
            SettingsSectionLabel("Плашка информации")
            SettingsCard {
                SettingsValueRow(
                    title = "Показывать в плашке",
                    subtitle = when (prefs.infoChip) {
                        "text" -> "Текст песни"
                        "none" -> "Нет"
                        else -> "Источник"
                    },
                    value = when (prefs.infoChip) {
                        "text" -> "Текст песни"
                        "none" -> "Нет"
                        else -> "Источник"
                    },
                    trailing = when (prefs.infoChip) {
                        "text" -> Icons.Outlined.Description
                        "none" -> Icons.Outlined.Block
                        else -> Icons.Outlined.Info
                    },
                    choiceOptions = listOf(
                        ChoiceOption("source", "Источник", null, Icons.Outlined.Info),
                        ChoiceOption("text", "Текст песни", null, Icons.Outlined.Description),
                        ChoiceOption("none", "Нет", null, Icons.Outlined.Block),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(infoChip = v) } },
                )
            }

            // ФОН ПЛЕЕРА
            SettingsSectionLabel("Фон плеера")
            SettingsCard {
                SettingsValueRow(
                    title = "Фон плеера",
                    subtitle = when (settings.playerBackground) {
                        PlayerBackground.Artwork -> "Обложка"
                        PlayerBackground.Lava -> "Цвет"
                        else -> "Нет"
                    },
                    value = when (settings.playerBackground) {
                        PlayerBackground.Artwork -> "Обложка"
                        PlayerBackground.Lava -> "Цвет"
                        else -> "Нет"
                    },
                    trailing = when (settings.playerBackground) {
                        PlayerBackground.Artwork -> Icons.Outlined.Image
                        PlayerBackground.Lava -> Icons.Outlined.Palette
                        else -> Icons.Outlined.Block
                    },
                    choiceOptions = listOf(
                        ChoiceOption("artwork", "Обложка", "Размытая обложка трека", Icons.Outlined.Image),
                        ChoiceOption("lava", "Цвет", "Адаптивный градиент под обложку", Icons.Outlined.Palette),
                        ChoiceOption("dark", "Нет", "Без фона", Icons.Outlined.Block),
                    ),
                    onPick = { v ->
                        viewModel.setPlayerBackground(
                            when (v) {
                                "artwork" -> PlayerBackground.Artwork
                                "lava" -> PlayerBackground.Lava
                                else -> PlayerBackground.Dark
                            },
                        )
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_lyrics_over_art),
                    subtitle = stringResource(R.string.settings_lyrics_over_art_sub),
                    checked = settings.showLyricsOverArtwork,
                    onChange = viewModel::setShowLyricsOverArtwork,
                )
            }

            // МИНИ-ПЛЕЕР
            SettingsSectionLabel("Мини-плеер")
            SettingsCard {
                SettingsNavRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Готовые пресеты",
                    subtitle = "Выбрать пресет",
                    onClick = onOpenPresets,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = "Фон",
                    subtitle = if (prefs.miniBg == "artwork") "Цвет обложки" else "Обычный",
                    value = if (prefs.miniBg == "artwork") "Цвет обложки" else "Обычный",
                    trailing = Icons.Outlined.Palette,
                    choiceOptions = listOf(
                        ChoiceOption("artwork", "Цвет обложки", null, Icons.Outlined.Palette),
                        ChoiceOption("plain", "Обычный", null, Icons.Outlined.Block),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniBg = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = "Индикаторы прогресса",
                    subtitle = when (prefs.miniProgress) {
                        "bar" -> "Полоса"
                        "none" -> "Нет"
                        else -> "Кольцо на обложке"
                    },
                    value = when (prefs.miniProgress) {
                        "bar" -> "Полоса"
                        "none" -> "Нет"
                        else -> "Кольцо на обложке"
                    },
                    trailing = Icons.Outlined.Equalizer,
                    choiceOptions = listOf(
                        ChoiceOption("ring", "Кольцо на обложке", null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("bar", "Полоса", null, Icons.Outlined.Remove),
                        ChoiceOption("none", "Нет", null, Icons.Outlined.Block),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniProgress = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = "Форма обложки",
                    subtitle = if (prefs.miniCoverShape == "circle") "Круг" else "Закругленная",
                    value = if (prefs.miniCoverShape == "circle") "Круг" else "Закругленная",
                    trailing = Icons.Outlined.RadioButtonUnchecked,
                    choiceOptions = listOf(
                        ChoiceOption("circle", "Круг", null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("rounded", "Закругленная", null, Icons.Outlined.CropSquare),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniCoverShape = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = "Скругление границ",
                    subtitle = when (prefs.miniCorners) {
                        "none" -> "Нет"
                        "soft" -> "Мягкое"
                        "rounded" -> "Закругленное"
                        else -> "Круглое (Pill)"
                    },
                    value = when (prefs.miniCorners) {
                        "none" -> "Нет"
                        "soft" -> "Мягкое"
                        "rounded" -> "Закругленное"
                        else -> "Круглое (Pill)"
                    },
                    trailing = Icons.Outlined.RadioButtonUnchecked,
                    choiceOptions = listOf(
                        ChoiceOption("none", "Нет", null, Icons.Outlined.CropSquare),
                        ChoiceOption("soft", "Мягкое", null, Icons.Outlined.GridView),
                        ChoiceOption("rounded", "Закругленное", null, Icons.Outlined.GridView),
                        ChoiceOption("pill", "Круглое (Pill)", null, Icons.Outlined.RadioButtonUnchecked),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniCorners = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = "Стиль кнопок",
                    subtitle = when (prefs.miniButtonStyle) {
                        "standard" -> "Стандартные"
                        "minimal" -> "Минималистичные"
                        else -> "Залитые"
                    },
                    value = when (prefs.miniButtonStyle) {
                        "standard" -> "Стандартные"
                        "minimal" -> "Минималистичные"
                        else -> "Залитые"
                    },
                    trailing = Icons.Outlined.RadioButtonUnchecked,
                    choiceOptions = listOf(
                        ChoiceOption("standard", "Стандартные", null, Icons.Outlined.MoreHoriz),
                        ChoiceOption("filled", "Залитые", null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("minimal", "Минималистичные", null, Icons.Outlined.Grain),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniButtonStyle = v) } },
                )
            }
        }
    }
}
