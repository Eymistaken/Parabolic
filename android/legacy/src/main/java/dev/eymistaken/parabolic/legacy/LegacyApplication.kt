package dev.eymistaken.parabolic.legacy

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dev.eymistaken.parabolic.core.DownloadRepository
import dev.eymistaken.parabolic.core.Notifications
import dev.eymistaken.parabolic.core.Prefs
import dev.eymistaken.parabolic.core.YoutubeAccount
import dev.eymistaken.parabolic.core.YtdlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Same start-up as the main app's ParabolicApplication, minus the variable font
 * it registers for the Expressive UI: this APK draws with the system font.
 */
class LegacyApplication : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var downloads: DownloadRepository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        downloads = DownloadRepository(this, prefs)
        Notifications.createChannels(this)
        YoutubeAccount.load(this)

        scope.launch {
            runCatching { YtdlpEngine.ensureInit(this@LegacyApplication) }
                .onFailure { Log.e(TAG, "yt-dlp runtime failed to initialize", it) }
            maybeAutoUpdate()
        }
    }

    /** At most once a day, and only on an unmetered network. */
    private suspend fun maybeAutoUpdate() {
        if (!prefs.autoUpdateOnLaunch) return
        val age = System.currentTimeMillis() - prefs.lastAutoUpdateCheck
        if (age < TimeUnit.HOURS.toMillis(24)) return
        if (!isOnUnmeteredNetwork()) {
            Log.i(TAG, "auto update skipped: metered network")
            return
        }
        runCatching { YtdlpEngine.updateYtdlp(this, prefs.updateChannel) }
            .onSuccess {
                prefs.lastAutoUpdateCheck = System.currentTimeMillis()
                Log.i(TAG, it)
            }
            .onFailure { Log.w(TAG, "auto update failed", it) }
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val TAG = "Parabolic"
    }
}
