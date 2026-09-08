# Votify Android

Kotlin + Jetpack Compose (Material 3), Media3/ExoPlayer, Room, DataStore, OkHttp, Coil. Design: `../design/`.

## Запуск

1. Запусти бекенд: `npm start` в корне репозитория (порт 17217).
2. Открой папку `android/` в Android Studio (Koala или новее, JDK 17).
3. Адрес бекенда:
   - **Эмулятор** — ничего делать не надо, по умолчанию `http://10.0.2.2:17217` (это localhost хоста).
   - **Реальный телефон** — телефон и ПК в одной Wi‑Fi сети; в `android/local.properties`
     или `~/.gradle/gradle.properties` добавь строку
     `votifyApiBase=http://192.168.x.x:17217` (IP твоего ПК) и пересобери.
4. Run ▶.

CI: `.github/workflows/android.yml` собирает debug APK на каждый push в ветку — артефакт
`votify-debug-apk` во вкладке Actions.

## Структура

```
app/src/main/java/app/votify/mobile/
  VotifyApp.kt            — синглтоны: VotifyApi, VotifyDatabase, LibraryRepository, SettingsRepository, PlayerController
  MainActivity.kt         — edge-to-edge, подключение к MediaSession
  data/
    Models.kt, VotifyApi.kt   — модели и HTTP-клиент бекенда
    Lyrics.kt                 — парсер LRC (`[mm:ss.xx]`) + поиск активной строки
    LibraryRepository.kt      — любимое / история / плейлисты поверх Room
    SettingsRepository.kt     — DataStore: тема, стиль обложки, качество, жесты
    local/                    — Room: TrackEntity, Favorite, History, Playlist(+Track), DAO, VotifyDatabase
  player/
    PlaybackService.kt    — MediaSessionService + ExoPlayer (фон, уведомление, наушники)
    PlayerController.kt   — MediaController → StateFlow<PlayerUiState>; очередь, playNext/enqueue; пишет историю
  ui/
    theme/                — палитры OLED Black / Графит / светлая (системная), VotifyColors через CompositionLocal
    components/           — Artwork, TrackRow, PillChip, VotifyCard, MiniPlayer, шиты (меню трека, выбор плейлиста, очередь)
    home/                 — «Моя волна» из истории (custom-wave), Недавние, плитки
    search/               — поиск с debounce, результаты, меню «⋮»
    library/              — Коллекция: любимое, плейлисты, история + экраны списков
    artist/               — экран исполнителя (/api/artist)
    player/               — плеер: винил/квадрат/размытие, синхронизированный текст, ♥, очередь
    settings/             — настройки по макету (качество ↔ сервер, тема, обложка, жесты, данные, о приложении)
    VotifyRoot.kt         — NavHost, нижняя навигация, мини-плеер, оверлей плеера, глобальные шиты
```

## Локальные данные

- База `votify.db` (Room, schema в `app/schemas/`): таблицы `tracks`, `favorites`, `history`,
  `playlists`, `playlist_tracks`. История пишется автоматически при старте каждого трека
  (`PlayerController.onTrackStarted`) и обрезается до 2000 записей.
- «Моя волна» строится из истории: топ‑артисты → `seeds`, последние прослушанные и любимые → `trackSeeds`,
  недавние id → `exclude` (`GET /api/custom-wave`). Пока истории нет — `GET /api/recommendations`.
- Качество стриминга хранится **на сервере** (`GET/POST /api/network/settings`), приложение только
  синхронизирует его; остальные настройки — локально в DataStore.
