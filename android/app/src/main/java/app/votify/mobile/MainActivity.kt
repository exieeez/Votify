package app.votify.mobile

import android.Manifest
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    var openPlayerRequested by mutableStateOf(false)
        internal set

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_player", false)) {
            openPlayerRequested = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("open_player", false) == true) {
            openPlayerRequested = true
        }
        enableEdgeToEdge()
        enableHighRefreshRate()
        requestNotificationPermissionIfNeeded()
        setContent {
            // VotifyRoot applies VotifyTheme itself (the palette comes from user settings).
            VotifyRoot()
        }
        reconcileAppIcon()
    }

    /**
     * Enables 120Hz / high refresh rate display mode on devices that support it,
     * ensuring ultra-smooth scrolling and animations across the entire UI.
     */
    private fun enableHighRefreshRate() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val modes = display?.supportedModes.orEmpty()
                val maxRefreshRateMode = modes.maxByOrNull { it.refreshRate }
                if (maxRefreshRateMode != null && maxRefreshRateMode.refreshRate >= 90f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxRefreshRateMode.modeId
                    window.attributes = params
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val modes = window.windowManager.defaultDisplay.supportedModes
                val maxRefreshRateMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshRateMode != null && maxRefreshRateMode.refreshRate >= 90f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxRefreshRateMode.modeId
                    window.attributes = params
                }
            }
        }
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
