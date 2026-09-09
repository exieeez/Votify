# Votify Android

Kotlin + Jetpack Compose (Material 3), Media3/ExoPlayer, Room, DataStore, OkHttp, Coil. Design: `../design/`.

**Приложение работает самостоятельно** — без сервера: поиск (YouTube Music), стриминг, тексты
песен и импорт плейлистов выполняются прямо на телефоне через NewPipeExtractor.

## Запуск

1. Открой папку `android/` в Android Studio (JDK 17).
2. Run ▶. Ничего настраивать не нужно — только интернет на телефоне.

Если раньше был сохранён адрес сервера, приложение проверит его при запуске и при
недоступности автоматически перейдёт в автономный режим.

CI: `.github/workflows/android.yml` собирает debug APK на каждый push в ветку — артефакт
`votify-debug-apk` во вкладке Actions; `release-apk.yml` публикует APK в
[Releases](https://github.com/exieeez/Votify/releases/tag/android-debug) (постоянная ссылка).

## Структура

```
app/src/main/java/app/votify/mobile/
  VotifyApp.kt            — синглтоны: VotifyApi, VotifyDatabase, LibraryRepository, SettingsRepository, MusicRepository, PlayerController
  MainActivity.kt         — edge-to-edge, подключение к MediaSession
  data/
    Models.kt, VotifyApi.kt   — модели и HTTP-клиент бекенда
    EmbeddedMusicSource.kt    — автономный режим: NewPipeExtractor (поиск YT Music, прямые
                               аудиопотоки, импорт YT/SC), Spotify embed, lrclib.net
    MusicRepository.kt        — фасад: «Без сервера» (EmbeddedMusicSource) или «Свой сервер» (VotifyApi)
    Lyrics.kt                 — парсер LRC (`[mm:ss.xx]`) + поиск активной строки
    LibraryRepository.kt      — любимое / история / плейлисты поверх Room
    SettingsRepository.kt     — DataStore: тема, стиль обложки, качество, жесты, адрес сервера,
                              фон плеера, аккаунт (JWT)
    local/                    — Room: TrackEntity, Favorite, History, Playlist(+Track), DAO, VotifyDatabase
  player/
    PlaybackService.kt    — MediaSessionService + ExoPlayer; ResolvingDataSource превращает
                            `votify://stream/<id>` в реальный URL (сервер или прямая ссылка)
    PlayerController.kt   — MediaController → StateFlow<PlayerUiState>; очередь, playNext/enqueue; пишет историю
  ui/
    theme/                — палитры OLED Black / Графит / светлая (системная), VotifyColors через CompositionLocal
    components/           — Artwork, TrackRow, PillChip, VotifyCard, VotifyTextField, MiniPlayer, шиты (меню трека, выбор плейлиста, очередь)
    home/                 — «Моя волна» из истории, Недавние, плитки
    search/               — поиск с debounce, результаты, меню «⋮», подсказки при проблемах с сетью
    library/              — Коллекция: любимое, плейлисты, история, импорт из сервиса + экраны списков
    account/              — вход / регистрация / восстановление пароля (/api/auth/*, режим сервера)
    artist/               — экран исполнителя
    player/               — плеер: винил/квадрат/размытие, синхронизированный текст, ♥, очередь,
                            цветной фон из обложки (Palette), «Поделиться» и меню «⋮»
    settings/             — настройки: режим работы, аккаунт, качество, тема, обложка, фон плеера,
                            жесты, данные, о приложении
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

## Код, привязанный к серверу (сейчас не используется в UI)

| Функция | Экран | Эндпоинты |
| --- | --- | --- |
| Адрес сервера (без пересборки) | Настройки → Режим работы | `GET /api/health` |
| Аккаунт: вход / регистрация / сброс пароля | Настройки → Аккаунт | `POST /api/auth/login`, `/register`, `/forgot-password`, `/reset-password` |
| Импорт плейлиста | Коллекция → «Импортировать из сервиса» | `GET /api/playlist`, `GET /api/soundcloud/import` |

Адрес сохраняется в DataStore и применяется при старте приложения (`VotifyApp.bootstrap`);
токен аккаунта подписывает все запросы (`Authorization: Bearer …`). Импортированный плейлист
превращается в локальный плейлист Room со стрим-идентификаторами YouTube/SoundCloud — треки
играют сразу, без повторного поиска.

## Автономный режим («Без сервера»)

По умолчанию. Всё работает на телефоне:

- поиск и «популярные треки» — YouTube Music (NewPipeExtractor), с фолбэком на обычный YouTube;
- воспроизведение — прямые аудиопотоки (прогрессивные AAC/Opus, выбор по битрейту согласно
  настройке качества), ExoPlayer получает их через `ResolvingDataSource` (кэш ~4 ч);
- тексты — lrclib.net напрямую;
- импорт — плейлисты YouTube/SoundCloud через NewPipe, Spotify — через страницу embed
  (`__NEXT_DATA__`) + поиск каждого трека в YT Music.
