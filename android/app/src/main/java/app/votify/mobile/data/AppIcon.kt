package app.votify.mobile.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Варианты иконки приложения.
 *
 * Android не даёт приложению подменить иконку в лаунчере произвольной картинкой: иконка —
 * часть манифеста (документация `android:icon` / `activity-alias`). Поэтому у каждого
 * варианта есть свой `activity-alias` на [app.votify.mobile.MainActivity], а переключение =
 * включить один алиас и выключить остальные ([AppIcons.setLauncherIcon]).
 *
 * Своя картинка ставится так:
 *  - Android 16+ (One UI 8, Pixel Launcher): системный диалог «Сменить иконку» —
 *    иконку в лаунчере подменяет сама система, см. [AppIcons.iconCustomizationIntent];
 *  - любой Android: ярлык на рабочий стол с картинкой пользователя
 *    ([AppIcons.pinCustomShortcut]) плюс картинка в самом приложении.
 */
enum class AppIcon(val key: String, val componentSuffix: String) {
    /** Обычная иконка Votify (была всегда). */
    Current("current", "Current"),

    /** Та же иконка, но третья полоса белая — «Ч/Б». */
    BlackWhite("bw", "Bw"),

    /** Старый логотип в духе iOS 27: стеклянная плитка, блик, светлая кромка. */
    Glass("glass", "Glass"),

    /** Старый логотип как есть: тёмная плитка со светлым диском. */
    Classic("classic", "Classic"),

    /** Картинка пользователя (файл [customIconFile]). */
    Custom("custom", "Custom");

    companion object {
        fun fromKey(k: String?): AppIcon = entries.firstOrNull { it.key == k } ?: Current

        /** Варианты в порядке показа в настройках (Своя — отдельной плиткой). */
        val builtIn: List<AppIcon> = listOf(Current, BlackWhite, Glass, Classic)

        /** Идентификатор ярлыка со своей картинкой (один на приложение — без дублей). */
        const val CUSTOM_SHORTCUT_ID = "votify_custom_icon"
    }
}

/** Картинка пользователя: готовая квадратная PNG во внутреннем хранилище приложения. */
fun customIconFile(context: Context): File = File(context.filesDir, "launcher_icon.png")

object AppIcons {

    /** Компонент алиаса + ресурс его иконки, как они объявлены в манифесте. */
    private data class AliasInfo(val component: ComponentName, val iconRes: Int)

    private var cachePkg: String? = null
    private var cache: Map<AppIcon, AliasInfo> = emptyMap()

    /**
     * Алиасы читаем из манифеста, а не собираем строку из applicationId: имя компонента
     * зависит от namespace сборки (у сборки под «dynamic island» другой applicationId).
     */
    private fun aliases(context: Context): Map<AppIcon, AliasInfo> {
        val pkg = context.packageName
        cachePkg?.let { if (it == pkg && cache.isNotEmpty()) return cache }
        val map = runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(
                pkg,
                PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
            val declared = info.activities.orEmpty()
            AppIcon.entries.mapNotNull { icon ->
                declared
                    .firstOrNull { it.name.endsWith(".VotifyLauncher.${icon.componentSuffix}") }
                    ?.let { icon to AliasInfo(ComponentName(pkg, it.name), it.icon) }
            }.toMap()
        }.getOrDefault(emptyMap())
        if (map.isNotEmpty()) {
            cachePkg = pkg
            cache = map
        }
        return map
    }

    /** Какая иконка стоит сейчас — спрашиваем PackageManager, а не настройки. */
    fun active(context: Context): AppIcon {
        val pm = context.packageManager
        val enabled = aliases(context).entries.firstOrNull { (_, info) ->
            runCatching { pm.getComponentEnabledSetting(info.component) }.getOrNull() ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }?.key
        // Ничего не переключали (свежая установка) — действует значение из манифеста.
        return enabled ?: AppIcon.Current
    }

    /** Иконка варианта картинкой — для превью в настройках. */
    fun iconBitmap(context: Context, icon: AppIcon, size: Int = 160): Bitmap? = runCatching {
        val resId = aliases(context)[icon]?.iconRes ?: return null
        context.getDrawable(resId)?.toBitmap(size, size)
    }.getOrNull()

    /**
     * Включить вариант [icon]: включённым остаётся ровно один launcher-алиас — иначе на
     * рабочем столе появилось бы несколько иконок Votify. false = система отказалась
     * переключать компонент, состояние иконки откатывается к прежнему.
     */
    fun setLauncherIcon(context: Context, icon: AppIcon): Boolean {
        val pm = context.packageManager
        val all = aliases(context)
        val target = all[icon]?.component ?: return false
        val previous = all[active(context)]?.component
        if (previous == target) return true
        val applied = runCatching {
            pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            previous?.let {
                pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }.isSuccess
        if (!applied) return false
        // Приложение обязано остаться запускаемым с рабочего стола: если после переключения
        // лаунчер не находит точку входа — возвращаем прежний вариант.
        val launchable = runCatching { pm.getLaunchIntentForPackage(context.packageName) != null }
            .getOrDefault(true)
        if (!launchable && previous != null) {
            runCatching {
                pm.setComponentEnabledSetting(previous, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            return false
        }
        return true
    }

    /**
     * Настройки синхронизируются (аккаунт, пресеты, облако), поэтому на новом устройстве
     * иконка ещё стандартная — приводим её к сохранённому выбору. «Своя» применяется,
     * только если картинка на месте.
     */
    suspend fun reconcile(context: Context, savedKey: String?) {
        val want = AppIcon.fromKey(savedKey)
        if (want == AppIcon.Custom && !customIconFile(context).exists()) return
        if (active(context) == want) return
        setLauncherIcon(context, want)
    }

    /**
     * Android 16+: системный диалог «Сменить иконку», где пользователь может поставить любую
     * картинку — лаунчер подменяет иконку сам. Компонент указываем явно: иначе система
     * запустит «то, что по умолчанию». null = прошивка такого не умеет.
     */
    fun iconCustomizationIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 36) return null
        val alias = aliases(context)[active(context)]?.component ?: return null
        val probe = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = alias
        }
        @Suppress("DEPRECATION")
        val resolvable = runCatching {
            context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
        }.getOrDefault(false)
        if (!resolvable) return null
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory("android.intent.category.LAUNCHER_APP_ICON_OVERRIDE")
            component = alias
        }
    }

    /**
     * Картинка пользователя → квадратная PNG 288×288 со скруглёнными углами: одинаково
     * смотрится в галерее, в настройках и на ярлыке рабочего стола.
     */
    suspend fun importCustomIcon(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                processIcon(input, customIconFile(context))
            } ?: false
        }.getOrDefault(false)
    }

    private fun processIcon(input: InputStream, out: File): Boolean {
        val src = BitmapFactory.decodeStream(input) ?: return false
        val size = 288
        // Квадрат по центру: масштабируем по короткой стороне и обрезаем лишнее.
        val scale = max(size / src.width.toFloat(), size / src.height.toFloat())
        val w = (src.width * scale).roundToInt().coerceAtLeast(size)
        val h = (src.height * scale).roundToInt().coerceAtLeast(size)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val square = Bitmap.createBitmap(scaled, (w - size) / 2, (h - size) / 2, size, size)

        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        val radius = size * 0.22f
        canvas.clipPath(
            Path().apply {
                addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, Path.Direction.CW)
            },
        )
        canvas.drawBitmap(square, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        out.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (square != scaled) square.recycle()
        if (scaled != src) scaled.recycle()
        return true
    }

    /**
     * Ярлык на рабочий стол с картинкой пользователя. Иконки ярлыков система берёт из
     * ShortcutManager, поэтому это единственный способ увидеть свою картинку на рабочем
     * столе на прошивках без системной смены иконки.
     */
    suspend fun pinCustomShortcut(context: Context, label: String): Boolean = withContext(Dispatchers.IO) {
        val file = customIconFile(context)
        if (!file.exists()) return@withContext false
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            ?: return@withContext false
        val alias = aliases(context)[active(context)]?.component ?: return@withContext false
        val target = Intent(Intent.ACTION_MAIN).apply {
            component = alias
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shortcut = ShortcutInfo.Builder(context, AppIcon.CUSTOM_SHORTCUT_ID)
            .setShortLabel(label.take(30))
            .setLongLabel(label.take(60))
            .setIcon(Icon.createWithBitmap(bitmap))
            .setIntent(target)
            .build()
        runCatching {
            context.getSystemService(ShortcutManager::class.java).requestPinShortcut(shortcut, null)
        }.isSuccess
    }
}
