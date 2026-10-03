package app.votify.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.votify.mobile.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val sha: String = "",
    val shortSha: String = "",
    val downloadUrl: String = "",
    val cdnUrl: String = "",
    val releaseUrl: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : UpdateState
    data class Ready(val info: UpdateInfo, val apkFile: File) : UpdateState
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
}

class AppUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var dismissed = false

    fun checkForUpdate(force: Boolean = false) {
        if (!force && dismissed) return
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Ready) return

        scope.launch {
            _state.value = UpdateState.Checking
            val info = withContext(Dispatchers.IO) { fetchUpdateInfo() }
            if (info == null) {
                _state.value = UpdateState.Idle
                return@launch
            }

            val curCode = BuildConfig.VERSION_CODE
            val curSha = BuildConfig.BUILD_SHA
            val isNewerCode = info.versionCode > curCode
            val isNewerSha = info.sha.isNotBlank() && curSha != "dev" &&
                    !curSha.startsWith(info.shortSha.ifBlank { info.sha.take(7) }) &&
                    !info.sha.startsWith(curSha)

            if (isNewerCode || isNewerSha) {
                val cachedApk = getUpdateApkFile()
                val metaFile = getUpdateMetaFile()
                val cachedSha = if (metaFile.exists()) runCatching { metaFile.readText().trim() }.getOrNull() else null
                if (cachedApk.exists() && cachedApk.length() > 5_000_000 && (cachedSha == null || cachedSha == info.sha)) {
                    _state.value = UpdateState.Ready(info, cachedApk)
                } else {
                    if (cachedApk.exists() && cachedSha != null && cachedSha != info.sha) {
                        cachedApk.delete()
                        metaFile.delete()
                    }
                    _state.value = UpdateState.Available(info)
                }
            } else {
                runCatching {
                    getUpdateApkFile().delete()
                    getUpdateMetaFile().delete()
                }
                _state.value = UpdateState.Idle
            }
        }
    }

    private fun fetchUpdateInfo(): UpdateInfo? {
        val urls = listOf(
            "https://raw.githubusercontent.com/exieeez/Votify/apk/version.json",
            "https://cdn.jsdelivr.net/gh/exieeez/Votify@apk/version.json"
        )
        for (url in urls) {
            runCatching {
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            return json.decodeFromString<UpdateInfo>(body)
                        }
                    }
                }
            }
        }
        return null
    }

    fun startDownload(info: UpdateInfo) {
        scope.launch {
            val apkFile = getUpdateApkFile()
            apkFile.parentFile?.mkdirs()
            if (apkFile.exists()) apkFile.delete()

            val downloadUrls = listOfNotNull(
                info.cdnUrl.ifBlank { null },
                info.downloadUrl.ifBlank { null },
                info.releaseUrl.ifBlank { null },
                "https://raw.githubusercontent.com/exieeez/Votify/apk/Votify-debug.apk"
            )

            var success = false
            for (url in downloadUrls) {
                _state.value = UpdateState.Downloading(info, 0f, 0L, -1L)
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val req = Request.Builder().url(url).build()
                        http.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching false
                            val body = resp.body ?: return@runCatching false
                            val total = body.contentLength()
                            val tempFile = File(apkFile.parentFile, "votify-update.tmp")
                            if (tempFile.exists()) tempFile.delete()

                            body.byteStream().use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    val buffer = ByteArray(32 * 1024)
                                    var read: Int
                                    var bytesCopied = 0L
                                    var lastReport = System.currentTimeMillis()

                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                        bytesCopied += read
                                        val now = System.currentTimeMillis()
                                        if (now - lastReport > 200 || bytesCopied == total) {
                                             lastReport = now
                                             val progress = if (total > 0) bytesCopied.toFloat() / total.toFloat() else 0f
                                             _state.value = UpdateState.Downloading(info, progress, bytesCopied, total)
                                        }
                                    }
                                    output.flush()
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 5_000_000) {
                                if (apkFile.exists()) apkFile.delete()
                                tempFile.renameTo(apkFile)
                                runCatching { getUpdateMetaFile().writeText(info.sha) }
                                true
                            } else {
                                tempFile.delete()
                                false
                            }
                        }
                    }.getOrDefault(false)
                }

                if (ok) {
                    success = true
                    _state.value = UpdateState.Ready(info, apkFile)
                    break
                }
            }

            if (!success) {
                _state.value = UpdateState.Error("Не удалось скачать обновление", info)
            }
        }
    }

    fun canInstallDirectly(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(fallback) }
            }
        }
    }

    fun installUpdate(apkFile: File) {
        if (!apkFile.exists()) {
            _state.value = UpdateState.Error("Файл обновления не найден")
            return
        }

        if (!canInstallDirectly()) {
            openInstallPermissionSettings()
            return
        }

        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
            _state.value = UpdateState.Error("Ошибка запуска установщика: ${e.message}")
        }
    }

    fun installAndRestart(apkFile: File) = installUpdate(apkFile)

    fun dismiss() {
        dismissed = true
        _state.value = UpdateState.Idle
    }

    private fun getUpdateDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.cacheDir
        return File(base, "updates").apply { mkdirs() }
    }

    private fun getUpdateApkFile(): File = File(getUpdateDir(), "Votify-update.apk")
    private fun getUpdateMetaFile(): File = File(getUpdateDir(), "Votify-update.sha")
}
