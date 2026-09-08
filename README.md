# Votify — Backend API (mobile edition)

Ветка `arena/01a07e3f-votify` — это **только серверная часть** Votify. Десктопная оболочка
(Electron, Discord Rich Presence, Google OAuth для десктопа) и веб‑интерфейс из `src/` удалены:
здесь живёт чистый HTTP API, поверх которого будет строиться мобильное приложение.

## Структура

```
server.js              — точка входа: HTTP-сервер, CORS, роутинг
routes/
  utils.js             — общие хелперы: поиск (YouTube / SoundCloud), yt-dlp, JWT, хранение
  music.js             — поиск, рекомендации, стриминг, тексты, импорт плейлистов
  auth.js              — локальная регистрация/вход (JWT), сброс пароля
  sync.js              — заглушка синхронизации (локальный режим)
  smtp.js              — заглушка SMTP-настроек
scripts/
  download-ytdlp.js    — скачивает yt-dlp при `npm install`
bin/yt-dlp             — бинарник yt-dlp (Linux)
assets/                — иконки приложения
firestore.rules        — правила Firestore (аккаунты + мастерская тем)
```

## Запуск

Требования: Node.js 20+.

```bash
npm install          # заодно скачает yt-dlp в bin/
npm start            # http://0.0.0.0:17217
npm run dev          # то же с авто-перезапуском (node --watch)
```

Переменные окружения:

| Переменная               | Назначение                                        | По умолчанию |
| ------------------------ | ------------------------------------------------- | ------------ |
| `PORT` / `VOTIFY_PORT`   | порт сервера                                      | `17217`      |
| `VOTIFY_HOST`            | адрес прослушивания                               | `0.0.0.0`    |
| `YT_DLP_PATH`            | путь к бинарнику yt-dlp                           | `bin/yt-dlp` |
| `VOTIFY_FIREBASE_CONFIG` | JSON Firebase Web Config (иначе читается `firebase-config.json`) | — |

Пользовательские данные (users.json, network.json, JWT‑секрет) хранятся в
`~/.config/Votify` (Linux), `~/Library/Application Support/Votify` (macOS) или `%APPDATA%\Votify` (Windows).

## API

Все ответы — JSON, CORS открыт (`*`), для стриминга поддерживаются `Range`‑запросы.

### Служебные

| Метод | Путь                    | Описание                                      |
| ----- | ----------------------- | --------------------------------------------- |
| GET   | `/` , `/api/health`     | статус сервера, версия, uptime                |
| GET   | `/api/firebase/config`  | Firebase Web Config для клиента               |
| GET   | `/api/network/settings` | текущие настройки (`audioQuality`)            |
| POST  | `/api/network/settings` | `{ audioQuality: 'low' \| 'medium' \| 'high' }` |

### Музыка

| Метод | Путь                     | Параметры                                          | Описание |
| ----- | ------------------------ | -------------------------------------------------- | -------- |
| GET   | `/api/search`            | `q`, `limit` (≤100)                                | поиск треков (YouTube + SoundCloud) |
| GET   | `/api/artist`            | `name`, `limit`                                    | треки исполнителя |
| GET   | `/api/recommendations`   | `limit`                                            | подборка для главной |
| GET   | `/api/custom-wave`       | `seeds`, `trackSeeds` (через `\|`), `exclude`, `limit` | персональная «волна» |
| GET   | `/api/stream`            | `id`, `download=1`                                 | **проксирование аудио** (поддерживает Range) |
| GET   | `/api/stream-url`        | `id`                                               | прямая ссылка на аудио |
| GET   | `/api/audio`             | `id`                                               | legacy: прямая ссылка |
| GET   | `/api/preload`           | `ids` (через `,`)                                  | прогрев кэша ссылок |
| GET   | `/api/lyrics`            | `track`, `artist`                                  | текст песни (lrclib) |
| GET   | `/api/playlist`          | `url`                                              | импорт плейлиста YouTube / Spotify |
| GET   | `/api/soundcloud/import` | `url`                                              | импорт плейлиста / лайков SoundCloud |

ID треков: 11‑символьный YouTube ID либо `sc_<id>` для SoundCloud.

### Аккаунты (локальный JWT)

| Метод | Путь                         | Тело                              |
| ----- | ---------------------------- | --------------------------------- |
| POST  | `/api/auth/register`         | `{ email, password, username? }`  |
| POST  | `/api/auth/login`            | `{ email, password }`             |
| GET   | `/api/auth/me`               | заголовок `Authorization: Bearer <token>` |
| POST  | `/api/auth/forgot-password`  | `{ email }`                       |
| POST  | `/api/auth/reset-password`   | `{ email, code, newPassword }`    |
| POST  | `/api/auth/update-password`  | `{ newPassword }` + Bearer        |
| POST  | `/api/auth/logout`           | —                                 |

### Синхронизация

`GET /api/sync/get`, `POST /api/sync/push` — пока заглушки (локальный режим).

## Firebase

Облачные аккаунты и мастерская тем используют Firebase напрямую с клиента; сервер лишь отдаёт
Web Config через `/api/firebase/config`. Правила публикуются командой:

```bash
npx firebase-tools@15.26.0 login
npm run deploy:firestore-rules
```

## Разработка

```bash
npm test             # node --test
npm run lint
npm run format
```
