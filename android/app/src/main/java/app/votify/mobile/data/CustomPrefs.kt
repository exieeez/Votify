package app.votify.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OAuth Web Client ID for Google Sign-In (Firebase project votify-f461a).
 * Public by design — every Google-enabled app ships its web client id in the APK;
 * the Android side needs no client secret (ID-token flow).
 */
const val DEFAULT_GOOGLE_CLIENT_ID = "283427548411-h1i4r87j2krlgpu4hdh4og7e3ahv6sk7.apps.googleusercontent.com"

/**
 * All Dotify-style customization settings, persisted as ONE JSON blob in DataStore
 * (key `custom_prefs`). Being a single serializable object makes snapshots («Пресеты»),
 * server sync («Отправить/Получить») and import/export trivial.
 */
@Serializable
data class CustomPrefs(
    // ---- Основные ----
    val notifications: Boolean = true,
    val canvas: Boolean = false,

    // ---- Аудио ----
    val caching: Boolean = false,
    val gapless: Boolean = true,
    val crossfade: Boolean = false,
    val normalization: Boolean = false,
    val onNotification: String = "duck", // duck | pause | continue
    val autoplay: Boolean = false,
    val similarToQueue: Boolean = true,
    val restoreQueue: Boolean = true,

    // ---- Интерфейс ----
    val themeMode: String = "dark", // dark | light | system
    val themeName: String = "neutral", // neutral | graphite | violet | azure | emerald | amber | rose | workshop
    val tabStyle: String = "standard", // standard | compact
    val accentFromArt: Boolean = false,
    val transparentCards: Boolean = false,
    val fontFamily: String = "system", // system | serif | mono | rounded
    val fontScale: String = "normal", // small | normal | large

    // ---- Плеер ----
    val playerStyle: String = "vinyl", // vinyl | square | blur  (maps ArtworkStyle)
    val titleAlign: String = "center", // center | left
    val sliderStyle: String = "ios", // ios | classic
    val playButtonStyle: String = "circle", // circle | pill
    val infoChip: String = "source", // source | text | none
    val miniBg: String = "plain", // plain | artwork
    val miniProgress: String = "ring", // ring | bar | none
    val miniCoverShape: String = "rounded", // circle | rounded
    val miniCorners: String = "pill", // pill | rounded
    val miniButtons: String = "both", // both | play | none
    val miniButtonStyle: String = "filled", // filled | outline

    // ---- Обложка ----
    val gifArtwork: String = "", // URL/path, "" = обычная обложка
    val artworkAnimation: String = "none", // none | spin | sway | pulse | float
    val artworkEffect: String = "none", // none | grayscale | blur
    val artBlur: Int = 0, // размытие обложки (dp), 0..30
    val artDim: Int = 0, // затемнение обложки (%, 0..80)

    // ---- Область применения / условия темы из Мастерской ----
    val themeApplyBackground: Boolean = true,
    val themeApplyArtwork: Boolean = false,
    val themeApplySlider: Boolean = false,
    val themeArtworkAlways: Boolean = true, // true = всегда; false = только заглушка без своей обложки
    val artworkInside: Boolean = false,

    // ---- Свайпы ----
    val librarySwipeLeft: String = "queue", // queue | favorite | delete | none
    val librarySwipeRight: String = "none", // queue | favorite | delete | none
    val queueSwipeLeft: String = "delete",
    val queueSwipeRight: String = "delete",
    val miniSwipeLeft: String = "next", // next | previous | queue
    val miniSwipeRight: String = "previous", // next | previous | queue
    val playerSwipes: Boolean = true,

    // ---- Библиотека (фоны) ----
    val backgrounds: List<String> = emptyList(),
    val bgDim: Int = 35, // затемнение фоновой картинки, % (0..92)
    val bgBlur: Int = 0, // размытие фоновой картинки, dp (0..60)
    val bgScale: Float = 1f, // масштаб фона (1..5) — чтобы обрезать широкий ПК-фон
    val bgOffsetX: Float = 0f, // сдвиг кадра по горизонтали (-1..1)
    val bgOffsetY: Float = 0f, // сдвиг кадра по вертикали (-1..1)
    val bgFit: Int = 0, // как фон ложится на экран: 0 — заполнить (обрезать), 1 — целиком, 2 — растянуть

    // ---- Прокси ----
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: String = "",

    // ---- Google Sign-In ----
    val googleClientId: String = "",

    // ---- Первый запуск ----
    val welcomeDone: Boolean = false,

    // ---- Пресеты ----
    val presets: List<NamedPreset> = emptyList(),
)

/** A saved snapshot of [CustomPrefs] («Пресеты»). */
@Serializable
data class NamedPreset(val name: String, val prefs: CustomPrefs = CustomPrefs())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun CustomPrefs.toJson(): String = json.encodeToString(CustomPrefs.serializer(), this)

fun parseCustomPrefs(raw: String): CustomPrefs =
    if (raw.isBlank()) CustomPrefs()
    else runCatching { json.decodeFromString<CustomPrefs>(raw) }.getOrDefault(CustomPrefs())

/** themeMode + themeName → the AppTheme enum actually applied. */
fun CustomPrefs.toAppTheme(): AppTheme = when (themeMode) {
    "light" -> AppTheme.Light
    "system" -> AppTheme.System
    else -> when (themeName) {
        "graphite" -> AppTheme.Graphite
        "violet" -> AppTheme.Violet
        "azure" -> AppTheme.Azure
        "emerald" -> AppTheme.Emerald
        "amber" -> AppTheme.Amber
        "rose" -> AppTheme.Rose
        "workshop" -> AppTheme.Workshop
        else -> AppTheme.OledBlack
    }
}

/** The AppTheme value back into themeMode/themeName pair (for the Interface screen). */
fun AppTheme.toPrefsPair(): Pair<String, String> = when (this) {
    AppTheme.Light -> "light" to "neutral"
    AppTheme.System -> "system" to "neutral"
    AppTheme.Graphite -> "dark" to "graphite"
    AppTheme.Violet -> "dark" to "violet"
    AppTheme.Azure -> "dark" to "azure"
    AppTheme.Emerald -> "dark" to "emerald"
    AppTheme.Amber -> "dark" to "amber"
    AppTheme.Rose -> "dark" to "rose"
    AppTheme.Workshop -> "dark" to "workshop"
    AppTheme.OledBlack -> "dark" to "neutral"
}
