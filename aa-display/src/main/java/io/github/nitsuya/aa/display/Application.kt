package io.github.nitsuya.aa.display

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.github.kyuubiran.ezxhelper.utils.tryOrNull
import com.google.android.material.color.DynamicColors
import com.topjohnwu.superuser.Shell
import io.github.nitsuya.aa.display.util.ConfigBackup
import io.github.nitsuya.aa.display.xposed.CoreManagerService
import io.github.nitsuya.aa.display.xposed.CoreManager


val IsSystemEnv by lazy {
    Process.myUid() == 1000
}
val CoreApi by lazy {
    if(!IsSystemEnv) CoreManager
    else CoreManagerService.instance!!
}
lateinit var App : Application
class Application: android.app.Application() {
    companion object {
        const val CHANNEL_DISPLAY_WINDOW = "display_window"
    }

    init {
        App = this
        tryOrNull {
            Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(30))
        }
    }

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        createNotificationChannels()
        migrateConfig()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_DISPLAY_WINDOW,
            "Display Window",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for the floating display window"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Migrate config from old version on first launch after update.
     */
    private fun migrateConfig() {
        val prefs = getSharedPreferences(io.github.nitsuya.aa.display.util.AADisplayConfig.ConfigName, MODE_PRIVATE)
        val savedVersion = io.github.nitsuya.aa.display.util.AADisplayConfig.ConfigVersion.get(prefs)
        if (savedVersion < BuildConfig.VERSION_CODE && savedVersion != 0) {
            val backup = ConfigBackup.backup(prefs)
            ConfigBackup.restore(prefs, backup)
        }
        io.github.nitsuya.aa.display.util.AADisplayConfig.ConfigVersion.put(prefs.edit(), BuildConfig.VERSION_CODE).apply()
    }
}
