package dev.eymistaken.parabolic.core

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.aria2c.Aria2c
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Everything that talks to yt-dlp lives here.
 *
 * The bundled runtime (python + ffmpeg + quickjs) comes from youtubedl-android;
 * the library passes `--js-runtimes quickjs:...` and `--ffmpeg-location` on every
 * invocation itself, which is what keeps YouTube's JS challenges solvable without
 * a Deno binary like the desktop build uses.
 */
object YtdlpEngine {

    private const val TAG = "YtdlpEngine"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var initialized = false

    class ProbeFailure(message: String) : Exception(message)

    suspend fun ensureInit(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        synchronized(this@YtdlpEngine) {
            if (initialized) return@synchronized
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            runCatching { Aria2c.getInstance().init(context) }
                .onFailure { Log.w(TAG, "aria2c unavailable", it) }
            initialized = true
        }
    }

    fun ytdlpVersion(context: Context): String? = runCatching {
        YoutubeDL.getInstance().version(context)
    }.getOrNull()

    /** Progress of the running yt-dlp update, or null when none is running. */
    val updateProgress: StateFlow<YtdlpUpdateProgress?> get() = YtdlpUpdater.progress

    /** Updates yt-dlp from GitHub and returns a message for the user; see [YtdlpUpdater]. */
    suspend fun updateYtdlp(context: Context, channel: UpdateChannelOption): String =
        YtdlpUpdater.update(context, channel)

    // -- probing -----------------------------------------------------------

    /**
     * @param wholePlaylist resolve the playlist a `watch?v=…&list=…` link points
     * into, instead of the single video (entries stay flat, so this is fast).
     */
    suspend fun probe(context: Context, url: String, wholePlaylist: Boolean = false): MediaProbe = withContext(Dispatchers.IO) {
        ensureInit(context)
        val ipv4First = preferIpv4
        val raw = try {
            probeOnce(context, url, wholePlaylist, forceIpv4 = ipv4First)
        } catch (failure: ProbeFailure) {
            if (!isBotCheck(failure.message)) throw failure
            if (ipv4First) throw BotCheckFailure(context)
            // Bot checks are mostly about the address; an IPv6 one is flagged
            // more often than the network's IPv4 one, so try that once.
            try {
                probeOnce(context, url, wholePlaylist, forceIpv4 = true).also { preferIpv4 = true }
            } catch (again: ProbeFailure) {
                if (isBotCheck(again.message)) throw BotCheckFailure(context)
                throw again
            }
        }
        parseProbe(url, raw)
    }

    private fun probeOnce(context: Context, url: String, wholePlaylist: Boolean, forceIpv4: Boolean): String {
        val cookies = YoutubeAccount.cookieFileFor(context, "probe-${System.nanoTime()}")
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption(if (wholePlaylist) "--yes-playlist" else "--no-playlist")
                addOption("--flat-playlist")
                addOption("--no-warnings")
                addOption("--ignore-config")
                addOption("--socket-timeout", "20")
                addOption("--retries", "3")
                addSessionOptions(context, cookies, forceIpv4)
            }
            val response = try {
                YtdlpUpdater.holdingBinary(context) {
                    YoutubeDL.getInstance().execute(request, null, null)
                }
            } catch (failure: YoutubeDLException) {
                throw ProbeFailure(failure.message?.lastMeaningfulLine() ?: "yt-dlp hata verdi")
            }
            val raw = response.out.trim()
            if (raw.isEmpty()) {
                throw ProbeFailure(response.err.lastMeaningfulLine() ?: "yt-dlp bilgi döndürmedi")
            }
            return raw
        } finally {
            cookies?.delete()
        }
    }

    // -- YouTube's bot check -------------------------------------------------

    /**
     * Set once a retry over IPv4 got past YouTube's bot check, so later runs
     * in this process start there instead of tripping it again first.
     */
    @Volatile
    private var preferIpv4 = false

    /** "Sign in to confirm you're not a bot", whatever the apostrophe. */
    fun isBotCheck(text: String?): Boolean = text?.contains("not a bot", ignoreCase = true) == true

    /** YouTube still asked to confirm this is not a bot after every retry. */
    class BotCheckFailure(context: Context) : Exception(YtdlpEngine.botCheckMessage(context))

    private fun botCheckMessage(context: Context): String {
        YoutubeAccount.load(context)
        return if (YoutubeAccount.signedIn.value) {
            "YouTube, giriş yapılmış olmasına rağmen bot olmadığını doğrulamanı istedi. " +
                "Biraz bekleyip yeniden dene; sürerse Ayarlar'da YouTube hesabından çıkıp tekrar giriş yap."
        } else {
            "Bu bağlantı YouTube'un bot denetimine takıldı (\"Sign in to confirm you're not a bot\"). " +
                "YouTube hesabınla giriş yaparsan genellikle düzelir; başka bir ağa (ör. Wi‑Fi) geçmek de işe yarayabilir."
        }
    }

    /**
     * What every yt-dlp run gets, so it behaves like yt-dlp on a desktop:
     * a persistent cache (youtubedl-android passes --no-cache-dir unless one
     * is set, which made every run fetch and solve YouTube's player code
     * again), the signed-in cookies if there are any, and IPv4 when asked.
     */
    private fun YoutubeDLRequest.addSessionOptions(context: Context, cookies: File?, forceIpv4: Boolean) {
        addOption("--cache-dir", File(context.cacheDir, "yt-dlp").absolutePath)
        cookies?.let { addOption("--cookies", it.absolutePath) }
        if (forceIpv4) addOption("--force-ipv4")
    }

    private fun parseProbe(url: String, raw: String): MediaProbe {
        // A playlist URL can print several JSON documents; take the first complete one.
        val root = json.parseToJsonElement(raw.substringBefore("\n{")).jsonObject
        val type = root.str("_type")
        val isPlaylist = type == "playlist" || type == "multi_video"

        val entries = root["entries"] as? JsonArray
        val playlistCount = root.num("playlist_count")?.toInt()
            ?: entries?.size
            ?: 0

        val formats = root["formats"] as? JsonArray
        val heights = formats
            ?.mapNotNull { (it as? JsonObject)?.num("height")?.toInt() }
            ?.filter { it > 0 }
            ?.distinct()
            ?.sortedDescending()
            .orEmpty()

        val hasAudioOnly = formats?.any { element ->
            val f = element as? JsonObject ?: return@any false
            val vcodec = f.str("vcodec")
            val acodec = f.str("acodec")
            (vcodec == null || vcodec == "none") && acodec != null && acodec != "none"
        } ?: true

        val title = root.str("title")
            ?: root.str("fulltitle")
            ?: entries?.firstOrNull()?.let { (it as? JsonObject)?.str("title") }
            ?: "Bilinmeyen başlık"

        val thumbnail = root.str("thumbnail")
            ?: (root["thumbnails"] as? JsonArray)?.lastOrNull()
                ?.let { (it as? JsonObject)?.str("url") }
            ?: entries?.firstOrNull()?.let { entry ->
                val obj = entry as? JsonObject
                obj?.str("thumbnail")
                    ?: (obj?.get("thumbnails") as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
            }

        return MediaProbe(
            url = url,
            title = title,
            uploader = root.str("uploader") ?: root.str("channel") ?: root.str("uploader_id"),
            thumbnail = thumbnail,
            durationSeconds = root.num("duration")?.toInt() ?: 0,
            isPlaylist = isPlaylist,
            playlistCount = playlistCount,
            availableHeights = heights,
            extractor = root.str("extractor_key") ?: root.str("extractor"),
            hasAudioOnly = hasAudioOnly,
        )
    }

    // -- downloading -------------------------------------------------------

    /**
     * How a run ended. The files themselves are picked up from the output folder
     * with [finishedMedia]; [errors] lists yt-dlp ERROR lines (failed playlist
     * items, failed post-processing) and [exitError] is set when it exited non-zero.
     */
    class DownloadResult(
        val warnings: List<String>,
        val errors: List<String> = emptyList(),
        val exitError: String? = null,
    ) {
        /** YouTube refused with "Sign in to confirm you're not a bot". */
        val botCheck: Boolean get() = YtdlpEngine.isBotCheck(exitError) || errors.any { YtdlpEngine.isBotCheck(it) }
    }

    /** yt-dlp's per-format parts (`name.f137.mp4`) that only exist before merging. */
    private val INTERMEDIATE_PART = Regex("""\.f[0-9]+(-[0-9]+)?\.[A-Za-z0-9]+$""")

    /**
     * Finished audio/video files in [outDir]: never partial downloads, fragments,
     * pre-merge parts, ffmpeg temp files, or sidecars like thumbnails and
     * subtitles (those are embedded; leftovers are not worth saving).
     */
    fun finishedMedia(outDir: File): List<File> = outDir.walkTopDown()
        .filter { it.isFile && it.length() > 0 }
        .filterNot { it.name.contains(".part") || it.name.endsWith(".ytdl") || it.name.contains(".temp.") }
        .filterNot { INTERMEDIATE_PART.containsMatchIn(it.name) }
        .filter {
            val mime = MediaStoreSaver.mimeOf(it.extension)
            mime.startsWith("video/") || mime.startsWith("audio/")
        }
        .sortedBy { it.path }
        .toList()

    private fun linesWith(prefix: String, text: String): List<String> = text.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix).trim() }
        .toList()

    /**
     * Runs one download to completion and returns the files yt-dlp produced.
     * [outDir] must be an empty directory owned by this task: that is how the
     * produced file names are discovered afterwards without parsing stdout.
     */
    suspend fun download(
        context: Context,
        taskId: String,
        spec: DownloadSpec,
        prefs: Prefs,
        outDir: File,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        ensureInit(context)
        outDir.mkdirs()
        val ipv4First = preferIpv4
        // Retrying is only safe while nothing has been downloaded: finished
        // playlist items are moved out of outDir as they complete, and a
        // second run would fetch them all again.
        var started = false
        val first = downloadOnce(context, taskId, spec, prefs, outDir, ipv4First) { progress, eta, line ->
            if (line.startsWith("[download] Destination:")) started = true
            onProgress(progress, eta, line)
        }
        if (!first.botCheck) return@withContext first
        if (ipv4First || started || finishedMedia(outDir).isNotEmpty()) {
            return@withContext first.withBotCheckMessage(context)
        }

        onProgress(-1f, -1, "[Parabolic] YouTube bot denetimi istedi; IPv4 ile yeniden deneniyor")
        val second = downloadOnce(context, taskId, spec, prefs, outDir, forceIpv4 = true, onProgress)
        when {
            !second.botCheck -> {
                if (second.exitError == null || finishedMedia(outDir).isNotEmpty()) preferIpv4 = true
                second
            }
            else -> second.withBotCheckMessage(context)
        }
    }

    private fun downloadOnce(
        context: Context,
        taskId: String,
        spec: DownloadSpec,
        prefs: Prefs,
        outDir: File,
        forceIpv4: Boolean,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
    ): DownloadResult {
        val cookies = YoutubeAccount.cookieFileFor(context, taskId)
        try {
            val request = buildRequest(spec, prefs, outDir).apply {
                addSessionOptions(context, cookies, forceIpv4)
            }
            return try {
                val response = YtdlpUpdater.holdingBinary(context) {
                    YoutubeDL.getInstance().execute(request, taskId, false) { progress, eta, line ->
                        onProgress(progress, eta, line)
                    }
                }
                DownloadResult(linesWith("WARNING:", response.err))
            } catch (failure: YoutubeDLException) {
                // With --ignore-errors, a failed post-processing step (cover art, say)
                // or a single unavailable playlist item still makes yt-dlp exit with 1.
                // Whether that sinks the whole task is decided by the caller, based on
                // whether any finished media file came out of it.
                val stderr = failure.message.orEmpty()
                val last = stderr.lastMeaningfulLine()
                DownloadResult(
                    warnings = linesWith("WARNING:", stderr),
                    errors = linesWith("ERROR:", stderr),
                    exitError = when {
                        last == null -> "yt-dlp hata verdi"
                        isNetworkError(last) -> NETWORK_FAILURE_MESSAGE
                        else -> last
                    },
                )
            }
        } finally {
            cookies?.delete()
        }
    }

    /**
     * yt-dlp's last line when the connection stayed gone through every retry:
     * "Giving up after 10 retries", or the socket error of the last attempt
     * (no DNS, no route, refused, reset, timed out).
     */
    private val NETWORK_ERROR = Regex(
        "Giving up after|Failed to establish a new connection|Failed to resolve|" +
            "No address associated with hostname|Temporary failure in name resolution|" +
            "Name or service not known|Network is unreachable|Network is down|No route to host|" +
            "Software caused connection abort|Connection reset|Connection aborted|Connection refused|" +
            "Remote end closed connection|RemoteDisconnected|IncompleteRead|timed out",
        RegexOption.IGNORE_CASE,
    )

    private fun isNetworkError(text: String): Boolean = NETWORK_ERROR.containsMatchIn(text)

    private const val NETWORK_FAILURE_MESSAGE =
        "İnternet bağlantısı koptu ve yt-dlp'nin yeniden denemeleri boyunca geri gelmedi. " +
            "Bağlantı gelince Tekrar dene'ye bas; indirme kaldığı yerden devam eder."

    private fun DownloadResult.withBotCheckMessage(context: Context) =
        DownloadResult(warnings, errors, botCheckMessage(context))

    fun cancel(taskId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) }.getOrDefault(false)

    private fun buildRequest(spec: DownloadSpec, prefs: Prefs, outDir: File): YoutubeDLRequest =
        YoutubeDLRequest(spec.url).apply {
            addOption("--ignore-config")
            // Subtitles, thumbnails and other extras must never cost the video
            // itself: with --ignore-errors yt-dlp turns their failures (YouTube
            // answering 429 to subtitle requests is common) into warnings, while
            // a failed media download still exits non-zero and is reported.
            addOption("--ignore-errors")
            addOption("--no-mtime")
            // A failed task keeps its work folder, so "Tekrar dene" picks the
            // .part files up again instead of starting over.
            addOption("--continue")
            // yt-dlp retries a dropped connection 10 times, but by default with
            // no wait in between: with the network gone (no DNS, no route) all
            // ten fail within a second or two. Waiting 1, 2, 4, 8, 16 and then
            // 30 s spreads them over about three minutes, so a Wi-Fi drop, a
            // tunnel or a switch to mobile data is ridden out and the download
            // goes on from the byte it had reached.
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--retry-sleep", "http,fragment:exp=1:30")
            // Looking the video up again at the start of the run: YouTube's
            // requests are retried on network errors too, for about 30 s.
            addOption("--extractor-retries", "5")
            addOption("--retry-sleep", "extractor:exp=1:30")
            addOption("--socket-timeout", "30")
            addOption("--paths", outDir.absolutePath)

            if (spec.playlist) {
                addOption("--yes-playlist")
                // <playlist>/<NNN - title>.<ext>; the folder becomes Movies|Music/Parabolic/<playlist>.
                addOption("-o", "%(playlist_title,playlist,uploader).80B/%(playlist_index)03d - %(title).120B.%(ext)s")
            } else {
                addOption("--no-playlist")
                addOption("-o", "%(title).150B.%(ext)s")
            }

            // Speed: parallel fragments is the single biggest win on DASH sources.
            addOption("--concurrent-fragments", prefs.concurrentFragments.coerceIn(1, 16))
            if (prefs.useAria2c) {
                addOption("--downloader", "libaria2c.so")
                // yt-dlp does not hand --retries on to aria2c, which on its own
                // gives up after 5 tries with no wait in between.
                addOption("--downloader-args", "aria2c:--max-tries=10 --retry-wait=15")
            }

            when (spec.kind) {
                MediaKind.VIDEO -> applyVideoOptions(spec)
                MediaKind.AUDIO -> applyAudioOptions(spec)
            }

            if (spec.embedMetadata) {
                addOption("--embed-metadata")
                addOption("--embed-chapters")
            }
            // yt-dlp can embed cover art in mp3, mkv/mka, ogg/opus/flac and
            // m4a/mp4/m4v/mov only; asking for it in a WebM fails the step.
            val coverSupported = !(spec.kind == MediaKind.VIDEO && spec.container == VideoContainer.WEBM)
            if (spec.embedThumbnail && coverSupported) {
                addOption("--embed-thumbnail")
                if (spec.kind == MediaKind.AUDIO && spec.cropAudioThumbnail) {
                    // Cover art should be square. YouTube Music uploads put the
                    // album art in the middle of a 16:9 frame, so a centered square
                    // crop recovers the real cover. Converting to PNG guarantees the
                    // convertor (and with it the crop) runs: it skips files already
                    // in the target format, and YouTube serves JPEG/WebP.
                    addOption("--convert-thumbnails", "png")
                    addOption(
                        "--ppa",
                        "ThumbnailsConvertor+ffmpeg_o:-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"",
                    )
                }
            }
            if (spec.embedSubtitles && spec.kind == MediaKind.VIDEO) {
                addOption("--write-subs")
                addOption("--write-auto-subs")
                addOption("--sub-langs", spec.subtitleLanguages.ifBlank { "tr,en" })
                // WebM only accepts WebVTT subtitle tracks; yt-dlp silently skips
                // anything else there. MP4 and MKV take SRT.
                addOption("--convert-subs", if (spec.container == VideoContainer.WEBM) "vtt" else "srt")
                addOption("--embed-subs")
                // Space out subtitle requests; bursts are what trigger HTTP 429.
                addOption("--sleep-subtitles", "2")
            }
            if (spec.sponsorBlock) {
                addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
            }
        }

    private fun YoutubeDLRequest.applyVideoOptions(spec: DownloadSpec) {
        val h = spec.quality.maxHeight
        val heightFilter = if (h != null) "[height<=$h]" else ""
        val selector = when (spec.container) {
            VideoContainer.MP4 ->
                // avc1 + m4a first: hardware decoding on virtually every Android device.
                "bv*${heightFilter}[vcodec^=avc1]+ba[acodec^=mp4a]/" +
                    "bv*${heightFilter}+ba/b${heightFilter}/bv*+ba/b"
            VideoContainer.WEBM ->
                "bv*${heightFilter}[vcodec^=vp9]+ba[acodec=opus]/" +
                    "bv*${heightFilter}+ba/b${heightFilter}/bv*+ba/b"
            VideoContainer.MKV ->
                "bv*${heightFilter}+ba/b${heightFilter}/bv*+ba/b"
        }
        addOption("-f", selector)
        addOption("--merge-output-format", spec.container.ext)
    }

    private fun YoutubeDLRequest.applyAudioOptions(spec: DownloadSpec) {
        addOption("-f", "ba/b")
        addOption("-x")
        addOption("--audio-format", spec.audioFormat.code)
        addOption("--audio-quality", "0")
    }
}

// -- small JSON helpers --------------------------------------------------

private fun JsonObject.str(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (content.isBlank() || content == "null") null else content
}

private fun JsonObject.num(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.toDoubleOrNull()
}

internal fun String.lastMeaningfulLine(): String? = lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .lastOrNull { !it.startsWith("WARNING") }
    ?.removePrefix("ERROR: ")
