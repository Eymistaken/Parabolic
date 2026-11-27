package dev.eymistaken.parabolic.core

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where a yt-dlp self-update is; null in [YtdlpUpdater.progress] means none is running. */
data class YtdlpUpdateProgress(
    val stage: Stage,
    /** 0f..1f while the new build downloads and its size is known; null while indeterminate. */
    val fraction: Float? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    /** Release tag being installed, e.g. 2026.11.25.233012; known once the check is done. */
    val version: String? = null,
) {
    enum class Stage(val label: String) {
        CHECKING("Sürüm denetleniyor"),
        DOWNLOADING("İndiriliyor"),
        INSTALLING("Kuruluyor"),
    }
}

/**
 * Updates the yt-dlp zipapp from GitHub releases, reporting progress as it goes.
 *
 * youtubedl-android's own `updateYoutubeDL` does the same steps but blocks
 * without saying how far it got, so the steps are done here instead: read the
 * channel's latest release, download its `yt-dlp` asset, and put it where the
 * library runs it from. The version is recorded under the library's own
 * preference keys, so [YoutubeDL.version] keeps reporting what is installed and
 * the library's updater would see the same state.
 *
 * The download lands next to the installed binary and is used only once it is
 * complete (and its SHA-256 matches, when GitHub lists one), so a failed or
 * interrupted update leaves the working version in place.
 *
 * It also never replaces the binary while a yt-dlp is running; see
 * [holdingBinary]. Python's zipimport reads the zipapp's table of contents
 * once, when the process starts, and reopens the file by path for every module
 * it loads later. yt-dlp loads most of its code that way, in the middle of a
 * run: the extractor for a link only when the link is looked up. A new zipapp
 * renamed over the old one in between makes those reads land at the old
 * offsets in the new file, and yt-dlp dies with "zipimport.ZipImportError: bad
 * local file header". The daily update starts with the app, so a link looked
 * up right after opening it ran into exactly that. A finished download is
 * therefore staged next to the binary and moved into place only while no
 * yt-dlp is running: at once if none is, otherwise when the last one exits.
 *
 * Updates run in their own scope: leaving the settings screen does not abort
 * one, and the daily automatic update shows up in the UI like a manual one.
 */
object YtdlpUpdater {

    private const val TAG = "YtdlpUpdater"

    // Same file and keys as com.yausername.youtubedl_common.SharedPrefsHelper.
    private const val LIBRARY_PREFS = "youtubedl-android"
    private const val KEY_VERSION = "dlpVersion"
    private const val KEY_VERSION_NAME = "dlpVersionName"

    // The staged build waiting for yt-dlp to go idle; survives a restart.
    private const val STAGING_PREFS = "ytdlp-staged"
    private const val KEY_STAGED_VERSION = "version"
    private const val KEY_STAGED_VERSION_NAME = "versionName"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<YtdlpUpdateProgress?>(null)
    val progress: StateFlow<YtdlpUpdateProgress?> = _progress.asStateFlow()

    private class InFlight(val channel: UpdateChannelOption, val job: Deferred<String>)

    private var inFlight: InFlight? = null

    /**
     * Brings yt-dlp up to date on [channel] and returns a short message for the
     * user. A request for the channel already being updated joins that update
     * instead of starting a second one. Cancelling the caller only stops the
     * waiting, not the update.
     */
    suspend fun update(context: Context, channel: UpdateChannelOption): String {
        val app = context.applicationContext
        val current = synchronized(this) {
            inFlight?.takeIf { it.channel == channel && it.job.isActive }
                ?: InFlight(channel, scope.async { mutex.withLock { run(app, channel) } })
                    .also { inFlight = it }
        }
        return current.job.await()
    }

    private suspend fun run(context: Context, channel: UpdateChannelOption): String {
        try {
            _progress.value = YtdlpUpdateProgress(YtdlpUpdateProgress.Stage.CHECKING)
            YtdlpEngine.ensureInit(context)

            val release = latestRelease(channel)
            val prefs = context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
            if (release.tag == prefs.getString(KEY_VERSION, null)) {
                return "yt-dlp zaten güncel: ${release.tag}"
            }

            val binary = installedBinary(context)
            val dir = binary.parentFile ?: throw IOException("yt-dlp dizini bulunamadı")
            dir.mkdirs()
            val staged = stagedBinary(context)
            val alreadyStaged = synchronized(gate) {
                staged.isFile && stagingPrefs(context).getString(KEY_STAGED_VERSION, null) == release.tag
            }
            if (!alreadyStaged) {
                val partial = File(dir, "${binary.name}.download")
                try {
                    download(release, partial)
                    synchronized(gate) {
                        // Replaces an older build that is still waiting, if any.
                        if (!partial.renameTo(staged)) {
                            partial.copyTo(staged, overwrite = true)
                        }
                        stagingPrefs(context).edit()
                            .putString(KEY_STAGED_VERSION, release.tag)
                            .putString(KEY_STAGED_VERSION_NAME, release.name)
                            .commit()
                    }
                } finally {
                    partial.delete()
                }
            }

            _progress.value = YtdlpUpdateProgress(
                stage = YtdlpUpdateProgress.Stage.INSTALLING,
                fraction = 1f,
                downloadedBytes = staged.length(),
                totalBytes = staged.length(),
                version = release.tag,
            )
            // A yt-dlp that started in the meantime may have put it in place already.
            val stillRunning = synchronized(gate) {
                if (running == 0) installStaged(context)
                running
            }
            return if (prefs.getString(KEY_VERSION, null) == release.tag) {
                Log.i(TAG, "installed yt-dlp ${release.tag} (${channel.name})")
                "yt-dlp güncellendi: ${release.tag}"
            } else {
                Log.i(TAG, "staged yt-dlp ${release.tag} (${channel.name}); $stillRunning run(s) still going")
                "yt-dlp ${release.tag} indirildi; süren işlemler bitince kendiliğinden kurulacak"
            }
        } finally {
            _progress.value = null
        }
    }

    // -- keeping the binary in place while yt-dlp runs ------------------------

    /** Guards [running] and every move of the staged build into place. */
    private val gate = Any()

    /** yt-dlp processes started through [holdingBinary] that have not exited yet. */
    private var running = 0

    /**
     * Runs [block], which must start yt-dlp and wait for it to exit, while
     * keeping the installed zipapp in place. A build staged by an update is
     * installed just before, if no other yt-dlp is running, or right after the
     * last one exits.
     */
    fun <T> holdingBinary(context: Context, block: () -> T): T {
        val app = context.applicationContext
        synchronized(gate) {
            if (running == 0) installStagedQuietly(app)
            running++
        }
        try {
            return block()
        } finally {
            synchronized(gate) {
                running--
                if (running == 0) installStagedQuietly(app)
            }
        }
    }

    private fun installStagedQuietly(context: Context) {
        try {
            installStaged(context)
        } catch (failure: Exception) {
            // The installed build still works; the next update downloads again.
            Log.w(TAG, "could not install the staged yt-dlp", failure)
            discardStaged(context)
        }
    }

    /**
     * Moves the staged build over the installed one and records its version.
     * Must be called holding [gate] with nothing running. Returns false when
     * there is nothing (complete) to install.
     */
    private fun installStaged(context: Context): Boolean {
        val staged = stagedBinary(context)
        val stagingPrefs = stagingPrefs(context)
        val version = stagingPrefs.getString(KEY_STAGED_VERSION, null)
        if (!staged.isFile || version == null) {
            // Half of a staging that the process did not live to finish.
            if (staged.exists() || version != null) discardStaged(context)
            return false
        }
        val binary = installedBinary(context)
        if (!staged.renameTo(binary)) {
            staged.copyTo(binary, overwrite = true)
            staged.delete()
        }
        context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VERSION, version)
            .putString(KEY_VERSION_NAME, stagingPrefs.getString(KEY_STAGED_VERSION_NAME, null) ?: version)
            .commit()
        stagingPrefs.edit().clear().commit()
        Log.i(TAG, "yt-dlp $version is now in place")
        return true
    }

    private fun discardStaged(context: Context) {
        stagedBinary(context).delete()
        stagingPrefs(context).edit().clear().commit()
    }

    private fun stagingPrefs(context: Context) =
        context.getSharedPreferences(STAGING_PREFS, Context.MODE_PRIVATE)

    private class Release(
        val tag: String,
        val name: String,
        val assetUrl: String,
        val assetSize: Long,
        val sha256: String?,
    )

    private fun latestRelease(channel: UpdateChannelOption): Release {
        val apiUrl = when (channel) {
            UpdateChannelOption.STABLE -> YoutubeDL.UpdateChannel.STABLE.apiUrl
            UpdateChannelOption.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY.apiUrl
            UpdateChannelOption.MASTER -> YoutubeDL.UpdateChannel.MASTER.apiUrl
        }
        val connection = open(apiUrl).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val body = try {
            connection.ensureOk()
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val root = json.parseToJsonElement(body).jsonObject
        val tag = root.string("tag_name") ?: throw IOException("GitHub sürüm bilgisi okunamadı")
        val asset = (root["assets"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("name") == YoutubeDL.ytdlpBin }
            ?: throw IOException("Bu sürümde yt-dlp dosyası yok")
        return Release(
            tag = tag,
            name = root.string("name") ?: tag,
            assetUrl = asset.string("browser_download_url") ?: throw IOException("İndirme adresi yok"),
            assetSize = asset.string("size")?.toLongOrNull() ?: -1L,
            // GitHub lists "sha256:<hex>" for release assets uploaded since mid-2025.
            sha256 = asset.string("digest")
                ?.takeIf { it.startsWith("sha256:") }
                ?.removePrefix("sha256:")
                ?.lowercase(),
        )
    }

    private suspend fun download(release: Release, target: File) {
        val connection = open(release.assetUrl)
        try {
            connection.ensureOk()
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.assetSize
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReported = -1L
            fun report() {
                _progress.value = YtdlpUpdateProgress(
                    stage = YtdlpUpdateProgress.Stage.DOWNLOADING,
                    fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null,
                    downloadedBytes = done,
                    totalBytes = total.coerceAtLeast(0),
                    version = release.tag,
                )
                lastReported = done
            }
            report()

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        // About every 1% of the file; the bar animates in between.
                        if (total <= 0 || done - lastReported >= total / 100) report()
                    }
                }
            }
            report()

            if (total > 0 && done != total) {
                throw IOException("İndirme yarım kaldı ($done / $total bayt)")
            }
            release.sha256?.let { expected ->
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != expected) throw IOException("İndirilen dosya doğrulanamadı")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** The zipapp youtubedl-android runs: noBackupFilesDir/youtubedl-android/yt-dlp/yt-dlp. */
    private fun installedBinary(context: Context): File =
        File(File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName), YoutubeDL.ytdlpBin)

    /** A verified new build next to [installedBinary], waiting for yt-dlp to go idle. */
    private fun stagedBinary(context: Context): File =
        installedBinary(context).let { File(it.parentFile, "${it.name}.staged") }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Parabolic-Android")
        }

    private fun HttpURLConnection.ensureOk() {
        val code = responseCode
        if (code !in 200..299) {
            val hint = if (code == 403 || code == 429) " (GitHub istek sınırı, biraz sonra tekrar dene)" else ""
            throw IOException("GitHub HTTP $code$hint")
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
}
