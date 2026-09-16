# Аккаунт, юзернеймы и друзья — как всё устроено на самом деле

Этот файл — **единственный источник правды по аккаунту**. Читай его до того, как писать код.
Если чего-то здесь нет — значит этого не существует, и придумывать нельзя: спроси или сначала
добавь реальный слой данных.

Проект: Votify (Electron-десктоп + Android-приложение). Аккаунт — **Firebase Authentication
(Identity Toolkit REST) + Cloud Firestore (REST)**. Никакого своего сервера для профилей и
друзей нет: `server.js`/[`routes/`](../routes/) — это музыка и локальный режим, к профилям они не относятся.

---

## 1. Где что лежит

| Что | Файл |
| --- | --- |
| REST-клиент Firebase (auth, Firestore, синхронизация, профили, друзья) | [`android/app/src/main/java/app/votify/mobile/data/FirebaseRest.kt`](../android/app/src/main/java/app/votify/mobile/data/FirebaseRest.kt) |
| Модели профиля и дружбы, валидация юзернейма | [`android/app/src/main/java/app/votify/mobile/data/Profile.kt`](../android/app/src/main/java/app/votify/mobile/data/Profile.kt) |
| Экран входа/регистрации/восстановления | [`android/app/src/main/java/app/votify/mobile/ui/account/AccountScreen.kt`](../android/app/src/main/java/app/votify/mobile/ui/account/AccountScreen.kt) |
| VM входа (два бэкенда: Firebase и локальный сервер) | [`android/app/src/main/java/app/votify/mobile/ui/account/AccountViewModel.kt`](../android/app/src/main/java/app/votify/mobile/ui/account/AccountViewModel.kt) |
| VM профиля и друзей | [`android/app/src/main/java/app/votify/mobile/ui/account/ProfileViewModel.kt`](../android/app/src/main/java/app/votify/mobile/ui/account/ProfileViewModel.kt) |
| Экран профиля и редактирования (по дизайну Stitch) | `android/app/src/main/java/app/votify/mobile/ui/account/ProfileScreen.kt` |
| Хранение сессии (DataStore) | [`android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt`](../android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt) |
| Навигация и фабрика ViewModel | [`android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt`](../android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt) |
| Правила доступа Firestore | `firestore.rules` (+ тесты [`test/workshop-security.test.js`](../test/workshop-security.test.js)) |
| Дизайн экрана аккаунта | [`design/stitch_user_account_profile_page.zip`](https://github.com/exieeez/Votify/blob/main/design/stitch_user_account_profile_page.zip) (в ветке `main`) |

---

## 2. Авторизация: как получить токен

Два бэкенда, переключаются автоматически (`AccountBackend { Firebase, Server, None }`):

1. **Firebase** — приоритет, если есть конфиг. Конфиг берётся так
   (`FirebaseRest.effectiveConfig`): сохранённый в настройках → запечённый в сборку
   (`BuildConfig.FIREBASE_CONFIG`) → встроенный по умолчанию. Конфиг — обычный Firebase Web
   Config (`apiKey`, `projectId`, `appId`, `authDomain`).
2. **Локальный сервер** (`/api/auth/*`), включается в server-режиме. **В нём нет ни профилей,
   ни друзей** — только JWT для музыки.

Сессия после входа (`SettingsRepository.Account`):

```kotlin
data class Account(
    val email: String,
    val username: String,
    val token: String,       // Firebase idToken (живёт 1 час!) или JWT сервера
    val uid: String = "",    // локальный сервер оставляет пустым
    val refreshToken: String = "",
)
```

Правила работы с токеном:

- У Firebase-аккаунта `uid` заполнен — **только с ним работают профили и друзья**.
- `idToken` протухает через час. Перед каждым обращением к Firestore его надо освежить:
  `POST https://securetoken.googleapis.com/v1/token?key=API_KEY`,
  тело `grant_type=refresh_token&refresh_token=...` (обёртка: `FirebaseRest.refreshIdToken`).
- В Firestore все запросы идут с заголовком `Authorization: Bearer <idToken>`.

---

## 3. Firestore: в приложении **нет** Firebase SDK

Это критично. Android-модуль не подключает `firebase-firestore` — только OkHttp и
`kotlinx.serialization`. Любой `Firebase.firestore`, `FirebaseFirestore.getInstance()`,
`collection("...").document(...)` — это галлюцинация, такого кода быть не может.
Все вызовы идут через методы `FirebaseRest` (внутри — `withContext(Dispatchers.IO)`).

Базовый URL:

```
https://firestore.googleapis.com/v1/projects/<projectId>/databases/(default)/documents
```

### Формат значений

Firestore REST принимает только типизированные значения:

```json
{ "stringValue": "exieeez" }
{ "integerValue": "1757520000000" }
{ "booleanValue": true }
{ "arrayValue": { "values": [ { "stringValue": "uid1" }, { "stringValue": "uid2" } ] } }
{ "mapValue": { "fields": { "key": { "stringValue": "v" } } } }
```

### Операции

| Действие | Запрос |
| --- | --- |
| Прочитать документ | `GET .../documents/<path>` — **404 означает «документа нет», это не ошибка** |
| Создать со своим id | `POST .../documents/usernames?documentId=exieeez` — если документ уже есть, вернётся `ALREADY_EXISTS` |
| Обновить поля точечно | `PATCH .../documents/profiles/<uid>?updateMask.fieldPaths=handle&updateMask.fieldPaths=bio` с телом `{"fields": {...}}` |
| Удалить | `DELETE .../documents/<path>` |
| Запрос | `POST .../documents:runQuery` с телом `{"structuredQuery": {...}}` |

Запрос по массиву (все связи пользователя):

```json
{
  "structuredQuery": {
    "from": [{ "collectionId": "friendships" }],
    "where": {
      "fieldFilter": {
        "field": { "fieldPath": "users" },
        "op": "ARRAY_CONTAINS",
        "value": { "stringValue": "<myUid>" }
      }
    },
    "limit": 200
  }
}
```

Ответ `:runQuery` — **JSON-массив** элементов `{"document": {...}, "readTime": "..."}`.
Элементы без `document` — пропуски, их надо отбрасывать.

Ошибки приходят как `{"error": {"message": "ALREADY_EXISTS: ..."}}`; человеческие тексты
(включая «Нет доступа (проверьте правила Firestore)») формирует `FirebaseRest.firebaseError`.

---

## 4. Модель данных

### `usernames/{handleLower}` — бронь юзернейма (Telegram-стайл)

```
uid:        string   // владелец
handle:     string   // как набрал человек: "Exieeez"
createdAt:  integer  // миллисекунды
```

- id документа = юзернейм **в нижнем регистре** (`normalizeHandle`): `Votify` → `votify`.
- Занять = создать документ. Кто первый создал — того и имя. Второй получит `ALREADY_EXISTS`.
- Сменил имя → занял новое и только потом удалил старую бронь
  (`claimHandle` → `saveProfile` → `releaseHandle`).
- Формат: `^[a-zA-Z0-9_]{5,32}$` (проверяет `isValidHandle`).

### `profiles/{uid}` — публичный профиль

```
uid, handle, handleLower, displayName, bio, avatarUrl,
telegram, soundcloud, vk, isPrivate, updatedAt
```

- `bio` ≤ 150 символов, `displayName` ≤ 40 (`BIO_MAX_LENGTH`, `DISPLAY_NAME_MAX_LENGTH`).
- `avatarUrl` — пока просто https-ссылка на картинку. **Загрузки файлов нет**: Firebase
  Storage в проекте не настроен, поэтому «Изменить фото» = вставить ссылку. Не выдумывай
  `putFile`/`FirebaseStorage` — его нет.

### `friendships/{a_b}` — заявка или дружба

```
a, b:        string   // uid'ы, отсортированы: a <= b
users:       array    // [a, b] — для запроса ARRAY_CONTAINS
status:      string   // "pending" | "accepted"
from, to:    string   // кто отправил, кто должен ответить
createdAt, updatedAt: integer
```

- id = `friendshipId(a, b)` = `min_max`, то есть **один документ на пару**, не зависит от того,
  кто первый отправил заявку.
- Отправить заявку = создать документ со `status = "pending"`.
- Принять = `PATCH status = "accepted"` (по правилам это может только адресат `to`).
- Отклонить заявку или удалить друга = `DELETE` документа.
- Входящие заявки: `status == pending && to == myUid`. Исходящие: `status == pending && from == myUid`.

### `users/{uid}` — приватный бэкап

Отдельная коллекция, не путать с профилем: там одним JSON-блобом лежит библиотека и настройки
(`pushUserSync` / `pullUserSync`). Читать и писать может только владелец.

### `workshopThemes/{themeId}` — Мастерская тем

Публичное чтение, создание только постоянными аккаунтами, обновление запрещено, удаление —
владельцем.

---

## 5. Правила Firestore

Правила живут в `firestore.rules` и **деплоятся отдельной командой**:

```bash
npm run deploy:firestore-rules   # firebase-tools, проект votify-f461a
```

Пока новые правила не задеплоены, приложение будет ловить `PERMISSION_DENIED` — это нормально
на время разработки, но UI должен показывать понятное сообщение, а не пустой список и не
фейковые данные.

Нужные правила (добавь в `match /databases/{database}/documents`):

```js
function isValidHandle(h) {
  return h is string && h.matches('^[a-zA-Z0-9_]{5,32}$');
}

// Юзернейм занимается один раз: создать может любой авторизованный, но только под своим uid.
match /usernames/{handle} {
  allow read: if isSignedIn();
  allow create: if isSignedIn()
    && isValidHandle(handle)
    && request.resource.data.keys().hasOnly(['uid', 'handle', 'createdAt'])
    && request.resource.data.uid == request.auth.uid;
  allow update: if false;
  allow delete: if isSignedIn() && resource.data.uid == request.auth.uid;
}

match /profiles/{uid} {
  allow read: if isSignedIn();
  allow create, update: if isSignedIn() && request.auth.uid == uid
    && request.resource.data.uid == uid
    && request.resource.data.displayName.size() <= 40
    && request.resource.data.bio.size() <= 150
    && (request.resource.data.handle == '' || isValidHandle(request.resource.data.handle))
    && request.resource.data.handleLower == request.resource.data.handle.lower()
    && (!('avatarUrl' in request.resource.data)
        || request.resource.data.avatarUrl == ''
        || request.resource.data.avatarUrl.matches('^https://.+$'));
  allow delete: if isSignedIn() && request.auth.uid == uid;
}

match /friendships/{linkId} {
  // Читать и удалять может только участник пары.
  allow read, delete: if isSignedIn() && request.auth.uid in resource.data.users;
  allow create: if isSignedIn()
    && request.auth.uid in request.resource.data.users
    && request.resource.data.users.size() == 2
    && request.resource.data.from == request.auth.uid
    && request.resource.data.to != request.auth.uid
    && request.resource.data.status == 'pending';
  // Принять заявку может только адресат, и только pending → accepted.
  allow update: if isSignedIn()
    && resource.data.status == 'pending'
    && resource.data.to == request.auth.uid
    && request.resource.data.status == 'accepted';
}
```

Тесты правил — [`test/workshop-security.test.js`](../test/workshop-security.test.js): это проверки текста правил регулярками
(без эмулятора). Если правишь существующие строки — не сломай эти тесты; если добавляешь
коллекции — добавь такие же проверки для них. Запуск: `npm test`.

---

## 6. Что уже реализовано в коде (ветка `arena/01a08af6-votify`)

- `data/Profile.kt` — `UserProfile`, `Friendship` (`pending`/`accepted`, `other(uid)`,
  `isPending`, `isAccepted`), `normalizeHandle`, `isValidHandle`, `friendshipId(a, b)`,
  `BIO_MAX_LENGTH = 150`, `DISPLAY_NAME_MAX_LENGTH = 40`.
- `FirebaseRest.kt` — приватные помощники `fs()`, `getDoc` (404 → `null`), `patchDoc` (с
  `updateMask`), `postDoc`, `deleteDoc`, `runQuery`, сборщики `str/int/bool/strings`,
  парсеры `fieldStr/fieldLong/fieldBool/parseProfile/parseFriendship`, и публичные:
  `loadProfile`, `loadProfiles`, `saveProfile`, `isHandleTaken`, `claimHandle`,
  `releaseHandle`, `profileByHandle`, `friendships`, `sendFriendRequest`,
  `acceptFriendRequest`, `deleteFriendship`.
- `ProfileViewModel.kt` — состояние `ProfileUiState` (profile/friends/incoming/outgoing/
  search/handleState), действия `refresh`, `checkHandle`, `saveProfile`, `findByHandle`,
  `sendRequest`, `acceptRequest`, `declineRequest`, `removeFriend`, `logout`.
  Токен освежается внутри `session()`; без `uid` или без конфига методы честно выходят,
  не рисуя данные.

Осталось: экраны (`ProfileScreen.kt` — профиль, редактирование, друзья), регистрация
`ProfileViewModel` в `AppViewModelFactory` (иначе — краш «Unknown ViewModel») и подключение в
`composable(Routes.ACCOUNT)` в `VotifyRoot.kt`.

---

## 7. Дизайн (Stitch) — чего ждут от экрана

Архив [`design/stitch_user_account_profile_page.zip`](https://github.com/exieeez/Votify/blob/main/design/stitch_user_account_profile_page.zip) лежит только в ветке `main`
(в этой ветке его нет — возьми `git show origin/main:design/stitch_user_account_profile_page.zip`).
Внутри:

- `exieeez_1/code.html`, `exieeez_2/code.html` — **профиль**: аватар ~96–116 dp (шестерёнка
  ведёт в настройки), имя крупно (34 sp), под ним `@юзернейм` серым, кнопка
  «Редактировать профиль» во всю ширину (белая, чёрный текст), статистика
  «N подписчиков / N подписок», ниже — «Публичные плейлисты» (сетка 2×N: обложка, название,
  «N треков»).
- `_1/code.html`, `_2/code.html` — **редактирование**: «Отмена | Редактировать профиль |
  Сохранить», аватар с бейджем камеры, «Изменить фото» / «Удалить фото профиля», поля
  «Имя», «Имя пользователя» (с `@`, зелёной галочкой и подсказкой `music.app/<name>`),
  «О себе» со счётчиком `38/150`, раздел «Ссылки» (Telegram, SoundCloud, ВКонтакте),
  «Приватный профиль» с переключателем, тост «Изменения сохранены».

Палитра (`nocturne_acoustic/DESIGN.md`): фон `#121317`, карточки `#1e1f23`,
поля ввода `#292a2e`, границы/подписи `#8e9192`, текст `#e3e2e7`, акцент (ссылки, успех)
`#53e076`, ошибка `#ffb4ab`.

---

## 8. Что считается заглушкой — запрещено

Не делай так:

- жёстко зашитые списки друзей, `listOf(UserProfile(...))` «для примера», `remember {
  mutableStateOf(fakeFriends) }`;
- липовые счётчики «0 подписчиков / 0 подписок», если реального источника нет;
- `delay(500)` + «загрузка», за которой ничего не происходит;
- `TODO()`/`Unit`-заглушки вместо методов, которые обязаны ходить в сеть;
- вызовы Firebase SDK (см. раздел 3) и выдуманные REST-эндпоинты вида
  `https://votify.app/api/friends`;
- «Аватар загружен в Storage» — Storage не подключён.

Делай так:

- если Firebase не настроен или `uid` пуст (серверный режим) → показать экран входа/честную
  пустоту с объяснением, а не выдуманные данные;
- пока данные грузятся — `CircularProgressIndicator`; при ошибке — текст ошибки из
  `ProfileUiState.error` и кнопка «Повторить»;
- пустой список друзей — это пустой список с подсказкой «Ищи друзей по @юзернейму»;
- весь ввод/вывод — только через `FirebaseRest`; UI не знает про HTTP.

---

## 9. Как проверять изменения

- **Компиляция Android**: локально Java/Gradle нет. Коммит и пуш в ветку → GitHub Actions
  ( workflows «Android CI» и «Release APK»). Готовый APK: тег `android-debug`,
  https://github.com/exieeez/Votify/releases/download/android-debug/Votify-debug.apk
- **Правила Firestore**: `npm test` (проверки текста правил) и `npm run deploy:firestore-rules`.
- **Ручная проверка друзей**: нужен реальный Firebase-аккаунт (uid ≠ пусто). Занять `@name` на
  одном устройстве, найти его на втором, отправить заявку, принять — и наоборот. Без этого
  сценария функция считается непроверенной, а не «работает, потому что компилируется».


---

## 10. Прямые ссылки на файлы (GitHub)

Ветка **`arena/01a08af6-votify`** (дизайн — в ветке `main`). Ссылки «raw» — прямое содержимое файла.

Если ветка недоступна — файлы есть локально по путям из таблицы в разделе 1.

| Что | Файл | Ссылка |
| --- | --- | --- |
| REST-клиент Firebase: auth, Firestore, профили, друзья | `android/app/src/main/java/app/votify/mobile/data/FirebaseRest.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/FirebaseRest.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/FirebaseRest.kt) |
| Модель профиля и дружбы, валидация юзернейма | `android/app/src/main/java/app/votify/mobile/data/Profile.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/Profile.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/Profile.kt) |
| Хранение сессии (Account, DataStore) | `android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/data/SettingsRepository.kt) |
| Экран входа/регистрации/восстановления | `android/app/src/main/java/app/votify/mobile/ui/account/AccountScreen.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/account/AccountScreen.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/account/AccountScreen.kt) |
| VM входа (Firebase / сервер) | `android/app/src/main/java/app/votify/mobile/ui/account/AccountViewModel.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/account/AccountViewModel.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/account/AccountViewModel.kt) |
| VM профиля, юзернейма и друзей | `android/app/src/main/java/app/votify/mobile/ui/account/ProfileViewModel.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/account/ProfileViewModel.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/account/ProfileViewModel.kt) |
| Навигация и фабрика ViewModel | `android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/android/app/src/main/java/app/votify/mobile/ui/VotifyRoot.kt) |
| Правила доступа Firestore | `firestore.rules` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/firestore.rules) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/firestore.rules) |
| Тесты правил | `test/workshop-security.test.js` | [открыть](https://github.com/exieeez/Votify/blob/arena/01a08af6-votify/test/workshop-security.test.js) · [raw](https://raw.githubusercontent.com/exieeez/Votify/arena/01a08af6-votify/test/workshop-security.test.js) |
| Дизайн экрана аккаунта (архив Stitch) | `design/stitch_user_account_profile_page.zip` | [открыть](https://github.com/exieeez/Votify/blob/main/design/stitch_user_account_profile_page.zip) |
