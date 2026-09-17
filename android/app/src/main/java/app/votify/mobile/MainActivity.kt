package app.votify.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.votify.mobile.data.AppIcons
import app.votify.mobile.data.parseCustomPrefs
import app.votify.mobile.ui.VotifyRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            // VotifyRoot applies VotifyTheme itself (the palette comes from user settings).
            VotifyRoot()
        }
        reconcileAppIcon()
    }

    override fun onStart() {
        super.onStart()
        VotifyApp.instance.player.connect()
    }

    override fun onStop() {
        // Playback keeps running in PlaybackService; we only drop the UI-side controller.
        VotifyApp.instance.player.disconnect()
        super.onStop()
    }

    /**
     * Иконку мог сменить другой телефон пользователя (настройки синхронизируются через
     * аккаунт/пресеты) — приводим лаунчер к сохранённому варианту. Ничего не делаем, если
     * вариант совпадает или для «своей» иконки нет картинки.
     */
    private fun reconcileAppIcon() {
        val app = VotifyApp.instance
        app.appScope.launch {
            runCatching {
                val prefs = parseCustomPrefs(app.settings.settings.first().customPrefs)
                AppIcons.reconcile(this@MainActivity, prefs.appIcon)
            }
        }
    }

    /** Android 13+: media notification is silent without this permission. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
