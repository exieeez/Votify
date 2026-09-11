package app.votify.mobile.data

/**
 * Public profile document: profiles/{uid} — the exact shape enforced by
 * firestore.rules isValidPublicProfile (desktop and Android share it).
 * `frame` is the optional avatar-frame id (Discord-like decorations).
 */
data class PublicProfile(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatar: String = "",
    val about: String = "",
    val links: SocialLinks = SocialLinks(),
    val isPrivate: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val showcase: List<ShowcaseItem> = emptyList(),
    val updatedAt: Long = 0,
    val frame: String = "",
)

/** Social handles stored as plain names; URLs are derived per network. */
data class SocialLinks(
    val telegram: String = "",
    val soundcloud: String = "",
    val vk: String = "",
) {
    fun isEmpty(): Boolean = telegram.isEmpty() && soundcloud.isEmpty() && vk.isEmpty()

    fun entries(): List<Pair<String, String>> =
        listOf("telegram" to telegram, "soundcloud" to soundcloud, "vk" to vk)
            .filter { it.second.isNotBlank() }
}

/** One published playlist card inside profiles/{uid}.showcase. */
data class ShowcaseItem(
    val name: String = "",
    val count: Int = 0,
    val cover: String = "",
    /** First N tracks (shared shape with desktop) — empty for cards published by older clients. */
    val tracks: List<ShowcaseTrack> = emptyList(),
)

/** Compact shareable track row inside a ShowcaseItem. */
data class ShowcaseTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val cover: String = "",
    val duration: Int = 0,
) {
    fun toTrack() = Track(id = id, title = title, artist = artist, cover = cover, duration = duration)
}

/** Compact row for follower / search / request lists. */
data class SocialUser(
    val uid: String,
    val displayName: String,
    val username: String,
    val avatar: String,
    val frame: String = "",
)

fun PublicProfile.toSocialUser() = SocialUser(uid, displayName, username, avatar, frame)

/** Private editable fields, merged from users/{uid} over profiles/{uid}. */
data class OwnSocialData(
    val displayName: String = "",
    val username: String = "",
    val avatar: String = "",
    val about: String = "",
    val links: SocialLinks = SocialLinks(),
    val isPrivate: Boolean = false,
    val email: String = "",
    val frame: String = "",
)

enum class FollowState { NONE, FOLLOWING, REQUESTED }
