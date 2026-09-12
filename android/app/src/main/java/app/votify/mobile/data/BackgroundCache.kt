package app.votify.mobile.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Локальный кэш фона приложения.
 *
 * Ссылки из Discord (основной источник гифок) протухают примерно за сутки:
 * после этого грузить нечего и фон превращается в сплошной цвет. Поэтому
 * успешно загруженный фон сохраняется в файлы приложения — они переживают
 * и смерть ссылки, и переустановку (через системный бэкап).
 */
object BackgroundCache {
    private const val DIR = "backgrounds"
    private const val MAX_BYTES = 30L * 1024 * 1024

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR)

    fun fileFor(context: Context, url: String): File {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it in setOf("gif", "webp", "png", "jpg", "jpeg", "bmp", "avif") } ?: "img"
        return File(dir(context), "bg_" + sha1(url) + "." + ext)
    }

    /** Уже скачанный файл для URL или null. Быстрая проверка, можно звать из UI. */
    fun cachedFile(context: Context, url: String): File? {
        if (url.isBlank()) return null
        return fileFor(context, url).takeIf { it.isFile && it.length() > 0 }
    }

    /** Скачать фон в файлы приложения (если ещё нет). Возвращает файл или null. */
    suspend fun ensureCached(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val out = fileFor(context, url)
        if (out.isFile && out.length() > 0) return@withContext out
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 Votify").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                if (!resp.header("Content-Type").orEmpty().startsWith("image/")) return@withContext null
                val body = resp.body ?: return@withContext null
                dir(context).mkdirs()
                val tmp = File(dir(context), out.name + ".tmp")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) {
                                tmp.delete()
                                return@withContext null
                            }
                            output.write(buf, 0, n)
                        }
                    }
                }
                if (tmp.length() == 0L) {
                    tmp.delete()
                    return@withContext null
                }
                tmp.renameTo(out)
                out
            }
        }.getOrNull()
    }

    /** Отдаёт ли URL картинку (для проверки ссылки при установке фона). */
    suspend fun probeImage(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 Votify").get().build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful && resp.header("Content-Type").orEmpty().startsWith("image/")
            }
        }.getOrDefault(false)
    }

    /**
     * Страницы-поделиться (Giphy/Tenor/...) — не картинки. Достаём прямую
     * ссылку через og:image, чтобы фон реально грузился.
     */
    suspend fun resolveDirect(url: String): String = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (clean.isBlank()) return@withContext clean
        val host = runCatching { java.net.URI(clean).host.orEmpty().lowercase() }.getOrDefault("")
        val looksDirect = clean.substringBefore('?')
            .matches(Regex("(?i).+\\.(gif|webp|png|jpe?g|bmp|avif|svg)$"))
        val shareHosts = setOf("giphy.com", "www.giphy.com", "tenor.com", "www.tenor.com", "media.tenor.com")
        if (host !in shareHosts && looksDirect) return@withContext clean
        runCatching {
            val req = Request.Builder().url(clean)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
                )
                .get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext clean
                val html = resp.body?.string()?.take(200_000) ?: return@withContext clean
                val og = Regex("property=[\"']og:image[\"']\\s+content=[\"']([^\"']+)")
                    .find(html)?.groupValues?.get(1)
                    ?: Regex("content=[\"']([^\"']+)[\"']\\s+property=[\"']og:image[\"']")
                        .find(html)?.groupValues?.get(1)
                val direct = og?.replace("&amp;", "&")?.trim().orEmpty()
                if (direct.startsWith("http")) direct else clean
            }
        }.getOrDefault(clean)
    }

    /** Удалить чужие закэшированные фоны, оставив файл текущего URL. */
    suspend fun prune(context: Context, keepUrl: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val keep = if (keepUrl.isBlank()) null else fileFor(context, keepUrl).name
            dir(context).listFiles()
                ?.filter { it.isFile && it.name.startsWith("bg_") && it.name != keep }
                ?.forEach { it.delete() }
        }
    }

    suspend fun pruneAll(context: Context): Unit = withContext(Dispatchers.IO) {
        runCatching {
            dir(context).listFiles()
                ?.filter { it.isFile && it.name.startsWith("bg_") }
                ?.forEach { it.delete() }
        }
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
