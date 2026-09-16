package app.votify.mobile.data

/** Публичный профиль — то, что видят другие пользователи (profiles/{uid}). */
data class UserProfile(
    val uid: String = "",
    /** Юзернейм в том виде, как его набрал человек (для показа — @Name). */
    val handle: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val telegram: String = "",
    val soundcloud: String = "",
    val vk: String = "",
    val isPrivate: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val handleAt: String get() = if (handle.isBlank()) "" else "@$handle"
    val nameOrHandle: String get() = displayName.ifBlank { handle }.ifBlank { "Пользователь" }
}

/** Заявка в друзья или дружба двух людей (friendships/{a_b}). */
data class Friendship(
    val id: String = "",
    val a: String = "",
    val b: String = "",
    /** [STATUS_PENDING] — заявка отправлена, [STATUS_ACCEPTED] — дружба подтверждена. */
    val status: String = STATUS_PENDING,
    /** Кто отправил заявку. */
    val from: String = "",
    /** Кто должен ответить. */
    val to: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Собеседник: кто в этой паре не я. */
    fun other(uid: String): String = if (a == uid) b else a

    val isPending: Boolean get() = status == STATUS_PENDING
    val isAccepted: Boolean get() = status == STATUS_ACCEPTED

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
    }
}

/** Юзернейм как в Telegram: 5..32 символа, латиница, цифры и подчёркивание. */
private val HANDLE_RE = Regex("[a-zA-Z0-9_]{5,32}")

fun normalizeHandle(raw: String): String = raw.trim().trimStart('@').lowercase()

/** Юзернейм, который можно занять: «@Votify» и «votify» — одно и то же имя. */
fun isValidHandle(raw: String): Boolean = HANDLE_RE.matches(normalizeHandle(raw))

/** Идентификатор связи: одинаковый независимо от того, кто первым отправил заявку. */
fun friendshipId(a: String, b: String): String = if (a <= b) "${a}_$b" else "${b}_$a"

const val BIO_MAX_LENGTH = 150
const val DISPLAY_NAME_MAX_LENGTH = 40
