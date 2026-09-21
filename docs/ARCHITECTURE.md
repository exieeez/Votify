# Как работает Votify на телефоне (движок)

Спецификация для другой нейросети/агента: что за движок внутри, из каких слоёв состоит
Android-приложение и где что находится. Всё ниже — факты из кода. Если чего-то здесь нет,
значит этого в проекте нет: **не выдумывай SDK, эндпоинты и классы**.

По аккаунту, юзернеймам и друзьям — отдельный документ: **[`docs/ACCOUNT.md`](ACCOUNT.md)**.

---

## 1. Два приложения в одном репозитории

| Часть | Что это | Где |
| --- | --- | --- |
| **ПК-приложение** | Electron + Node, локальный HTTP-сервер (порт 17217) и музыкальное API | `main.js`, `server.js`, [`routes/`](../routes/), [`src/`](../src/) |
| **Android-приложение** | Нативный клиент: Kotlin, Jetpack Compose, Media3/ExoPlayer, Room | [`android/`](../android/) |

Телефон **не зависит** от ПК: он умеет работать полностью самостоятельно. ПК-сервер —
необязательный режим (Settings → Сервер).

---

## 2. Движок музыки: два режима

`MusicRepository` (`data/MusicRepository.kt`) — единственная точка входа для всей музыки в UI.
Режим (`SourceMode`) выбирается автоматически:

- **`Embedded` (по умолчанию, «самостоятельное приложение»)** — всё считается на самом телефоне:
  поиск, поток, плейлисты, тексты песен, скачивание.
- **`Server`** — всё делегируется своему Votify-бэкенду (как в ПК-версии).

Выбор: если в `SettingsRepository` сохранён непустой `serverUrl` — режим серверный. При старте
`MusicRepository` проверяет `api.health()` с таймаутом 4 секунды; не ответил — включается
`forcedEmbedded`, и приложение молча остаётся автономным (устаревший адрес сервера не ломает
приложение). Заодно с живого сервера кэшируется Firebase Web Config.

Каждый метод репозитория выглядит как развилка: `search`, `artist`, `customWave`,
`recommendations`, `trending`, `lyrics`, `importPlaylist`, `preload`, `resolveStreamUrlSync`,
`isDownloaded`, `downloadTrackSync`, `downloadTrackTracked`, `deleteDownload`, `downloadStats`.

---

## 3. Чем именно является встроенный движок

**`EmbeddedMusicSource`** построен на **NewPipeExtractor** (`org.schabi.newpipe.extractor`) —
том же движке, на котором работает приложение NewPipe. Инициализация:
`NewPipe.init(OkHttpDownloader(http), Localization("ru", "UA"), ContentCountry("UA"))`.

Что он умеет:

- **Поиск** — `ServiceList.YouTube`: склеивает каталог **YouTube Music** (чистые метаданные,
  только песни) с обычным поиском YouTube, дальше дедуп и разведение по исполнителям.
- **Рекомендации** — `wave()` и `relatedTracks()`: семена из истории/топ-артистов, блок «Далее»
  у видео-якоря, параллельный обход (`mapParallel`, `fanOut`, лимит 6 потоков).
- **Чарт «В тренде»** — `trendingCis()`: позиции берутся из живого чарта Apple Music (RSS),
  затем параллельно ищутся на YouTube Music с сохранением порядка.
- **Тексты песен** — `lrclib.net` (и на ПК, и на телефоне).
- **Ссылки на поток** — кэшируются на 4 часа: у YouTube они живут ~6 часов.

Никаких «своих» API музыки у проекта нет: это извлечение из YouTube/YouTube Music/SoundCloud.

---

## 4. Воспроизведение

| Класс | Роль |
| --- | --- |
| `player/PlaybackService.kt` | `MediaSessionService` (Media3): живёт в фоне, отдаёт MediaSession систему (блокировка, Bluetooth, уведомление) |
| `player/PlayerController.kt` | Мост между Compose и MediaController: очередь, состояние, прогресс, обработка ошибок |
| `data/MusicRepository.kt` | По id трека даёт реальный URL потока |

Схема URI: элементы очереди — это **`votify://stream/<id>`**. В `PlaybackService` поверх
`DefaultDataSource` стоит `ResolvingDataSource`, который на каждый запрос подменяет этот URI на
настоящий адрес: скачанный файл с диска (если трек в офлайне), URL потока из NewPipeExtractor
(embedded) или `/api/stream?id=...` (server mode). Поэтому UI никогда не знает реальных ссылок.

Состояние плеера — `PlayerUiState` (current, queue, index, isPlaying, isBuffering, positionMs,
durationMs, shuffle, repeatMode, error). Позиция обновляется `Handler`-тикером на главном потоке,
пока идёт воспроизведение. Событие старта трека уходит в `library.recordPlay(track)` — так
собирается история.

UI: `ui/player/PlayerScreen.kt` (полный экран) и `ui/components/MiniPlayer.kt` (мини-плеер).

---

## 5. Офлайн

- Скачанные треки лежат в `filesDir/downloads` (`EmbeddedMusicSource.downloadsDir`).
- `data/DownloadService.kt` — **foreground-сервис** типа `dataSync`: процесс не умирает при
  выключенном экране, в шторке виден процент. Один трек — процент по `Content-Length`;
  пачка — `(готово + доля текущего) / всего`; без `Content-Length` — indeterminate и счётчик
  «трек N из M».
- Есть многопоточная загрузка пачками и отслеживание уже скачанного (`DownloadTracker`).

---

## 6. Данные на телефоне

**Room** (`data/local/`): `tracks`, `favorites`, `history`, `playlists`, `playlist_tracks`,
плюс `PlaylistSummary` для списков. Схема генерируется KSP, файлы схем — `app/schemas`.

- `LibraryRepository` — избранное, история, плейлисты, запись прослушиваний.
- `MusicRepository` — сеть/NewPipe (см. разделы 2–3).
- `SettingsRepository` — **DataStore**: адрес сервера, качество звука, тема, оформление
  обложек, `customPrefs` (кастомная тема, см. `CustomPrefs.kt`), конфиг Firebase, аккаунт.
- `VotifyApi` — обёртка над серверным REST: `api/search`, `api/artist`, `api/recommendations`,
  `api/charts`, `api/stream`, `api/lyrics`, `api/import*`, `api/auth/*`, `api/sync/*`,
  `api/health`, `api/network/settings`, `api/firebase/config`.
- `VotifyApp` — «бедный DI»: единственный `Application`, где создаются все синглтоны,
  `appScope`, Coil `ImageLoader` (GIF/WebP: ImageDecoder на 28+, GifDecoder ниже).

---

## 7. UI-слой

- **Jetpack Compose + Material 3**, одна `MainActivity`, вся навигация — в `ui/VotifyRoot.kt`.
- Три нижние вкладки: `Home`, `Search`, `Library`.
- Остальные маршруты (`Routes`): `favorites`, `history`, `settings` (+ подразделы
  `general`, `audio`, `storage`, `swipes`, `interface`, `player`, `artwork`, `backgrounds`,
  `presets`, `proxy`), `account`, `import`, `playlist/{id}`, `artist/{name}`, `trending`,
  `workshop`.
- Экраны — по пакетам `ui/home`, `ui/search`, `ui/library`, `ui/player`, `ui/artist`,
  `ui/account`, `ui/settings`, `ui/trending`, `ui/workshop`; общие элементы — `ui/components`.
- Каждый экран получает свою ViewModel из `AppViewModelFactory` внизу `VotifyRoot.kt`.
  **Новую ViewModel обязательно нужно зарегистрировать в этой фабрике**, иначе — краш
  `IllegalArgumentException: Unknown ViewModel`.
- Визуальный стиль: `ui/theme/` (`VotifyColors` — набор из ~30 токенов Material 3,
  темы-пресеты). Темы можно менять прямо в приложении, включая импорт из «Мастерской тем»
  (`ui/workshop`) и кастомную тему через `customPrefs`.

---

## 8. Аккаунт и облако

Firebase Authentication (Identity Toolkit REST) + Cloud Firestore REST — **без Firebase SDK**,
через OkHttp (`data/FirebaseRest.kt`). Облачная синхронизация библиотеки и настроек:
`CloudSync` + `CloudSyncAuto` (автопуш в приватный документ `users/{uid}` через несколько
секунд после изменений). Профили, юзернеймы и друзья — в `docs/ACCOUNT.md`.

---

## 9. Сборка и проверка

- [`android/app/build.gradle.kts`](../android/app/build.gradle.kts): `minSdk 26`, `targetSdk/compileSdk 35`, Kotlin 2.x +
  плагин Compose + KSP (Room), **coreLibraryDesugaring** (NewPipeExtractor нужен `java.time`).
- Основные зависимости: Media3 ExoPlayer/Session **1.4.1**, OkHttp 4.12, kotlinx-serialization,
  Compose (BOM 2024.09.03, navigation-compose 2.8.2), Coil 2.7 (+ GIF), Credential Manager
  (Google Sign-In), NewPipeExtractor.
- Подпись — закоммиченный отладочный keystore ([`android/keystore/votify-debug.p12`](../android/keystore/votify-debug.p12), пароль
  `android`): у всех сборок одинаковая подпись, поэтому APK обновляется поверх установленного.
- Переопределения сборки: `-PvotifyApiBase=http://192.168.1.10:17217` (адрес ПК-сервера;
  по умолчанию `http://10.0.2.2:17217` для эмулятора), переменная окружения
  `VOTIFY_FIREBASE_CONFIG` (в CI — из секретов), `-PvotifyIslandPackage=<id>` (сборка с другим
  package id для OEM-«динамических островов»).
- Проверка: **локально Java/Gradle нет** — только коммит + пуш в ветку, дальше GitHub Actions
  («Android CI» и «Release APK»). Готовый APK публикуется в релизе с тегом `android-debug`:
  https://github.com/exieeez/Votify/releases/download/android-debug/Votify-debug.apk
- Правила Firestore: `npm run deploy:firestore-rules` (проект `votify-f461a`), тесты — `npm test`.

---

## 10. Правила игры для агента

Нельзя:

- добавлять `firebase-firestore`, `firebase-auth`, `firebase-storage` и другой Firebase SDK —
  в приложении только REST;
- придумывать REST-эндпоинты: реальные перечислены в `VotifyApi` и `FirebaseRest`;
- городить свой HTTP-клиент рядом с `VotifyApi`/`FirebaseRest` — есть OkHttp-синглтоны;
- тянуть музыку/поиск напрямую из экрана: только через `MusicRepository`/`LibraryRepository`;
- писать `Thread.sleep`/сетевые вызовы на главном потоке (всё в `Dispatchers.IO` и корутинах);
- называть вещи «движком Votify» без уточнения: движок = NewPipeExtractor (embedded) или
  `/api/*` своего сервера (server mode).

Нужно:

- новый экран → пакет `ui/<раздел>`, ViewModel + регистрация в `AppViewModelFactory`;
- новые строки → `res/values/strings.xml` (интерфейс русский);
- любая работа с сетью → `withContext(Dispatchers.IO)`, ошибки - в состояние экрана, а не в
  `try {} catch {}` с пустым телом;
- изменения, влияющие на Firestore, — вместе с правилами в `firestore.rules` и их деплоем.


---

## 11. Прямые ссылки на файлы (GitHub)

Ветка **`arena/01a08af6-votify`**. Ссылки вида «открыть» — страница файла, «raw» — прямое содержимое файла
(его удобно скормить модели целиком).

Если ветка недоступна или удалена — те же файлы лежат локально по путям из разделов выше
(пути относительно корня репозитория).

| Что | Файл | Ссылка |
| --- | --- | --- |
| Точка входа приложения, DI-синглтоны | `android/app/src/main/java/app/votify/mobile/VotifyApp.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/VotifyApp.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/VotifyApp.kt) |
| Музыка: два режима (Embedded/Server) | `android/app/src/main/java/app/votify/mobile/data/MusicRepository.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/MusicRepository.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/MusicRepository.kt) |
| Встроенный движок (NewPipeExtractor) | `android/app/src/main/java/app/votify/mobile/data/EmbeddedMusicSource.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/EmbeddedMusicSource.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/EmbeddedMusicSource.kt) |
| Клиент своего сервера (/api/*) | `android/app/src/main/java/app/votify/mobile/data/VotifyApi.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/VotifyApi.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/VotifyApi.kt) |
| Настройки и сессия (DataStore) | `android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt) |
| Избранное, история, плейлисты | `android/app/src/main/java/app/votify/mobile/data/LibraryRepository.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/LibraryRepository.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/LibraryRepository.kt) |
| Офлайн-загрузки (foreground service) | `android/app/src/main/java/app/votify/mobile/data/DownloadService.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/DownloadService.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/DownloadService.kt) |
| База Room | `android/app/src/main/java/app/votify/mobile/data/local/VotifyDatabase.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/local/VotifyDatabase.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/local/VotifyDatabase.kt) |
| Сущности Room | `android/app/src/main/java/app/votify/mobile/data/local/Entities.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/local/Entities.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/local/Entities.kt) |
| DAO | `android/app/src/main/java/app/votify/mobile/data/local/Daos.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/local/Daos.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/local/Daos.kt) |
| Фоновый плеер (MediaSessionService) | `android/app/src/main/java/app/votify/mobile/player/PlaybackService.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/player/PlaybackService.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/player/PlaybackService.kt) |
| Мост UI ↔ MediaController | `android/app/src/main/java/app/votify/mobile/player/PlayerController.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/player/PlayerController.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/player/PlayerController.kt) |
| Навигация, табы, фабрика ViewModel | `android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt) |
| Цветовые токены темы | `android/app/src/main/java/app/votify/mobile/ui/theme/Color.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/theme/Color.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/theme/Color.kt) |
| Разметка зависимостей и сборки | `android/app/build.gradle.kts` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/build.gradle.kts) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/build.gradle.kts) |
| Манифест, сервисы, разрешения | `android/app/src/main/AndroidManifest.xml` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/AndroidManifest.xml) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/AndroidManifest.xml) |
| Все строки интерфейса | `android/app/src/main/res/values/strings.xml` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/res/values/strings.xml) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/res/values/strings.xml) |
