package app.votify.mobile.data

/**
 * Social validators (usernames, links, follows) — a line-for-line port of the
 * desktop client's src/social-validate.js. Username rules are Telegram-like:
 * lowercase latin, digits, underscores, must start with a letter, 3-32 chars,
 * globally unique (first come, first served).
 */
object SocialValidate {
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 32
    private val USERNAME_PATTERN = Regex("^[a-z][a-z0-9_]{2,31}$")
    const val DISPLAY_NAME_MAX = 40
    const val BIO_MAX = 150
    const val LINK_MAX = 120
    const val SHOWCASE_LIMIT = 20

    /** Reserved handles that can never be claimed (services, impersonation, routes). */
    val RESERVED_USERNAMES = setOf(
        "admin", "administrator", "api", "app", "account", "album", "albums",
        "android", "apple", "artist", "artists", "blog", "bot", "bots",
        "chart", "charts", "client", "desktop", "dev", "developer", "donate",
        "email", "favorites", "favourites", "gift", "gifts", "guest", "help",
        "history", "home", "invite", "ios", "library", "liked", "linux",
        "login", "macos", "mail", "mobile", "mod", "moderator", "music",
        "news", "now", "official", "player", "playing", "playlist",
        "playlists", "plus", "premium", "pro", "profile", "promo", "root",
        "search", "server", "settings", "shop", "soundcloud", "spotify",
        "staff", "status", "store", "support", "system", "telegram", "test",
        "track", "tracks", "update", "updates", "user", "users", "verified",
        "vk", "votify", "web", "windows", "youtube",
    )

    val LINK_KINDS = listOf("telegram", "soundcloud", "vk")
    private val LINK_PATTERNS = mapOf(
        "telegram" to Regex("^[A-Za-z0-9_]{3,32}$"),
        "vk" to Regex("^[A-Za-z0-9_.]{3,64}$"),
        "soundcloud" to Regex("^[A-Za-z0-9_.-]{3,64}$"),
    )

    /** "  @ExIeEez " -> "exieeez" */
    fun normalizeUsername(raw: String?): String =
        (raw ?: "").trim().replace(Regex("^@+"), "").lowercase()

    /**
     * Search prefix: normalized, non-latin stripped (usernames are latin-only),
     * capped at 32 chars. Mirrors the desktop client's searchUsers exactly so
     * both apps find the same people for the same input.
     */
    fun searchPrefix(raw: String?): String =
        normalizeUsername(raw).filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(32)

    enum class UsernameError { EMPTY, TOO_SHORT, TOO_LONG, BAD_CHARS, RESERVED }

    data class UsernameCheck(val ok: Boolean, val value: String, val error: UsernameError? = null)

    fun validateUsername(raw: String?): UsernameCheck {
        val value = normalizeUsername(raw)
        if (value.isEmpty()) return UsernameCheck(false, "", UsernameError.EMPTY)
        if (value.length < USERNAME_MIN) return UsernameCheck(false, value, UsernameError.TOO_SHORT)
        if (value.length > USERNAME_MAX) return UsernameCheck(false, value, UsernameError.TOO_LONG)
        if (!USERNAME_PATTERN.matches(value)) return UsernameCheck(false, value, UsernameError.BAD_CHARS)
        if (RESERVED_USERNAMES.contains(value)) return UsernameCheck(false, value, UsernameError.RESERVED)
        return UsernameCheck(true, value)
    }

    fun sanitizeDisplayName(raw: String?): String =
        (raw ?: "").replace(Regex("\\s+"), " ").trim().take(DISPLAY_NAME_MAX)

    fun sanitizeBio(raw: String?): String =
        (raw ?: "").replace("\r", "").trim().take(BIO_MAX)

    /** Accepts a handle ("durov"), "@durov" or a full link; returns a clean handle or ''. */
    fun normalizeLink(kind: String, raw: String?): String {
        if (kind !in LINK_KINDS) return ""
        var value = (raw ?: "").trim()
        if (value.isEmpty()) return ""
        // Strip protocol / domain / leading @ so "https://t.me/durov" -> "durov".
        value = value
            .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(www\\.)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(t\\.me|telegram\\.me|vk\\.com|soundcloud\\.com)/", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^@+"), "")
            .split(Regex("[?#]"))[0]
            .replace(Regex("/+$"), "")
        // soundcloud links may include a locale prefix, keep the last path segment
        if ("/" in value) value = value.split("/").last()
        value = value.trim().take(LINK_MAX)
        if (value.isEmpty()) return ""
        val pattern = LINK_PATTERNS[kind]
        if (pattern != null && !pattern.matches(value)) return ""
        return value
    }

    fun linkUrl(kind: String, handle: String): String {
        if (handle.isEmpty()) return ""
        return when (kind) {
            "telegram" -> "https://t.me/$handle"
            "vk" -> "https://vk.com/$handle"
            "soundcloud" -> "https://soundcloud.com/$handle"
            else -> ""
        }
    }

    fun sanitizeLinks(telegram: String?, soundcloud: String?, vk: String?): SocialLinks =
        SocialLinks(
            normalizeLink("telegram", telegram),
            normalizeLink("soundcloud", soundcloud),
            normalizeLink("vk", vk),
        )

    /** Firestore doc id for a follow edge / request: "followerUid_followingUid". */
    fun followDocId(followerUid: String, followingUid: String): String =
        "${followerUid}_${followingUid}"

    fun isSafeHttpUrl(value: String?, maxLength: Int = 2048): Boolean {
        val input = (value ?: "").trim()
        if (input.isEmpty() || input.length > maxLength) return false
        return Regex("^https://[^/\\s@]+[^@\\s]*$").matches(input)
    }

    fun isSafeAvatar(value: String?, maxLength: Int = 153600): Boolean {
        val input = value ?: ""
        if (input.isEmpty()) return true
        if (input.length > maxLength) return false
        return input.startsWith("data:image/")
    }
}
