package app.votify.mobile.ui.account

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.votify.mobile.R

/**
 * Discord-like avatar frames: decorative rings drawn around the avatar circle.
 * The selected frame id is stored in profiles/{uid}.frame (see firestore.rules)
 * so friends see it too; unknown ids render as no frame.
 */
data class AvatarFrame(
    val id: String,
    val label: Int,
    val brush: Brush?,
)

val AVATAR_FRAMES: List<AvatarFrame> = listOf(
    AvatarFrame("", R.string.frame_none, null),
    AvatarFrame(
        "gold",
        R.string.frame_gold,
        Brush.sweepGradient(
            listOf(
                Color(0xFFFFE9A8),
                Color(0xFFD9930D),
                Color(0xFFFFF3C4),
                Color(0xFFB8770A),
                Color(0xFFFFE9A8),
            ),
        ),
    ),
    AvatarFrame(
        "neon",
        R.string.frame_neon,
        Brush.sweepGradient(
            listOf(
                Color(0xFF00E5FF),
                Color(0xFF2979FF),
                Color(0xFFD500F9),
                Color(0xFF00E5FF),
            ),
        ),
    ),
    AvatarFrame(
        "ocean",
        R.string.frame_ocean,
        Brush.linearGradient(
            listOf(
                Color(0xFF2196F3),
                Color(0xFF00BCD4),
                Color(0xFF3D5AFE),
            ),
        ),
    ),
    AvatarFrame(
        "sunset",
        R.string.frame_sunset,
        Brush.linearGradient(
            listOf(
                Color(0xFFFFB300),
                Color(0xFFFF512F),
                Color(0xFFDD2476),
            ),
        ),
    ),
    AvatarFrame(
        "forest",
        R.string.frame_forest,
        Brush.linearGradient(
            listOf(
                Color(0xFF9CCC65),
                Color(0xFF43A047),
                Color(0xFF1B5E20),
            ),
        ),
    ),
    AvatarFrame(
        "royal",
        R.string.frame_royal,
        Brush.sweepGradient(
            listOf(
                Color(0xFFE040FB),
                Color(0xFF7C4DFF),
                Color(0xFF311B92),
                Color(0xFFE040FB),
            ),
        ),
    ),
    AvatarFrame(
        "crimson",
        R.string.frame_crimson,
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF8A80),
                Color(0xFFE53935),
                Color(0xFF880E4F),
                Color(0xFFFF8A80),
            ),
        ),
    ),
    AvatarFrame(
        "mono",
        R.string.frame_mono,
        Brush.sweepGradient(
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFF9E9E9E),
                Color(0xFFE0E0E0),
                Color(0xFF616161),
                Color(0xFFFFFFFF),
            ),
        ),
    ),
)

fun frameBrush(id: String): Brush? =
    AVATAR_FRAMES.firstOrNull { it.id == id }?.brush
