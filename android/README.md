# Votify Android

Kotlin + Jetpack Compose (Material 3), Media3/ExoPlayer, OkHttp, Coil. Design: `../design/`.

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
  VotifyApp.kt            — синглтоны: VotifyApi, PlayerController
  MainActivity.kt         — edge-to-edge, подключение к MediaSession
  data/                   — модели (Track…) и HTTP-клиент VotifyApi
  player/
    PlaybackService.kt    — MediaSessionService + ExoPlayer (фон, уведомление, наушники)
    PlayerController.kt   — MediaController → StateFlow<PlayerUiState> для Compose
  ui/
    theme/                — палитра Monochrome Audio, Inter, формы
    components/           — Artwork, TrackRow, PillChip, VotifyCard, MiniPlayer…
    home/                 — «Моя волна» (орбита из обложек), Недавние, плитки
    search/               — поиск с debounce, результаты
    library/              — каркас Коллекции
    player/               — полноэкранный плеер с винилом
    VotifyRoot.kt         — NavHost, нижняя навигация, мини-плеер, оверлей плеера
```
