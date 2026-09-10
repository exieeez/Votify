package app.votify.mobile.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

sealed interface SocialFailure {
    data object NoAuth : SocialFailure
    data object NoBackend : SocialFailure
    data object UsernameTaken : SocialFailure
    data class Message(val text: String) : SocialFailure
}

class SocialException(val failure: SocialFailure) : Exception()

private fun FirebaseRestException.httpCode(): Int? =
    code.removePrefix("HTTP ").trim().toIntOrNull()

/** True when the error means a create-precondition failed (name/edge already exists). */
private fun FirebaseRestException.isConflict(): Boolean {
    if (httpCode() == 409) return true
    val m = message ?: ""
    return m.contains("ALREADY_EXISTS") || m.contains("FAILED_PRECONDITION")
}

/**
 * Telegram-like profiles + follows over Firestore REST — the same collections and
 * rules the desktop app uses (usernames, profiles, follows, followRequests).
 *
 * Two REST subtleties worth knowing:
 * - follow edges require createdAt == request.time (firestore.rules), which only a
 *   server transform can produce — edge creates are update+transform commit pairs,
 *   exactly what the SDK batches compile serverTimestamp writes to;
 * - counter nudges are transform-only writes; isCounterNudge() permits them from
 *   any signed-in user, so a bare transform with no updateMask is the whole write.
 */
class SocialRepository(
    private val firebase: FirebaseRest,
    private val settings: SettingsRepository,
) {
    data class Auth(val uid: String, val token: String)

    private suspend fun auth(): Auth? {
        val acc = settings.account.first() ?: return null
        if (!acc.isFirebase || acc.uid.isBlank()) return null
        return Auth(acc.uid, acc.token)
    }

    /** Runs [block], refreshing the hourly idToken once on 401. */
    private suspend fun <T> authed(block: suspend (Auth) -> T): T {
        if (!firebase.isConfigured) throw SocialException(SocialFailure.NoBackend)
        var a = auth() ?: throw SocialException(SocialFailure.NoAuth)
        try {
            return block(a)
        } catch (e: FirebaseRestException) {
            if (e.httpCode() != 401) throw e
            val acc = settings.account.first()
            val fresh = acc?.refreshToken?.takeIf { it.isNotBlank() }
                ?.let { runCatching { firebase.refreshIdToken(it) }.getOrNull() }
                ?: throw e
            settings.setAccount(acc.email, acc.username, fresh, acc.uid, acc.refreshToken)
            a = a.copy(token = fresh)
            return block(a)
        }
    }

    // ------------------------------------------------------------ value helpers

    private fun str(v: String) = buildJsonObject { put("stringValue", v) }
    private fun num(v: Long) = buildJsonObject { put("integerValue", v.toString()) }
    private fun flag(v: Boolean) = buildJsonObject { put("booleanValue", v) }
    private fun mapVal(fields: JsonObject) =
        buildJsonObject { put("mapValue", buildJsonObject { put("fields", fields) }) }

    private fun JsonObject?.s(key: String): String =
        this?.get(key)?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""

    private fun JsonObject?.l(key: String): Long =
        this?.get(key)?.jsonObject?.get("integerValue")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    private fun JsonObject?.b(key: String): Boolean =
        this?.get(key)?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content
            ?.toBooleanStrictOrNull() ?: false

    private fun JsonObject?.m(key: String): JsonObject? =
        this?.get(key)?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject

    private fun JsonObject?.arr(key: String): List<JsonObject> =
        this?.get(key)?.jsonObject?.get("arrayValue")?.jsonObject?.get("values")
            ?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?: emptyList()

    private fun fieldsOf(doc: JsonObject?): JsonObject? = doc?.get("fields")?.jsonObject

    private fun docName(path: String): String =
        "projects/${firebase.firestoreProject}/databases/(default)/documents/$path"

    // ------------------------------------------------------------ reads

    suspend fun fetchPublicProfile(uid: String): PublicProfile? = authed { a ->
        val fields = fieldsOf(firebase.firestoreGet("profiles/$uid", a.token)) ?: return@authed null
        parseProfile(uid, fields)
    }

    suspend fun fetchProfileByUsername(username: String): PublicProfile? = authed { a ->
        val uname = SocialValidate.normalizeUsername(username)
        if (uname.isEmpty()) return@authed null
        val reg = fieldsOf(firebase.firestoreGet("usernames/$uname", a.token)) ?: return@authed null
        val uid = reg.s("uid")
        if (uid.isEmpty()) return@authed null
        fieldsOf(firebase.firestoreGet("profiles/$uid", a.token))?.let { parseProfile(uid, it) }
    }

    private fun parseProfile(uid: String, f: JsonObject): PublicProfile {
        val links = f.m("links")
        return PublicProfile(
            uid = uid,
            displayName = f.s("displayName"),
            username = f.s("username"),
            avatar = f.s("avatar"),
            about = f.s("about"),
            links = SocialLinks(links.s("telegram"), links.s("soundcloud"), links.s("vk")),
            isPrivate = f.b("isPrivate"),
            followersCount = f.l("followersCount").toInt().coerceAtLeast(0),
            followingCount = f.l("followingCount").toInt().coerceAtLeast(0),
            showcase = f.arr("showcase").map { wrapper ->
                val im = wrapper["mapValue"]?.jsonObject?.get("fields")?.jsonObject
                ShowcaseItem(im.s("name"), im.l("count").toInt().coerceAtLeast(0), im.s("cover"))
            },
            updatedAt = f.l("updatedAt"),
        )
    }

    /** Private editor data: users/{uid} first, profiles/{uid} as fallback. */
    suspend fun fetchOwnSocialData(): OwnSocialData = authed { a ->
        val priv = fieldsOf(firebase.firestoreGet("users/${a.uid}", a.token))
        val pub = fieldsOf(firebase.firestoreGet("profiles/${a.uid}", a.token))
        val pubLinks = pub.m("links")
        val privLinks = priv.m("links")
        val acc = settings.account.first()
        fun pick(p: String, q: String): String = p.ifEmpty { q }
        OwnSocialData(
            displayName = pick(priv.s("displayName"), pub.s("displayName")),
            username = pick(priv.s("username"), pub.s("username")),
            avatar = pick(priv.s("avatar"), pub.s("avatar")),
            about = pick(priv.s("about"), pub.s("about")),
            links = SocialLinks(
                pick(privLinks.s("telegram"), pubLinks.s("telegram")),
                pick(privLinks.s("soundcloud"), pubLinks.s("soundcloud")),
                pick(privLinks.s("vk"), pubLinks.s("vk")),
            ),
            isPrivate = if (priv?.containsKey("isPrivate") == true) priv.b("isPrivate") else pub.b("isPrivate"),
            email = priv.s("email").ifEmpty { acc?.email.orEmpty() },
        )
    }

    sealed interface UsernameStatus {
        data object Available : UsernameStatus
        data object Taken : UsernameStatus
        data object Mine : UsernameStatus
        data class Invalid(val error: SocialValidate.UsernameError?) : UsernameStatus
    }

    suspend fun checkUsername(raw: String): UsernameStatus = authed { a ->
        val v = SocialValidate.validateUsername(raw)
        if (!v.ok) return@authed UsernameStatus.Invalid(v.error)
        val reg = fieldsOf(firebase.firestoreGet("usernames/${v.value}", a.token))
        val owner = reg.s("uid")
        when {
            reg == null || owner.isEmpty() -> UsernameStatus.Available
            owner == a.uid -> UsernameStatus.Mine
            else -> UsernameStatus.Taken
        }
    }

    // ------------------------------------------------------------ writes

    /**
     * Atomically claims [name] for [uid], releasing [previous] in the same commit.
     * Returns false when the name was taken concurrently.
     */
    suspend fun claimUsername(uid: String, name: String, previous: String): Boolean = authed { a ->
        val now = System.currentTimeMillis()
        val writes = buildJsonArray {
            add(
                buildJsonObject {
                    put(
                        "update",
                        buildJsonObject {
                            put("name", docName("usernames/$name"))
                            put(
                                "fields",
                                buildJsonObject {
                                    put("uid", str(uid))
                                    put("updatedAt", num(now))
                                },
                            )
                        },
                    )
                    put("currentDocument", buildJsonObject { put("exists", false) })
                },
            )
            if (previous.isNotEmpty() && previous != name) {
                add(buildJsonObject { put("delete", docName("usernames/$previous")) })
            }
        }
        try {
            firebase.firestoreCommit(writes, a.token)
            true
        } catch (e: FirebaseRestException) {
            if (e.isConflict()) return@authed false
            throw e
        }
    }

    /**
     * Saves the private users/{uid} fields (masked merge — the cloud-sync blob and
     * desktop-only styling keys survive) and rewrites the public profiles/{uid}
     * snapshot, echoing live counters. Retries once when counters moved mid-save.
     */
    suspend fun saveAccountProfile(uid: String, data: OwnSocialData): PublicProfile = authed { a ->
        if (uid != a.uid) throw SocialException(SocialFailure.Message("uid mismatch"))
        val now = System.currentTimeMillis()
        val linksObj = buildJsonObject {
            put("telegram", str(data.links.telegram))
            put("soundcloud", str(data.links.soundcloud))
            put("vk", str(data.links.vk))
        }
        // 1) Private doc — masked so `sync` and desktop-only styling keys survive.
        firebase.firestorePatch(
            "users/$uid",
            buildJsonObject {
                put("displayName", str(data.displayName))
                put("username", str(data.username))
                put("avatar", str(data.avatar))
                put("about", str(data.about))
                put("links", mapVal(linksObj))
                put("isPrivate", flag(data.isPrivate))
                put("email", str(data.email))
                put("isAnonymous", flag(false))
                put("updatedAt", num(now))
            },
            a.token,
            masks = listOf(
                "displayName", "username", "avatar", "about", "links",
                "isPrivate", "email", "isAnonymous", "updatedAt",
            ),
        )
        // 2) Public snapshot — full replace with live counters echoed.
        var attempt = 0
        var lastError: FirebaseRestException? = null
        val emptyShowcase = buildJsonObject {
            put("arrayValue", buildJsonObject { put("values", buildJsonArray {}) })
        }
        while (attempt < 2) {
            attempt++
            val existing = fieldsOf(firebase.firestoreGet("profiles/$uid", a.token))
            val followers = existing.l("followersCount")
            val following = existing.l("followingCount")
            val showcaseVal = existing?.get("showcase")?.jsonObject ?: emptyShowcase
            try {
                firebase.firestorePatch(
                    "profiles/$uid",
                    buildJsonObject {
                        put("displayName", str(data.displayName))
                        put("username", str(data.username))
                        put("avatar", str(data.avatar))
                        put("about", str(data.about))
                        put("links", mapVal(linksObj))
                        put("isPrivate", flag(data.isPrivate))
                        put("followersCount", num(followers))
                        put("followingCount", num(following))
                        put("showcase", showcaseVal)
                        put("updatedAt", num(now))
                    },
                    a.token,
                )
                lastError = null
                break
            } catch (e: FirebaseRestException) {
                lastError = e
            }
        }
        lastError?.let { throw it }
        fetchPublicProfile(uid)
            ?: PublicProfile(uid = uid, displayName = data.displayName, username = data.username)
    }

    /** Creates the public snapshot on the first social action (never on plain view). */
    suspend fun ensurePublicSnapshot(): PublicProfile = authed { a ->
        fieldsOf(firebase.firestoreGet("profiles/${a.uid}", a.token))?.let {
            return@authed parseProfile(a.uid, it)
        }
        val data = fetchOwnSocialData()
        val acc = settings.account.first()
        val name = data.displayName
            .ifEmpty { acc?.username.orEmpty() }
            .ifEmpty { acc?.email.orEmpty().substringBefore("@") }
            .ifEmpty { "User" }
        saveAccountProfile(
            a.uid,
            data.copy(displayName = SocialValidate.sanitizeDisplayName(name).ifEmpty { "User" }),
        )
    }

    // ------------------------------------------------------------ search

    /** Prefix search over the username registry (orderBy __name__ range — no index needed). */
    suspend fun searchUsers(prefix: String, limit: Int = 8): List<SocialUser> = authed { a ->
        val clean = SocialValidate.normalizeUsername(prefix)
        if (clean.isEmpty()) return@authed emptyList()
        val base = docName("usernames")
        val query = buildJsonObject {
            put("from", buildJsonArray { add(buildJsonObject { put("collectionId", "usernames") }) })
            put(
                "orderBy",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("field", buildJsonObject { put("fieldPath", "__name__") })
                            put("direction", "ASCENDING")
                        },
                    )
                },
            )
            put(
                "startAt",
                buildJsonObject {
                    put(
                        "values",
                        buildJsonArray { add(buildJsonObject { put("referenceValue", "$base/$clean") }) },
                    )
                },
            )
            put(
                "endAt",
                buildJsonObject {
                    put(
                        "values",
                        buildJsonArray {
                            // Prefix range end: prefix + the highest BMP char.
                            add(buildJsonObject { put("referenceValue", "$base/$clean\uF8FF") })
                        },
                    )
                },
            )
            put("limit", limit)
        }
        val docs = firebase.firestoreRunQuery(query, a.token)
        coroutineScope {
            docs.mapNotNull { doc ->
                val uid = fieldsOf(doc).s("uid")
                if (uid.isEmpty()) null
                else async { runCatching { fetchPublicProfile(uid)?.toSocialUser() }.getOrNull() }
            }.awaitAll().filterNotNull()
        }
    }

    // ------------------------------------------------------------ follow graph

    suspend fun getFollowState(targetUid: String): FollowState = authed { a ->
        if (targetUid == a.uid) return@authed FollowState.NONE
        val edgeId = SocialValidate.followDocId(a.uid, targetUid)
        if (firebase.firestoreGet("follows/$edgeId", a.token) != null) return@authed FollowState.FOLLOWING
        if (firebase.firestoreGet("followRequests/$edgeId", a.token) != null) {
            return@authed FollowState.REQUESTED
        }
        FollowState.NONE
    }

    /**
     * Edge create as an update+transform pair: createdAt must equal request.time
     * (firestore.rules), which only a REQUEST_TIME transform can produce.
     */
    private fun edgeCreateWrite(
        collection: String,
        edgeId: String,
        follower: String,
        following: String,
    ): List<JsonObject> {
        val name = docName("$collection/$edgeId")
        return listOf(
            buildJsonObject {
                put(
                    "update",
                    buildJsonObject {
                        put("name", name)
                        put(
                            "fields",
                            buildJsonObject {
                                put("follower", str(follower))
                                put("following", str(following))
                            },
                        )
                    },
                )
                put("currentDocument", buildJsonObject { put("exists", false) })
            },
            buildJsonObject {
                put(
                    "transform",
                    buildJsonObject {
                        put("document", name)
                        put(
                            "fieldTransforms",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("fieldPath", "createdAt")
                                        put("setToServerValue", "REQUEST_TIME")
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    /** Transform-only counter nudge — the whole write is covered by isCounterNudge(). */
    private fun counterNudgeWrite(uid: String, field: String, delta: Long): JsonObject =
        buildJsonObject {
            put(
                "transform",
                buildJsonObject {
                    put("document", docName("profiles/$uid"))
                    put(
                        "fieldTransforms",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("fieldPath", field)
                                    put(
                                        "increment",
                                        buildJsonObject { put("integerValue", delta.toString()) },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }

    /** Follows [target] directly, or files a request when the profile is private. */
    suspend fun follow(target: PublicProfile): FollowState = authed { a ->
        if (target.uid == a.uid) throw SocialException(SocialFailure.Message("self follow"))
        ensurePublicSnapshot()
        val edgeId = SocialValidate.followDocId(a.uid, target.uid)
        if (!target.isPrivate) {
            val writes = buildJsonArray {
                edgeCreateWrite("follows", edgeId, a.uid, target.uid).forEach { add(it) }
                add(counterNudgeWrite(a.uid, "followingCount", 1))
                add(counterNudgeWrite(target.uid, "followersCount", 1))
            }
            try {
                firebase.firestoreCommit(writes, a.token)
            } catch (e: FirebaseRestException) {
                if (e.isConflict()) return@authed getFollowState(target.uid)
                throw e
            }
            FollowState.FOLLOWING
        } else {
            val writes = buildJsonArray {
                edgeCreateWrite("followRequests", edgeId, a.uid, target.uid).forEach { add(it) }
            }
            try {
                firebase.firestoreCommit(writes, a.token)
            } catch (e: FirebaseRestException) {
                if (e.isConflict()) return@authed getFollowState(target.uid)
                throw e
            }
            FollowState.REQUESTED
        }
    }

    suspend fun unfollow(targetUid: String): Unit = authed { a ->
        val edgeId = SocialValidate.followDocId(a.uid, targetUid)
        // Read-then-write: deleting a missing edge would still nudge counters.
        if (firebase.firestoreGet("follows/$edgeId", a.token) == null) return@authed
        firebase.firestoreCommit(
            buildJsonArray {
                add(buildJsonObject { put("delete", docName("follows/$edgeId")) })
                add(counterNudgeWrite(a.uid, "followingCount", -1))
                add(counterNudgeWrite(targetUid, "followersCount", -1))
            },
            a.token,
        )
    }

    suspend fun removeFollower(followerUid: String): Unit = authed { a ->
        val edgeId = SocialValidate.followDocId(followerUid, a.uid)
        if (firebase.firestoreGet("follows/$edgeId", a.token) == null) return@authed
        firebase.firestoreCommit(
            buildJsonArray {
                add(buildJsonObject { put("delete", docName("follows/$edgeId")) })
                add(counterNudgeWrite(a.uid, "followersCount", -1))
                add(counterNudgeWrite(followerUid, "followingCount", -1))
            },
            a.token,
        )
    }

    suspend fun acceptRequest(fromUid: String): Unit = authed { a ->
        val edgeId = SocialValidate.followDocId(fromUid, a.uid)
        // Idempotent: nothing pending means the lists just need a refresh.
        if (firebase.firestoreGet("followRequests/$edgeId", a.token) == null) return@authed
        firebase.firestoreCommit(
            buildJsonArray {
                edgeCreateWrite("follows", edgeId, fromUid, a.uid).forEach { add(it) }
                add(buildJsonObject { put("delete", docName("followRequests/$edgeId")) })
                add(counterNudgeWrite(fromUid, "followingCount", 1))
                add(counterNudgeWrite(a.uid, "followersCount", 1))
            },
            a.token,
        )
    }

    suspend fun declineRequest(fromUid: String): Unit = authed { a ->
        firebase.firestoreDelete("followRequests/${SocialValidate.followDocId(fromUid, a.uid)}", a.token)
    }

    suspend fun cancelRequest(targetUid: String): Unit = authed { a ->
        firebase.firestoreDelete("followRequests/${SocialValidate.followDocId(a.uid, targetUid)}", a.token)
    }

    // ------------------------------------------------------------ lists

    /**
     * Single-field equality query (no composite index): returns the [other]-side
     * uids newest-first (createdAt is an ISO timestamp — lexicographic sort works).
     */
    private suspend fun edgeUids(
        collection: String,
        field: String,
        uid: String,
        other: String,
        limit: Int = 200,
    ): List<String> = authed { a ->
        val query = buildJsonObject {
            put("from", buildJsonArray { add(buildJsonObject { put("collectionId", collection) }) })
            put(
                "where",
                buildJsonObject {
                    put(
                        "fieldFilter",
                        buildJsonObject {
                            put("field", buildJsonObject { put("fieldPath", field) })
                            put("op", "EQUAL")
                            put("value", buildJsonObject { put("stringValue", uid) })
                        },
                    )
                },
            )
            put("limit", limit)
        }
        firebase.firestoreRunQuery(query, a.token)
            .mapNotNull { doc ->
                val f = fieldsOf(doc) ?: return@mapNotNull null
                val created = f["createdAt"]?.jsonObject
                    ?.get("timestampValue")?.jsonPrimitive?.content ?: ""
                f.s(other).ifEmpty { null }?.let { it to created }
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
    }

    suspend fun listFollowerUids(uid: String): List<String> =
        edgeUids("follows", "following", uid, "follower")

    suspend fun listFollowingUids(uid: String): List<String> =
        edgeUids("follows", "follower", uid, "following")

    suspend fun listIncomingRequestUids(uid: String): List<String> =
        edgeUids("followRequests", "following", uid, "follower", 100)

    suspend fun listOutgoingRequestUids(uid: String): List<String> =
        edgeUids("followRequests", "follower", uid, "following", 100)

    suspend fun fetchUsers(uids: List<String>): List<SocialUser> = coroutineScope {
        uids.distinct().map { uid ->
            async { runCatching { fetchPublicProfile(uid)?.toSocialUser() }.getOrNull() }
        }.awaitAll().filterNotNull()
    }

    // ------------------------------------------------------------ showcase

    /** Publishes the playlist showcase (masked merge — counters untouched). */
    suspend fun syncShowcase(items: List<ShowcaseItem>): Unit = authed { a ->
        ensurePublicSnapshot()
        val values = items.take(SocialValidate.SHOWCASE_LIMIT).map { item ->
            buildJsonObject {
                put(
                    "mapValue",
                    buildJsonObject {
                        put(
                            "fields",
                            buildJsonObject {
                                put("name", str(item.name.take(60)))
                                put("count", num(item.count.coerceIn(0, 999999).toLong()))
                                put("cover", str(item.cover.take(2048)))
                            },
                        )
                    },
                )
            }
        }
        val showcaseVal = buildJsonObject {
            put(
                "arrayValue",
                buildJsonObject { put("values", buildJsonArray { values.forEach { add(it) } }) },
            )
        }
        firebase.firestorePatch(
            "profiles/${a.uid}",
            buildJsonObject { put("showcase", showcaseVal) },
            a.token,
            masks = listOf("showcase"),
        )
    }

}
