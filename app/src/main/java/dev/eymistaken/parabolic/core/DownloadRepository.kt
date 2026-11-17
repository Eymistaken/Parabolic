package dev.eymistaken.parabolic.core

import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import dev.eymistaken.parabolic.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the download queue, the running yt-dlp processes and the history.
 * One instance per process, created by [dev.eymistaken.parabolic.ParabolicApplication].
 */
class DownloadRepository(
    private val context: Context,
    val prefs: Prefs,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyStore = HistoryStore(context)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scheduleLock = Mutex()
    private val running = mutableSetOf<String>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _history = MutableStateFlow(historyStore.load())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val activeCount: Int get() = _tasks.value.count { it.state.isActive }

    fun showMessage(message: String) {
        _toast.value = message
    }

    fun consumeMessage() {
        _toast.value = null
    }

    // -- queue -------------------------------------------------------------

    fun enqueue(specs: List<DownloadSpec>) {
        if (specs.isEmpty()) return
        val newTasks = specs.map { DownloadTask(id = UUID.randomUUID().toString(), spec = it) }
        _tasks.value = newTasks + _tasks.value
        syncService()
        pump()
    }

    fun enqueue(spec: DownloadSpec) = enqueue(listOf(spec))

    fun retry(task: DownloadTask) {
        update(task.id) {
            it.copy(
                state = DownloadState.QUEUED,
                stage = DownloadStage.WAITING,
                progress = null,
                error = null,
                speed = null,
                etaSeconds = -1,
                log = emptyList(),
            )
        }
        syncService()
        pump()
    }

    private val cancelRequested = ConcurrentHashMap.newKeySet<String>()

    fun cancel(id: String) {
        cancelRequested += id
        // Killing yt-dlp lets the task wind down on its own, which is what keeps
        // already-finished playlist items. Only a task with no process yet (still
        // queued or initializing) has its coroutine cancelled outright.
        if (!YtdlpEngine.cancel(id)) jobs.remove(id)?.cancel()
        update(id) {
            if (it.state.isTerminal) it else it.copy(state = DownloadState.CANCELED, speed = null, progress = null)
        }
        scope.launch { finish(id) }
    }

    fun dismiss(id: String) {
        if (_tasks.value.firstOrNull { it.id == id }?.state?.isActive == true) {
            cancel(id)
        }
        _tasks.value = _tasks.value.filterNot { it.id == id }
        syncService()
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.state.isActive }
        syncService()
    }

    private fun pump() = scope.launch {
        scheduleLock.withLock {
            val limit = prefs.concurrentDownloads.coerceIn(1, 6)
            while (running.size < limit) {
                val next = _tasks.value.firstOrNull {
                    it.state == DownloadState.QUEUED && it.id !in running
                } ?: break
                running += next.id
                jobs[next.id] = launchTask(next)
            }
        }
    }

    private suspend fun finish(id: String) {
        scheduleLock.withLock {
            running -= id
            jobs.remove(id)
        }
        syncService()
        pump()
    }

    // -- one download ------------------------------------------------------

    private fun launchTask(task: DownloadTask): Job = scope.launch {
        val outDir = File(context.filesDir, "dl/${task.id}")
        val spec = task.spec
        update(task.id) { it.copy(state = DownloadState.RUNNING, stage = DownloadStage.PREPARING) }
        syncService()

        val tracker = ProgressTracker(spec.kind)
        val folder = spec.targetFolder ?: prefs.folderFor(spec.kind)
        val saved = mutableListOf<MediaStoreSaver.Saved>()
        val handled = mutableSetOf<String>()
        val sweepLock = Mutex()
        var folderFailed = false

        // Moves every finished media file out of the private work folder. For a
        // playlist this runs each time yt-dlp moves on to the next item, so the
        // finished ones show up in the gallery right away and survive a cancel.
        suspend fun sweep() = sweepLock.withLock {
            for (file in YtdlpEngine.finishedMedia(outDir)) {
                if (!handled.add(file.absolutePath)) continue
                val subfolder = file.parentFile
                    ?.takeIf { it.absolutePath != outDir.absolutePath }
                    ?.relativeTo(outDir)?.path
                val result = runCatching {
                    if (folder != null && !folderFailed) {
                        try {
                            MediaStoreSaver.saveToTree(context, file, Uri.parse(folder), subfolder)
                        } catch (e: Exception) {
                            Log.w("DownloadRepository", "picked folder unusable, falling back", e)
                            folderFailed = true
                            MediaStoreSaver.save(context, file, spec.kind, subfolder)
                        }
                    } else {
                        MediaStoreSaver.save(context, file, spec.kind, subfolder)
                    }
                }
                result.onSuccess { saved += it }
                    .onFailure { Log.e("DownloadRepository", "could not save ${file.name}", it) }
            }
            update(task.id) { it.copy(savedNames = saved.map { s -> s.displayName }) }
        }

        var lastEmit = 0L
        var lastItem = 0
        try {
            val result = YtdlpEngine.download(
                context = context,
                taskId = task.id,
                spec = spec,
                prefs = prefs,
                outDir = outDir,
            ) { percent, eta, line ->
                if (task.id in cancelRequested) YtdlpEngine.cancel(task.id)
                val changed = tracker.consume(line, percent)
                if (tracker.itemIndex > lastItem) {
                    val hadPrevious = lastItem > 0
                    lastItem = tracker.itemIndex
                    if (hadPrevious) scope.launch { sweep() }
                }
                val now = System.currentTimeMillis()
                if (now - lastEmit < 200 && !changed) return@download
                lastEmit = now
                update(task.id) { current ->
                    current.copy(
                        state = if (tracker.stage.isPostProcessing) DownloadState.PROCESSING else DownloadState.RUNNING,
                        stage = tracker.stage,
                        progress = tracker.overall,
                        itemIndex = tracker.itemIndex,
                        itemCount = tracker.itemCount,
                        etaSeconds = if (tracker.stage.isPostProcessing) -1 else eta,
                        speed = if (tracker.stage.isPostProcessing) null else tracker.speed,
                        totalSize = tracker.size ?: current.totalSize,
                        log = tracker.snapshotLog(),
                    )
                }
                syncService()
            }

            update(task.id) {
                it.copy(state = DownloadState.SAVING, stage = DownloadStage.SAVING, progress = 1f, speed = null, etaSeconds = -1)
            }
            syncService()
            sweep()

            if (saved.isEmpty()) {
                throw IllegalStateException(
                    result.exitError ?: result.errors.lastOrNull() ?: result.warnings.lastOrNull()
                        ?: "yt-dlp hiçbir dosya üretmedi",
                )
            }

            val notice = listOfNotNull(
                noticeFor(result.warnings, result.errors, spec.playlist),
                if (folderFailed) "Seçilen klasöre yazılamadı; varsayılan konuma kaydedildi." else null,
            ).joinToString(" ").ifBlank { null }
            // A playlist plays from its first item; a single download opens the
            // main file (largest, in case anything else slipped through).
            val ordered = if (spec.playlist) saved.toList() else saved.sortedByDescending { it.size }
            val shownPath = if (spec.playlist) {
                ordered.firstOrNull()?.path?.substringBeforeLast('/')
            } else {
                ordered.firstOrNull()?.path
            }
            update(task.id) {
                it.copy(
                    state = DownloadState.DONE,
                    stage = DownloadStage.DONE,
                    progress = 1f,
                    savedNames = ordered.map { s -> s.displayName },
                    savedUris = ordered.map { s -> s.uri?.toString().orEmpty() },
                    savedMimes = ordered.map { s -> s.mime },
                    savedPath = shownPath,
                    etaSeconds = -1,
                    notice = notice,
                    log = tracker.snapshotLog() +
                        result.warnings.map { w -> "WARNING: $w" } +
                        result.errors.map { e -> "ERROR: $e" },
                )
            }
            addHistory(
                HistoryEntry(
                    id = task.id,
                    url = spec.url,
                    title = spec.title,
                    thumbnail = spec.thumbnail,
                    kind = spec.kind,
                    savedNames = ordered.map { s -> s.displayName },
                    savedUris = ordered.map { s -> s.uri?.toString().orEmpty() },
                    savedMimes = ordered.map { s -> s.mime },
                    sizeBytes = ordered.sumOf { s -> s.size },
                    spec = spec,
                ),
            )
            Notifications.completed(
                context,
                "İndirme tamamlandı",
                if (spec.playlist) "${spec.title}\n${ordered.size} öğe · $shownPath" else "${spec.title}\n${shownPath ?: ""}",
                task.id.hashCode(),
            )
        } catch (canceled: YoutubeDL.CanceledException) {
            onCanceled(task, { sweep() }, saved)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            update(task.id) { it.copy(state = DownloadState.CANCELED, speed = null) }
            throw cancellation
        } catch (error: Throwable) {
            Log.e("DownloadRepository", "download failed", error)
            val message = (error.message ?: error.toString()).lastMeaningfulLine()
                ?: "Bilinmeyen hata"
            update(task.id) {
                it.copy(
                    state = DownloadState.FAILED,
                    error = message,
                    speed = null,
                    log = tracker.snapshotLog() + (error.message ?: "").lines().filter { l -> l.isNotBlank() }.takeLast(20),
                )
            }
            Notifications.completed(
                context,
                "İndirme başarısız",
                "${spec.title}\n$message",
                task.id.hashCode(),
            )
        } finally {
            cancelRequested -= task.id
            runCatching { outDir.deleteRecursively() }
            finish(task.id)
        }
    }

    /** A cancelled playlist keeps the items that had already finished. */
    private suspend fun onCanceled(
        task: DownloadTask,
        sweep: suspend () -> Unit,
        saved: List<MediaStoreSaver.Saved>,
    ) {
        runCatching { withContext(NonCancellable) { sweep() } }
        if (saved.isEmpty()) {
            update(task.id) { it.copy(state = DownloadState.CANCELED, speed = null) }
            return
        }
        update(task.id) {
            it.copy(
                state = DownloadState.CANCELED,
                speed = null,
                savedNames = saved.map { s -> s.displayName },
                savedUris = saved.map { s -> s.uri?.toString().orEmpty() },
                savedMimes = saved.map { s -> s.mime },
                savedPath = saved.first().path?.substringBeforeLast('/'),
                notice = "İptal edildi; bitmiş ${saved.size} öğe kaydedildi.",
            )
        }
        addHistory(
            HistoryEntry(
                id = task.id,
                url = task.spec.url,
                title = task.spec.title,
                thumbnail = task.spec.thumbnail,
                kind = task.spec.kind,
                savedNames = saved.map { s -> s.displayName },
                savedUris = saved.map { s -> s.uri?.toString().orEmpty() },
                savedMimes = saved.map { s -> s.mime },
                sizeBytes = saved.sumOf { s -> s.size },
                spec = task.spec,
            ),
        )
    }

    /**
     * Turns yt-dlp's warnings and post-processing errors into short Turkish
     * sentences, all of them, so one problem never hides another.
     */
    private fun noticeFor(warnings: List<String>, errors: List<String>, playlist: Boolean = false): String? {
        val all = warnings + errors
        val parts = mutableListOf<String>()

        // In a playlist, an ERROR that is not about post-processing is an item
        // yt-dlp skipped (private, removed, region-locked...).
        val failedItems = errors.count { !it.startsWith("Postprocessing") && !it.contains("thumbnail", ignoreCase = true) }
        if (playlist && failedItems > 0) {
            parts += "$failedItems öğe indirilemedi (gizli, kaldırılmış ya da erişilemiyor olabilir)."
        }

        val failedLanguages = all.flatMap { line ->
            SUBTITLE_LANG.findAll(line).map { it.groupValues[1] }.toList() +
                SKIPPED_SUBTITLE.findAll(line).map { it.groupValues[1] }.toList()
        }.distinct()
        if (failedLanguages.isNotEmpty()) {
            val names = failedLanguages.joinToString(", ") { languageName(it) }
            val rateLimited = all.any { it.contains("429") }
            parts += if (rateLimited) {
                "$names altyazı indirilemedi; YouTube çok fazla istek dedi (429)."
            } else {
                "$names altyazı indirilemedi."
            }
        } else if (all.any { it.contains("subtitle", ignoreCase = true) && !it.contains("impersonat") }) {
            parts += "Bazı altyazılar gömülemedi."
        }

        val errorText = errors.joinToString(" ")
        when {
            errorText.contains("thumbnail", ignoreCase = true) -> parts += "Kapak resmi gömülemedi."
            errorText.contains("metadata", ignoreCase = true) -> parts += "Etiketler yazılamadı."
            errorText.contains("Postprocessing") && parts.isEmpty() -> parts += "Bir son işlem adımı başarısız oldu."
        }
        if (parts.isEmpty()) return null
        return parts.joinToString(" ") + " Dosya yine de kaydedildi."
    }

    private fun languageName(code: String): String {
        val turkish = java.util.Locale.forLanguageTag("tr")
        val name = java.util.Locale.forLanguageTag(code).getDisplayLanguage(turkish)
        return if (name.isNullOrBlank() || name == code) code else name.replaceFirstChar { it.uppercase(turkish) }
    }

    // -- history -----------------------------------------------------------

    private fun addHistory(entry: HistoryEntry) {
        _history.value = listOf(entry) + _history.value.filterNot { it.id == entry.id }
        historyStore.save(_history.value)
    }

    fun deleteHistory(entry: HistoryEntry) {
        _history.value = _history.value.filterNot { it.id == entry.id }
        historyStore.save(_history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        historyStore.save(_history.value)
    }

    // -- plumbing ----------------------------------------------------------

    private fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }

    private fun syncService() {
        val active = _tasks.value.filter { it.state.isActive }
        val intent = Intent(context, DownloadService::class.java)
        if (active.isEmpty()) {
            runCatching { context.stopService(intent) }
            return
        }
        val leader = active.firstOrNull { it.state != DownloadState.QUEUED } ?: active.first()
        intent.putExtra(DownloadService.EXTRA_TITLE, leader.spec.title)
        intent.putExtra(
            DownloadService.EXTRA_TEXT,
            buildString {
                append(if (leader.state == DownloadState.QUEUED) "Sırada" else leader.stage.label)
                leader.progress?.let { append(" · %").append((it * 100).toInt()) }
                leader.speed?.let { append(" · ").append(it) }
                if (active.size > 1) append(" · +${active.size - 1} sırada")
            },
        )
        intent.putExtra(
            DownloadService.EXTRA_PERCENT,
            leader.progress?.let { (it * 100).toInt() } ?: -1,
        )
        runCatching { context.startService(intent) }
            .onFailure { Log.w("DownloadRepository", "foreground service refused", it) }
    }

}

private val SUBTITLE_LANG = Regex("""subtitles for '([^']+)'""")
private val SKIPPED_SUBTITLE = Regex("""Skipping embedding ([A-Za-z-]+) subtitle""")

internal fun DownloadState.labelTr(): String = when (this) {
    DownloadState.QUEUED -> "Sırada"
    DownloadState.RUNNING -> "İndiriliyor"
    DownloadState.PROCESSING -> "İşleniyor"
    DownloadState.SAVING -> "Kaydediliyor"
    DownloadState.DONE -> "Tamamlandı"
    DownloadState.FAILED -> "Başarısız"
    DownloadState.CANCELED -> "İptal edildi"
}
