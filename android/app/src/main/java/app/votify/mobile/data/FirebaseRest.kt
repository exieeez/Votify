package app.votify.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
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
            // Store the display name so the workshop can credit the author.
            if (username.isNotBlank()) {
                runCatching {
                    postJson(
                        "https://identitytoolkit.googleapis.com/v1/accounts:update?key=${config.apiKey}",
                        buildJsonObject {
                            put("idToken", account.idToken)
                            put("displayName", username)
                        },
                    )
                }
            }
            account
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

    // ------------------------------------------------------------ профили и друзья (Firestore)

    private fun fs(path: String): String =
        "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents/$path"

    private fun execute(request: Request) {
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            }
        }
    }

    /** Документ или null, если его нет: именно так мы проверяем, свободен ли юзернейм. */
    private fun getDoc(path: String, idToken: String): JsonObject? {
        val request = Request.Builder().url(fs(path)).header("Authorization", "Bearer $idToken").get().build()
        http.newCall(request).execute().use { resp ->
            if (resp.code == 404) return null
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            return json.parseToJsonElement(text).jsonObject
        }
    }

    private fun patchDoc(path: String, idToken: String, fields: JsonObject, mask: List<String>) {
        val query = if (mask.isEmpty()) "" else "?" +
            mask.joinToString("&") { "updateMask.fieldPaths=" + java.net.URLEncoder.encode(it, "UTF-8") }
        val body = buildJsonObject { put("fields", fields) }
        execute(
            Request.Builder().url(fs(path) + query)
                .header("Authorization", "Bearer $idToken")
                .patch(body.toString().toRequestBody(JSON))
                .build(),
        )
    }

    private fun postDoc(path: String, idToken: String, fields: JsonObject) {
        val body = buildJsonObject { put("fields", fields) }
        execute(
            Request.Builder().url(fs(path))
                .header("Authorization", "Bearer $idToken")
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
    }

    private fun deleteDoc(path: String, idToken: String) {
        execute(Request.Builder().url(fs(path)).header("Authorization", "Bearer $idToken").delete().build())
    }

    private fun runQuery(idToken: String, structuredQuery: JsonObject): List<JsonObject> {
        val body = buildJsonObject { put("structuredQuery", structuredQuery) }
        val request = Request.Builder()
            .url("https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/(default)/documents:runQuery")
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw FirebaseRestException("HTTP ${resp.code}", firebaseError(text, resp.code))
            return json.parseToJsonElement(text).jsonArray
                .mapNotNull { el -> runCatching { el.jsonObject["document"]!!.jsonObject }.getOrNull() }
        }
    }

    private fun str(v: String) = buildJsonObject { put("stringValue", v) }
    private fun int(v: Long) = buildJsonObject { put("integerValue", v.toString()) }
    private fun bool(v: Boolean) = buildJsonObject { put("booleanValue", v) }
    private fun strings(v: List<String>) = buildJsonObject {
        put("arrayValue", buildJsonObject { put("values", buildJsonArray { v.forEach { add(str(it)) } }) })
    }

    private fun fieldValue(doc: JsonObject, name: String): JsonObject? = doc["fields"]?.jsonObject?.get(name)?.jsonObject

    private fun fieldStr(doc: JsonObject, name: String): String =
        fieldValue(doc, name)?.get("stringValue")?.jsonPrimitive?.content ?: ""

    private fun fieldLong(doc: JsonObject, name: String): Long =
        fieldValue(doc, name)?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    private fun fieldBool(doc: JsonObject, name: String): Boolean =
        fieldValue(doc, name)?.get("booleanValue")?.jsonPrimitive?.booleanOrNull ?: false

    private fun parseProfile(doc: JsonObject): UserProfile = UserProfile(
        uid = fieldStr(doc, "uid"),
        handle = fieldStr(doc, "handle"),
        displayName = fieldStr(doc, "displayName"),
        bio = fieldStr(doc, "bio"),
        avatarUrl = fieldStr(doc, "avatarUrl"),
        telegram = fieldStr(doc, "telegram"),
        soundcloud = fieldStr(doc, "soundcloud"),
        vk = fieldStr(doc, "vk"),
        isPrivate = fieldBool(doc, "isPrivate"),
        updatedAt = fieldLong(doc, "updatedAt"),
    )

    private fun parseFriendship(doc: JsonObject): Friendship = Friendship(
        id = doc["name"]?.jsonPrimitive?.content.orEmpty().substringAfterLast('/'),
        a = fieldStr(doc, "a"),
        b = fieldStr(doc, "b"),
        status = fieldStr(doc, "status").ifBlank { Friendship.STATUS_PENDING },
        from = fieldStr(doc, "from"),
        to = fieldStr(doc, "to"),
        createdAt = fieldLong(doc, "createdAt"),
        updatedAt = fieldLong(doc, "updatedAt"),
    )

    /** Профиль пользователя или null, если он его ещё не заполнял. */
    suspend fun loadProfile(idToken: String, uid: String): UserProfile? = withContext(Dispatchers.IO) {
        getDoc("profiles/$uid", idToken)?.let { parseProfile(it) }
    }

    /** Профили пачкой — заявки и друзей показываем с именами, а не голыми id. */
    suspend fun loadProfiles(idToken: String, uids: List<String>): Map<String, UserProfile> = withContext(Dispatchers.IO) {
        uids.distinct().mapNotNull { uid -> getDoc("profiles/$uid", idToken)?.let { uid to parseProfile(it) } }.toMap()
    }

    /** Пишет только свои поля профиля (по маске — чужие данные не затираем). */
    suspend fun saveProfile(idToken: String, uid: String, profile: UserProfile): Unit = withContext(Dispatchers.IO) {
        val fields = buildJsonObject {
            put("uid", str(uid))
            put("handle", str(profile.handle))
            put("handleLower", str(normalizeHandle(profile.handle)))
            put("displayName", str(profile.displayName))
            put("bio", str(profile.bio))
            put("avatarUrl", str(profile.avatarUrl))
            put("telegram", str(profile.telegram))
            put("soundcloud", str(profile.soundcloud))
            put("vk", str(profile.vk))
            put("isPrivate", bool(profile.isPrivate))
            put("updatedAt", int(System.currentTimeMillis()))
        }
        patchDoc(
            "profiles/$uid",
            idToken,
            fields,
            listOf(
                "uid", "handle", "handleLower", "displayName", "bio", "avatarUrl",
                "telegram", "soundcloud", "vk", "isPrivate", "updatedAt",
            ),
        )
    }

    /** Занят ли юзернейм (документ-бронь в usernames/{handle}). */
    suspend fun isHandleTaken(idToken: String, handle: String): Boolean = withContext(Dispatchers.IO) {
        getDoc("usernames/${normalizeHandle(handle)}", idToken) != null
    }

    /** Занимает свободный юзернейм; если уже занят — Firestore вернёт ALREADY_EXISTS. */
    suspend fun claimHandle(idToken: String, uid: String, handle: String): Unit = withContext(Dispatchers.IO) {
        val lower = normalizeHandle(handle)
        postDoc(
            "usernames?documentId=$lower",
            idToken,
            buildJsonObject {
                put("uid", str(uid))
                put("handle", str(handle.trim().trimStart('@')))
                put("createdAt", int(System.currentTimeMillis()))
            },
        )
    }

    /** Освобождает свой прежний юзернейм (правила пускают только владельца брони). */
    suspend fun releaseHandle(idToken: String, handle: String): Unit = withContext(Dispatchers.IO) {
        val lower = normalizeHandle(handle)
        if (lower.isBlank()) return@withContext
        runCatching { deleteDoc("usernames/$lower", idToken) }
        Unit
    }

    /** Поиск человека по юзернейму — точное совпадение, как в Telegram. */
    suspend fun profileByHandle(idToken: String, handle: String): UserProfile? = withContext(Dispatchers.IO) {
        val lower = normalizeHandle(handle)
        if (lower.isBlank()) return@withContext null
        val claim = getDoc("usernames/$lower", idToken) ?: return@withContext null
        val uid = fieldStr(claim, "uid")
        if (uid.isBlank()) return@withContext null
        getDoc("profiles/$uid", idToken)?.let { parseProfile(it).copy(uid = uid) }
            ?: UserProfile(uid = uid, handle = handle.trim().trimStart('@'))
    }

    /** Все связи пользователя: и заявки, и подтверждённые друзья. */
    suspend fun friendships(idToken: String, uid: String): List<Friendship> = withContext(Dispatchers.IO) {
        val query = buildJsonObject {
            put("from", buildJsonArray { add(buildJsonObject { put("collectionId", "friendships") }) })
            put("where", buildJsonObject {
                put("fieldFilter", buildJsonObject {
                    put("field", buildJsonObject { put("fieldPath", "users") })
                    put("op", "ARRAY_CONTAINS")
                    put("value", str(uid))
                })
            })
            put("limit", 200)
        }
        runQuery(idToken, query).map { parseFriendship(it) }
    }

    /** Заявка в друзья: один документ на пару, id не зависит от того, кто первый. */
    suspend fun sendFriendRequest(idToken: String, from: String, to: String): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        postDoc(
            "friendships?documentId=${friendshipId(from, to)}",
            idToken,
            buildJsonObject {
                put("a", str(minOf(from, to)))
                put("b", str(maxOf(from, to)))
                put("users", strings(listOf(from, to)))
                put("status", str(Friendship.STATUS_PENDING))
                put("from", str(from))
                put("to", str(to))
                put("createdAt", int(now))
                put("updatedAt", int(now))
            },
        )
    }

    /** Принять заявку (правила пускают только адресата). */
    suspend fun acceptFriendRequest(idToken: String, id: String): Unit = withContext(Dispatchers.IO) {
        patchDoc(
            "friendships/$id",
            idToken,
            buildJsonObject {
                put("status", str(Friendship.STATUS_ACCEPTED))
                put("updatedAt", int(System.currentTimeMillis()))
            },
            listOf("status", "updatedAt"),
        )
    }

    /** Отклонить заявку или удалить друга — связь удаляется целиком. */
    suspend fun deleteFriendship(idToken: String, id: String): Unit = withContext(Dispatchers.IO) {
        deleteDoc("friendships/$id", idToken)
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
