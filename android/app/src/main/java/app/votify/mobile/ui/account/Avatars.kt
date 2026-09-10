package app.votify.mobile.ui.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Avatar edge stored in Firestore (matches the desktop client). */
const val AVATAR_SIZE = 128

/** Upper bound for the decoded bitmap side before downscaling to [AVATAR_SIZE]. */
private const val DECODE_MAX_SIDE = 1024

/**
 * Reads [uri] (gallery pick), center-crops to a square, scales to 128px and encodes
 * a data:image/jpeg URL for the `avatar` profile field. Null on failure or when the
 * result would exceed the 153600-char limit from firestore.rules.
 */
fun processAvatarImage(context: Context, uri: Uri): String? {
    return runCatching {
        val decoded = decodeBounded(context, uri) ?: return null
        val side = min(decoded.width, decoded.height)
        if (side <= 0) return null
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

/**
 * Decodes [uri] subsampled to [DECODE_MAX_SIDE]. ImageDecoder first (API 28+ handles
 * HEIC and exotic formats BitmapFactory chokes on), BitmapFactory as fallback.
 */
private fun decodeBounded(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                var sample = 1
                while (info.size.width / (sample * 2) >= DECODE_MAX_SIDE &&
                    info.size.height / (sample * 2) >= DECODE_MAX_SIDE
                ) {
                    sample *= 2
                }
                decoder.setTargetSampleSize(sample)
            }
        }.getOrNull()?.let { return it }
    }
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= DECODE_MAX_SIDE &&
            bounds.outHeight / (sample * 2) >= DECODE_MAX_SIDE
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}

/** Decodes a data-URL avatar ("data:image/...") into an [ImageBitmap], or null. */
fun decodeAvatarDataUrl(dataUrl: String): ImageBitmap? {
    if (!dataUrl.startsWith("data:image/")) return null
    val payload = dataUrl.substringAfter(",", "")
    if (payload.isEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
