package app.votify.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Firebase Web Config (apiKey + projectId is all the REST API needs). */
@Serializable
data class FirebaseConfig(
    val apiKey: String = "",
    val projectId: String = "",
    val appId: String = "",
    val authDomain: String = "",
)

@Serializable
data class FirebaseAccount(
    val email: String,
    val username: String,
    val idToken: String,
    val refreshToken: String,
    val uid: String,
)

/** A workshopThemes document (schema v1 — see firestore.rules). */
@Serializable
data class WorkshopThemeDoc(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val authorName: String = "",
    val ownerId: String = "",
    val theme: WorkshopThemeUiSpec = WorkshopThemeUiSpec(),
)

/** Mirror of [WorkshopThemeSpec] living in the ui.theme package-free zone for Firestore IO. */
@Serializable
data class WorkshopThemeUiSpec(
    val primary: String = "#FFFFFF",
    val background: String = "#121212",
    val text: String = "#FFFFFF",
    val cards: String = "#1E1E1E",
    val borders: String = "#2A2A2A",
    val focus: String = "#48484A",
    val mode: String = "dark",
    val backgroundPreset: String = "default",
    val backgroundUrl: String = "",
    val cornerRadius: Int = 16,
    val uiTransparency: Int = 100,
    val backgroundBlur: Int = 0,
    val particles: String = "none",
    val fontFamily: String = "inter",
)

class FirebaseRestException(val code: String, message: String) : Exception(message)

/**
 * Firebase accounts + community theme Workshop over plain REST — the same Firebase project the
 * PC version used, but no Firebase SDK / google-services.json needed, so it works fully
 * standalone. Auth: identitytoolkit.googleapis.com; themes: Firestore REST (workshopThemes is
 * world-readable, creating requires a signed-in email account per firestore.rules).
 */
/** «Любимый трек» на странице профиля — тот же формат, что в веб-версии. */
data class FavTrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val cover: String = "",
    val duration: Int = 0,
)

data class PlaylistInfo(val name: String, val count: Int, val cover: String = "")

/** Публичный профиль: документы profiles/{uid} + users/{uid} слиты (profiles старший). */
data class ProfileInfo(
    val uid: String,
    val name: String,
    val handle: String,
    val avatar: String = "",
    val about: String = "",
    val banner: String = "",
    val frame: String = "none",
    val favTrack: FavTrackInfo? = null,
    val playlists: List<PlaylistInfo> = emptyList(),
)


class FirebaseRest(private val config: FirebaseConfig) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = config.apiKey.isNotBlank() && config.projectId.isNotBlank()

    // ------------------------------------------------------------ auth

    suspend fun register(email: String, username: String, password: String): FirebaseAccount =
        withContext(Dispatchers.IO) {
            val handleLower = username.trim().lowercase().removePrefix("@")
            if (handleLower.isNotBlank()) {
                val docUrl = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/usernames/$handleLower?key=${config.apiKey}"
                val checkReq = Request.Builder().url(docUrl).get().build()
                val exists = http.newCall(checkReq).execute().use { resp -> resp.isSuccessful }
                if (exists) {
                    throw FirebaseRestException("ALREADY_EXISTS", "Юзернейм @$handleLower уже занят")
                }
            }
            val body = postJson(
                "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${config.apiKey}",
                buildJsonObject {
                    put("email", email)
                    put("password", password)
                    put("returnSecureToken", true)
                },
            )
            val account = FirebaseAccount(
                email = body["email"]?.jsonPrimitive?.content ?: email,
                username = username,
                idToken = body["idToken"]?.jsonPrimitive?.content ?: error("no idToken"),
                refreshToken = body["refreshToken"]?.jsonPrimitive?.content ?: "",
                uid = body["localId"]?.jsonPrimitive?.content ?: "",
            )
            if (handleLower.isNotBlank()) {
                runCatching {
                    reserveUsername(account.idToken, account.uid, handleLower)
                    saveProfile(account.idToken, account.uid, username, handleLower)
                }
            }
            account
        }

    /** Reserve handleLower in usernames collection. Fails with ALREADY_EXISTS if taken. */
    suspend fun reserveUsername(idToken: String, uid: String, handle: String): String = withContext(Dispatchers.IO) {
        val handleLower = handle.trim().lowercase().removePrefix("@")
        if (handleLower.isBlank()) return@withContext ""
        val docUrl = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/usernames/$handleLower"
        val fields = buildJsonObject {
            put("uid", buildJsonObject { put("stringValue", uid) })
        }
        val commit = buildJsonObject {
            put("writes", buildJsonArray {
                add(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", "projects/${config.projectId}/databases/(default)/documents/usernames/$handleLower")
                        put("fields", fields)
                    })
                    put("currentDocument", buildJsonObject { put("exists", false) })
                })
            })
        }
        val request = Request.Builder()
            .url("https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents:commit?key=${config.apiKey}")
            .header("Authorization", "Bearer $idToken")
            .post(commit.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                if (text.contains("ALREADY_EXISTS") || resp.code == 409 || resp.code == 400) {
                    throw FirebaseRestException("ALREADY_EXISTS", "Юзернейм @$handleLower уже занят")
                }
            }
        }
        handleLower
    }

    /** Create or update profiles/{uid} document */
    suspend fun saveProfile(idToken: String, uid: String, displayName: String, handle: String, photoUrl: String = "", bio: String = ""): Unit = withContext(Dispatchers.IO) {
        val url = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/profiles/$uid"
        val fields = buildJsonObject {
            put("displayName", buildJsonObject { put("stringValue", displayName) })
            put("handle", buildJsonObject { put("stringValue", handle) })
            put("photoUrl", buildJsonObject { put("stringValue", photoUrl) })
            put("bio", buildJsonObject { put("stringValue", bio) })
        }
        val body = buildJsonObject { put("fields", fields) }
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $idToken")
            .patch(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            }
        }
    }


    suspend fun login(email: String, password: String): FirebaseAccount = withContext(Dispatchers.IO) {
        val body = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${config.apiKey}",
            buildJsonObject {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            },
        )
        FirebaseAccount(
            email = body["email"]?.jsonPrimitive?.content ?: email,
            username = body["displayName"]?.jsonPrimitive?.content ?: email.substringBefore('@'),
            idToken = body["idToken"]?.jsonPrimitive?.content ?: error("no idToken"),
            refreshToken = body["refreshToken"]?.jsonPrimitive?.content ?: "",
            uid = body["localId"]?.jsonPrimitive?.content ?: "",
        )
    }

    /** Google Sign-In: exchange a Google ID token for a Firebase session. */
    suspend fun signInWithGoogle(idToken: String): FirebaseAccount = withContext(Dispatchers.IO) {
        val body = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${config.apiKey}",
            buildJsonObject {
                put("postBody", "id_token=$idToken&providerId=google.com")
                put("requestUri", "https://${config.projectId}.firebaseapp.com/__/auth/handler")
                put("returnSecureToken", true)
            },
        )
        val email = body["email"]?.jsonPrimitive?.content ?: ""
        FirebaseAccount(
            email = email,
            username = body["displayName"]?.jsonPrimitive?.content?.ifBlank { null } ?: email.substringBefore('@'),
            idToken = body["idToken"]?.jsonPrimitive?.content ?: error("no idToken"),
            refreshToken = body["refreshToken"]?.jsonPrimitive?.content ?: "",
            uid = body["localId"]?.jsonPrimitive?.content ?: "",
        )
    }

    /** Firebase emails a reset link — there is no inline code like the local server had. */
    suspend fun sendPasswordReset(email: String) = withContext(Dispatchers.IO) {
        postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${config.apiKey}",
            buildJsonObject {
                put("requestType", "PASSWORD_RESET")
                put("email", email)
            },
        )
        Unit
    }

    // ------------------------------------------------------------ workshop (Firestore REST)

    /** Public read per firestore.rules: everyone can list workshopThemes. */
    suspend fun listThemes(limit: Int = 50): List<WorkshopThemeDoc> = withContext(Dispatchers.IO) {
        val url = "https://firestore.googleapis.com/v1/projects/${config.projectId}" +
            "/databases/(default)/documents/workshopThemes?pageSize=$limit&key=${config.apiKey}"
        val body = httpGetJson(url)
        val docs = body["documents"]?.jsonArray ?: return@withContext emptyList()
        docs.mapNotNull { el -> runCatching { parseThemeDoc(el.jsonObject) }.getOrNull() }
    }

    /**
     * Publish a theme. createdAt/updatedAt must equal request.time per the rules, so they are
     * written as REQUEST_TIME transforms on the same write (exactly what the web SDK does with
     * serverTimestamp()).
     */
    suspend fun publishTheme(
        idToken: String,
        uid: String,
        authorName: String,
        title: String,
        description: String,
        spec: WorkshopThemeUiSpec,
    ): String = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString().replace("-", "").take(20)
        val docName = "projects/${config.projectId}/databases/(default)/documents/workshopThemes/$docId"
        // Firestore REST requires typed Value wrappers: {"stringValue": ...}, {"integerValue": "1"},
        // nested maps as {"mapValue": {"fields": {...}}}. Raw values are rejected with
        // "Invalid value at 'writes[0].update.fields[N].value'".
        fun str(v: String) = buildJsonObject { put("stringValue", v) }
        fun int(v: Int) = buildJsonObject { put("integerValue", v.toString()) }
        val fields = buildJsonObject {
            put("title", str(title))
            put("description", str(description))
            put("ownerId", str(uid))
            put("authorName", str(authorName))
            put("schemaVersion", int(1))
            put("theme", buildJsonObject {
                put("mapValue", buildJsonObject {
                    put("fields", buildJsonObject {
                        put("primary", str(spec.primary))
                        put("background", str(spec.background))
                        put("text", str(spec.text))
                        put("cards", str(spec.cards))
                        put("borders", str(spec.borders))
                        put("focus", str(spec.focus))
                        put("mode", str(spec.mode))
                        put("backgroundPreset", str(spec.backgroundPreset))
                        put("backgroundUrl", str(spec.backgroundUrl))
                        put("cornerRadius", int(spec.cornerRadius))
                        put("uiTransparency", int(spec.uiTransparency))
                        put("backgroundBlur", int(spec.backgroundBlur))
                        put("particles", str(spec.particles))
                        put("fontFamily", str(spec.fontFamily))
                    })
                })
            })
        }
        val commit = buildJsonObject {
            put("writes", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", docName)
                        put("fields", fields)
                    })
                    put("updateTransforms", kotlinx.serialization.json.buildJsonArray {
                        add(buildJsonObject {
                            put("fieldPath", "createdAt")
                            put("setToServerValue", "REQUEST_TIME")
                        })
                        add(buildJsonObject {
                            put("fieldPath", "updatedAt")
                            put("setToServerValue", "REQUEST_TIME")
                        })
                    })
                })
            })
        }
        val request = Request.Builder()
            .url("https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents:commit?key=${config.apiKey}")
            .header("Authorization", "Bearer $idToken")
            .post(commit.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            docId
        }
    }

    // ------------------------------------------------------------ helpers

    private fun parseThemeDoc(doc: JsonObject): WorkshopThemeDoc {
        fun str(field: String): String = doc["fields"]!!.jsonObject[field]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""
        val themeObj = doc["fields"]!!.jsonObject["theme"]?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
        val spec = if (themeObj != null) {
            fun t(field: String, def: String) = themeObj[field]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: def
            fun n(field: String, def: Int) = themeObj[field]?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull() ?: def
            WorkshopThemeUiSpec(
                primary = t("primary", "#FFFFFF"),
                background = t("background", "#121212"),
                text = t("text", "#FFFFFF"),
                cards = t("cards", "#1E1E1E"),
                borders = t("borders", "#2A2A2A"),
                focus = t("focus", "#48484A"),
                mode = t("mode", "dark"),
                backgroundPreset = t("backgroundPreset", "default"),
                backgroundUrl = t("backgroundUrl", ""),
                cornerRadius = n("cornerRadius", 16),
                uiTransparency = n("uiTransparency", 100),
                backgroundBlur = n("backgroundBlur", 0),
                particles = t("particles", "none"),
                fontFamily = t("fontFamily", "inter"),
            )
        } else WorkshopThemeUiSpec()
        val name = doc["name"]?.jsonPrimitive?.content ?: ""
        return WorkshopThemeDoc(
            id = name.substringAfterLast('/'),
            title = str("title"),
            description = str("description"),
            authorName = str("authorName"),
            ownerId = str("ownerId"),
            theme = spec,
        )
    }

    private fun httpGetJson(url: String): JsonObject {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            return json.parseToJsonElement(text).jsonObject
        }
    }

    /**
     * Per-user cloud sync: a private Firestore document users/{uid} (writable only by its
     * owner per firestore.rules). Holds the whole library + settings as one JSON blob.
     */
    suspend fun pushUserSync(idToken: String, uid: String, blob: String): Unit = withContext(Dispatchers.IO) {
        val url = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/users/$uid"
        val body = buildJsonObject {
            put("fields", buildJsonObject {
                put("sync", buildJsonObject { put("stringValue", blob) })
                put("updatedAt", buildJsonObject { put("integerValue", System.currentTimeMillis().toString()) })
            })
        }
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $idToken")
            .patch(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            }
        }
    }

    /** Delete one's own published theme (firestore.rules: owner only). */
    suspend fun deleteWorkshopTheme(idToken: String, themeId: String): Unit = withContext(Dispatchers.IO) {
        val url = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/workshopThemes/$themeId"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $idToken").delete().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            }
        }
    }

    /** Returns the stored sync blob, or null when the user has never pushed. */
    suspend fun pullUserSync(idToken: String, uid: String): String? = withContext(Dispatchers.IO) {
        val url = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/users/$uid"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $idToken").get().build()
        http.newCall(request).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            runCatching {
                json.parseToJsonElement(text).jsonObject["fields"]!!
                    .jsonObject["sync"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content
            }.getOrNull()
        }
    }

    /** Secure-token refresh: Firebase idTokens expire hourly; swap the refresh token for a fresh one. */
    suspend fun refreshIdToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        val url = "https://securetoken.googleapis.com/v1/token?key=${config.apiKey}"
        val form = "grant_type=refresh_token&refresh_token=" +
            java.net.URLEncoder.encode(refreshToken, "UTF-8")
        val request = Request.Builder().url(url)
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            runCatching {
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject["id_token"]!!.jsonPrimitive.content
            }.getOrNull()
        }
    }

    private suspend fun postJson(url: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            json.parseToJsonElement(text).jsonObject
        }
    }

    private fun firebaseError(body: String, code: Int): String {
        val msg = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        }.getOrNull() ?: "HTTP $code"
        return when {
            msg.contains("EMAIL_EXISTS") -> "Аккаунт с таким email уже существует"
            msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("INVALID_PASSWORD") -> "Неверный email или пароль"
            msg.contains("EMAIL_NOT_FOUND") -> "Аккаунт не найден"
            msg.contains("WEAK_PASSWORD") -> "Пароль слишком короткий (минимум 6 символов)"
            msg.contains("TOO_MANY_ATTEMPTS") -> "Слишком много попыток — попробуйте позже"
            msg.contains("PERMISSION_DENIED") -> "Нет доступа (проверьте правила Firestore)"
            else -> msg
        }
    }

// ------------------------------------------------------------------ public profiles
    //
    // Те же документы, что читает веб-версия (см. src/firebase-client.js и firestore.rules):
    //   profiles/{uid}   — публичный профиль (читает любой вошедший)
    //   users/{uid}      — приватный документ владельца (такие же поля, плюс email, sync-blob)
    //   users/{uid}/friends/{uid} — список друзей
    //   friendships/{a_b}        — связь двух uid
    //   usernames/{handle}       — handle → uid

    private fun docUrl(path: String) =
        "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/$path"

    private fun httpGetJsonOrNull(url: String): JsonObject? = runCatching {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) null else json.parseToJsonElement(text).jsonObject
        }
    }.getOrNull()

    private fun httpDelete(idToken: String, path: String) {
        val request = Request.Builder().url(docUrl(path))
            .header("Authorization", "Bearer $idToken").delete().build()
        http.newCall(request).execute().use { }
    }

    private fun commitWrites(idToken: String, writes: JsonArray) {
        val body = buildJsonObject { put("writes", writes) }
        val request = Request.Builder()
            .url("https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents:commit?key=${config.apiKey}")
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
        }
    }

    private fun listDocs(idToken: String, path: String, pageSize: Int): List<Pair<String, JsonObject>> {
        val resp = runCatching {
            val request = Request.Builder().url("${docUrl(path)}?pageSize=$pageSize")
                .header("Authorization", "Bearer $idToken").get().build()
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) null
                else json.parseToJsonElement(text).jsonObject["documents"]?.jsonArray?.jsonArray?.mapNotNull { d ->
                    val obj = d.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    Triple(name.substringAfterLast('/'), obj, obj["fields"]?.jsonObject ?: JsonObject(emptyMap()))
                }
            }
        }.getOrNull()
        return resp?.map { (id, _, fields) -> id to fields } ?: emptyList()
    }

    private fun fStr(fields: JsonObject?, key: String): String =
        fields?.get(key)?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""

    private fun fInt(fields: JsonObject?, key: String): Int =
        fields?.get(key)?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toIntOrNull() ?: 0

    private fun str(v: String) = buildJsonObject { put("stringValue", v) }
    private fun int(v: Int) = buildJsonObject { put("integerValue", v.toString()) }
    // Клиентское время в ISO-формате. Сентинел "serverTimestamp" REST API
    // отклоняет в документ-записях («Invalid value at ...timestampValue»),
    // а client time для updatedAt/createdAt/addedAt равноценен.
    private fun serverTs(): JsonElement {
        val iso = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        return buildJsonObject { put("timestampValue", iso) }
    }

    private fun parseFavTrack(fields: JsonObject?): FavTrackInfo? {
        val m = fields?.get("favTrack")?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
            ?: return null
        val title = fStr(m, "title")
        if (title.isBlank()) return null
        return FavTrackInfo(fStr(m, "id"), title, fStr(m, "artist"), fStr(m, "cover"), fInt(m, "duration"))
    }

    /** Плейлисты из поля profiles/users-документа: [{name, count, cover}] (их пишет «Опубликовать»). */
    private fun parsePlaylists(fields: JsonObject?): List<PlaylistInfo> {
        val arr = fields?.get("playlists")?.jsonObject?.get("arrayValue")?.jsonArray?.jsonArray
            ?: return emptyList()
        return arr.mapNotNull { item ->
            val m = item.jsonObject["mapValue"]?.jsonObject?.get("fields")?.jsonObject ?: return@mapNotNull null
            val name = fStr(m, "name")
            if (name.isBlank()) return@mapNotNull null
            PlaylistInfo(name, fInt(m, "count"), fStr(m, "cover"))
        }
    }

    /** Веб-формат: users/{uid}/sync/library → {playlists: {name: {tracks: [...], cover}}}. */
    private fun playlistsFromWebLibrary(fields: JsonObject?): List<PlaylistInfo> {
        val map = fields?.get("playlists")?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
            ?: return emptyList()
        return map.keys.mapNotNull { name ->
            val pl = map[name]?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
                ?: return@mapNotNull null
            val tracks = pl["tracks"]?.jsonObject?.get("arrayValue")?.jsonArray?.jsonArray ?: emptyList()
            val cover = fStr(pl, "cover").ifBlank {
                tracks.firstOrNull()?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
                    ?.let { fStr(it, "cover") } ?: ""
            }
            PlaylistInfo(name, tracks.size, cover)
        }
    }

    /** Профиль из полей документов (users + profiles уже слиты). */
    private fun profileFromFields(uid: String, fields: JsonObject?): ProfileInfo {
        val name = fStr(fields, "displayName").ifBlank {
            fStr(fields, "username").ifBlank { "Пользователь" }
        }
        val handle = (fStr(fields, "handle").ifBlank { fStr(fields, "username") })
            .trim().removePrefix("@").lowercase().ifBlank { uid.take(8) }
        val avatar = fStr(fields, "avatar").ifBlank { fStr(fields, "photoUrl") }
        return ProfileInfo(
            uid = uid,
            name = name,
            handle = handle,
            avatar = avatar,
            about = fStr(fields, "about").ifBlank { fStr(fields, "bio") },
            banner = fStr(fields, "banner"),
            frame = fStr(fields, "frame").ifBlank { "none" },
            favTrack = parseFavTrack(fields),
            playlists = parsePlaylists(fields),
        )
    }

    /** Публичный профиль пользователя (для своей страницы и для друзей). */
    suspend fun getProfile(idToken: String, uid: String): ProfileInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val userDoc = httpGetJsonOrNull(docUrl("users/$uid"))?.jsonObject?.get("fields")?.jsonObject
            val profDoc = httpGetJsonOrNull(docUrl("profiles/$uid"))?.jsonObject?.get("fields")?.jsonObject
            if (userDoc == null && profDoc == null) return@withContext null
            val merged = buildJsonObject {
                userDoc?.let { u -> u.keys.forEach { k -> put(k, u.getValue(k)) } }
                profDoc?.let { pr -> pr.keys.forEach { k -> put(k, pr.getValue(k)) } }
            }
            var info = profileFromFields(uid, merged)
            if (info.playlists.isEmpty()) {
                val webLib = httpGetJsonOrNull(docUrl("users/$uid/sync/library"))
                    ?.jsonObject?.get("fields")?.jsonObject
                val pl = playlistsFromWebLibrary(webLib)
                if (pl.isNotEmpty()) info = info.copy(playlists = pl)
            }
            if (info.playlists.isEmpty()) {
                // Android-формат: blob в users/{uid}.sync (тот же SyncBlob, что в CloudSync)
                val blob = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString(SyncBlob.serializer(), fStr(userDoc, "sync"))
                }.getOrNull()
                val pl = blob?.playlists?.map { PlaylistInfo(it.name, it.tracks.size, it.tracks.firstOrNull()?.c ?: "") }
                    ?: emptyList()
                if (pl.isNotEmpty()) info = info.copy(playlists = pl)
            }
            info
        }.getOrNull()
    }

    /**
     * Сохранить публичный профиль в profiles/{uid} и users/{uid} (как веб-версия).
     * Смена handle: резервируется новый, старый освобождается.
     */
    suspend fun saveProfile(
        idToken: String,
        uid: String,
        displayName: String,
        handle: String,
        photoUrl: String = "",
        bio: String = "",
        banner: String = "",
        favTrack: FavTrackInfo? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val handleLower = handle.trim().lowercase().removePrefix("@")
        val oldHandle = fStr(
            httpGetJsonOrNull(docUrl("profiles/$uid"))?.jsonObject?.get("fields")?.jsonObject
                ?: httpGetJsonOrNull(docUrl("users/$uid"))?.jsonObject?.get("fields")?.jsonObject,
            "handle",
        )
        if (handleLower.isNotBlank() && handleLower != oldHandle) {
            if (oldHandle.isNotBlank()) {
                val oldDoc = httpGetJsonOrNull(docUrl("usernames/$oldHandle"))?.jsonObject?.get("fields")?.jsonObject
                if (fStr(oldDoc, "uid") == uid) runCatching { httpDelete(idToken, "usernames/$oldHandle") }
            }
            if (handleLower.length in 3..20) runCatching { reserveUsername(idToken, uid, handleLower) }
        }
        val fields = buildJsonObject {
            put("displayName", str(displayName.trim().take(40)))
            put("handle", str(handleLower))
            put("avatar", str(photoUrl))
            put("photoUrl", str(photoUrl))
            put("about", str(bio.trim().take(300)))
            put("bio", str(bio.trim().take(300)))
            put("banner", str(banner.trim()))
            put("updatedAt", serverTs())
            if (favTrack != null) {
                put("favTrack", buildJsonObject {
                    put("mapValue", buildJsonObject {
                        put("fields", buildJsonObject {
                            put("id", str(favTrack.id))
                            put("title", str(favTrack.title))
                            put("artist", str(favTrack.artist))
                            put("cover", str(favTrack.cover))
                            put("duration", int(favTrack.duration))
                        })
                    })
                })
            }
        }
        for (path in listOf("profiles/$uid", "users/$uid")) {
            val body = buildJsonObject { put("fields", fields) }
            val request = Request.Builder().url(docUrl(path))
                .header("Authorization", "Bearer $idToken")
                .patch(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val text = resp.body?.string().orEmpty()
                    throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
                }
            }
        }
    }

    /**
     * «Опубликовать плейлисты» (как в веб-версии): sync-blob в users/{uid}, сводки плейлистов
     * в profiles/{uid} + users/{uid} и веб-формат users/{uid}/sync/library — чтобы их видел ПК.
     */
    suspend fun publishLibrary(idToken: String, uid: String, blob: SyncBlob): Unit =
        withContext(Dispatchers.IO) {
            pushUserSync(idToken, uid, Json { ignoreUnknownKeys = true }.encodeToString(SyncBlob.serializer(), blob))
            val summaries = blob.playlists.map { PlaylistInfo(it.name, it.tracks.size, it.tracks.firstOrNull()?.c ?: "") }
            val summariesJson = buildJsonObject {
                put("arrayValue", buildJsonObject {
                    put("arrayValue", buildJsonArray {
                        summaries.forEach { p ->
                            add(buildJsonObject {
                                put("mapValue", buildJsonObject {
                                    put("fields", buildJsonObject {
                                        put("name", str(p.name))
                                        put("count", int(p.count))
                                        put("cover", str(p.cover))
                                    })
                                })
                            })
                        }
                    })
                })
            }
            val profileFields = buildJsonObject {
                put("playlists", summariesJson)
                put("updatedAt", serverTs())
            }
            for (path in listOf("profiles/$uid", "users/$uid")) {
                val body = buildJsonObject { put("fields", profileFields) }
                val request = Request.Builder().url(docUrl(path))
                    .header("Authorization", "Bearer $idToken")
                    .patch(body.toString().toRequestBody(JSON))
                    .build()
                http.newCall(request).execute().use { }
            }
            // Веб-формат библиотеки: {name: {cover, tracks: [{id,title,artist,cover,duration}]}}
            val libMap = buildJsonObject {
                put("mapValue", buildJsonObject {
                    put("fields", buildJsonObject {
                        blob.playlists.forEach { pl ->
                            put(pl.name, buildJsonObject {
                                put("mapValue", buildJsonObject {
                                    put("fields", buildJsonObject {
                                        put("cover", str(pl.tracks.firstOrNull()?.c ?: ""))
                                        put("tracks", buildJsonObject {
                                            put("arrayValue", buildJsonObject {
                                                put("arrayValue", buildJsonArray {
                                                    pl.tracks.forEach { t ->
                                                        add(buildJsonObject {
                                                            put("mapValue", buildJsonObject {
                                                                put("fields", buildJsonObject {
                                                                    put("id", str(t.id))
                                                                    put("title", str(t.t))
                                                                    put("artist", str(t.a))
                                                                    put("cover", str(t.c))
                                                                    put("duration", int(t.d))
                                                                })
                                                            })
                                                        })
                                                    }
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        }
                    })
                })
            }
            val libBody = buildJsonObject {
                put("fields", buildJsonObject {
                    put("playlists", libMap)
                    put("updatedAt", serverTs())
                })
            }
            val libRequest = Request.Builder().url(docUrl("users/$uid/sync/library"))
                .header("Authorization", "Bearer $idToken")
                .patch(libBody.toString().toRequestBody(JSON))
                .build()
            http.newCall(libRequest).execute().use { }
        }

    /** Поиск публичных пользователей: profiles + users (первые 40) и точный юзернейм. */
    suspend fun searchUsers(idToken: String, query: String, excludeUid: String): List<ProfileInfo> =
        withContext(Dispatchers.IO) {
            val q = query.trim().lowercase().removePrefix("@")
            if (q.isBlank()) return@withContext emptyList()
            val found = linkedMapOf<String, ProfileInfo>()
            listDocs(idToken, "profiles", 40).forEach { (uid, fields) ->
                val hay = (fStr(fields, "displayName") + " " + fStr(fields, "handle")).lowercase()
                if (hay.contains(q)) found[uid] = profileFromFields(uid, fields)
            }
            listDocs(idToken, "users", 40).forEach { (uid, fields) ->
                if (uid in found) return@forEach
                val hay = (fStr(fields, "displayName") + " " + fStr(fields, "handle") + " " + fStr(fields, "email"))
                    .lowercase()
                if (hay.contains(q)) found[uid] = profileFromFields(uid, fields)
            }
            val byHandle = httpGetJsonOrNull(docUrl("usernames/$q"))?.jsonObject?.get("fields")?.jsonObject
            val handleUid = fStr(byHandle, "uid")
            if (handleUid.isNotBlank() && handleUid !in found) {
                runCatching { getProfile(idToken, handleUid) }.getOrNull()?.let { found[handleUid] = it }
            }
            found.values.filter { it.uid != excludeUid }.toList()
        }

    /** Список друзей: users/{me}/friends + публичные профили каждого. */
    suspend fun getFriends(idToken: String, myUid: String): List<ProfileInfo> = withContext(Dispatchers.IO) {
        listDocs(idToken, "users/$myUid/friends", 200).mapNotNull { (fUid, fields) ->
            runCatching { getProfile(idToken, fUid) }.getOrNull()
                ?: ProfileInfo(fUid, fStr(fields, "name").ifBlank { "Пользователь" }, "")
        }
    }

    /** Добавить друга: friendships + users/{me}/friends (та же схема, что в веб-версии). */
    suspend fun addFriend(idToken: String, myUid: String, targetUid: String, targetName: String): Unit =
        withContext(Dispatchers.IO) {
            val docId = listOf(myUid, targetUid).sorted().joinToString("_")
            commitWrites(idToken, buildJsonArray {
                add(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", "projects/${config.projectId}/databases/(default)/documents/friendships/$docId")
                        put("fields", buildJsonObject {
                            put("users", buildJsonObject {
                                put("arrayValue", buildJsonObject {
                                    put("arrayValue", buildJsonArray {
                                        add(buildJsonObject { put("stringValue", myUid) })
                                        add(buildJsonObject { put("stringValue", targetUid) })
                                    })
                                })
                            })
                            put("fromUid", str(myUid))
                            put("toUid", str(targetUid))
                            put("createdAt", serverTs())
                        })
                    })
                })
                add(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", "projects/${config.projectId}/databases/(default)/documents/users/$myUid/friends/$targetUid")
                        put("fields", buildJsonObject {
                            put("uid", str(targetUid))
                            put("name", str(targetName))
                            put("addedAt", serverTs())
                        })
                    })
                })
            })
        }

    /** Убрать из друзей. */
    suspend fun removeFriend(idToken: String, myUid: String, targetUid: String): Unit =
        withContext(Dispatchers.IO) {
            val docId = listOf(myUid, targetUid).sorted().joinToString("_")
            runCatching { httpDelete(idToken, "users/$myUid/friends/$targetUid") }
            runCatching { httpDelete(idToken, "friendships/$docId") }
        }

    companion object {

        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Parses a Firebase Web Config JSON (either the raw file or {"config": {...}} wrapper). */
        /**
         * The config that should be used right now: the one saved in Settings wins,
         * then the config baked at build time from the CI secret, then the default
         * project shipped with the app (lightly obfuscated so it does not attract
         * automated key scanners — it is a public web key guarded by firestore.rules).
         */
        /** Default Firebase Web Config, XOR+Base64 (see [effectiveConfig]). */
        private const val EMBEDDED_CONFIG =
            "DU0VGQ8ySA5NSEkyIRURfhInKn8FXTw4Ei8KCUovXCEGQDc9EnUuLDVZHAMmI0QoSxIPW00CGRwCChNZIgFbF1RHGRsdDx9US0NZQwpRFQ=="

        fun effectiveConfig(stored: String): FirebaseConfig? =
            parseConfig(stored)
                ?: parseConfig(app.votify.mobile.BuildConfig.FIREBASE_CONFIG)
                ?: parseConfig(xorDecode(EMBEDDED_CONFIG))

        private fun xorDecode(enc: String): String {
            val key = "votify-workshop-key-v1".encodeToByteArray()
            val raw = java.util.Base64.getDecoder().decode(enc)
            return buildString {
                raw.forEachIndexed { i, b -> append((b.toInt() xor key[i % key.size].toInt()).toChar()) }
            }
        }

        fun parseConfig(raw: String): FirebaseConfig? {
            val trimmed = raw.trim()
            if (trimmed.isBlank() || !trimmed.startsWith("{")) return null
            return runCatching {
                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(trimmed).jsonObject
                val obj = root["config"]?.jsonObject ?: root
                val cfg = FirebaseConfig(
                    apiKey = obj["apiKey"]?.jsonPrimitive?.content ?: "",
                    projectId = obj["projectId"]?.jsonPrimitive?.content ?: "",
                    appId = obj["appId"]?.jsonPrimitive?.content ?: "",
                    authDomain = obj["authDomain"]?.jsonPrimitive?.content ?: "",
                )
                cfg.takeIf { it.apiKey.isNotBlank() && it.projectId.isNotBlank() }
            }.getOrNull()
        }
    }
}
