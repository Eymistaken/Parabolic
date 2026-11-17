package dev.eymistaken.parabolic.core

/**
 * Turns yt-dlp's chatty stdout into what the UI actually wants: a short stage
 * label, one monotonic overall progress value, the playlist position, and a log
 * tail for the "Ayrıntılar" panel.
 *
 * A merged video is two downloads (video stream, then audio stream) and yt-dlp
 * restarts its percentage for each, so the parts are weighted into one bar. A
 * playlist restarts everything per item; the bar then spans the whole list.
 */
class ProgressTracker(private val kind: MediaKind) {
    var stage: DownloadStage = DownloadStage.PREPARING
        private set
    var overall: Float? = null
        private set
    var speed: String? = null
        private set
    var size: String? = null
        private set
    /** 1-based playlist position and length; 0 when not a playlist. */
    var itemIndex: Int = 0
        private set
    var itemCount: Int = 0
        private set

    private var part = 0
    private var separateStreams = false
    private var itemProgress = 0f
    private val log = ArrayDeque<String>()
    private var lastLineWasProgress = false

    fun consume(rawLine: String, percent: Float): Boolean {
        val line = rawLine.trim()
        if (line.isEmpty()) return false
        val before = stage
        val beforeItem = itemIndex
        val isProgress = line.startsWith("[download]") && line.contains('%')
        appendLog(line, isProgress)

        ITEM.find(line)?.let { match ->
            itemIndex = match.groupValues[1].toIntOrNull() ?: itemIndex
            itemCount = match.groupValues[2].toIntOrNull() ?: itemCount
            // A new item starts from scratch.
            part = 0
            separateStreams = false
            itemProgress = 0f
            size = null
            stage = DownloadStage.PREPARING
        }

        when {
            line.startsWith("[download] Destination:") -> {
                part++
                separateStreams = separateStreams || STREAM_SUFFIX.containsMatchIn(line)
                stage = when {
                    kind == MediaKind.AUDIO -> DownloadStage.DOWNLOADING
                    separateStreams && part == 1 -> DownloadStage.VIDEO
                    separateStreams && part >= 2 -> DownloadStage.AUDIO
                    else -> DownloadStage.DOWNLOADING
                }
            }
            line.startsWith("[Merger]") -> stage = DownloadStage.MERGING
            line.startsWith("[ExtractAudio]") ||
                line.startsWith("[VideoConvertor]") ||
                line.startsWith("[VideoRemuxer]") -> stage = DownloadStage.CONVERTING
            line.startsWith("[Metadata]") ||
                line.startsWith("[EmbedThumbnail]") ||
                line.startsWith("[EmbedSubtitle]") ||
                line.startsWith("[ThumbnailsConvertor]") -> stage = DownloadStage.TAGGING
            line.startsWith("[SponsorBlock]") ||
                line.startsWith("[ModifyChapters]") -> stage = DownloadStage.SPONSORBLOCK
        }

        if (stage.isPostProcessing) {
            speed = null
            itemProgress = 1f
        } else if (isProgress && percent >= 0f) {
            if (stage == DownloadStage.PREPARING) stage = DownloadStage.DOWNLOADING
            val p = (percent / 100f).coerceIn(0f, 1f)
            val next = when {
                separateStreams && part <= 1 -> p * VIDEO_WEIGHT
                separateStreams -> VIDEO_WEIGHT + p * (1f - VIDEO_WEIGHT)
                else -> p
            }
            itemProgress = maxOf(itemProgress, next)
            SPEED.find(line)?.groupValues?.getOrNull(1)?.let { speed = it }
            SIZE.find(line)?.groupValues?.getOrNull(1)?.let { if (part <= 1) size = it }
        }

        overall = when {
            // A playlist always shows how far through the list it is.
            itemCount > 0 -> {
                val whole = ((itemIndex - 1).coerceAtLeast(0) + itemProgress) / itemCount
                maxOf(overall ?: 0f, whole.coerceIn(0f, 1f))
            }
            // A single file shows the calm indeterminate bar while post-processing.
            stage.isPostProcessing -> null
            stage == DownloadStage.PREPARING -> overall
            else -> maxOf(overall ?: 0f, itemProgress)
        }
        return stage != before || itemIndex != beforeItem
    }

    fun snapshotLog(): List<String> = log.toList()

    private fun appendLog(line: String, isProgress: Boolean) {
        if (isProgress && lastLineWasProgress && log.isNotEmpty()) {
            log.removeLast()
        }
        log.addLast(line)
        while (log.size > MAX_LOG) log.removeFirst()
        lastLineWasProgress = isProgress
    }

    private companion object {
        /** The video stream is almost always the bulk of the bytes. */
        const val VIDEO_WEIGHT = 0.88f
        const val MAX_LOG = 160
        val ITEM = Regex("""Downloading (?:item|video) (\d+) of (\d+)""")
        val STREAM_SUFFIX = Regex("""\.f[0-9]+(-[0-9]+)?\.[A-Za-z0-9]+$""")
        val SPEED = Regex("""at\s+([0-9.]+\s?[KMGT]?i?B/s)""")
        val SIZE = Regex("""of\s+~?\s*([0-9.]+\s?[KMGT]?i?B)""")
    }
}

val DownloadStage.isPostProcessing: Boolean
    get() = this == DownloadStage.MERGING ||
        this == DownloadStage.CONVERTING ||
        this == DownloadStage.TAGGING ||
        this == DownloadStage.SPONSORBLOCK ||
        this == DownloadStage.SAVING
