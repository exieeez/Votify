package app.votify.mobile.ui.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Avatar edge stored in Firestore (matches the desktop client). */
const val AVATAR_SIZE = 128

/**
 * Reads [uri] (gallery pick), center-crops to a square, scales to 128px and encodes
 * a data:image/jpeg URL for the `avatar` profile field. Null on failure or when the
 * result would exceed the 153600-char limit from firestore.rules.
 */
fun processAvatarImage(context: Context, uri: Uri): String? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= AVATAR_SIZE * 2 &&
            bounds.outHeight / (sample * 2) >= AVATAR_SIZE * 2
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val side = min(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
        )
        val scaled = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        dataUrl.takeIf { it.length <= 153600 }
    }.getOrNull()
}

/** Decodes a data:image/* avatar URL into an [ImageBitmap], or null. */
fun decodeAvatarDataUrl(dataUrl: String): ImageBitmap? {
    if (!dataUrl.startsWith("data:image/")) return null
    val payload = dataUrl.substringAfter(",", "")
    if (payload.isEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
